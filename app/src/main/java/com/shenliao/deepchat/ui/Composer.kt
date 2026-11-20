package com.shenliao.deepchat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenliao.deepchat.data.Attachment

@Composable
fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    pending: List<Attachment>,
    onRemovePending: (String) -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit,
    streaming: Boolean,
    thinking: Boolean,
    onToggleThinking: () -> Unit,
    webSearch: Boolean,
    onToggleWeb: () -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modelLabel: String,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
    ) {
        if (pending.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                pending.forEach { att ->
                    if (att.isImage) {
                        PendingImage(att, onRemove = { onRemovePending(att.id) })
                    } else {
                        PendingDoc(att, onRemove = { onRemovePending(att.id) })
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Box {
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "添加附件",
                        tint = AppColors.TextDim,
                        modifier = Modifier.size(24.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = AppColors.SurfaceHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("相册图片", fontSize = 14.sp, color = AppColors.TextMain) },
                        leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null, tint = AppColors.TextDim) },
                        onClick = {
                            menuOpen = false
                            onPickImage()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("拍照", fontSize = 14.sp, color = AppColors.TextMain) },
                        leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, tint = AppColors.TextDim) },
                        onClick = {
                            menuOpen = false
                            onTakePhoto()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("PDF / Word / PPT 文件", fontSize = 14.sp, color = AppColors.TextMain) },
                        leadingIcon = { Icon(Icons.Outlined.Description, null, tint = AppColors.TextDim) },
                        onClick = {
                            menuOpen = false
                            onPickFile()
                        },
                    )
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("问点什么…", color = AppColors.TextFaint, fontSize = 15.sp) },
                maxLines = 6,
                shape = RoundedCornerShape(28.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppColors.Surface,
                    unfocusedContainerColor = AppColors.Surface,
                    focusedBorderColor = AppColors.Primary.copy(alpha = 0.55f),
                    unfocusedBorderColor = AppColors.Border,
                    cursorColor = AppColors.Primary,
                    focusedTextColor = AppColors.TextMain,
                    unfocusedTextColor = AppColors.TextMain,
                ),
            )

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (streaming) SolidColor(AppColors.SurfaceHigh)
                        else if (canSend) Brush.linearGradient(listOf(AppColors.Primary, AppColors.Accent))
                        else SolidColor(AppColors.SurfaceHigh),
                    )
                    .clickable(enabled = streaming || canSend) {
                        if (streaming) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (streaming) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                    contentDescription = if (streaming) "停止" else "发送",
                    tint = if (streaming || canSend) Color.White else AppColors.TextFaint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallChip(
                selected = thinking,
                onClick = onToggleThinking,
                icon = Icons.Outlined.AutoAwesome,
                label = if (thinking) "思考中" else "直接回答",
            )
            Spacer(Modifier.width(8.dp))
            SmallChip(
                selected = webSearch,
                onClick = onToggleWeb,
                icon = Icons.Outlined.Public,
                label = if (webSearch) "联网" else "离线",
            )
            Spacer(Modifier.width(8.dp))
            SmallChip(
                selected = false,
                onClick = onPickFile,
                icon = Icons.Outlined.AttachFile,
                label = "文件",
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = modelLabel,
                fontSize = 11.sp,
                color = AppColors.TextFaint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PendingImage(att: Attachment, onRemove: () -> Unit) {
    val bitmap = rememberAttachmentBitmap(att.path)
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceHigh),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "待发送图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        RemoveBadge(onRemove)
    }
}

@Composable
private fun PendingDoc(att: Attachment, onRemove: () -> Unit) {
    val tint = when (att.state) {
        Attachment.STATE_ERROR -> AppColors.Danger
        Attachment.STATE_UPLOADING -> AppColors.TextDim
        else -> AppColors.Primary
    }
    Row(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = att.name.ifBlank { "文件" },
                fontSize = 12.sp,
                color = AppColors.TextMain,
                maxLines = 1,
            )
            Text(
                text = when (att.state) {
                    Attachment.STATE_UPLOADING -> "上传解析中…"
                    Attachment.STATE_ERROR -> att.error ?: "上传失败"
                    else -> humanSize(att.sizeBytes)
                },
                fontSize = 10.sp,
                color = if (att.state == Attachment.STATE_ERROR) AppColors.Danger else AppColors.TextFaint,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "移除", tint = AppColors.TextFaint, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun BoxScope.RemoveBadge(onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(2.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(0xCC000000))
            .clickable { onRemove() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Close, "移除", tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

@Composable
fun SmallChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AppColors.PrimaryDim else AppColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) AppColors.Primary.copy(alpha = 0.45f) else AppColors.Border,
                shape = RoundedCornerShape(50),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) AppColors.Primary else AppColors.TextFaint,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) AppColors.Primary else AppColors.TextDim,
        )
    }
}

fun humanSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
