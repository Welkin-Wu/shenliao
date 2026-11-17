package com.shenliao.deepchat.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
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
) {
    if (message.isUser) {
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
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        if (message.reasoning.isNotBlank()) {
            ThinkingBlock(message.reasoning, streaming = streaming)
            Spacer(Modifier.height(8.dp))
        }

        if (message.text.isNotBlank()) {
            MarkdownText(message.text)
        } else if (streaming && message.reasoning.isBlank()) {
            TypingDots()
        }

        if (message.error != null) {
            Spacer(Modifier.height(8.dp))
            ErrorCard(message.error, onRetry = onRegenerate)
        }

        if (!streaming && (message.text.isNotBlank() || message.error != null)) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.text.isNotBlank()) {
                    TextButton(onClick = { onCopy(message.text) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = AppColors.TextFaint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制", fontSize = 12.sp, color = AppColors.TextFaint)
                    }
                }
                if (isLast) {
                    TextButton(onClick = onRegenerate, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Outlined.Refresh, null, tint = AppColors.TextFaint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重试", fontSize = 12.sp, color = AppColors.TextFaint)
                    }
                }
                if (message.outputTokens > 0) {
                    Text(
                        text = "↑${message.inputTokens} ↓${message.outputTokens} tokens",
                        fontSize = 11.sp,
                        color = AppColors.TextFaint,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
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
