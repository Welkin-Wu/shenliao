package com.shenliao.deepchat

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shenliao.deepchat.data.Attachment
import com.shenliao.deepchat.data.Conversation
import com.shenliao.deepchat.data.ConversationStore
import com.shenliao.deepchat.data.Message
import com.shenliao.deepchat.data.Prefs
import com.shenliao.deepchat.data.Reply
import com.shenliao.deepchat.data.Settings
import com.shenliao.deepchat.net.ApiException
import com.shenliao.deepchat.net.ChatApi
import com.shenliao.deepchat.net.FilesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val store = ConversationStore(app)

    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    var currentId by mutableStateOf<String?>(null)
        private set

    var streaming by mutableStateOf(false)
        private set

    var settings by mutableStateOf(prefs.load())
        private set

    var loaded by mutableStateOf(false)
        private set

    var pending by mutableStateOf<List<Attachment>>(emptyList())
        private set

    /** 上次启动的崩溃堆栈；非空时界面先显示崩溃恢复页 */
    var lastCrash by mutableStateOf<String?>(null)
        private set

    private var activeCall: Call? = null
    private var turnJob: Job? = null
    private var stopRequested = false

    val current: Conversation?
        get() = conversations.firstOrNull { it.id == currentId }

    init {
        lastCrash = readCrash()
        // 开了「附带大概位置」就在启动时抓一次定位，请求时直接用缓存
        if (settings.withLocation) {
            runCatching { com.shenliao.deepchat.util.RuntimeContext.refreshLocation(getApplication()) {} }
        }
        viewModelScope.launch {
            conversations = withContext(Dispatchers.IO) { store.load() }
            // 和 DS 手机端一致：每次启动都落在「新对话」，历史在侧边抽屉里
            currentId = null
            loaded = true
        }
    }

    // ---------------------------------------------------------------- 崩溃恢复

    private fun crashFile(): File = File(File(getApplication<Application>().filesDir, "crash"), "last.txt")

    private fun readCrash(): String? = runCatching {
        val f = crashFile()
        if (f.isFile) f.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    fun dismissCrash() {
        lastCrash = null
        runCatching { crashFile().delete() }
    }

    /** 清空对话 + 设置 + 缓存，用于崩溃后自救 */
    fun factoryReset() {
        if (streaming) stop()
        dismissCrash()
        conversations = emptyList()
        currentId = null
        pending = emptyList()
        settings = Settings()
        prefs.reset()
        viewModelScope.launch(Dispatchers.IO) {
            store.clearAll()
            store.clearCache()
        }
    }

    // ---------------------------------------------------------------- 会话管理

    fun startNewChat() {
        if (streaming) stop()
        currentId = null
    }

    fun openConversation(id: String) {
        if (streaming) stop()
        currentId = id
    }

    fun deleteConversation(id: String) {
        val wasCurrent = currentId == id
        conversations = conversations.filterNot { it.id == id }
        if (wasCurrent) currentId = conversations.firstOrNull()?.id
        persist()
    }

    fun renameConversation(id: String, title: String) {
        val t = title.trim().ifBlank { "未命名对话" }
        conversations = conversations.map { if (it.id == id) it.copy(title = t) else it }
        persist()
    }

    fun clearAllConversations() {
        if (streaming) stop()
        conversations = emptyList()
        currentId = null
        pending = emptyList()
        viewModelScope.launch(Dispatchers.IO) { store.clearAll() }
    }

    fun updateSettings(next: Settings) {
        settings = next
        prefs.save(next)
    }

    // ---------------------------------------------------------------- 附件

    fun attachImage(uri: Uri) {
        if (pending.size >= 6) return
        viewModelScope.launch {
            val att = withContext(Dispatchers.IO) { store.importImage(uri) }
            if (att != null) pending = pending + att
        }
    }

    fun attachDocument(uri: Uri, displayName: String, mime: String) {
        val snapshot = settings
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) { store.importDocument(uri, displayName, mime) }
            if (imported == null) return@launch
            pending = pending + imported

            if (!snapshot.ready) {
                pending = pending.map {
                    if (it.id == imported.id) {
                        imported.copy(
                            state = Attachment.STATE_ERROR,
                            error = "还没填 API Key，填完再试",
                        )
                    } else {
                        it
                    }
                }
                return@launch
            }

            val purpose = purposeFor(imported.mime, snapshot.docMode)
            val result = withContext(Dispatchers.IO) {
                try {
                    val id = FilesApi.upload(
                        baseUrl = snapshot.baseUrl,
                        apiKey = snapshot.apiKey,
                        file = File(imported.path),
                        filename = imported.name,
                        mime = imported.mime,
                        purpose = purpose,
                    )
                    imported.copy(
                        remoteId = id,
                        purpose = purpose,
                        state = Attachment.STATE_READY,
                        error = null,
                    )
                } catch (t: Throwable) {
                    imported.copy(state = Attachment.STATE_ERROR, error = describe(t))
                }
            }
            pending = pending.map { if (it.id == imported.id) result else it }
        }
    }

    fun removePending(id: String) {
        pending = pending.filterNot { it.id == id }
    }

    private fun purposeFor(mime: String, docMode: String): String {
        val m = mime.lowercase()
        val plain = m.startsWith("text/") || m.contains("json") || m.contains("csv") ||
            m.contains("spreadsheet") || m.contains("ms-excel") ||
            m.contains("epub") || m.contains("mobi")
        if (plain) return "user_data"
        return if (docMode == Settings.DOC_MODE_RICH) "rich_layout_recognition" else "text_recognition"
    }

    // ---------------------------------------------------------------- 发消息

    fun send(rawText: String) {
        if (streaming) return
        if (!settings.ready) return
        val trimmed = rawText.trim()
        val atts = pending
        if (trimmed.isEmpty() && atts.isEmpty()) return
        if (atts.any { it.busy }) return
        pending = emptyList()

        val existing = current
        turnJob = viewModelScope.launch {
            streaming = true
            stopRequested = false
            try {
                val userMsg = Message(role = "user", text = trimmed, attachments = atts)
                val assistant = Message(role = "assistant", replies = listOf(Reply()))
                val base = existing ?: Conversation()
                val updated = base.copy(
                    title = if (base.messages.isEmpty()) titleOf(trimmed, atts) else base.title,
                    messages = base.messages + userMsg + assistant,
                    updatedAt = System.currentTimeMillis(),
                )
                conversations = if (existing == null) {
                    listOf(updated) + conversations
                } else {
                    conversations.map { if (it.id == updated.id) updated else it }
                }
                currentId = updated.id
                executeTurn(updated.id, assistant.id, 0)
                persist()
            } finally {
                streaming = false
                activeCall = null
            }
        }
    }

    /** 再生成一个候选回答（左右箭头切换） */
    fun regenerate(messageId: String) {
        if (streaming) return
        val conv = current ?: return
        val target = conv.messages.firstOrNull { it.id == messageId } ?: return
        if (target.isUser) return
        val newIndex = target.replies.size

        val updated = conv.copy(
            messages = conv.messages.map {
                if (it.id == messageId) {
                    it.copy(replies = it.replies + Reply(), selected = newIndex)
                } else {
                    it
                }
            },
            updatedAt = System.currentTimeMillis(),
        )
        conversations = conversations.map { if (it.id == updated.id) updated else it }

        turnJob = viewModelScope.launch {
            streaming = true
            stopRequested = false
            try {
                executeTurn(updated.id, messageId, newIndex)
                persist()
            } finally {
                streaming = false
                activeCall = null
            }
        }
    }

    /** 改变某条助手消息当前展示的候选 */
    fun selectReply(messageId: String, index: Int) {
        val conv = current ?: return
        var changed = false
        val messages = conv.messages.map { m ->
            if (m.id == messageId && index in m.replies.indices && m.selected != index) {
                changed = true
                m.copy(selected = index)
            } else {
                m
            }
        }
        if (!changed) return
        conversations = conversations.map {
            if (it.id == conv.id) it.copy(messages = messages) else it
        }
        persist()
    }

    /** 编辑一条用户消息，并从这条开始重新生成 */
    fun editAndResend(messageId: String, newText: String) {
        if (streaming) return
        val conv = current ?: return
        val idx = conv.messages.indexOfFirst { it.id == messageId }
        if (idx < 0 || !conv.messages[idx].isUser) return
        val text = newText.trim()
        if (text.isEmpty()) return

        val kept = conv.messages.subList(0, idx).toList()
        val edited = conv.messages[idx].copy(text = text)
        val assistant = Message(role = "assistant", replies = listOf(Reply()))
        val updated = conv.copy(
            title = if (kept.isEmpty()) titleOf(text, emptyList()) else conv.title,
            messages = kept + edited + assistant,
            updatedAt = System.currentTimeMillis(),
        )
        conversations = conversations.map { if (it.id == updated.id) updated else it }

        turnJob = viewModelScope.launch {
            streaming = true
            stopRequested = false
            try {
                executeTurn(updated.id, assistant.id, 0)
                persist()
            } finally {
                streaming = false
                activeCall = null
            }
        }
    }

    fun stop() {
        stopRequested = true
        runCatching { activeCall?.cancel() }
    }

    private suspend fun executeTurn(convId: String, assistantId: String, replyIndex: Int) {
        val conv = conversations.firstOrNull { it.id == convId } ?: return
        // 只取到这个助手消息之前的历史（含用户消息）
        val stopIndex = conv.messages.indexOfFirst { it.id == assistantId }
        val history = if (stopIndex >= 0) conv.messages.subList(0, stopIndex).toList() else conv.messages

        val body = ChatApi.buildBody(
            settings,
            history,
            stream = true,
            context = com.shenliao.deepchat.util.RuntimeContext.build(
                getApplication(),
                settings.withTime,
                settings.withLocation,
            ),
        )
        val call = try {
            ChatApi.newCall(body, settings, stream = true)
        } catch (t: Throwable) {
            patchReply(convId, assistantId, replyIndex) { it.copy(error = "地址不合法：${t.message ?: ""}") }
            return
        }
        activeCall = call

        val content = StringBuilder()
        val reasoning = StringBuilder()
        var inTok = 0
        var outTok = 0
        var toolCalls = ""
        var citations = ""
        var lastFlush = 0L

        fun flush(force: Boolean) {
            val now = SystemClock.uptimeMillis()
            if (!force && now - lastFlush < 45L) return
            lastFlush = now
            val c = content.toString()
            val r = reasoning.toString()
            val tc = toolCalls
            val ct = citations
            patchReply(convId, assistantId, replyIndex) {
                it.copy(
                    text = c,
                    reasoning = r,
                    inputTokens = inTok,
                    outputTokens = outTok,
                    serverToolCalls = tc,
                    citations = ct,
                )
            }
        }

        val deltas = Channel<Delta>(Channel.UNLIMITED)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ChatApi.stream(
                    call,
                    onContent = { deltas.trySend(Delta(content = it)) },
                    onReasoning = { deltas.trySend(Delta(reason = it)) },
                    onUsage = { i, o -> deltas.trySend(Delta(inTok = i, outTok = o)) },
                    onToolCalls = { toolCalls = it },
                    onCitations = { citations = it },
                )
            } catch (t: Throwable) {
                deltas.trySend(Delta(error = describe(t)))
            } finally {
                deltas.close()
            }
        }

        var errMsg: String? = null
        for (d in deltas) {
            d.content?.let { content.append(it) }
            d.reason?.let { reasoning.append(it) }
            d.inTok?.let { inTok = it }
            d.outTok?.let { outTok = it }
            d.error?.let { if (errMsg == null) errMsg = it }
            flush(false)
        }
        flush(true)

        val stopped = stopRequested
        stopRequested = false
        val error = errMsg

        // 引用兜底：① 正文里的链接 → ② 用模型真实用过的搜索词再搜一次
        var finalCitations = citations
        var citSource = if (citations.isNotBlank()) "api" else ""
        if (finalCitations.isBlank()) {
            val fromText = extractCitations(content.toString())
            if (fromText.isNotBlank()) {
                finalCitations = fromText
                citSource = "text"
            }
        }
        if (finalCitations.isBlank() && !stopped && content.isNotBlank() && ChatApi.wantsSearch(settings)) {
            val queries = parseSearchQueries(toolCalls)
            val query = queries.firstOrNull() ?: lastUserText(history)
            if (query.isNotBlank()) {
                val remote = withContext(Dispatchers.IO) {
                    runCatching { ChatApi.search(settings, query, 6) }.getOrDefault("")
                }
                if (remote.isNotBlank() && remote != "[]") {
                    finalCitations = remote
                    citSource = "search"
                }
            }
        }

        if (error != null && !stopped) {
            patchReply(convId, assistantId, replyIndex) {
                it.copy(
                    text = content.toString(),
                    reasoning = reasoning.toString(),
                    error = if (content.isBlank()) error else null,
                    inputTokens = inTok,
                    outputTokens = outTok,
                    serverToolCalls = toolCalls,
                    citations = finalCitations,
                    citationsSource = citSource,
                )
            }
        } else if (content.isBlank() && reasoning.isBlank() && !stopped) {
            patchReply(convId, assistantId, replyIndex) {
                it.copy(
                    error = "模型没有返回任何内容，可以点重试",
                    serverToolCalls = toolCalls,
                    citations = finalCitations,
                    citationsSource = citSource,
                )
            }
        } else if (finalCitations != citations) {
            patchReply(convId, assistantId, replyIndex) {
                it.copy(citations = finalCitations, citationsSource = citSource)
            }
        }
    }

    /** 从 server_tool_calls 里捞出模型真正用过的搜索词 */
    private fun parseSearchQueries(toolCallsJson: String): List<String> {
        if (toolCallsJson.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(toolCallsJson)
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val input = arr.optJSONObject(i)?.optJSONObject("input") ?: continue
                val queries = input.optJSONArray("queries")
                if (queries != null) {
                    for (j in 0 until queries.length()) {
                        queries.optString(j).takeIf { it.isNotBlank() }?.let { out.add(it) }
                    }
                } else {
                    input.optString("url", "").takeIf { it.isNotBlank() }?.let { out.add(it) }
                }
            }
            out.distinct().take(3)
        }.getOrDefault(emptyList())
    }

    private fun lastUserText(history: List<Message>): String =
        history.lastOrNull { it.isUser }?.text?.trim()?.replace("\n", " ")?.take(60) ?: ""

    /** 从回答正文里找 http(s) 链接，兜底做来源列表 */
    private fun extractCitations(text: String): String {
        if (text.isBlank()) return ""
        val urls = Regex("https?://[^\\s<>()\\[\\]\"'，。；、）]+")
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':', ')', ']', '。', '，', '；') }
            .filter { it.length > 12 }
            .distinct()
            .take(20)
            .toList()
        if (urls.isEmpty()) return ""
        val arr = JSONArray()
        urls.forEach { arr.put(JSONObject().put("url", it).put("title", "")) }
        return arr.toString()
    }

    // ---------------------------------------------------------------- 工具

    fun testConnection(onResult: (String) -> Unit) {
        val s = settings
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    if (!ChatApi.isUrlUsable(s.baseUrl)) {
                        return@withContext "Base URL 不是合法地址"
                    }
                    if (s.apiKey.isBlank()) return@withContext "还没填 API Key"
                    val body = JSONObject()
                        .put("model", s.model.trim())
                        .put("stream", false)
                        .put("max_tokens", 16)
                        .put("thinking", JSONObject().put("type", "disabled"))
                        .put(
                            "messages",
                            JSONArray().put(
                                JSONObject().put("role", "user").put("content", "只回复两个字：在线"),
                            ),
                        )
                    val reply = ChatApi.complete(
                        ChatApi.newCall(body, s, stream = false, timeoutSeconds = 40),
                    )
                    "连接成功 · 模型说：${reply.trim().take(40).ifBlank { "（空回复）" }}"
                } catch (t: Throwable) {
                    "连接失败：" + describe(t)
                }
            }
            onResult(msg)
        }
    }

    private fun describe(t: Throwable): String = when (t) {
        is ApiException -> when (t.status) {
            400 -> if (t.detail.contains("tool", ignoreCase = true)) {
                "当前接口不支持联网工具，把「联网」关掉再试（400）：${t.detail}"
            } else {
                "请求被拒绝（400）：${t.detail}"
            }
            401 -> "API Key 无效或未授权（401）"
            402 -> "账户余额不足（402）"
            403 -> "没有权限访问该模型（403）"
            404 -> "接口地址不存在（404），检查 Base URL：${t.detail}"
            429 -> "请求太频繁或并发超限（429）"
            in 500..599 -> "服务端错误（${t.status}）：${t.detail}"
            else -> "HTTP ${t.status}：${t.detail}"
        }
        is UnknownHostException -> "域名解析失败，检查网络或 Base URL"
        is SocketTimeoutException -> "请求超时，稍后重试"
        is java.net.ConnectException -> "连不上服务器，检查网络或 Base URL"
        is javax.net.ssl.SSLException -> "TLS 握手失败：${t.message ?: ""}"
        is IOException -> t.message?.takeIf { it.isNotBlank() }?.let { "网络错误：$it" } ?: "网络错误"
        else -> t.message ?: t.javaClass.simpleName
    }

    private fun titleOf(text: String, atts: List<Attachment>): String {
        val t = text.trim().replace("\n", " ")
        return when {
            t.isNotEmpty() -> if (t.length > 22) t.take(22) + "…" else t
            atts.any { it.isDoc } -> "文档对话"
            atts.isNotEmpty() -> "图片对话"
            else -> "新对话"
        }
    }

    private fun patchReply(convId: String, msgId: String, index: Int, block: (Reply) -> Reply) {
        conversations = conversations.map { c ->
            if (c.id != convId) {
                c
            } else {
                c.copy(
                    messages = c.messages.map { m ->
                        if (m.id != msgId) {
                            m
                        } else {
                            m.copy(
                                replies = m.replies.mapIndexed { i, r -> if (i == index) block(r) else r },
                            )
                        }
                    },
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun persist() {
        val snapshot = conversations
        if (!loaded) return
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    private data class Delta(
        val content: String? = null,
        val reason: String? = null,
        val inTok: Int? = null,
        val outTok: Int? = null,
        val error: String? = null,
    )
}
