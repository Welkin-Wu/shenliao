package com.shenliao.deepchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 上次启动崩溃时先显示这个，让用户能清数据、复制堆栈，而不是永远闪退。
 */
@Composable
fun CrashScreen(
    trace: String,
    onReset: () -> Unit,
    onContinue: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text("上次启动时崩了", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextMain)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "下面这段堆栈可以直接复制发给我。急着用的话点「清空本地数据」——对话会没了，但 App 能正常打开。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = AppColors.TextDim,
        )

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Surface)
                .padding(12.dp),
        ) {
            Text(
                text = trace.take(6000),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                color = AppColors.CodeText,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                text = "复制堆栈",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = { clipboard.setText(AnnotatedString(trace)) },
            )
            ActionButton(
                text = "清空本地数据",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = onReset,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onContinue() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("忽略这次崩溃，继续用", fontSize = 13.sp, color = AppColors.TextFaint)
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (primary) AppColors.Primary else AppColors.Surface
    val fg = if (primary) Color.White else AppColors.TextMain
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 14.sp, color = fg, textAlign = TextAlign.Center)
    }
}
