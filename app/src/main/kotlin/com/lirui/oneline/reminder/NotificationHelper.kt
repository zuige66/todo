package com.lirui.oneline.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lirui.oneline.MainActivity
import com.lirui.oneline.R
import com.lirui.oneline.util.Quotes

object NotificationHelper {

    const val CHANNEL_REMINDER = "reminders"

    /**
     * Fixed id for the "发送测试通知" button — it isn't tied to any todo.
     * Real reminders use [reminderId] so several can coexist.
     */
    private const val NOTIFICATION_ID_TEST = 1001

    /** 「完成」态的正文，正式提醒与测试通知共用一份，避免两处写法漂移。 */
    private const val DONE_TEXT = "已完成 · 点「撤回」可恢复"

    // ── 标题装饰 emoji ────────────────────────────────────────────────────────
    // 选型原则：只用老 Unicode（≤ 9.0）、绝不用 ZWJ 组合（👨‍💻 这类在多数字体里会碎成
    // 两三个字符），并且最多各两个 —— 一个 emoji 约 1.2 个汉字宽，多了会把待办名挤没。
    private const val EMOJI_TODO = "🚀🚀"
    private const val EMOJI_TIME = "⏳⏳"
    private const val EMOJI_DONE = "🎉🎉"

    /** Action ids handled by [NotificationActionReceiver]. */
    const val ACTION_COMPLETE = "com.lirui.oneline.action.COMPLETE"
    const val ACTION_UNDO = "com.lirui.oneline.action.UNDO"
    const val EXTRA_TODO_ID = "todoId"
    const val EXTRA_MESSAGE = "message"

    /**
     * Marks an action intent as coming from the *test* notification.
     *
     * 测试通知没有对应的待办，收到这些动作时只能翻转通知本身的状态，
     * 绝不能去动数据库 —— 否则会拿一个不存在的 id 去 archive。
     */
    const val EXTRA_IS_TEST = "isTest"
    const val EXTRA_TITLE = "title"
    const val EXTRA_QUOTE = "quote"

    /**
     * Per-todo notification id. The previous code reused ONE constant for every reminder,
     * so two todos going off at the same instant silently overwrote each other and the user
     * only ever saw the last one. Ids stay well clear of [NOTIFICATION_ID_TEST].
     */
    private fun reminderId(todoId: Long): Int = (200_000 + todoId % 1_000_000).toInt()

    fun init(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "待办提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "到期的待办提醒" }
        nm.createNotificationChannel(reminder)
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Post a reminder notification. Requires [canPostNotifications]; silently no-ops if denied
     * (excluding: exact-alarm scheduling still runs; only display is suppressed).
     *
     * The action button lives in the system's action row, which StatusBar lays out from the
     * leading edge — its position (left) is not something an app can override, only a fully
     * custom `RemoteViews` layout could, at the cost of losing the stock notification styling.
     *
     * @param done renders the "already completed" state of the SAME notification, whose only
     *   action becomes 「撤回」. Tapping 「完成」 re-posts this method with `done = true` on the
     *   same stable id, which is what makes the button "flip" in place instead of stacking a
     *   second notification.
     * @param silent suppresses sound/vibration/heads-up for this update. Used when the user
     *   undoes a completion: the notification must stay (and flip back to 「完成」) but there is
     *   no reason to alert them again for an action they just took.
     */
    fun postReminder(
        context: Context,
        todoId: Long,
        title: String,
        message: String,
        done: Boolean = false,
        silent: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        // 文案用 todoId 当种子取，**不能用随机**：
        // 「完成 / 撤回」是同一 notification id 的原地重发，随机会让每点一次按钮文案就换一条，
        // 看起来像 bug。同一条待办从生到死必须是同一句。
        val quote = Quotes.forTodo(todoId)
        // 正文（折叠态唯一那一行）放文案。教训：上一版把文案塞进 BigTextStyle，正文留 message，
        // 而折叠态只渲染 contentText，用户看到的还是"该办了"，等于文案根本没出现过。
        val body = if (done) DONE_TEXT else quote
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(decoratedTitle(title, message, done))
            .setContentText(body)
            // 正文本身就是文案，大字区只是给它更多行 —— 折叠态的 contentText 放长句会被
            // 省略号截断，展开后才是完整一句。
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, todoId, done))
            .setAutoCancel(true)
            // The in-place update must not buzz/ring a second time.
            .setOnlyAlertOnce(done || silent)
            .addAction(
                R.drawable.ic_notification,
                if (done) "撤回" else "完成",
                // Carry the original message so 「撤回」 can restore the exact same text.
                actionIntent(context, todoId, if (done) ACTION_UNDO else ACTION_COMPLETE, message)
            )
        if (silent) builder.setSilent(true)
        try {
            NotificationManagerCompat.from(context).notify(reminderId(todoId), builder.build())
        } catch (_: SecurityException) {
            // permission revoked mid-flight; ignore
        }
    }

    /**
     * One-off notification from the settings test button (fixed id, replaces itself).
     *
     * **它故意带一个「完成」按钮。** 早先这里没有 `addAction`，于是"发送测试通知"只能验证
     * "通知能不能弹出来"，验证不了"动作按钮在通知栏里长什么样、点了有没有反应" ——
     * 用户看到的现象就是「正式提醒有完成，测试通知没有」，很容易误判成 ROM 的锅。
     *
     * 按钮只翻转这条通知自己的状态（「完成」⇄「撤回」），不碰数据库，原因见 [EXTRA_IS_TEST]。
     *
     * @param quote 直接进 `setContentText` —— 折叠态那一行就是它。别塞进 BigTextStyle：
     *   折叠态不渲染大字区，等于文案根本没出现过（踩过一次）。
     */
    fun postTest(
        context: Context,
        title: String,
        message: String,
        quote: String? = null,
        done: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        // 与真实提醒完全同一套：标题走 [decoratedTitle]，正文放文案。
        val body = if (done) DONE_TEXT else (quote ?: message)
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(decoratedTitle(title, message, done))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, 0L, done))
            .setAutoCancel(true)
            // 「完成」后的重发不该再响一次，和正式提醒保持一致的取舍。
            .setOnlyAlertOnce(done)
            .addAction(
                R.drawable.ic_notification,
                if (done) "撤回" else "完成",
                testActionIntent(
                    context,
                    if (done) ACTION_UNDO else ACTION_COMPLETE,
                    title,
                    message,
                    quote
                )
            )
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TEST, builder.build())
        } catch (_: SecurityException) {
            // permission revoked mid-flight; ignore
        }
    }

    /**
     * 通知标题的排布：`事件 + 空格 + 🚀🚀 + 状态语 + ⏳⏳`；完成后换成 `事件 + 空格 + 🎉🎉`。
     *
     * 间距是 zuige 定死的：**「事件」与后面 emoji 之间有且只有一个空格**，
     * emoji 与状态语之间不留空格（`开组会 🚀🚀该办了⏳⏳`）。
     * 只影响通知栏；列表里的待办名不带任何装饰。
     *
     * 状态语复用的是 [message]（该办了 / 提前提醒 / 1小时后到期 / 还没完成，别忘了哦），
     * 所以四种提醒类型共用这一套版式，不用各写一遍。
     */
    private fun decoratedTitle(title: String, message: String, done: Boolean): String =
        if (done) "$title $EMOJI_DONE" else "$title $EMOJI_TODO$message$EMOJI_TIME"

    /** Removes a todo's notification (e.g. after undo puts it back in the list). */
    fun cancelReminder(context: Context, todoId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId(todoId))
    }

    /** Tapping the notification body opens the app. */
    private fun openAppIntent(context: Context, todoId: Long, done: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.lirui.oneline.OPEN_TODO"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TODO_ID, todoId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode(todoId, if (done) 2 else 1),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Distinct requestCode per (todo, action): reusing one would let `FLAG_UPDATE_CURRENT`
     * silently rewrite the other action's extras.
     *
     * [message] rides along so 「撤回」 can restore the original reminder text after the action
     * row flips back to 「完成」.
     */
    private fun actionIntent(
        context: Context,
        todoId: Long,
        action: String,
        message: String
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_MESSAGE, message)
        }
        val slot = if (action == ACTION_UNDO) 4 else 3
        return PendingIntent.getBroadcast(
            context,
            requestCode(todoId, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(todoId: Long, slot: Int): Int =
        (todoId % 100_000L * 10L + slot).toInt()

    /**
     * 测试通知的动作意图。
     *
     * 单独一套 requestCode（900_005 / 900_006）。真实待办只用 1~4 号 slot
     * （1=打开未完成、2=打开已完成、3=完成、4=撤回），算出来的 requestCode 尾数必然是 1~4，
     * 所以尾数 5/6 的这两个**结构上撞不到**真实待办。
     *
     * 更要紧的是带上标题 / 正文 / 文案三个 extra：测试通知没有数据库记录可回查，
     * 而「撤回」必须能把原文案一字不差地重发出来。
     */
    private fun testActionIntent(
        context: Context,
        action: String,
        title: String,
        message: String,
        quote: String?
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_IS_TEST, true)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_QUOTE, quote)
        }
        val slot = if (action == ACTION_UNDO) 6 else 5
        return PendingIntent.getBroadcast(
            context,
            900_000 + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
