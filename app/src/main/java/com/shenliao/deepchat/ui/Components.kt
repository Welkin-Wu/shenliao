package com.shenliao.deepchat.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shenliao.deepchat.data.Attachment
import com.shenliao.deepchat.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MessageRow(
    message: Message,
    streaming: Boolean,
    isLast: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onSelectReply: (Int) -> Unit,
    onEdit: (String) -> Unit,
) {
    if (message.isUser) {
        UserBubble(message, onEdit)
        return
    }

    val reply = message.reply ?: return
    val hasContent = reply.text.isNotBlank() || reply.reasoning.isNotBlank() || reply.error != null

    Column(Modifier.fillMaxWidth()) {
        if (reply.reasoning.isNotBlank()) {
            ThinkingBlock(reply.reasoning, streaming = streaming)
            Spacer(Modifier.height(8.dp))
        }

        if (reply.text.isNotBlank()) {
            MarkdownText(reply.text)
        } else if (streaming && reply.reasoning.isBlank()) {
            TypingDots()
        }

        if (reply.citations.isNotBlank()) {
            CitationBlock(reply.citations)
        }

        if (reply.error != null) {
            Spacer(Modifier.height(8.dp))
            ErrorCard(reply.error, onRetry = onRegenerate)
        }

        if (!streaming && hasContent) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reply.text.isNotBlank()) {
                    TextButton(
                        onClick = { onCopy(reply.text) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = AppColors.TextFaint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制", fontSize = 12.sp, color = AppColors.TextFaint)
                    }
                }
                TextButton(
                    onClick = onRegenerate,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, null, tint = AppColors.TextFaint, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重新生成", fontSize = 12.sp, color = AppColors.TextFaint)
                }
                if (reply.outputTokens > 0) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "↑${reply.inputTokens} ↓${reply.outputTokens}",
                        fontSize = 11.sp,
                        color = AppColors.TextFaint,
                    )
                }
            }

            if (message.replies.size > 1) {
                Spacer(Modifier.height(6.dp))
                VariantSwitcher(
                    index = message.selected.coerceIn(0, message.replies.lastIndex),
                    total = message.replies.size,
                    onSelect = onSelectReply,
                )
            }
        }
    }
}

@Composable
private fun VariantSwitcher(index: Int, total: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(50))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(enabled = index > 0) { onSelect(index - 1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = "上一个回答",
                tint = if (index > 0) AppColors.Primary else AppColors.Border,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "回答 ${index + 1}/${total}",
            fontSize = 12.sp,
            color = AppColors.TextMain,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(enabled = index < total - 1) { onSelect(index + 1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "下一个回答",
                tint = if (index < total - 1) AppColors.Primary else AppColors.Border,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun UserBubble(message: Message, onEdit: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.End,
        ) {
            AttachmentStrip(message.attachments)
            if (message.text.isNotBlank()) {
                Surface(
                    color = AppColors.BubbleUser,
                    shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                ) {
                    Text(
                        text = message.text,
                        color = AppColors.TextMain,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { editing = true }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = AppColors.TextFaint,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("编辑", fontSize = 11.sp, color = AppColors.TextFaint)
            }
        }
    }

    if (editing) {
        EditMessageDialog(
            initial = message.text,
            onDismiss = { editing = false },
            onConfirm = {
                editing = false
                onEdit(it)
            },
        )
    }
}

@Composable
private fun EditMessageDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改这条提问", fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppColors.SurfaceHigh,
                        unfocusedContainerColor = AppColors.SurfaceHigh,
                        focusedBorderColor = AppColors.Primary.copy(alpha = 0.55f),
                        unfocusedBorderColor = AppColors.Border,
                        cursorColor = AppColors.Primary,
                        focusedTextColor = AppColors.TextMain,
                        unfocusedTextColor = AppColors.TextMain,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "保存后会从这条开始重新回答，后面的对话会被替换。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = AppColors.TextFaint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("重新生成", color = AppColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.TextMain,
        textContentColor = AppColors.TextDim,
    )
}

@Composable
fun ThinkingBlock(text: String, streaming: Boolean) {
    var expanded by remember { mutableStateOf(streaming) }
    LaunchedEffect(streaming) { if (streaming) expanded = true }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface.copy(alpha = 0.75f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = AppColors.Accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (streaming) "正在思考…" else "思考过程",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextDim,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = AppColors.TextFaint,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Text(
                text = text,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = AppColors.TextDim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun CitationBlock(citationsJson: String) {
    val items = remember(citationsJson) { parseCitations(citationsJson) }
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    var openedIndex by remember { mutableStateOf(-1) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(vertical = 6.dp, horizontal = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    expanded = !expanded
                    if (!expanded) openedIndex = -1
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                tint = AppColors.Primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${items.size} 个来源",
                fontSize = 12.sp,
                color = AppColors.TextDim,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = AppColors.TextFaint,
                modifier = Modifier.size(18.dp),
            )
        }

        if (expanded) {
            items.forEachIndexed { index, item ->
                val isOpen = openedIndex == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (isOpen) {
                                runCatching { uriHandler.openUri(item.first) }
                            } else {
                                openedIndex = index
                            }
                        }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 11.sp,
                            color = AppColors.Primary,
                            modifier = Modifier.width(18.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.second.ifBlank { item.first },
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = AppColors.TextMain,
                                maxLines = if (isOpen) 4 else 1,
                            )
                            Text(
                                text = hostOf(item.first),
                                fontSize = 10.sp,
                                color = AppColors.TextFaint,
                            )
                            if (isOpen) {
                                Text(
                                    text = item.first,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = AppColors.TextFaint,
                                    maxLines = 3,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                                Text(
                                    text = "再点一下打开网页",
                                    fontSize = 10.sp,
                                    color = AppColors.Primary,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        if (isOpen) {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = AppColors.Primary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(color = AppColors.Border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun parseCitations(json: String): List<Pair<String, String>> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url", "")
            if (url.isBlank()) null else url to o.optString("title", "")
        }
    }.getOrElse { emptyList() }
}

private fun hostOf(url: String): String = runCatching {
    url.substringAfter("://").substringBefore('/').ifBlank { url }
}.getOrDefault(url)

@Composable
fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AppColors.Primary.copy(alpha = alpha)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("正在思考…", fontSize = 12.sp, color = AppColors.TextFaint)
    }
}

@Composable
fun ErrorCard(text: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A1519))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = AppColors.Danger,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color(0xFFFFB4B4), fontSize = 13.sp, lineHeight = 19.sp)
        }
        TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
            Text("重新发送", fontSize = 13.sp, color = AppColors.Primary)
        }
    }
}

@Composable
fun AttachmentStrip(attachments: List<Attachment>) {
    if (attachments.isEmpty()) return
    val images = attachments.filter { it.isImage }
    val docs = attachments.filter { it.isDoc }
    var preview by remember { mutableStateOf<Attachment?>(null) }

    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
        docs.forEach { doc ->
            Row(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Surface)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = doc.name.ifBlank { "文档" },
                    fontSize = 12.sp,
                    color = AppColors.TextMain,
                    maxLines = 1,
                )
                if (doc.sizeBytes > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(humanSize(doc.sizeBytes), fontSize = 10.sp, color = AppColors.TextFaint)
                }
            }
        }

        if (images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                images.take(4).forEach { att ->
                    val bitmap = rememberAttachmentBitmap(att.path)
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.SurfaceHigh)
                            .clickable { preview = att },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Outlined.Image, null, tint = AppColors.TextFaint)
                        }
                    }
                }
            }
        }
    }

    preview?.let { att ->
        Dialog(onDismissRequest = { preview = null }) {
            val bitmap = rememberAttachmentBitmap(att.path)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "查看大图",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
    }
}

@Composable
fun rememberAttachmentBitmap(path: String): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}
