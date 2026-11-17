package com.shenliao.deepchat.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .padding(horizontal = 26.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Accent))),
            contentAlignment = Alignment.Center,
        ) {
            Text("深", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "深聊 DeepChat",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextMain,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "API Key 在你自己手里，对话只存本机。",
            fontSize = 13.sp,
            color = AppColors.TextDim,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(34.dp))

        FeatureRow(Icons.Outlined.VpnKey, "填一次 Key", "去 platform.deepseek.com 建一个，粘进来即可")
        Spacer(Modifier.height(14.dp))
        FeatureRow(Icons.Outlined.Image, "能看图", "相册选图或直接拍照，让模型读截图、看照片")
        Spacer(Modifier.height(14.dp))
        FeatureRow(Icons.Outlined.AutoAwesome, "会思考", "先推理再回答，思考过程实时流出来")

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Accent)))
                .clickable { onOpenSettings() }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("填 API Key，开始用", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onSkip() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("先随便看看", color = AppColors.TextFaint, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AppColors.PrimaryDim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, color = AppColors.TextMain, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 11.sp, lineHeight = 16.sp, color = AppColors.TextFaint)
        }
    }
}
