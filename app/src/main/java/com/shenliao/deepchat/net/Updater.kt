package com.shenliao.deepchat.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 检查更新：读 GitHub Releases 的 latest。没发过 Release 时返回空，
 * 界面会提示「还没有发布版本」而不是报错。
 */
object Updater {

    private const val OWNER_REPO = "Welkin-Wu/shenliao"
    private const val LATEST_API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$OWNER_REPO/releases"

    data class Release(
        val tag: String,
        val name: String,
        val notes: String,
        val url: String,
    )

    val releasesPage: String get() = RELEASES_PAGE

    /**
     * 阻塞调用，请放在 IO 线程。
     * @return 最新 release；仓库还没有 release 或网络失败时返回 null
     */
    fun checkLatest(): Release? {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(LATEST_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DeepChat-Android")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) return@use null
                val o = JSONObject(text)
                val tag = o.optString("tag_name")
                if (tag.isBlank()) return@use null
                Release(
                    tag = tag,
                    name = o.optString("name").ifBlank { tag },
                    notes = o.optString("body", ""),
                    url = o.optString("html_url").ifBlank { RELEASES_PAGE },
                )
            }
        }.getOrNull()
    }

    /** 比较 a.b.c 形式；无法解析时返回 null */
    fun compareVersions(current: String, latest: String): Int? {
        fun parts(s: String): List<Int>? {
            val cleaned = s.trim().removePrefix("v").removePrefix("V")
            val nums = cleaned.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
            return if (nums.isEmpty()) null else nums
        }

        val a = parts(current) ?: return null
        val b = parts(latest) ?: return null
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
