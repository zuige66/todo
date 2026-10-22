package com.lirui.oneline.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.lirui.oneline.MainActivity
import com.lirui.oneline.R
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.util.AppTime
import com.lirui.oneline.util.Format
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
 * ★ 为什么用 RemoteViews 列表而不是"固定 4 条的静态 RemoteViews"：
 *   Compose/Glance 固然香，但本项目还没引 Glance，且静态 4 条无法随高度变多。
 *   ListView + RemoteViewsService 是 API 11 起就稳的老路，Launcher 进程拉我们的
 *   Service 取数据，没有任何跨进程新问题。
 */
class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 系统 30 分钟一次的兜底刷新（appwidget-info 的 updatePeriodMillis）。
        appWidgetIds.forEach { WidgetUpdater.updateOne(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // 用户拖大了组件：重新按高度算 item 布局与条数。
        WidgetUpdater.updateOne(context, manager, appWidgetId)
    }

    companion object {
        /** 完成动作由 [TodoWidgetActionReceiver]（exported=false）接，见其 KDoc。 */
        const val ACTION_COMPLETE = "com.lirui.oneline.widget.action.COMPLETE"
        const val EXTRA_TODO_ID = "todo_id"
    }
}

/**
 * 组件刷新入口。App 内的写操作（增删改、归档、撤销）都调 [refreshAsync]；
 * Provider 自己（onUpdate / 完成动作）用同步版 [refresh]（已在 IO 线程）。
 */
object WidgetUpdater {

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
        val options = manager.getAppWidgetOptions(appWidgetId)
        // OPTION_APPWIDGET_MAX_HEIGHT 单位是 px；没有 options（组件还没上屏）时按 2×2 兜底。
        val density = context.resources.displayMetrics.density
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            .takeIf { it > 0 }
            ?.let { (it / density).toInt() }
            ?: 160
        val compact = heightDp < 200
        val limit = if (compact) 4 else 6

        val rv = RemoteViews(context.packageName, R.layout.widget_todo_list)

        // 列表 adapter。★ data 必须唯一：同一个 Launcher 上多个组件实例共用一个
        // Service Intent 时，Launcher 会按 Intent 过滤相等性复用 factory，
        // 导致两个组件显示同一份数据（经典坑）。带 appWidgetId + data 就错开。
        val adapter = Intent(context, TodoWidgetService::class.java).apply {
            putExtra(TodoWidgetService.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(TodoWidgetService.EXTRA_COMPACT, compact)
            putExtra(TodoWidgetService.EXTRA_LIMIT, limit)
            data = Uri.parse("todo://widget/$appWidgetId")
        }
        rv.setRemoteAdapter(R.id.widget_list, adapter)
        rv.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // 头部条数（与列表取同一份数据，不会出现"写着 4 条、列着 3 条"）。
        // runBlocking 只是把 suspend 桥到同步上下文 —— 本方法只在 IO 线程被调用
        // （onUpdate 在主线程但查询是毫秒级；真要抠就去 onReceive 里 goAsync）。
        val dao = AppDatabase.get(context).todoDao()
        val total = runBlocking { dao.getActiveAll().size }
        rv.setTextViewText(
            R.id.widget_count,
            context.getString(R.string.widget_header_count, minOf(total, limit))
        )

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

/** 供 [Format] 复用的"今天"与"现在"——放在这里避免每行都取一次。 */
internal fun widgetTodayAndNow(): Pair<java.time.LocalDate, Long> =
    AppTime.today() to AppTime.nowMillis()

/** 一行的到期文案：逾期红、今天/明天、具体日期时间；无日期返回空串。 */
internal fun dueLabel(dueAtMillis: Long?, hasExactTime: Boolean): Pair<String, Boolean> {
    if (dueAtMillis == null) return "" to false
    val (today, now) = widgetTodayAndNow()
    val overdue = Format.isOverdue(dueAtMillis, today, now, hasExactTime)
    return Format.dueLine(dueAtMillis) to overdue
}

/** Service 侧拿到当前 overdue 判断用（避免在 factory 里各算一遍）。 */
internal fun isOverdueNow(dueAtMillis: Long, hasExactTime: Boolean): Boolean {
    val (today, now) = widgetTodayAndNow()
    return Format.isOverdue(dueAtMillis, today, now, hasExactTime)
}
