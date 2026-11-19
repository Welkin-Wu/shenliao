package com.shenliao.deepchat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 启动动画：先画一片海，再画一头鲸。
 *
 * 直接移植自工作区里的 `boot-splash/client.js`（社区版）：
 * 400×225 画布、xMidYMax slice、六条带种子抖动的海平线、九颗闪烁的星、
 * 2.1s 贝塞尔跃迁（九段关键帧）、两次破水的水花、三条 HUD 文案。
 * 点击任意处跳过。
 */

private const val ART_W = 400f
private const val ART_H = 225f
private const val HORIZON = 150f
private const val TAU = (PI * 2).toFloat()

/** 整段时长：3.0s 播放 + 0.62s 淡出（与社区版一致） */
private const val SPLASH_MS = 3000
private const val EXIT_MS = 620
private const val TOTAL_MS = SPLASH_MS + EXIT_MS

private val SKY_TOP = Color(0xFF040B18)
private val SKY_MID = Color(0xFF071830)
private val SKY_BOTTOM = Color(0xFF0D2743)
private val INK = Color(0xFFEAF3FF)
private val WATER_FILL = Color(0xCC041226)

private data class SeaLine(
    val y: Float,
    val amplitude: Float,
    val period: Float,
    val phase: Float,
    val wobble: Float,
    val opacity: Float,
    val delay: Float,
    val width: Float,
    val seed: Int,
)

private val SEA = listOf(
    SeaLine(150f, 1.4f, 210f, 0.4f, 0.5f, 0.95f, 0.05f, 2.4f, 11),
    SeaLine(161f, 2.2f, 148f, 1.9f, 0.6f, 0.62f, 0.16f, 2f, 23),
    SeaLine(173f, 2.8f, 118f, 3.4f, 0.7f, 0.46f, 0.27f, 2f, 37),
    SeaLine(186f, 3.2f, 96f, 5f, 0.8f, 0.34f, 0.38f, 2f, 41),
    SeaLine(199f, 3.6f, 78f, 0.9f, 0.8f, 0.25f, 0.49f, 2f, 53),
    SeaLine(213f, 4f, 64f, 2.6f, 0.9f, 0.18f, 0.6f, 2f, 67),
)

/** 鲸鱼（以自身原点为中心，取自社区版的 orca 路径） */
private val BODY = "M 100 0 C 94 -13 74 -22 44 -25 C 10 -28 -30 -24 -62 -15 " +
    "C -78 -10 -90 -6 -100 -2 C -90 5 -78 10 -62 15 " +
    "C -30 25 10 28 44 25 C 74 22 94 12 100 0 Z"
private val DORSAL = "M -18 -25 C -24 -42 -28 -55 -16 -57 C -8 -46 0 -34 12 -27 Z"
private val PECTORAL = "M 26 24 C 16 34 6 40 -4 40 C 0 32 10 26 22 22 Z"
private val TAIL_UP = "M -100 -4 C -112 -22 -128 -28 -138 -18 C -130 -10 -116 -6 -104 -2 Z"
private val TAIL_DOWN = "M -100 0 C -112 14 -126 20 -136 10 C -128 4 -114 2 -104 2 Z"
private val MOUTH = "M 92 2 C 86 7 78 8 72 5"

/** 跃迁关键帧：t / x / y / rotate */
private val LEAP = listOf(
    floatArrayOf(0.00f, 30f, 224f, 44f),
    floatArrayOf(0.14f, 72f, 196f, 40f),
    floatArrayOf(0.26f, 118f, 158f, 30f),
    floatArrayOf(0.40f, 172f, 112f, 16f),
    floatArrayOf(0.54f, 228f, 90f, 2f),
    floatArrayOf(0.66f, 284f, 102f, -12f),
    floatArrayOf(0.78f, 322f, 132f, -24f),
    floatArrayOf(0.86f, 352f, 164f, -32f),
    floatArrayOf(1.00f, 420f, 228f, -44f),
)

private val DROP_ANGLES = floatArrayOf(-64f, -48f, -34f, -20f, -8f, 14f, 30f)

// ---------------------------------------------------------------- 工具

private fun mulberry32(seed: Int): () -> Float {
    var state = seed
    return {
        state += 0x6D2B79F5.toInt()
        var v = (state xor (state ushr 15)) * (1 or state)
        v = (v + (v xor (v ushr 7)) * (61 or v)) xor v
        ((v xor (v ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

/** cubic-bezier 求值（与 CSS timing-function 同义） */
private fun bezier(x1: Float, y1: Float, x2: Float, y2: Float): (Float) -> Float {
    fun sampleX(t: Float): Float = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
    fun sampleY(t: Float): Float = 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
    return { x ->
        if (x <= 0f) 0f else if (x >= 1f) 1f else {
            var lo = 0f
            var hi = 1f
            var t = x
            repeat(24) {
                val v = sampleX(t)
                if (abs(v - x) < 1e-4f) return@repeat
                if (v < x) lo = t else hi = t
                t = (lo + hi) / 2
            }
            sampleY(t)
        }
    }
}

private val easeLeap = bezier(0.45f, 0f, 0.55f, 1f)
private val easeDraw = bezier(0.35f, 0.05f, 0.5f, 1f)
private val easeRing = bezier(0.2f, 0.6f, 0.4f, 1f)
private val easeDrop = bezier(0.25f, 0.6f, 0.4f, 1f)
private val easeRise = bezier(0.2f, 0.7f, 0.3f, 1f)

/** 极简 SVG path 解析：只支持 M / L / C / Z */
private fun svgPath(d: String): Path {
    val path = Path()
    val tokens = d.split(' ', ',', '\n').filter { it.isNotBlank() }
    var i = 0
    var cmd = 'M'
    fun num(): Float = tokens[i++].toFloat()

    while (i < tokens.size) {
        val tk = tokens[i]
        if (tk.length == 1 && tk[0].isLetter()) {
            cmd = tk[0]
            i++
            if (cmd == 'Z' || cmd == 'z') {
                path.close()
                continue
            }
        }
        when (cmd) {
            'M' -> {
                path.moveTo(num(), num())
                cmd = 'L'
            }
            'L' -> path.lineTo(num(), num())
            'C' -> {
                val x1 = num(); val y1 = num()
                val x2 = num(); val y2 = num()
                val x3 = num(); val y3 = num()
                path.cubicTo(x1, y1, x2, y2, x3, y3)
            }
            else -> i++
        }
    }
    return path
}

private fun mid(a: FloatArray, b: FloatArray) = floatArrayOf((a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f)

/** 与社区版 smoothPath 一致：中点起笔，逐点二次贝塞尔，末尾用反射控制点收尾 */
private fun smoothWave(points: List<FloatArray>): Path {
    val path = Path()
    if (points.size < 2) return path
    val start = mid(points[0], points[1])
    path.moveTo(start[0], start[1])
    var lastCtrlX = start[0]
    var lastCtrlY = start[1]
    for (index in 1 until points.size - 1) {
        val p = points[index]
        val next = mid(p, points[index + 1])
        path.quadraticTo(p[0], p[1], next[0], next[1])
        lastCtrlX = p[0]
        lastCtrlY = p[1]
    }
    val last = points.last()
    // T 指令：控制点 = 当前点关于上一个控制点的对称点
    val curX = lastCtrlX // 近似当前点
    val curY = lastCtrlY
    path.quadraticTo(2 * curX - lastCtrlX, 2 * curY - lastCtrlY, last[0], last[1])
    return path
}

private fun wavePath(line: SeaLine): Path {
    val random = mulberry32(line.seed)
    val points = mutableListOf<FloatArray>()
    var x = -24f
    while (x <= 424f) {
        val sway = sin((x / line.period) * TAU + line.phase) * line.amplitude
        val wobble = (random() - 0.5f) * line.wobble
        points.add(floatArrayOf(x, line.y + sway + wobble))
        x += 16f
    }
    return smoothWave(points)
}

private val WATER_PATH: Path = run {
    val random = mulberry32(11)
    val points = mutableListOf<FloatArray>()
    var x = -24f
    while (x <= 424f) {
        val sway = sin((x / 210f) * TAU + 0.4f) * 1.4f
        points.add(floatArrayOf(x, HORIZON + sway + (random() - 0.5f) * 0.5f))
        x += 16f
    }
    val p = smoothWave(points)
    p.lineTo(424f, 240f)
    p.lineTo(-24f, 240f)
    p.close()
    p
}

private data class Star(val x: Float, val y: Float, val radius: Float, val opacity: Float, val delaySec: Float, val durationSec: Float)

private val STARS: List<Star> = run {
    val random = mulberry32(97)
    (0 until 9).map {
        val x = 20f + random() * 360f
        val y = 14f + random() * 96f
        val radius = if (random() > 0.7f) 1.1f else 0.8f
        val opacity = 0.25f + random() * 0.4f
        val delay = -random() * 4f
        val duration = 3f + random() * 3f
        Star(x, y, radius, opacity, delay, duration)
    }
}

// ---------------------------------------------------------------- 组件

@Composable
fun WhaleSplash(onFinish: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var leaving by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsed = now - start
            if (elapsed >= SPLASH_MS && leaving < 0) leaving = elapsed
            if (leaving >= 0 && elapsed - leaving >= EXIT_MS) {
                onFinish()
                break
            }
        }
    }

    val exitProgress = if (leaving < 0) 0f else ((elapsed - leaving).toFloat() / EXIT_MS).coerceIn(0f, 1f)
    val globalAlpha = 1f - exitProgress
    val globalScale = 1f + 0.015f * exitProgress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SKY_TOP)
            .clickable { onFinish() },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 社区版是给横屏窗口设计的（xMidYMax slice）。竖屏手机上按宽度铺满 + 底对齐，
            // 再放大一点让海面别挤在最底下；否则取 max(w/400, h/225) 会把画面放大十倍只剩中间一条。
            val unit = size.width / ART_W * 1.3f
            val offsetX = (size.width - ART_W * unit) / 2f
            val offsetY = size.height - ART_H * unit

            // 天空铺满整屏（画布上方多出来的部分也要有渐变）
            drawRect(
                brush = Brush.verticalGradient(
                    0f to SKY_TOP,
                    0.55f to SKY_MID,
                    1f to SKY_BOTTOM,
                    startY = 0f,
                    endY = size.height,
                ),
                topLeft = Offset.Zero,
                size = size,
            )

            withTransform({
                translate(offsetX, offsetY)
                scale(unit, unit, pivot = Offset.Zero)
                scale(globalScale, globalScale, pivot = Offset(ART_W / 2f, ART_H / 2f))
            }) {
                drawScene(elapsed, globalAlpha, 1f)
            }

            // 暗角（社区版的 vignette）
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Transparent,
                    1f to Color(0x9E02060E),
                    center = Offset(size.width / 2f, size.height * 0.45f),
                    radius = size.maxDimension * 0.85f,
                ),
                topLeft = Offset.Zero,
                size = size,
            )
        }

        // HUD
        SplashHud(
            elapsed = elapsed,
            alpha = globalAlpha,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SplashHud(elapsed: Long, alpha: Float, modifier: Modifier = Modifier) {
    val rise = easeRise((elapsed / 900f).coerceIn(0f, 1f))
    val hintAlpha = ((elapsed - 1300).toFloat() / 900f).coerceIn(0f, 1f) * alpha
    val progress = (elapsed.toFloat() / SPLASH_MS).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 54.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "DEEPCHAT",
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 8.sp,
            color = Color(0xF0F0F6FF).copy(alpha = 0.92f * alpha * rise),
        )
        Text(
            text = "先画一片海，再画一头鲸",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
            color = Color(0xC6DEFA).copy(alpha = 0.52f * alpha * rise),
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .height(1.dp)
                .background(INK.copy(alpha = 0.16f * alpha)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(1.dp)
                    .background(INK.copy(alpha = 0.85f * alpha)),
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = "点击任意处跳过",
            fontSize = 11.sp,
            letterSpacing = 2.5.sp,
            color = Color(0xB0CCFF).copy(alpha = 0.32f * hintAlpha),
            modifier = Modifier.padding(bottom = 26.dp),
        )
    }
}

private fun DrawScope.drawScene(elapsed: Long, alpha: Float, unit: Float) {
    val w = ART_W * unit
    val h = ART_H * unit
    fun s(v: Float) = v * unit

    // 天空
    drawRect(
        brush = Brush.verticalGradient(
            0f to SKY_TOP,
            0.55f to SKY_MID,
            1f to SKY_BOTTOM,
             startY = 0f,
            endY = h,
        ),
        topLeft = Offset.Zero,
        size = Size(w, h),
        alpha = alpha,
    )

    // 月亮
    drawCircle(
        color = Color(0x38CFE2FF).copy(alpha = 0.22f * alpha),
        radius = s(21f),
        center = Offset(s(322f), s(52f)),
        style = Stroke(width = s(1.4f)),
    )

    // 星星闪烁
    for (star in STARS) {
        val phase = ((elapsed / 1000f + star.delaySec) % star.durationSec) / star.durationSec
        val twinkle = 0.12f + 0.53f * (0.5f + 0.5f * cos((phase * TAU).toDouble()).toFloat())
        drawCircle(
            color = Color(0xFFDBEAFF).copy(alpha = star.opacity * twinkle * alpha),
            radius = s(star.radius),
            center = Offset(s(star.x), s(star.y)),
        )
    }

    // 海面底色
    withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
        drawPath(WATER_PATH, WATER_FILL.copy(alpha = WATER_FILL.alpha * alpha))
    }

    // 六条海平线，依次画出
    val linePaths = rememberLines()
    for ((index, line) in SEA.withIndex()) {
        val startAt = line.delay * 1000f
        val p = easeDraw(((elapsed - startAt) / 950f).coerceIn(0f, 1f))
        if (p <= 0.001f) continue
        val path = linePaths[index]
        val drawn = partialPath(path, p)
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            drawPath(
                path = drawn,
                color = INK.copy(alpha = line.opacity * alpha),
                style = Stroke(width = line.width, cap = StrokeCap.Round),
            )
        }
    }

    // 鲸鱼（水面以上）
    val leapP = easeLeap(((elapsed - 750f) / 2100f).coerceIn(0f, 1f))
    if (elapsed >= 750) {
        val (tx, ty, rot) = sampleLeap(leapP)
        clipRect(
            left = 0f,
            top = 0f,
            right = w,
            bottom = s(HORIZON),
        ) {
            withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
                translate(tx, ty) {
                    rotate(rot, pivot = Offset.Zero) {
                        val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val ink = INK.copy(alpha = alpha)
                        drawPath(svgPath(BODY), ink, style = stroke)
                        drawPath(svgPath(DORSAL), ink, style = stroke)
                        drawPath(svgPath(PECTORAL), ink, style = stroke)
                        drawPath(svgPath(TAIL_UP), ink, style = stroke)
                        drawPath(svgPath(TAIL_DOWN), ink, style = stroke)
                        drawPath(svgPath(MOUTH), ink, style = stroke)
                        rotate(-16f, pivot = Offset(60f, -11f)) {
                            drawOval(
                                color = Color(0xFF040B18).copy(alpha = alpha),
                                topLeft = Offset(46f, -18f),
                                size = Size(28f, 14f),
                            )
                        }
                        drawCircle(
                            color = Color(0xFF0B1F38).copy(alpha = alpha),
                            radius = 1.8f,
                            center = Offset(69f, -9f),
                        )
                    }
                }
            }
        }
    }

    // 两次破水
    burst(elapsed, at = 104f, delayMs = 1280f, alpha = alpha, unit = unit, ease = { p -> easeRing(p) }, dropEase = { p -> easeDrop(p) })
    burst(elapsed, at = 334f, delayMs = 2420f, alpha = alpha, unit = unit, ease = { p -> easeRing(p) }, dropEase = { p -> easeDrop(p) })
}

/** 缓存的六条海平线路径（避免每帧重建） */
private var cachedLines: List<Path>? = null
private fun rememberLines(): List<Path> {
    cachedLines?.let { return it }
    val built = SEA.map { wavePath(it) }
    cachedLines = built
    return built
}

private fun rampPath(path: Path, progress: Float): Path {
    val measure = PathMeasure()
    measure.setPath(path, false)
    val segment = Path()
    measure.getSegment(0f, measure.length * progress, segment, true)
    return segment
}

private val measureCache = HashMap<Path, PathMeasure>()

private fun partialPath(path: Path, progress: Float): Path {
    val measure = measureCache.getOrPut(path) { PathMeasure().also { it.setPath(path, false) } }
    val segment = Path()
    measure.getSegment(0f, measure.length * progress, segment, true)
    return segment
}

private fun sampleLeap(p: Float): Triple<Float, Float, Float> {
    var i = 0
    while (i < LEAP.size - 2 && p > LEAP[i + 1][0]) i++
    val a = LEAP[i]
    val b = LEAP[i + 1]
    val span = (b[0] - a[0]).coerceAtLeast(1e-4f)
    val k = ((p - a[0]) / span).coerceIn(0f, 1f)
    return Triple(
        a[1] + (b[1] - a[1]) * k,
        a[2] + (b[2] - a[2]) * k,
        a[3] + (b[3] - a[3]) * k,
    )
}

private fun DrawScope.burst(
    elapsed: Long,
    at: Float,
    delayMs: Float,
    alpha: Float,
    unit: Float,
    ease: (Float) -> Float,
    dropEase: (Float) -> Float,
) {
    if (elapsed < delayMs) return
    val p = ease(((elapsed - delayMs) / 900f).coerceIn(0f, 1f))
    val cx = at * unit
    val cy = HORIZON * unit

    // 扩散的椭圆环
    val ringScale = 0.45f + (3.1f - 0.45f) * p
    val ringAlpha = when {
        p < 0.7f -> 0.85f - (0.85f - 0.35f) * (p / 0.7f)
        else -> 0.35f * (1f - (p - 0.7f) / 0.3f)
    }.coerceIn(0f, 1f)
    drawOval(
        color = INK.copy(alpha = ringAlpha * alpha),
        topLeft = Offset(cx - 16f * unit * ringScale, cy - 3.6f * unit * ringScale),
        size = Size(32f * unit * ringScale, 7.2f * unit * ringScale),
        style = Stroke(width = 1.6f * unit),
    )

    // 七根水珠
    DROP_ANGLES.forEachIndexed { index, angle ->
        val dp = dropEase(((elapsed - delayMs - index * 40f) / 850f).coerceIn(0f, 1f))
        if (dp <= 0.001f) return@forEachIndexed
        val dropAlpha = when {
            dp < 0.18f -> 0.95f * (dp / 0.18f)
            else -> 0.95f * (1f - (dp - 0.18f) / 0.82f)
        }.coerceIn(0f, 1f)
        val dy = -42f * dp
        val rad = Math.toRadians(angle.toDouble())
        val dx = -sin(rad).toFloat()
        val dyy = -cos(rad).toFloat()
        drawLine(
            color = INK.copy(alpha = dropAlpha * alpha),
            start = Offset(cx + dx * 9f * unit, cy + dyy * 9f * unit + dy * unit),
            end = Offset(cx + dx * 15f * unit, cy + dyy * 15f * unit + dy * unit),
            strokeWidth = 1.6f * unit,
            cap = StrokeCap.Round,
        )
    }
}
