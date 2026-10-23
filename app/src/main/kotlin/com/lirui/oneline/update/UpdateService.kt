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
 * 检查更新：主源 GitHub Releases `/releases/latest`，兜底源 blog 静态 `latest.json`。
 *
 * 主源 vs 兜底源（借自 `D:/Workplace/Hyper Music Scape Lock` 的 `UpdateService`）：
 * - **主源 GitHub**：权威、实时，连通时一律以它为准，避免被镜像源劫持 / 降级；
 * - **兜底源 blog**：部署在 `blog.zuiges.com`（GitHub Pages 自定义域名，国内通常可直连），
 *   仅当主源「网络不可达」（DNS 污染 / 超时 / TLS 拦截）或「被限流」（403/429）时回退，
 *   取 `latest.json` 的 `versionName` + `apkUrl`（APK 同站托管，国内可直连）；
 * - **下载兜底**：`UpdateDownloadService` 在主源 APK（GitHub 资产 CDN）下载失败时，
 *   会再向 blog 源要一次镜像直链重试（`blogFallbackApkUrl()`）。
 *
 * 只比 versionName 一条线（release 的 asset 直接覆盖安装，versionCode 由构建脚本保证单调）。
 * 失败分类刻意做细：DNS 污染 / 超时 / TLS / HTTP 状态码是四种不同的病，
 * 统一糊成"检查失败"的话，用户和我都没法判断该改什么。
 */
object UpdateService {

    private const val TAG = "Oneline[Update]"

    /** 发布页（浏览器兜底 / 没有 APK 资产时用）。 */
    const val RELEASES_PAGE = "https://github.com/zuige66/todo/releases"

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/zuige66/todo/releases/latest"

    /** blog 静态兜底源：GitHub 被墙 / 限流时回退到这里（同站托管 APK，国内通常可直连）。 */
    private const val BLOG_LATEST_URL =
        "https://blog.zuiges.com/downloads/oneline/latest.json"

    /**
     * 6s。GitHub API 通的话 1.5s 内必回；6s 不回基本等于被墙/没网，
     * 再等下去只是让按钮一直转圈。
     */
    private const val TIMEOUT_MILLIS = 6_000

    suspend fun check(currentVersion: String): UpdateCheck = withContext(Dispatchers.IO) {
        // 主源：GitHub Releases API。连通即以它为准（权威、实时，防镜像劫持/降级）。
        // 只有「网络不可达」（DNS 污染 / 超时 / TLS 拦截）或「被限流」（403/429）才回退 blog。
        val github = runCatching { fetch(LATEST_RELEASE_API) }
        val ghCode = github.getOrNull()?.code
        val shouldFallback = github.isFailure || ghCode == 403 || ghCode == 429

        if (!shouldFallback) {
            val r = github.getOrNull()!!
            return@withContext when {
                r.code == HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheck.NoRelease
                r.code !in 200..299 -> UpdateCheck.Failed("服务器返回 HTTP ${r.code}")
                else -> parse(r.body, currentVersion)
            }
        }

        // 兜底源：blog 静态 latest.json（格式见 hexo 仓库 source/downloads/oneline/latest.json）。
        Log.i(TAG, "GitHub 主源不可用，回退 blog 源：$BLOG_LATEST_URL")
        val blog = runCatching { fetch(BLOG_LATEST_URL) }
        if (blog.isFailure) {
            val reason = if (github.isFailure) {
                githubReason(github.exceptionOrNull())
            } else {
                "GitHub 请求受限（HTTP $ghCode），备用源也连不上"
            }
            return@withContext UpdateCheck.Failed(reason)
        }
        val br = blog.getOrNull()!!
        if (br.code !in 200..299) {
            return@withContext UpdateCheck.Failed("更新源（blog）返回 HTTP ${br.code}")
        }
        return@withContext parseBlog(br.body, currentVersion)
    }

    /** 主源网络错的汉字提示（主源其实连通但被限流时不会走到这里）。 */
    private fun githubReason(e: Throwable?): String = when (e) {
        is UnknownHostException -> "连不上服务器，请检查网络后重试"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is SSLException -> "网络被拦截，请换一个网络再试"
        is IOException -> "网络异常，请稍后重试"
        else -> "更新源暂时不可用，请稍后再试"
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

    /**
     * 解析 blog 兜底源的 latest.json（简化格式，与 GitHub release JSON 不同）：
     * `{ "versionCode": 21, "versionName": "0.8.3", "apkUrl": "...", "changelog": "..." }`。
     * 只比 versionName（与 [parse] 一致；versionCode 仅作信息保留）。
     */
    private fun parseBlog(body: String, currentVersion: String): UpdateCheck {
        val json = try {
            JSONObject(body)
        } catch (_: JSONException) {
            return UpdateCheck.Failed("发布信息格式异常")
        }

        val remoteVersion = json.optString("versionName").trim()
        if (remoteVersion.isBlank()) return UpdateCheck.Failed("发布信息里没有版本号")

        val newer = Version.isNewer(remoteVersion, currentVersion)
            ?: return UpdateCheck.Failed("版本号格式异常：$remoteVersion（本机 $currentVersion）")
        if (!newer) return UpdateCheck.UpToDate

        val apkUrl = json.optString("apkUrl").orEmpty()
        val changelog = json.optString("changelog").orEmpty()
        Log.i(TAG, "New version v$remoteVersion from blog (apk=${apkUrl.ifBlank { "none" }})")
        return UpdateCheck.Available(
            AppUpdate(
                version = remoteVersion,
                changelog = changelog,
                releaseUrl = RELEASES_PAGE,
                apkUrl = apkUrl
            )
        )
    }

    /**
     * 下载兜底：取出 blog 源里挂的 APK 直链（国内可直连），供 [UpdateDownloadService]
     * 在主源（GitHub 资产 CDN）下载失败时重试一次。任何异常 / 非 200 都返回空串。
     */
    fun blogFallbackApkUrl(): String {
        return try {
            val r = fetch(BLOG_LATEST_URL)
            if (r.code !in 200..299) return ""
            JSONObject(r.body).optString("apkUrl").orEmpty()
        } catch (_: Throwable) {
            ""
        }
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
