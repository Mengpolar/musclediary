package com.eelan.musclediary.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Releases 在线更新。仅此模块需要联网。 */
object Updater {

    const val REPO = "Mengpolar/musclediary"
    const val RELEASES_URL = "https://github.com/$REPO/releases"

    /** 更新源：0 GitHub 官方 / 1 gh-proxy.com 加速代理（https://gh-proxy.com/docs/github-accelerator） */
    const val SRC_OFFICIAL = 0
    const val SRC_PROXY = 1

    data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val sha256: String)

    /** 按更新源包装 URL：官方直连原样返回；代理源在完整 URL 前拼接 https://gh-proxy.com/ 前缀 */
    fun srcUrl(url: String, source: Int): String =
        if (source == SRC_PROXY) "https://gh-proxy.com/$url" else url

    /** 查询最新版本；无更新返回 null；网络失败抛异常由调用方提示 */
    suspend fun check(currentVersion: String, source: Int = SRC_OFFICIAL): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = httpGet(srcUrl("https://api.github.com/repos/$REPO/releases/latest", source))
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").removePrefix("v")
        if (tag.isEmpty()) throw IllegalStateException("仓库还没有发布过 Release")
        if (!isNewer(currentVersion, tag)) return@withContext null
        val asset = obj.optJSONArray("assets")
            ?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            ?.firstOrNull { it.optString("name").endsWith(".apk") }
            ?: throw IllegalStateException("Release 中没有 APK 附件")
        // GitHub 返回的资产摘要形如 sha256:xxxx；下载完成后用于完整性校验
        val sha256 = asset.optString("digest").removePrefix("sha256:")
        UpdateInfo(tag, obj.optString("body"), asset.optString("browser_download_url"),
            asset.optLong("size"), sha256)
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1 shl 20 -> String.format("%.1f MB", bytes / 1048576.0)
        bytes >= 1 shl 10 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    suspend fun downloadApk(ctx: Context, url: String, onProgress: (Int) -> Unit, source: Int = SRC_OFFICIAL): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "apks").apply { mkdirs() }
            val out = File(dir, "update.apk")
            val conn = URL(srcUrl(url, source)).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.connect()
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
            out
        }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /** 校验下载的 APK 是否与 Release 资产摘要一致；expectedSha256 为空时跳过（兼容旧 Release） */
    fun verifySha256(apk: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return true
        val md = java.security.MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    /** 语义化版本比较：0.1.10 > 0.1.9 */
    fun isNewer(current: String, candidate: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(current); val b = parts(candidate)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connect()
        if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
