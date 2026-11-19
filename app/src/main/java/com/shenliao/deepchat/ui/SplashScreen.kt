package com.shenliao.deepchat.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenliao.deepchat.R
import kotlin.math.abs

/**
 * 启动画面：移植自社区插件 `dsh-splash-fish`（v0.3.0）。
 *
 * 原版是「从黑场里显影 → 模糊聚焦 → 缓慢推近 → 光斑/扫光/浮尘 → 淡出进工作区」，
 * 时序全部照抄 client.template.js：
 *   - sfFade   1250ms 透明度 0→1
 *   - sfFocus  1400ms blur 28→0、brightness .28→.92、scale 1.14→1.02
 *   - sfDrift  3000ms 延迟 1300ms，scale 1.02→1.06
 *   - sfVignette 1500ms 延迟 200ms、sfGlow 1800ms 延迟 260ms
 *   - sfSheen  1500ms 延迟 1250ms 扫光
 *   - 字标 620ms、副标 900ms、细线 420ms 进场
 *   - sfFlash  3320ms 闪白 620ms
 *   - sfExit   680ms 淡出 + 放大到 1.045
 *
 * 原版把头发位移做成带 alpha 的 VP9 WebM 叠在静态图上；Android 这边没法直接解带 alpha 的视频，
 * 所以用底图 + 推近 + 模糊聚焦来还原观感，头发的逐帧位移这一步没有做。
 */

private const val T_FADE = 1250L
private const val T_FOCUS = 1400L
private const val T_DRIFT_DELAY = 1300L
private const val T_DRIFT = 3000L
private const val T_FLASH_AT = 3320L
private const val T_FLASH = 620L
private const val T_HOLD = 4300L
private const val T_EXIT = 680L
private const val T_TOTAL = T_HOLD + T_EXIT

private val SHEEN_START = 1250L
private const val SHEEN_LEN = 1500L

private fun ease(progress: Float): Float = progress.coerceIn(0f, 1f)

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var leavingAt by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsed = now - start
            if (elapsed >= T_HOLD && leavingAt < 0) leavingAt = elapsed
            if (leavingAt >= 0 && elapsed - leavingAt >= T_EXIT) {
                onFinish()
                break
            }
        }
    }

    val t = elapsed
    val exitP = if (leavingAt < 0) 0f else ease((t - leavingAt).toFloat() / T_EXIT)

    // sfFade
    val fadeIn = ease(t.toFloat() / T_FADE)
    // sfFocus
    val focus = ease(t.toFloat() / T_FOCUS)
    val focusEased = focus * focus * (3 - 2 * focus) // smoothstep，接近 cubic-bezier(.22,.61,.36,1)
    val blurPx = 28f * (1 - focusEased)
    val brightness = 0.28f + (0.92f - 0.28f) * focusEased
    // sfFocus 的 1.14→1.02，再接 sfDrift 的 1.02→1.06
    val focusScale = 1.14f + (1.02f - 1.14f) * focusEased
    val driftP = if (t <= T_DRIFT_DELAY) 0f else ease((t - T_DRIFT_DELAY).toFloat() / T_DRIFT)
    val scale = focusScale * (1.02f + (1.06f - 1.02f) * driftP / 1.02f)
    // sfExit
    val exitScale = 1f + 0.045f * exitP
    val alpha = fadeIn * (1f - exitP)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onFinish() },
    ) {
        // 底图：一张 16:9 的插画放进竖屏，直接裁会只剩一只眼睛。
        // 所以铺一层模糊放大版当背景，再把完整原图居中放上去。
        val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0.4f) {
            Modifier.blur(blurPx.dp)
        } else {
            Modifier
        }
        Image(
            painter = painterResource(R.drawable.splash_fish),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale * exitScale
                    scaleY = scale * exitScale
                    this.alpha = alpha * 0.85f
                }
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(48.dp) else Modifier,
                ),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f * alpha)))
        Image(
            painter = painterResource(R.drawable.splash_fish),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale * exitScale
                    scaleY = scale * exitScale
                    this.alpha = alpha
                }
                .then(blurModifier),
        )

        // brightness 用黑罩近似（0.28 → 0.92）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ((1f - brightness) * alpha).coerceIn(0f, 1f))),
        )

        // sfGlow：人物那一侧透出来的柔光
        Canvas(Modifier.fillMaxSize()) {
            val glowP = ease((t - 260f) / 1800f)
            if (glowP > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color(0x40A9D6FF).copy(alpha = 0.42f * glowP * alpha),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.66f, size.height * 0.44f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            }
        }

        // sfSheen：斜向扫光
        val sheenP = ease((t - SHEEN_START).toFloat() / SHEEN_LEN)
        if (t >= SHEEN_START && sheenP < 1f) {
            val sweepAlpha = when {
                sheenP < 0.14f -> sheenP / 0.14f
                else -> 1f - sheenP
            }.coerceIn(0f, 1f) * 0.5f * (1f - exitP)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (-0.7f + 1.4f * sheenP) * size.width
                        rotationZ = -18f
                    }
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to Color(0x66DCEBFF),
                            1f to Color.Transparent,
                        ),
                        shape = RoundedCornerShape(0.dp),
                    )
                    .then(Modifier)
                    .graphicsLayer { this.alpha = sweepAlpha },
            )
        }

        // sfVignette
        val vignetteP = ease((t - 200f) / 1500f)
        Canvas(Modifier.fillMaxSize()) {
            if (vignetteP > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color(0xB3060C16),
                        center = Offset(size.width / 2f, size.height * 0.45f),
                        radius = size.maxDimension * 0.78f,
                    ),
                    alpha = vignetteP * alpha,
                )
            }
        }

        // sfFlash：3320ms 起闪一下白
        val flashP = ease((t - T_FLASH_AT).toFloat() / T_FLASH)
        if (t >= T_FLASH_AT && flashP < 1f) {
            val fa = when {
                flashP < 0.22f -> 0.42f * (flashP / 0.22f)
                else -> 0.42f * (1f - (flashP - 0.22f) / 0.78f)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = fa.coerceIn(0f, 1f) * (1f - exitP))),
            )
        }

        // 字标 / 细线 / 副标
        SplashMark(t = t, alpha = alpha, exitP = exitP)

        // 底部提示
        Text(
            text = "点击任意处跳过",
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = Color(0x9EB9D8FF).copy(alpha = 0.55f * alpha * ease((t - 900f) / 900f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp),
        )
    }
}

@Composable
private fun SplashMark(t: Long, alpha: Float, exitP: Float) {
    val ruleP = ease((t - 420f) / 900f)
    val markP = ease((t - 620f) / 1000f)
    val subP = ease((t - 900f) / 900f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 30.dp, end = 30.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        // 细线
        Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth(0.24f * ruleP)
                .background(Color(0xFFCFE4FF).copy(alpha = 0.55f * alpha)),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "DeepChat",
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp,
            color = Color(0xFFF2F7FF).copy(alpha = 0.94f * markP * alpha),
            modifier = Modifier.graphicsLayer { translationY = (1f - markP) * 26f },
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { translationY = (1f - subP) * 20f },
        ) {
            // 会呼吸的小光点
            val breathe = 0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.cos((t / 1400f) * Math.PI.toFloat() * 2))
            Box(
                Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFA9D6FF).copy(alpha = 0.9f * breathe * subP * alpha)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "把 DeepSeek 装进口袋",
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                color = Color(0xFFB9D2F0).copy(alpha = 0.72f * subP * alpha),
            )
        }
        Spacer(Modifier.height(6.dp))
        if (exitP > 0f) {
            Spacer(Modifier.height(0.dp))
        }
    }
}
