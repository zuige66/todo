package com.lirui.oneline.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 一条可用的更新。 */
data class AppUpdate(
    /** 远端版本号，已去掉 `v` 前缀。 */
    val version: String,
    val changelog: String,
    /** GitHub 发布页（浏览器兜底入口）。 */
    val releaseUrl: String,
    /** APK 直链；为空表示这次 release 没挂 APK 资产，只能走 [releaseUrl]。 */
    val apkUrl: String
)

/**
 * 检查更新的结果。
 *
 * 刻意做成 sealed 而不是 `AppUpdate?`：`null` 无法区分"已是最新"和"仓库还没发过版"，
 * 而这两种情况给用户的提示完全不同（前者是正常状态，后者要告诉他"还没发布过版本"）。
 */
sealed interface UpdateCheck {
    /** 已是最新。 */
    data object UpToDate : UpdateCheck

    /** 仓库里还没有任何 release（404）。 */
    data object NoRelease : UpdateCheck

    data class Available(val update: AppUpdate) : UpdateCheck

    /** 检查失败；[reason] 已经是给人看的中文句子。 */
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * 检查更新：GitHub Releases `/releases/latest`。
 *
 * 实现思路借自 `D:/Workplace/Hyper Music Scape Lock` 的 `UpdateService`，
 * 但只保留本项目真实可用的那一层：
 * - 那边还有 blog 静态 `latest.json` 回退源，本项目没有对应站点，**不做伪回退**
 *   （代码里留一个不存在的回退源，等于给"检查失败"多加 5 秒等待）；
 * - 那边比较 versionCode + versionName 双维度，本项目只有 versionName 一条线
 *   （release 的 asset 直接覆盖安装，versionCode 由构建脚本保证单调），所以只比 versionName。
 *
 * 失败分类刻意做细：DNS 污染 / 超时 / TLS / HTTP 状态码是四种不同的病，
 * 统一糊成"检查失败"的话，用户和我都没法判断该改什么。
 */
object UpdateService {

    private const val TAG = "Oneline[Update]"

    /** 发布页（浏览器兜底 / 没有 APK 资产时用）。 */
    const val RELEASES_PAGE = "https://github.com/zuige66/todo/releases"

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/zuige66/todo/releases/latest"

    /**
     * 6s。GitHub API 通的话 1.5s 内必回；6s 不回基本等于被墙/没网，
     * 再等下去只是让按钮一直转圈。
     */
    private const val TIMEOUT_MILLIS = 6_000

    suspend fun check(currentVersion: String): UpdateCheck = withContext(Dispatchers.IO) {
        val response = try {
            fetch(LATEST_RELEASE_API)
        } catch (error: UnknownHostException) {
            Log.w(TAG, "DNS failed: ${error.message}")
            return@withContext UpdateCheck.Failed("连不上服务器，请检查网络后重试")
        } catch (error: SocketTimeoutException) {
            Log.w(TAG, "Timeout: ${error.message}")
            return@withContext UpdateCheck.Failed("连接超时，请稍后重试")
        } catch (error: SSLException) {
            Log.w(TAG, "TLS failed: ${error.message}")
            return@withContext UpdateCheck.Failed("网络被拦截，请换一个网络再试")
        } catch (error: IOException) {
            Log.w(TAG, "IO failed: ${error::class.simpleName}: ${error.message}")
            return@withContext UpdateCheck.Failed("网络异常，请稍后重试")
        }

        when {
            response.code == HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheck.NoRelease
            response.code == 403 || response.code == 429 ->
                UpdateCheck.Failed("请求过于频繁（HTTP ${response.code}），请稍后再试")
            response.code !in 200..299 ->
                UpdateCheck.Failed("服务器返回 HTTP ${response.code}")
            else -> parse(response.body, currentVersion)
        }
    }

    private fun parse(body: String, currentVersion: String): UpdateCheck {
        val json = try {
            JSONObject(body)
        } catch (_: JSONException) {
            return UpdateCheck.Failed("发布信息格式异常")
        }

        val tag = json.optString("tag_name").trim()
        if (tag.isBlank()) return UpdateCheck.Failed("发布信息里没有版本号")

        val remoteVersion = tag.removePrefix("v").removePrefix("V")
        val newer = Version.isNewer(remoteVersion, currentVersion)
            ?: return UpdateCheck.Failed("版本号格式异常：$tag（本机 $currentVersion）")
        if (!newer) return UpdateCheck.UpToDate

        // 取第一个 .apk 资产。本项目每个 release 只挂一个 APK，取第一个即可。
        var apkUrl = ""
        json.optJSONArray("assets")?.let { assets ->
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val url = asset.optString("browser_download_url").orEmpty()
                if (url.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = url
                    break
                }
            }
        }

        val page = json.optString("html_url").ifBlank { RELEASES_PAGE }
        Log.i(TAG, "New version v$remoteVersion (apk=${apkUrl.ifBlank { "none" }})")
        return UpdateCheck.Available(
            AppUpdate(
                version = remoteVersion,
                changelog = json.optString("body").orEmpty(),
                releaseUrl = page,
                apkUrl = apkUrl
            )
        )
    }

    private class RawResponse(val code: Int, val body: String)

    private fun fetch(url: String): RawResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Oneline-Android")
        }
        try {
            val code = connection.responseCode
            // 错误码也要读流：GitHub 的 403/404 body 里有 message，日志里能看出是限流还是仓库改名。
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code: ${body.take(200)}")
            }
            return RawResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}
