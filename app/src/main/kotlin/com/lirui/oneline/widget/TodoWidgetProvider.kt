package com.lirui.oneline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.lirui.oneline.MainActivity
import com.lirui.oneline.R
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.data.model.Todo
import com.lirui.oneline.ui.theme.Palettes
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.Format
import com.lirui.oneline.util.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 桌面小组件：一个 provider，纵横都可拉伸，**恒显示最近 4 条**。
 *
 * ★ 2026-10-23 起**不再按组件尺寸切布局**：vivo 桌面（com.bbk.launcher2）报的
 *   OPTION_APPWIDGET_MIN/MAX_HEIGHT 恒为 152px，2×2 和 2×4 一模一样 —— 按高度判档位
 *   必然判错（用户把组件拉到 2×4，设置里还说"当前是 2×2"）。
 *   现在日期怎么显示由设置里的三个开关决定，与尺寸无关：
 *     widget_show_date / widget_date_below / widget_date_only（见 SettingsRepo）
 *
 * ★ 架构（v0.8.1 重构）：**静态多行 RemoteViews**（removeAllViews + addView），
 *   不是 ListView + RemoteViewsService。
 *   原因：vivo 桌面实测从不 bind 数据服务——onDataSetChanged 一次都不跑，列表恒空
 *   且无任何报错。行数本来就是固定的（4 条），静态塞行完全覆盖需求，
 *   还消灭了 service/bind/factory 这一整层 ROM 兼容性问题。
 *   ★ 每次更新必须 `removeAllViews` 再 `addView`：桌面会复用视图树，addView 是追加，
 *     漏了清空就会堆出多余的历史行（表现为"日期关了还在""行数对不上"）。
 */
class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 系统 30 分钟一次的兜底刷新（appwidget-info 的 updatePeriodMillis）。
        // ★ onUpdate 在主线程；updateOne 里要查 Room —— Room 禁止主线程访问，
        //   必须在 IO 上跑。goAsync 把广播生命周期拉到协程结束。
        val pendingResult = goAsync()
        WidgetUpdater.ioScope.launch {
            try {
                appWidgetIds.forEach { WidgetUpdater.updateOne(context, manager, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // 用户拖大了组件：重新按高度选行布局（条数恒 4）。同样要在 IO 上查库。
        val pendingResult = goAsync()
        WidgetUpdater.ioScope.launch {
            try {
                WidgetUpdater.updateOne(context, manager, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** 完成动作由 [TodoWidgetActionReceiver]（exported=false）接，见其 KDoc。 */
        const val ACTION_COMPLETE = "com.lirui.oneline.widget.action.COMPLETE"
        const val EXTRA_TODO_ID = "todo_id"
    }
}

/**
 * 组件刷新入口。App 内的写操作（增删改、归档、撤销）都调 [refreshAsync]；
 * Provider 自己（onUpdate / 完成动作）在 IO 协程里直接跑 [refresh]。
 */
object WidgetUpdater {

    private const val TAG = "TodoWidget"

    /** fire-and-forget 的 IO 作用域。写库后刷新组件不应拖累调用方（可能是主线程）。 */
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAsync(context: Context) {
        ioScope.launch { refresh(context) }
    }

    /** 同步刷新全部组件。**禁止在主线程调用**（内部同步查 Room）。 */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, TodoWidgetProvider::class.java))
        if (ids.isEmpty()) return
        ids.forEach { updateOne(context, manager, it) }
    }

    /**
     * 组件最多显示几条待办。**固定 4 条**（zuige 2026-10-23 要求）：
     * 不随尺寸、也不随「隐藏头部」变化 —— 隐藏头部只省 26dp，不该变成"多显示一条"。
     */
    private const val MAX_ROWS = 4

    // ---------- 「两帧归档」的瞬时状态 ----------
    //
    // RemoteViews **没有任何动画 API**（没有 ViewPropertyAnimator、没有 ObjectAnimator，
    // 系统只接受"整棵视图树的一次快照"）。所以组件里的归档反馈只能靠两次
    // updateAppWidget 拼出来：
    //   第 1 帧（t=0）    ：这条画成"已勾选"（圆圈里出现勾 + 标题变灰）→ 点击有即时反馈
    //   第 2 帧（t=+300ms）：真正归档并刷新 → 这一行消失，下面的补位
    // 中间这 300ms 就是"动效"：用户看到勾亮起，再看到行被收走。
    //
    // 状态放在进程内的 @Volatile 字段，而不是 SharedPreferences：一次归档的生命周期
    // 只有 300ms，落盘既慢又没必要；万一进程在这中间被杀，[COMPLETING_TTL_MS] 的
    // 时间窗保证下次读到的旧 id 会被忽略（不会把某条永远画成已勾选）。

    /** 第 1 帧与第 2 帧之间的间隔。太短桌面来不及重绘，太长点击会"发木"。 */
    const val TICK_MILLIS = 300L

    /** 瞬时状态的有效期。超过它说明第 2 帧没跑成，当作没有。 */
    private const val COMPLETING_TTL_MS = 2_000L

    @Volatile
    private var completingId: Long = -1L

    @Volatile
    private var completingAt: Long = 0L

    /** 标记"这条正在播放归档"，下一次 [refresh] 会把它画成已勾选。 */
    fun markCompleting(id: Long) {
        completingId = id
        completingAt = SystemClock.elapsedRealtime()
    }

    fun clearCompleting() {
        completingId = -1L
    }

    private fun isCompleting(id: Long): Boolean =
        id == completingId && SystemClock.elapsedRealtime() - completingAt < COMPLETING_TTL_MS

    fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        try {
            // ★ 不再读 AppWidgetOptions 判尺寸：vivo 桌面报的 MIN/MAX_HEIGHT 恒为 152px，
            //   2×2 和 2×4 一模一样（实测），按高度分档必然判错。
            //   日期怎么显示完全由设置里的三个开关决定，与尺寸无关。
            val showDate = SettingsRepo.widgetShowDate(context)
            val dateBelow = SettingsRepo.widgetDateBelow(context)
            val dateOnly = SettingsRepo.widgetDateOnly(context)
            val hideHeader = SettingsRepo.widgetHideHeader(context)

            val dao = AppDatabase.get(context).todoDao()
            val todos = runBlocking { dao.getActiveAll().take(MAX_ROWS) }

            val rv = RemoteViews(context.packageName, R.layout.widget_todo_list)
            rv.setTextViewText(
                R.id.widget_count,
                context.getString(R.string.widget_header_count, todos.size)
            )
            rv.setViewVisibility(
                R.id.widget_header,
                if (hideHeader) View.GONE else View.VISIBLE
            )

            if (todos.isEmpty()) {
                rv.removeAllViews(R.id.widget_rows)
                rv.setViewVisibility(R.id.widget_rows, View.GONE)
                rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.widget_rows, View.VISIBLE)
                rv.setViewVisibility(R.id.widget_empty, View.GONE)
                // ★★ 必须先清空容器再 addView。桌面（vivo 实测）会复用已 inflate 的视图树，
                //    addView 是**追加**而不是替换 —— 少了这句，历史更新的行会一直堆在下面：
                //    于是"日期已经关掉"的新行在前、还带着日期的旧行在后（被裁到可视区边缘），
                //    看起来就是「设置里关了，组件上还是显示时间」，行数也会多出来。
                //    类注释里一直写着 removeAllViews + addView，实现里却漏了这一句。
                rv.removeAllViews(R.id.widget_rows)
                todos.forEach { todo ->
                    rv.addView(
                        R.id.widget_rows,
                        buildRow(
                            context,
                            todo,
                            showDate,
                            dateBelow,
                            dateOnly,
                            done = isCompleting(todo.id)
                        )
                    )
                }
            }

            // 点头部（列表以外的空白也算）→ 打开 App。
            val openApp = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widget_header, openApp)
            rv.setOnClickPendingIntent(R.id.widget_empty, openApp)

            manager.updateAppWidget(appWidgetId, rv)
            Log.d(
                TAG,
                "updateOne id=$appWidgetId rows=${todos.size} showDate=$showDate " +
                    "dateBelow=$dateBelow dateOnly=$dateOnly hideHeader=$hideHeader " +
                    "done=${completingId}"
            )
        } catch (error: Throwable) {
            // 组件更新失败绝不能带崩广播（上次主线程查库就是这么炸出"空白组件"的）。
            Log.e(TAG, "updateOne id=$appWidgetId failed", error)
        }
    }

    /**
     * 构建一行。两个 PendingIntent 挂不同 view：
     * 整行 = 打开 App；圆圈 = 完成广播（独立 receiver，见 TodoWidgetActionReceiver）。
     *
     * 三张布局按设置选（**不再按组件尺寸**，vivo 报的尺寸不可信）：
     *  - [showDate]=false      → widget_item_todo_nodate（布局里根本没有 widget_due，
     *                            不靠 setViewVisibility —— vivo 复用行视图时会藏不住）
     *  - [dateBelow]=true      → widget_item_todo_below（日期在标题下方，**事件优先**）
     *  - 否则                   → widget_item_todo（日期在右侧，**日期优先**）
     *
     * [done]=true 是归档第 1 帧：圆圈换成"带勾的圈"、标题转灰，300ms 后整行消失。
     */
    private fun buildRow(
        context: Context,
        todo: Todo,
        showDate: Boolean,
        dateBelow: Boolean,
        dateOnly: Boolean,
        done: Boolean = false
    ): RemoteViews {
        val layout = when {
            !showDate -> R.layout.widget_item_todo_nodate
            dateBelow -> R.layout.widget_item_todo_below
            else -> R.layout.widget_item_todo
        }
        val rv = RemoteViews(context.packageName, layout)
        rv.setTextViewText(R.id.widget_title, todo.title)

        // 圆圈描边跟随 App 里选的配色（设置 → 外观 → 配色）。
        // shape 是空心圆，setColorFilter 会整体着色 → 描边变成当前主题主色。
        // 已勾选态（widget_circle_done 是单色 vector）同样靠这个 filter 上色。
        rv.setInt(
            R.id.widget_circle,
            "setColorFilter",
            Palettes.byId(SettingsRepo.paletteId(context)).primary.toArgb()
        )
        if (done) {
            rv.setImageViewResource(R.id.widget_circle, R.drawable.widget_circle_done)
            // 标题转灰 = "已经处理掉了"，和 App 里 progress 把标题淡化的观感对齐。
            rv.setTextColor(
                R.id.widget_title,
                context.getColor(R.color.widget_text_secondary)
            )
        }

        // 到期文案 + 逾期标红。无日期（dueAtMillis == null）时右侧留空。
        // 组件**不带星期**（zuige 2026-10-23：只要日期），故 showWeekday 恒 false。
        val due = todo.dueAtMillis
        if (due != null && showDate) {
            val (label, overdue) = dueLabel(due, todo.hasExactTime, showTime = !dateOnly)
            rv.setTextViewText(R.id.widget_due, label)
            rv.setTextColor(
                R.id.widget_due,
                context.getColor(
                    if (overdue) R.color.widget_overdue else R.color.widget_text_secondary
                )
            )
        }

        // 整行 → 打开 App（带 id，先留着给将来的"跳到该待办"）。
        // requestCode 用 todo.id：不同行的 PendingIntent 不能互相复用，否则 extras 串。
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra("todoId", todo.id)
            data = Uri.parse("todo://open/${todo.id}")
        }
        rv.setOnClickPendingIntent(
            R.id.widget_row,
            PendingIntent.getActivity(
                context,
                todo.id.toInt(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // 行首圆圈 → 直接完成（显式 ComponentName 投递给 exported=false 的 receiver）。
        val complete = Intent(context, TodoWidgetActionReceiver::class.java).apply {
            action = TodoWidgetProvider.ACTION_COMPLETE
            putExtra(TodoWidgetProvider.EXTRA_TODO_ID, todo.id)
            data = Uri.parse("todo://complete/${todo.id}")
        }
        rv.setOnClickPendingIntent(
            R.id.widget_circle,
            PendingIntent.getBroadcast(
                context,
                todo.id.toInt(),
                complete,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return rv
    }
}

/** Repository 的进程内单例（widget 侧专用，避免每个广播都新建）。 */
object TodoWidgetRepo {
    @Volatile
    private var instance: com.lirui.oneline.data.TodoRepository? = null

    fun get(context: Context): com.lirui.oneline.data.TodoRepository =
        instance ?: synchronized(this) {
            instance ?: com.lirui.oneline.data.TodoRepository(context.applicationContext)
                .also { instance = it }
        }
}

/**
 * 一行的到期文案：是否逾期。无日期返回空串 + false。
 *
 * 组件里**不带「周几」**（zuige 2026-10-23：只要日期），所以这里恒传 `showWeekday = false`；
 * App 列表仍用 `Format.dueLine` 的默认值（带星期）。
 * [showTime]=false 时只到日期（「10月23日」）—— 对应设置里的「只显示日期」开关。
 */
internal fun dueLabel(
    dueAtMillis: Long?,
    hasExactTime: Boolean,
    showTime: Boolean = true
): Pair<String, Boolean> {
    if (dueAtMillis == null) return "" to false
    val today = AppTime.today()
    val now = AppTime.nowMillis()
    val overdue = Format.isOverdue(dueAtMillis, today, now, hasExactTime)
    return Format.dueLine(dueAtMillis, showWeekday = false, showTime = showTime) to overdue
}
