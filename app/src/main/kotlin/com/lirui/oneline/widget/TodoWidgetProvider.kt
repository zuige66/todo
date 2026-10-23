package com.lirui.oneline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.lirui.oneline.MainActivity
import com.lirui.oneline.R
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.data.model.Todo
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.Format
import com.lirui.oneline.util.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 桌面小组件：一个 provider，纵横都可拉伸，按高度切换两种行布局。
 *
 * - 高 < 200dp（2×2 / 4×2）：紧凑行，最多 4 条
 * - 否则（2×4 / 4×4）：标准行，最多 6 条
 *
 * ★ 架构（v0.8.1 重构）：**静态多行 RemoteViews**（removeAllViews + addView），
 *   不是 ListView + RemoteViewsService。
 *   原因：vivo 桌面（com.bbk.launcher2）实测从不 bind 数据服务——onDataSetChanged
 *   一次都不跑，列表恒空且无任何报错。行数本来就是固定的（4/6 条），静态塞行
 *   完全覆盖需求，还消灭了 service/bind/factory 这一整层 ROM 兼容性问题。
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
        // 用户拖大了组件：重新按高度算行布局与条数。同样要在 IO 上查库。
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

    fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        try {
            val options = manager.getAppWidgetOptions(appWidgetId)
            // OPTION_APPWIDGET_MAX_HEIGHT 单位是 px；没有 options（组件还没上屏）时按 2×2 兜底。
            val density = context.resources.displayMetrics.density
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                .takeIf { it > 0 }
                ?.let { (it / density).toInt() }
                ?: 160
            val compact = heightDp < 200
            val limit = if (compact) 4 else 6

            // 行内容可配（zuige）：紧凑行默认只显示事件（2×2 宽度小，日期会挤掉标题）；
            // 标准行默认事件+日期。头部（「待办」+ 条数）也可隐藏。
            val showDate = if (compact) SettingsRepo.widgetCompactShowDate(context)
            else SettingsRepo.widgetStandardShowDate(context)
            val hideHeader = SettingsRepo.widgetHideHeader(context)

            val dao = AppDatabase.get(context).todoDao()
            val todos = runBlocking { dao.getActiveAll().take(limit) }

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
                rv.setViewVisibility(R.id.widget_rows, View.GONE)
                rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.widget_rows, View.VISIBLE)
                rv.setViewVisibility(R.id.widget_empty, View.GONE)
                todos.forEach { todo ->
                    rv.addView(R.id.widget_rows, buildRow(context, todo, compact, showDate))
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
            Log.d(TAG, "updateOne id=$appWidgetId compact=$compact limit=$limit rows=${todos.size}")
        } catch (error: Throwable) {
            // 组件更新失败绝不能带崩广播（上次主线程查库就是这么炸出"空白组件"的）。
            Log.e(TAG, "updateOne id=$appWidgetId failed", error)
        }
    }

    /**
     * 构建一行。两个 PendingIntent 挂不同 view：
     * 整行 = 打开 App；圆圈 = 完成广播（独立 receiver，见 TodoWidgetActionReceiver）。
     * [showDate] = false 时隐藏日期列（2×2 默认，事件占满一行）。
     */
    private fun buildRow(
        context: Context,
        todo: Todo,
        compact: Boolean,
        showDate: Boolean
    ): RemoteViews {
        val rv = RemoteViews(
            context.packageName,
            if (compact) R.layout.widget_item_todo_compact else R.layout.widget_item_todo
        )
        rv.setTextViewText(R.id.widget_title, todo.title)

        // 到期文案 + 逾期标红。无日期（dueAtMillis == null）不显示右侧文字。
        val due = todo.dueAtMillis
        if (due != null && showDate) {
            val (label, overdue) = dueLabel(due, todo.hasExactTime)
            rv.setTextViewText(R.id.widget_due, label)
            if (overdue) {
                rv.setTextColor(R.id.widget_due, context.getColor(R.color.widget_overdue))
            }
        } else {
            rv.setViewVisibility(R.id.widget_due, View.GONE)
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

/** 一行的到期文案：是否逾期。无日期返回空串 + false。 */
internal fun dueLabel(dueAtMillis: Long?, hasExactTime: Boolean): Pair<String, Boolean> {
    if (dueAtMillis == null) return "" to false
    val today = AppTime.today()
    val now = AppTime.nowMillis()
    val overdue = Format.isOverdue(dueAtMillis, today, now, hasExactTime)
    return Format.dueLine(dueAtMillis) to overdue
}
