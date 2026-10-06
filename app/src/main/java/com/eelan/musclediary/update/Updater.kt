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

    data class UpdateInfo(val version: String, val notes: String, val apkUrl: String)

    /** 查询最新版本；无更新返回 null；网络失败抛异常由调用方提示 */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = httpGet("https://api.github.com/repos/$REPO/releases/latest")
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").removePrefix("v")
        if (tag.isEmpty()) throw IllegalStateException("仓库还没有发布过 Release")
        if (!isNewer(currentVersion, tag)) return@withContext null
        val apk = obj.optJSONArray("assets")
            ?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            ?.firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
            ?: throw IllegalStateException("Release 中没有 APK 附件")
        UpdateInfo(tag, obj.optString("body"), apk)
    }

    suspend fun downloadApk(ctx: Context, url: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "apks").apply { mkdirs() }
            val out = File(dir, "update.apk")
            val conn = URL(url).openConnection() as HttpURLConnection
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
