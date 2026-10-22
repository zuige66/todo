package com.lirui.oneline.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.FileProvider
import com.lirui.oneline.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新包下载服务（前台服务）。
 *
 * 为什么不用系统 `DownloadManager` + 完成广播：那个广播只能由**本进程**接收，
 * 而 APK 下载期间用户很可能切走/锁屏，进程被回收后广播无人接收 ——
 * "下载完了却不弹安装"。前台服务在下载期间保活，下完在同一个进程里直接拉起安装界面，
 * 链路最短。这套取舍在 Hyper MeloLock 上真机验证过，直接沿用。
 *
 * 与它的差别：那边有三层直链级联（asset API → 浏览器直链 → blog 静态源），
 * 本项目只有一个 GitHub 浏览器直链（`browser_download_url`），所以没有级联。
 * 以后加 blog 源时，在这里补一层 try/catch 即可，UI 不用改。
 */
class UpdateDownloadService : Service() {

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Volatile
    private var cancelled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val fileName = intent?.getStringExtra(EXTRA_NAME) ?: DEFAULT_APK_NAME
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        val started = try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                progressNotification(0),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
            true
        } catch (error: Throwable) {
            // Android 14+ 在后台启动前台服务会抛异常。这里不能崩，退化成普通线程下载
            // （进程被杀的风险由系统决定，但至少不会在用户点"下载"的瞬间闪退）。
            Log.w(TAG, "startForeground failed: ${error::class.simpleName}: ${error.message}")
            false
        }

        Thread {
            val target = File(File(filesDir, UPDATE_DIR).apply { mkdirs() }, fileName)
            try {
                download(url, target)
                if (cancelled) return@Thread
                notifyDone(target, launchInstall(target))
            } catch (error: Throwable) {
                Log.w(TAG, "Download failed: ${error::class.simpleName}: ${error.message}")
                broadcastProgress(-1, done = true)
                notifyFailed()
            } finally {
                if (started) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                }
                stopSelf()
            }
        }.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            val total = connection.contentLength.coerceAtLeast(0)
            var downloaded = 0L
            var lastNotify = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (!cancelled) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > PROGRESS_INTERVAL_MILLIS) {
                            lastNotify = now
                            val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                progressNotification(percent)
                            )
                            // 应用内进度：部分 ROM 会把前台服务通知折叠/降级，
                            // 关于页靠这条明确广播显示百分比。
                            broadcastProgress(percent)
                        }
                    }
                }
            }
            if (cancelled) return
            broadcastProgress(100, done = true)
            Log.i(TAG, "Downloaded ${target.length()} bytes -> ${target.name}")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 拉起系统安装界面。没有「允许安装未知应用」时会抛
     * `ActivityNotFoundException`/`SecurityException`，此时跳对应的设置页做一次性引导。
     */
    private fun launchInstall(apk: File): Boolean {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            startActivity(install)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Cannot open installer: ${error::class.simpleName}: ${error.message}")
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (settingsError: Throwable) {
                Log.w(TAG, "Cannot open unknown-source settings: ${settingsError.message}")
            }
            false
        }
    }

    /** 应用内进度广播：显式 setPackage 到本 App；接收方必须用 RECEIVER_NOT_EXPORTED 注册。 */
    private fun broadcastProgress(percent: Int, done: Boolean = false) {
        sendBroadcast(
            Intent(ACTION_UPDATE_PROGRESS)
                .setPackage(packageName)
                .putExtra(EXTRA_PROGRESS, percent)
                .putExtra(EXTRA_DONE, done)
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "更新下载", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun progressNotification(percent: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("正在下载更新")
        .setContentText(if (percent > 0) "$percent%" else "准备中")
        .setProgress(100, percent, percent <= 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun notifyDone(apk: File, installed: Boolean) {
        notificationManager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(
                    if (installed) "下载完成，请在安装界面确认" else "下载完成，需要允许安装未知应用"
                )
                .setContentText(apk.name)
                .setOngoing(false)
                .build()
        )
    }

    private fun notifyFailed() {
        notificationManager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("下载失败")
                .setContentText("请检查网络后重试，或用浏览器打开发布页手动下载")
                .setOngoing(false)
                .build()
        )
    }

    companion object {
        private const val TAG = "Oneline[Update]"
        private const val CHANNEL_ID = "update_download"
        private const val NOTIFICATION_ID = 9201
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val CONNECT_TIMEOUT_MILLIS = 30_000
        private const val READ_TIMEOUT_MILLIS = 30_000
        private const val PROGRESS_INTERVAL_MILLIS = 300L
        private const val UPDATE_DIR = "update"
        private const val DEFAULT_APK_NAME = "Oneline-update.apk"

        const val EXTRA_URL = "extra_apk_url"
        const val EXTRA_NAME = "extra_apk_name"

        /** 下载进度广播：progress(0..100) + done(是否结束)。 */
        const val ACTION_UPDATE_PROGRESS = "com.lirui.oneline.action.UPDATE_PROGRESS"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_DONE = "extra_done"
    }
}
