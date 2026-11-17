package com.shenliao.deepchat.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 用户消息里的附件：图片或文档（PDF / Word / PPT / 文本…）。 */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: Int = KIND_IMAGE,
    val path: String = "",
    val mime: String = "image/jpeg",
    val name: String = "",
    val sizeBytes: Long = 0L,
    /** 上传到 Files API 后拿到的 file-api-xxx，聊天时用它引用 */
    val remoteId: String = "",
    val purpose: String = "",
    val state: String = STATE_READY,
    val error: String? = null,
) {
    val isImage: Boolean get() = kind == KIND_IMAGE
    val isDoc: Boolean get() = kind == KIND_DOC
    val busy: Boolean get() = state == STATE_UPLOADING

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind)
        put("path", path)
        put("mime", mime)
        if (name.isNotEmpty()) put("name", name)
        if (sizeBytes > 0) put("size", sizeBytes)
        if (remoteId.isNotEmpty()) put("remoteId", remoteId)
        if (purpose.isNotEmpty()) put("purpose", purpose)
        put("state", state)
        error?.let { put("error", it) }
    }

    companion object {
        const val KIND_IMAGE = 0
        const val KIND_DOC = 1

        const val STATE_UPLOADING = "uploading"
        const val STATE_READY = "ready"
        const val STATE_ERROR = "error"

        fun fromJson(o: JSONObject): Attachment = Attachment(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            kind = o.optInt("kind", KIND_IMAGE),
            path = o.optString("path", ""),
            mime = o.optString("mime", "image/jpeg"),
            name = o.optString("name", ""),
            sizeBytes = o.optLong("size", 0L),
            remoteId = o.optString("remoteId", ""),
            purpose = o.optString("purpose", ""),
            state = o.optString("state", STATE_READY),
            error = if (o.has("error")) o.optString("error") else null,
        )
    }
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String = "",
    val reasoning: String = "",
    val attachments: List<Attachment> = emptyList(),
    val error: String? = null,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    /** 联网工具的服务端调用记录，多轮要原样回传（DeepSeek 扩展字段） */
    val serverToolCalls: String = "",
    /** 联网回答的来源引用（JSON 数组：[{url,title}]） */
    val citations: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isUser: Boolean get() = role == "user"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("text", text)
        if (reasoning.isNotEmpty()) put("reasoning", reasoning)
        if (attachments.isNotEmpty()) {
            put("att", JSONArray().also { arr -> attachments.forEach { arr.put(it.toJson()) } })
        }
        error?.let { put("error", it) }
        if (inputTokens > 0) put("inTok", inputTokens)
        if (outputTokens > 0) put("outTok", outputTokens)
        if (serverToolCalls.isNotEmpty()) put("toolCalls", serverToolCalls)
        if (citations.isNotEmpty()) put("cites", citations)
        put("ts", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Message = Message(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            role = o.optString("role", "assistant"),
            text = o.optString("text", ""),
            reasoning = o.optString("reasoning", ""),
            attachments = o.optJSONArray("att")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(Attachment::fromJson) }
            } ?: emptyList(),
            error = if (o.has("error")) o.optString("error") else null,
            inputTokens = o.optInt("inTok", 0),
            outputTokens = o.optInt("outTok", 0),
            serverToolCalls = o.optString("toolCalls", ""),
            citations = o.optString("cites", ""),
            createdAt = o.optLong("ts", System.currentTimeMillis()),
        )
    }
}

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("messages", JSONArray().also { arr -> messages.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Conversation = Conversation(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = o.optString("title", "新对话"),
            messages = o.optJSONArray("messages")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(Message::fromJson) }
            } ?: emptyList(),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }
}

/** 用户可调的运行参数（持久化在 SharedPreferences）。 */
data class Settings(
    val preset: String = PRESET_DEEPSEEK,
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val systemPrompt: String = "",
    val thinking: Boolean = true,
    val effort: String = "high",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 0,
    /** 文档解析方式：text = 只取文字；rich = 文字 + 图表 */
    val docMode: String = DOC_MODE_TEXT,
    /** 联网搜索（服务端工具 deepseek_web_search） */
    val webSearch: Boolean = true,
    /** 公式字体：stix（默认，期刊风）/ lm（LaTeX 教科书风） */
    val mathFont: String = "stix",
) {
    val ready: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        const val PRESET_DEEPSEEK = "deepseek"
        const val PRESET_CUSTOM = "custom"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-flash"
        const val DOC_MODE_TEXT = "text"
        const val DOC_MODE_RICH = "rich"

        /** DeepSeek 官方模型（写死两个常用名字，方便一键切换）。 */
        val DEEPSEEK_MODELS = listOf(
            "deepseek-flash" to "deepseek-flash · 能看图",
            "deepseek-v4-pro" to "deepseek-v4-pro · 纯文本",
        )
    }
}
