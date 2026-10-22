package com.lirui.oneline.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lirui.oneline.MainActivity
import com.lirui.oneline.R
import com.lirui.oneline.data.AppDatabase
import com.lirui.oneline.data.model.Todo
import kotlinx.coroutines.runBlocking

/**
 * Widget 列表的数据服务（跑在本应用进程，由 Launcher 绑定）。
 *
 * 与 UI 的 [com.lirui.oneline.data.TodoRepository] 是两条独立读路径：
 * 这里直接查 DAO 同步取（RemoteViewsFactory 的方法运行在 binder 线程，允许阻塞），
 * 不建 Flow —— 列表只在"组件上屏 / onDataSetChanged"时取一次，常驻订阅没有意义。
 */
class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodoRemoteViewsFactory(applicationContext, intent)

    companion object {
        const val EXTRA_APPWIDGET_ID = "appwidget_id"
        const val EXTRA_COMPACT = "compact"
        const val EXTRA_LIMIT = "limit"
    }
}

private class TodoRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val compact = intent.getBooleanExtra(TodoWidgetService.EXTRA_COMPACT, true)
    private val limit = intent.getIntExtra(TodoWidgetService.EXTRA_LIMIT, 4)
    private val dao = AppDatabase.get(context).todoDao()

    private var items: List<Todo> = emptyList()

    override fun onCreate() = Unit

    /** Launcher 在每次要显示列表前调（上屏、notifyAppWidgetViewDataChanged、组件更新）。 */
    override fun onDataSetChanged() {
        // getActiveAll 的 ORDER BY：COALESCE(dueAtMillis, 极大值) —— 无日期天然排最后，
        // 与首页板块顺序一致，不需要在内存里再排一次。
        items = runBlocking { dao.getActiveAll().take(limit) }
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val todo = items[position]
        val rv = RemoteViews(
            context.packageName,
            if (compact) R.layout.widget_item_todo_compact else R.layout.widget_item_todo
        )
        rv.setTextViewText(R.id.widget_title, todo.title)

        // 到期文案 + 逾期标红。无日期（dueAtMillis == null）不显示右侧文字。
        val due = todo.dueAtMillis
        if (due != null) {
            val (label, overdue) = dueLabel(due, todo.hasExactTime)
            rv.setTextViewText(R.id.widget_due, label)
            if (overdue) {
                rv.setTextColor(R.id.widget_due, context.getColor(R.color.widget_overdue))
            }
        }

        // 整行 → 打开 App（带 id，MainActivity 目前不消费它，先留着给将来的"跳到该待办"）。
        // requestCode 用 todo.id：不同行的 PendingIntent 不能互相复用，否则 extras 串。
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra("todoId", todo.id)
            data = android.net.Uri.parse("todo://open/${todo.id}")
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

        // 行首圆圈 → 直接完成（广播给 Provider，走 Repository.archive）。
        val complete = Intent(context, TodoWidgetProvider::class.java).apply {
            action = TodoWidgetProvider.ACTION_COMPLETE
            putExtra(TodoWidgetProvider.EXTRA_TODO_ID, todo.id)
            data = android.net.Uri.parse("todo://complete/${todo.id}")
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

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = items[position].id

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        items = emptyList()
    }
}
