package com.shenliao.deepchat.net

import android.util.Base64
import com.shenliao.deepchat.data.Message
import com.shenliao.deepchat.data.Settings
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, val detail: String) : IOException(detail)

/**
 * 极简 OpenAI 兼容 Chat Completions 客户端：只做流式对话 + 图片输入。
 */
object ChatApi {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 允许用户填 https://api.deepseek.com 或 .../v1 之类，统一拼到 chat/completions。 */
    fun endpointOf(baseUrl: String): String {
        val b = baseUrl.trim().trimEnd('/')
        return when {
            b.isEmpty() -> "https://api.deepseek.com/chat/completions"
            b.endsWith("/chat/completions") -> b
            else -> "$b/chat/completions"
        }
    }

    fun isUrlUsable(url: String): Boolean = url.trim().toHttpUrlOrNull() != null

    fun newCall(body: JSONObject, settings: Settings, stream: Boolean, timeoutSeconds: Long = 0): Call {
        val request = Request.Builder()
            .url(endpointOf(settings.baseUrl))
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val c = if (timeoutSeconds > 0) {
            client.newBuilder()
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
        } else {
            client
        }
        return c.newCall(request)
    }

    /** 联网工具只在官方接口 + deepseek-flash 下可用 */
    fun wantsWeb(settings: Settings): Boolean {
        if (!settings.webSearch) return false
        if (settings.preset != Settings.PRESET_DEEPSEEK) return false
        val model = settings.model.trim().lowercase()
        return model == "deepseek-flash" || model == "deepseek-v4-flash"
    }

    /** 构造请求体。history 里不应包含正在生成的占位消息。 */
    fun buildBody(settings: Settings, history: List<Message>, stream: Boolean): JSONObject {
        val messages = JSONArray()

        if (settings.systemPrompt.isNotBlank()) {
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", settings.systemPrompt.trim()),
            )
        }

        // 每次请求最多带 8 张图（越新越优先），更早的图片退化成文字占位，省 token
        val keep = HashSet<String>()
        var budget = 8
        for (i in history.indices.reversed()) {
            val m = history[i]
            if (!m.isUser) continue
            for (j in m.attachments.indices.reversed()) {
                if (budget <= 0) break
                if (!m.attachments[j].isImage) continue
                keep.add("${m.id}:$j")
                budget--
            }
        }

        var docCount = 0
        for (m in history) {
            if (m.role != "user" && m.role != "assistant") continue
            val rep = m.reply
            if (!m.isUser && (rep == null || rep.error != null)) continue
            val text = if (m.isUser) m.text.trim() else (rep?.text ?: "").trim()
            val atts = if (m.isUser) m.attachments else emptyList()
            if (text.isEmpty() && atts.isEmpty()) continue

            val obj = JSONObject().put("role", m.role)
            if (atts.isEmpty()) {
                obj.put("content", text)
                if (m.role == "assistant" && rep != null && rep.serverToolCalls.isNotBlank()) {
                    runCatching { JSONArray(rep.serverToolCalls) }.getOrNull()?.let {
                        obj.put("server_tool_calls", it)
                    }
                }
                messages.put(obj)
                continue
            }

            val parts = JSONArray()
            var omittedImages = false
            var missedDocs = false
            if (text.isNotEmpty()) {
                parts.put(JSONObject().put("type", "text").put("text", text))
            }
            for ((idx, a) in atts.withIndex()) {
                when {
                    a.isImage -> {
                        if (!keep.contains("${m.id}:$idx")) {
                            omittedImages = true
                            continue
                        }
                        val dataUrl = ImagePayload.dataUrl(a.path)
                        if (dataUrl == null) {
                            omittedImages = true
                        } else {
                            parts.put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put(
                                        "image_url",
                                        JSONObject().put("url", dataUrl).put("detail", "high"),
                                    ),
                            )
                        }
                    }

                    a.remoteId.isNotBlank() -> {
                        if (docCount >= 20) {
                            missedDocs = true
                        } else {
                            docCount++
                            parts.put(JSONObject().put("type", "file").put("file_id", a.remoteId))
                        }
                    }

                    else -> missedDocs = true
                }
            }
            if (omittedImages) {
                parts.put(JSONObject().put("type", "text").put("text", "（此处原有图片已省略以节省上下文）"))
            }
            if (missedDocs) {
                parts.put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", "（有附件没能解析上传成功，用户只提供了文件名）"),
                )
            }
            obj.put("content", parts)
            messages.put(obj)
        }

        val body = JSONObject()
        body.put("model", settings.model.trim())
        body.put("messages", messages)
        body.put("stream", stream)
        body.put("thinking", JSONObject().put("type", if (settings.thinking) "enabled" else "disabled"))
        if (settings.thinking) {
            body.put("reasoning_effort", settings.effort)
        } else {
            body.put("temperature", settings.temperature.toDouble().coerceIn(0.0, 2.0))
        }
        if (settings.maxTokens > 0) body.put("max_tokens", settings.maxTokens)
        if (stream && settings.preset == Settings.PRESET_DEEPSEEK) {
            body.put("stream_options", JSONObject().put("include_usage", true))
        }
        if (wantsWeb(settings)) {
            // 服务端工具：模型自己决定搜不搜，客户端不执行任何工具
            body.put(
                "tools",
                JSONArray()
                    .put(JSONObject().put("type", "deepseek_web_search").put("name", "deepseek_web_search"))
                    .put(JSONObject().put("type", "deepseek_read_url").put("name", "deepseek_read_url")),
            )
        }
        return body
    }

    /**
     * 阻塞式读取 SSE。调用方负责放到 IO 线程，并通过 call.cancel() 支持中断。
     */
    fun stream(
        call: Call,
        onContent: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onUsage: (Int, Int) -> Unit,
        onToolCalls: (String) -> Unit = {},
        onCitations: (String) -> Unit = {},
    ) {
        val toolCalls = JSONArray()
        val citations = JSONArray()
        val seenUrls = HashSet<String>()

        fun collect(src: JSONArray?) {
            if (src == null || src.length() == 0) return
            for (i in 0 until src.length()) toolCalls.put(src.opt(i))
            onToolCalls(toolCalls.toString())
        }

        fun collectCitations(src: JSONArray?) {
            if (src == null || src.length() == 0) return
            var added = false
            for (i in 0 until src.length()) {
                val o = src.optJSONObject(i) ?: continue
                val url = o.optString("url", "")
                if (url.isBlank() || !seenUrls.add(url)) continue
                citations.put(
                    JSONObject()
                        .put("url", url)
                        .put("title", o.optString("title", "")),
                )
                added = true
            }
            if (added) onCitations(citations.toString())
        }

        call.execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, readableError(resp.body?.string().orEmpty()))
            }
            val source = resp.body?.source() ?: throw IOException("服务端返回了空响应")
            while (true) {
                val line = source.readUtf8Line() ?: break
                val t = line.trim()
                if (t.isEmpty() || t.startsWith(":")) continue
                if (!t.startsWith("data:")) continue
                val payload = t.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue

                chunk.optJSONObject("usage")?.let { u ->
                    onUsage(u.optInt("prompt_tokens", 0), u.optInt("completion_tokens", 0))
                }
                val choices = chunk.optJSONArray("choices") ?: continue
                if (choices.length() == 0) continue
                val choice = choices.optJSONObject(0) ?: continue

                // 联网工具：服务端调用记录，多轮要原样回传
                collect(choice.optJSONArray("server_tool_calls"))
                // 引用来源：流式里可能挂在 delta / choice / message 任一处
                collectCitations(choice.optJSONArray("annotations"))
                collectCitations(choice.optJSONObject("message")?.optJSONArray("annotations"))
                collectCitations(chunk.optJSONArray("annotations"))
                val delta = choice.optJSONObject("delta") ?: continue
                collect(delta.optJSONArray("server_tool_calls"))
                collectCitations(delta.optJSONArray("annotations"))

                val reasoning = delta.optString("reasoning_content", "")
                if (reasoning.isNotEmpty() && reasoning != "null") onReasoning(reasoning)
                val content = delta.optString("content", "")
                if (content.isNotEmpty() && content != "null") onContent(content)
            }
        }
    }

    /** 非流式请求，用于“测试连接”。 */
    fun complete(call: Call): String {
        call.execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, readableError(text))
            val obj = runCatching { JSONObject(text) }
                .getOrElse { throw IOException("返回格式不是 JSON：${text.take(120)}") }
            return obj.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
        }
    }

    fun readableError(raw: String): String {
        if (raw.isBlank()) return "服务端没有返回内容"
        val msg = runCatching {
            val o = JSONObject(raw)
            o.optJSONObject("error")?.optString("message")
                ?: o.optString("message")
        }.getOrNull()
        return (msg?.takeIf { it.isNotBlank() } ?: raw).take(400)
    }
}

/** 图片 → data URL，带一个小缓存，避免每轮重复 base64。 */
internal object ImagePayload {
    private val cache = object : LinkedHashMap<String, String>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 6
    }

    fun dataUrl(path: String): String? {
        synchronized(cache) { cache[path]?.let { return it } }
        val f = File(path)
        if (!f.isFile) return null
        val bytes = runCatching { f.readBytes() }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val url = "data:image/jpeg;base64,$encoded"
        synchronized(cache) { cache[path] = url }
        return url
    }

    fun clear() = synchronized(cache) { cache.clear() }
}
