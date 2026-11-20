package com.shenliao.deepchat.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.AddComment
import java.util.Calendar
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.shenliao.deepchat.AppViewModel
import com.shenliao.deepchat.R
import com.shenliao.deepchat.net.ChatApi
import java.io.File

private val DOC_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/plain",
    "text/markdown",
    "text/csv",
    "application/json",
    "application/epub+zip",
    "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var draft by rememberSaveable { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6),
    ) { uris ->
        uris.forEach { vm.attachImage(it) }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.attachImage(uri)
        cameraUri = null
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            val (name, mime) = queryDocMeta(context, uri)
            vm.attachDocument(uri, name, mime)
        }
    }

    fun launchGallery() {
        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            takePicture.launch(uri)
        } catch (t: Throwable) {
            Toast.makeText(context, "打不开相机：${t.message ?: ""}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchFilePicker() {
        try {
            pickFiles.launch(DOC_MIME_TYPES)
        } catch (t: Throwable) {
            Toast.makeText(context, "打开文件选择器失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun doSend() {
        if (!vm.settings.ready) {
            onOpenSettings()
            return
        }
        if (vm.streaming) return
        if (vm.pending.any { it.busy }) {
            Toast.makeText(context, "文件还在解析，稍等一下", Toast.LENGTH_SHORT).show()
            return
        }
        vm.send(draft)
        draft = ""
        keyboard?.hide()
    }

    val conversation = vm.current
    val messages = conversation?.messages.orEmpty()
    val listState = rememberLazyListState()
    val attachmentsBusy = vm.pending.any { it.busy }

    // 生成时是否跟着往下走：用户手动往上翻就停止跟随，滑回底部再自动恢复
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (scrolling && canScrollForward) follow = false
                if (!canScrollForward) follow = true
            }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!follow) return@LaunchedEffect
        withFrameNanos { }
        listState.scrollToBottom()
    }

    val lastLength = messages.lastOrNull()?.body?.length ?: 0
    LaunchedEffect(lastLength) {
        // 流式输出时按增量往下推：只补「超出视口」的那一段，
        // 不能用 scrollToItem(最后一条)——那会把最后一条的顶部对齐屏幕顶部，看起来像跳回第一行
        if (!vm.streaming || messages.isEmpty() || !follow) return@LaunchedEffect
        withFrameNanos { }
        listState.scrollToBottom()
    }

    Scaffold(
        containerColor = AppColors.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = conversation?.title ?: "DeepChat",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextMain,
                            maxLines = 1,
                        )
                        Text(
                            text = if (vm.settings.thinking) "${vm.settings.model} · 思考开" else "${vm.settings.model} · 直答",
                            fontSize = 11.sp,
                            color = AppColors.TextFaint,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "会话列表", tint = AppColors.TextDim)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AppColors.Surface)
                            .clickable { vm.startNewChat() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.AddCircleOutline,
                            contentDescription = "新对话",
                            tint = AppColors.TextMain,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Bg),
            )
        },
        bottomBar = {
            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                pending = vm.pending,
                onRemovePending = { id -> vm.removePending(id) },
                onPickImage = { launchGallery() },
                onTakePhoto = { launchCamera() },
                onPickFile = { launchFilePicker() },
                streaming = vm.streaming,
                thinking = vm.settings.thinking,
                onToggleThinking = {
                    vm.updateSettings(vm.settings.copy(thinking = !vm.settings.thinking))
                },
                webSearch = vm.settings.webSearch,
                onToggleWeb = {
                    val next = !vm.settings.webSearch
                    vm.updateSettings(vm.settings.copy(webSearch = next))
                    if (next && !ChatApi.supportsWebTools(vm.settings)) {
                        Toast.makeText(
                            context,
                            "联网需要「DeepSeek 官方」接口 + deepseek-flash 模型",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                canSend = (draft.isNotBlank() || vm.pending.isNotEmpty()) && !attachmentsBusy,
                onSend = { doSend() },
                onStop = { vm.stop() },
                modelLabel = vm.settings.model,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (!vm.settings.ready) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.PrimaryDim)
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "还差一步：填入 DeepSeek API Key 就能开聊 →",
                        fontSize = 13.sp,
                        color = AppColors.Primary,
                    )
                }
            }

            if (messages.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    onPick = { draft = it },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageRow(
                            message = msg,
                            streaming = vm.streaming && msg.id == messages.lastOrNull()?.id,
                            isLast = msg.id == messages.lastOrNull()?.id,
                            onCopy = { text ->
                                clipboard.setText(AnnotatedString(text))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            },
                            onRegenerate = { vm.regenerate(msg.id) },
                            onSelectReply = { index -> vm.selectReply(msg.id, index) },
                            onEdit = { newText -> vm.editAndResend(msg.id, newText) },
                        )
                    }
                }
            }
        }
    }
}

private fun queryDocMeta(context: Context, uri: Uri): Pair<String, String> {
    var name = "文件"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: name
            }
        }
    }
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        ?: guessMime(name)
    return name to mime
}

private fun guessMime(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".doc") -> "application/msword"
        lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
        lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        lower.endsWith(".xls") -> "application/vnd.ms-excel"
        lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        lower.endsWith(".txt") -> "text/plain"
        lower.endsWith(".md") -> "text/markdown"
        lower.endsWith(".csv") -> "text/csv"
        lower.endsWith(".json") -> "application/json"
        lower.endsWith(".epub") -> "application/epub+zip"
        else -> "application/octet-stream"
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    // 和官方手机端一样：一个小图标 + 一句按时间变化的问候
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..8 -> "早上好"
        in 9..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
    val tail = when (hour) {
        in 5..17 -> "开始聊天吧"
        in 18..22 -> "想问点什么"
        else -> "还不睡吗"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "$greeting，$tail",
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextMain,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "能看图 · 会思考 · 读文档 · 排公式",
            fontSize = 12.sp,
            color = AppColors.TextFaint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** 把列表推到底：只补超出视口的那一段，不改变当前阅读位置 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToBottom() {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return
    val overflow = (last.offset + last.size) - info.viewportEndOffset
    if (overflow > 0) scrollBy(overflow.toFloat())
}