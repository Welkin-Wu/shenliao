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

/** 一次生成的结果，一条助手消息可以有多个候选（左右箭头切换）。 */
data class Reply(
    val text: String = "",
    val reasoning: String = "",
    /** 联网来源引用（JSON 数组：[{url,title}]） */
    val citations: String = "",
    /** 来源出处：api = 接口返回；text = 正文里的链接；search = 客户端补搜（仅供参考） */
    val citationsSource: String = "",
    /** 联网工具的服务端调用记录，多轮要原样回传 */
    val serverToolCalls: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        if (reasoning.isNotEmpty()) put("reasoning", reasoning)
        if (citations.isNotEmpty()) put("cites", citations)
        if (citationsSource.isNotEmpty()) put("citeSrc", citationsSource)
        if (serverToolCalls.isNotEmpty()) put("toolCalls", serverToolCalls)
        if (inputTokens > 0) put("inTok", inputTokens)
        if (outputTokens > 0) put("outTok", outputTokens)
        error?.let { put("error", it) }
        put("ts", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Reply = Reply(
            text = o.optString("text", ""),
            reasoning = o.optString("reasoning", ""),
            citations = o.optString("cites", ""),
            citationsSource = o.optString("citeSrc", ""),
            serverToolCalls = o.optString("toolCalls", ""),
            inputTokens = o.optInt("inTok", 0),
            outputTokens = o.optInt("outTok", 0),
            error = if (o.has("error")) o.optString("error") else null,
            createdAt = o.optLong("ts", System.currentTimeMillis()),
        )
    }
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    /** user / assistant */
    val role: String,
    /** 用户消息的正文 */
    val text: String = "",
    val attachments: List<Attachment> = emptyList(),
    /** 助手消息的候选回答 */
    val replies: List<Reply> = emptyList(),
    /** 当前展示第几个候选 */
    val selected: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isUser: Boolean get() = role == "user"

    val reply: Reply?
        get() = if (replies.isEmpty()) null else replies[selected.coerceIn(0, replies.lastIndex)]

    /** 当前候选的正文，用户消息直接取 text */
    val body: String
        get() = if (isUser) text else (reply?.text ?: "")

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        if (isUser) {
            put("text", text)
            if (attachments.isNotEmpty()) {
                put("att", JSONArray().also { arr -> attachments.forEach { arr.put(it.toJson()) } })
            }
        } else {
            if (replies.isNotEmpty()) {
                put("replies", JSONArray().also { arr -> replies.forEach { arr.put(it.toJson()) } })
            }
            put("sel", selected)
        }
        put("ts", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Message {
            val role = o.optString("role", "assistant")
            val attachments = o.optJSONArray("att")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(Attachment::fromJson) }
            } ?: emptyList()

            val replies = o.optJSONArray("replies")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(Reply::fromJson) }
            } ?: emptyList()

            // 兼容 1.4 及更早的存档：助手消息的正文直接存在 text 字段里
            val migrated = if (role != "user" && replies.isEmpty()) {
                val legacyText = o.optString("text", "")
                val legacyReasoning = o.optString("reasoning", "")
                val legacyError = if (o.has("error")) o.optString("error") else null
                val hasSomething = legacyText.isNotBlank() || legacyReasoning.isNotBlank() || legacyError != null
                if (hasSomething) {
                    listOf(
                        Reply(
                            text = legacyText,
                            reasoning = legacyReasoning,
                            citations = o.optString("cites", ""),
            citationsSource = o.optString("citeSrc", ""),
                            serverToolCalls = o.optString("toolCalls", ""),
                            inputTokens = o.optInt("inTok", 0),
                            outputTokens = o.optInt("outTok", 0),
                            error = legacyError,
                            createdAt = o.optLong("ts", System.currentTimeMillis()),
                        ),
                    )
                } else {
                    listOf(Reply())
                }
            } else {
                replies
            }

            return Message(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                role = role,
                text = if (role == "user") o.optString("text", "") else "",
                attachments = attachments,
                replies = migrated,
                selected = o.optInt("sel", 0),
                createdAt = o.optLong("ts", System.currentTimeMillis()),
            )
        }
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
    /** 阅读链接：消息里贴 URL 时模型自己去抓那一页（服务端工具 deepseek_read_url） */
    val readUrl: Boolean = true,
    /** 公式字体：stix（默认，期刊风）/ lm（LaTeX 教科书风） */
    val mathFont: String = "stix",
    /** 外观：system 跟随系统 / light 白天 / dark 夜间 */
    val themeMode: String = "system",
    /** 每次请求附带当前时间（模型自己没时钟） */
    val withTime: Boolean = true,
    /** 附带大概位置（需权限，城市级精度） */
    val withLocation: Boolean = false,
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
