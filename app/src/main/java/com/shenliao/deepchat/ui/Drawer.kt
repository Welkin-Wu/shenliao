package com.shenliao.deepchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenliao.deepchat.data.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DrawerContent(
    conversations: List<Conversation>,
    currentId: String?,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }

    ModalDrawerSheet(drawerContainerColor = AppColors.Surface) {
        Column(Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 12.dp, top = 22.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Accent))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("深", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("深聊", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMain)
                    Text("DeepChat", fontSize = 11.sp, color = AppColors.TextFaint)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.PrimaryDim)
                    .clickable { onNewChat() }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AddComment, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("新对话", fontSize = 14.sp, color = AppColors.Primary, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(10.dp))

            if (conversations.isEmpty()) {
                Text(
                    text = "还没有对话记录",
                    fontSize = 12.sp,
                    color = AppColors.TextFaint,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        val selected = conv.id == currentId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AppColors.SurfaceHigh else Color.Transparent)
                                .clickable { onOpen(conv.id) }
                                .padding(start = 12.dp, end = 2.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    fontSize = 13.sp,
                                    color = if (selected) AppColors.TextMain else AppColors.TextDim,
                                    maxLines = 1,
                                )
                                Text(
                                    text = relativeTime(conv.updatedAt),
                                    fontSize = 10.sp,
                                    color = AppColors.TextFaint,
                                )
                            }
                            IconButton(
                                onClick = { pendingDelete = conv },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "删除",
                                    tint = AppColors.TextFaint,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = AppColors.Border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Settings, null, tint = AppColors.TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("设置", fontSize = 14.sp, color = AppColors.TextDim)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    pendingDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这段对话？", fontSize = 16.sp) },
            text = { Text("「${conv.title}」将连同图片一起删除，无法恢复。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(conv.id)
                    pendingDelete = null
                }) { Text("删除", color = AppColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextMain,
            textContentColor = AppColors.TextDim,
        )
    }
}

fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ts))
    }
}
