package com.lirui.oneline.update

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 「下载并安装」的唯一入口：把下载交给 [UpdateDownloadService]。
 *
 * 为什么不在这里用协程自己下载：下载期间用户会切走 / 锁屏，Activity 的
 * `lifecycleScope` 会随销毁取消，下到一半就断了；前台服务不依赖任何 Activity。
 */
object ApkInstaller {

    fun downloadAndInstall(context: Context, apkUrl: String, version: String) {
        if (apkUrl.isBlank()) {
            Toast.makeText(context, "这条版本没有附 APK，请到发布页手动下载", Toast.LENGTH_LONG).show()
            return
        }
        val fileName = "Oneline-v${version.ifBlank { "update" }}.apk"
        val intent = Intent(context, UpdateDownloadService::class.java).apply {
            putExtra(UpdateDownloadService.EXTRA_URL, apkUrl)
            putExtra(UpdateDownloadService.EXTRA_NAME, fileName)
        }
        try {
            context.startForegroundService(intent)
            Toast.makeText(context, "开始下载，完成后会自动打开安装界面", Toast.LENGTH_LONG).show()
        } catch (error: Throwable) {
            Toast.makeText(
                context,
                "无法启动下载：${error.message ?: "未知错误"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
