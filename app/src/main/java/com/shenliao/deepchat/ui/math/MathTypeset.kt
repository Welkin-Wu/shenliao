package com.shenliao.deepchat.ui.math

import kotlin.math.max
import kotlin.math.min

data class TextBox(
    val text: String,
    val width: Float,
    val ascent: Float,
    val descent: Float,
)

/** 由宿主提供文字度量（Android 用 TextMeasurer，桌面预览用 Java2D） */
interface TextMeter {
    fun measure(text: String, sizePx: Float, italic: Boolean, bold: Boolean): TextBox
}

sealed interface MOp {
    /** y 为基线位置（盒子坐标系，y 向下） */
    data class Txt(
        val text: String,
        val x: Float,
        val y: Float,
        val size: Float,
        val italic: Boolean,
        val bold: Boolean,
    ) : MOp

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val w: Float) : MOp

    /** 扁平点集：x0,y0,x1,y1... */
    data class Poly(val pts: FloatArray, val w: Float) : MOp
}

class MathBox(val width: Float, val ascent: Float, val descent: Float, val ops: List<MOp>) {
    val height: Float get() = ascent + descent
}

private fun List<MOp>.shift(dx: Float, dy: Float): List<MOp> = map { op ->
    when (op) {
        is MOp.Txt -> op.copy(x = op.x + dx, y = op.y + dy)
        is MOp.Line -> op.copy(x1 = op.x1 + dx, y1 = op.y1 + dy, x2 = op.x2 + dx, y2 = op.y2 + dy)
        is MOp.Poly -> MOp.Poly(
            FloatArray(op.pts.size) { idx -> op.pts[idx] + if (idx % 2 == 0) dx else dy },
            op.w,
        )
    }
}

/**
 * 把 [MNode] 排成盒子。所有坐标以盒子左上角为原点，operators 的 Txt.y 是基线。
 */
class MathTypesetter(
    private val meter: TextMeter,
    private val baseSize: Float,
    private val display: Boolean,
) {

    private val scriptRatio = 0.72f

    fun layout(node: MNode): MathBox = when (node) {
        MNode.Empty -> MathBox(0f, baseSize * 0.72f, baseSize * 0.24f, emptyList())
        is MNode.Sym -> glyph(node.text, baseSize, node.italic, node.bold)
        is MNode.TextRun -> glyph(node.text, baseSize, false, node.bold)
        is MNode.Row -> layoutRow(node.items)
        is MNode.Space -> MathBox(
            node.em * baseSize,
            baseSize * 0.5f,
            baseSize * 0.1f,
            emptyList(),
        )
        is MNode.Script -> layoutScript(node)
        is MNode.Frac -> layoutFrac(node)
        is MNode.Sqrt -> layoutSqrt(node)
        is MNode.BigOp -> layoutBigOp(node)
        is MNode.Delim -> layoutDelim(node)
        is MNode.Accent -> layoutAccent(node)
        is MNode.Matrix -> layoutMatrix(node)
    }

    // ---------------------------------------------------------------- 基础

    private fun glyph(text: String, size: Float, italic: Boolean, bold: Boolean): MathBox {
        if (text.isEmpty()) return MathBox(0f, size * 0.72f, size * 0.24f, emptyList())
        val tb = meter.measure(text, size, italic, bold)
        val baseline = tb.ascent
        return MathBox(
            tb.width,
            tb.ascent,
            tb.descent,
            listOf(MOp.Txt(tb.text, 0f, baseline, size, italic, bold)),
        )
    }

    private fun layoutAt(node: MNode, size: Float): MathBox =
        MathTypesetter(meter, size, display).layout(node)

    private fun layoutRow(items: List<MNode>): MathBox {
        val boxes = items.map { layout(it) }
        if (boxes.isEmpty()) return MathBox(0f, 0f, 0f, emptyList())
        val ascent = boxes.maxOf { it.ascent }
        val descent = boxes.maxOf { it.descent }
        var x = 0f
        val ops = mutableListOf<MOp>()
        boxes.forEachIndexed { index, b ->
            if (index > 0) {
                // 关系/运算符两侧留一点空，别把 = 和括号糊在一起
                val gapEm = maxOf(relationWeight(items[index - 1]), relationWeight(items[index]))
                x += gapEm * baseSize * 0.22f
            }
            ops.addAll(b.ops.shift(x, ascent - b.ascent))
            x += b.width
        }
        return MathBox(x, ascent, descent, ops)
    }

    private fun relationWeight(node: MNode): Float = when {
        node is MNode.Sym && node.text in RELATION_CHARS -> 1f
        node is MNode.BigOp -> 0.6f
        else -> 0f
    }

    private val RELATION_CHARS = setOf(
        "+", "−", "=", "<", ">", "≤", "≥", "≠", "≈", "≡", "±", "∓",
        "×", "·", "÷", "→", "⇒", "⇔", "∈", "∉", "⊂", "⊆", "∪", "∩", "∝",
    )

    private fun layoutScript(node: MNode.Script): MathBox {
        val base = layout(node.base)
        val scriptSize = baseSize * scriptRatio
        val sup = node.sup?.let { layoutAt(it, scriptSize) }
        val sub = node.sub?.let { layoutAt(it, scriptSize) }

        // 右侧脚本的宽度
        val rightWidth = max(sup?.width ?: 0f, sub?.width ?: 0f)
        val gap = baseSize * 0.04f
        val width = base.width + gap + rightWidth

        val supRaise = max(baseSize * 0.38f, base.ascent * 0.42f)
        val subDrop = baseSize * 0.22f

        var ascent = base.ascent
        var descent = base.descent
        val ops = mutableListOf<MOp>()
        ops.addAll(base.ops.shift(0f, 0f))

        sup?.let {
            // sup 的基线在 base 基线之上 supRaise
            val topY = base.ascent - supRaise - it.ascent
            ops.addAll(it.ops.shift(base.width + gap, topY))
            ascent = max(ascent, supRaise + it.height)
        }
        sub?.let {
            val topY = base.ascent + subDrop - it.ascent
            ops.addAll(it.ops.shift(base.width + gap, topY))
            descent = max(descent, it.height - subDrop)
        }
        return MathBox(width, ascent, descent.coerceAtLeast(base.descent), ops)
    }

    private fun layoutFrac(node: MNode.Frac): MathBox {
        val fracSize = baseSize * if (display) 0.95f else 0.86f
        val num = layoutAt(node.num, fracSize)
        val den = layoutAt(node.den, fracSize)
        val pad = baseSize * 0.16f
        val gap = baseSize * (if (display) 0.16f else 0.12f)
        val rule = max(1f, baseSize * 0.05f)
        val axis = baseSize * 0.30f // 分数线高于基线多少
        val width = max(num.width, den.width) + pad * 2

        val ascent = axis + gap + num.height
        val descent = max(-axis + rule + gap + den.height, baseSize * 0.22f)
        val baseline = ascent

        val ops = mutableListOf<MOp>()
        val numTop = baseline - axis - gap - num.height
        ops.addAll(num.ops.shift((width - num.width) / 2f, numTop))
        val barY = baseline - axis
        ops.add(MOp.Line(pad * 0.35f, barY, width - pad * 0.35f, barY, rule))
        val denTop = barY + rule + gap
        ops.addAll(den.ops.shift((width - den.width) / 2f, denTop))
        return MathBox(width, ascent, descent, ops)
    }

    private fun layoutSqrt(node: MNode.Sqrt): MathBox {
        val body = layout(node.body)
        val thickness = max(1.2f, baseSize * 0.055f)
        val pad = baseSize * 0.10f
        val gap = baseSize * 0.13f
        val radicalW = max(baseSize * 0.52f, body.height * 0.34f)

        val indexBox = node.index?.let { layoutAt(it, baseSize * 0.52f) }
        val indexW = if (indexBox != null) indexBox.width * 0.8f else 0f
        val leftW = radicalW + indexW

        // 顶横线贴盒子顶部，本体从下面开始
        val barY = 0f
        val bodyTop = thickness + gap
        val ascent = bodyTop + body.ascent
        val descent = body.descent + baseSize * 0.08f
        val width = leftW + body.width + pad

        val ops = mutableListOf<MOp>()
        ops.addAll(body.ops.shift(leftW, bodyTop))

        val bottomY = ascent + body.descent * 0.55f
        val midY = (barY + bottomY) / 2f
        val pts = floatArrayOf(
            indexW, midY + (bottomY - midY) * 0.15f,
            indexW + radicalW * 0.34f, bottomY,
            indexW + radicalW * 0.62f, barY + thickness / 2f,
            width, barY + thickness / 2f,
        )
        ops.add(MOp.Poly(pts, thickness))

        indexBox?.let { idx ->
            val topY = barY + thickness - idx.ascent * 0.55f
            ops.addAll(idx.ops.shift(0f, topY))
        }
        return MathBox(width, ascent, descent, ops)
    }

    private fun layoutBigOp(node: MNode.BigOp): MathBox {
        val opSize = baseSize * if (display) 1.55f else 1.15f
        val opBox = glyph(node.symbol, opSize, false, false)
        val scriptSize = baseSize * 0.66f
        val sub = node.sub?.let { layoutAt(it, scriptSize) }
        val sup = node.sup?.let { layoutAt(it, scriptSize) }

        val useLimits = node.limits && display
        return if (useLimits && (sub != null || sup != null)) {
            val width = maxOf(opBox.width, sup?.width ?: 0f, sub?.width ?: 0f)
            var ascent = opBox.ascent
            var descent = opBox.descent
            val ops = mutableListOf<MOp>()
            var top = 0f
            sup?.let {
                ascent += it.height + baseSize * 0.06f
            }
            sub?.let {
                descent += it.height + baseSize * 0.06f
            }
            val baseline = ascent
            sup?.let { s ->
                val y = baseline - opBox.ascent - baseSize * 0.06f - s.height
                ops.addAll(s.ops.shift((width - s.width) / 2f, y))
            }
            ops.addAll(opBox.ops.shift((width - opBox.width) / 2f, baseline - opBox.ascent))
            sub?.let { s ->
                val y = baseline + opBox.descent + baseSize * 0.06f
                ops.addAll(s.ops.shift((width - s.width) / 2f, y))
            }
            MathBox(width, ascent, descent, ops)
        } else {
            // 右侧上下标
            val baseW = opBox.width
            val rightW = max(sup?.width ?: 0f, sub?.width ?: 0f)
            val gap = baseSize * 0.03f
            var ascent = opBox.ascent
            var descent = opBox.descent
            val ops = mutableListOf<MOp>()
            ops.addAll(opBox.ops.shift(0f, 0f))
            val baseline = opBox.ascent
            sup?.let {
                val y = baseline - max(baseSize * 0.42f, opBox.ascent * 0.4f) - it.ascent
                ops.addAll(it.ops.shift(baseW + gap, y))
                ascent = max(ascent, baseline - y)
            }
            sub?.let {
                val y = baseline + baseSize * 0.2f
                ops.addAll(it.ops.shift(baseW + gap, y))
                descent = max(descent, y + it.height - baseline)
            }
            MathBox(baseW + gap + rightW, ascent, descent, ops)
        }
    }

    private fun layoutDelim(node: MNode.Delim): MathBox {
        val body = layout(node.body)
        val open = normalizeDelim(node.open)
        val close = normalizeDelim(node.close)
        val needHeight = max(body.height, baseSize * 1.05f)
        val thickness = max(1.1f, baseSize * 0.05f)
        val widthEach = if (open.isEmpty()) 0f else baseSize * 0.34f
        val widthEach2 = if (close.isEmpty()) 0f else baseSize * 0.34f
        val gap = baseSize * 0.05f
        val totalW = widthEach + gap + body.width + gap + widthEach2

        val ascent = max(body.ascent, needHeight * 0.62f)
        val descent = max(body.descent, needHeight * 0.38f)
        val baseline = ascent
        val ops = mutableListOf<MOp>()

        if (open.isNotEmpty()) {
            ops.addAll(delimOps(open, 0f, 0f, widthEach, ascent + descent, thickness))
        }
        val bodyX = widthEach + gap
        ops.addAll(body.ops.shift(bodyX, ascent - body.ascent))
        if (close.isNotEmpty()) {
            ops.addAll(delimOps(close, totalW - widthEach2, 0f, widthEach2, ascent + descent, thickness))
        }
        return MathBox(totalW, ascent, descent, ops)
    }

    private fun normalizeDelim(d: String): String = when (d) {
        "", "." -> ""
        "\\{" -> "{"
        "\\}" -> "}"
        "\\|" -> "‖"
        "\\langle" -> "⟨"
        "\\rangle" -> "⟩"
        "\\lfloor" -> "⌊"
        "\\rfloor" -> "⌋"
        "\\lceil" -> "⌈"
        "\\rceil" -> "⌉"
        else -> d
    }

    private fun delimOps(d: String, x: Float, y: Float, w: Float, h: Float, t: Float): List<MOp> {
        val pad = h * 0.06f
        val top = y + pad
        val bottom = y + h - pad
        return when (d) {
            "(" , ")" -> {
                // 用正弦曲线逼近弧线，比折线顺眼
                val steps = 12
                val pts = FloatArray((steps + 1) * 2)
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val y = top + (bottom - top) * t
                    val bulge = kotlin.math.sin(Math.PI * t).toFloat()
                    val xPos = if (d == "(") x + w - w * bulge else x + w * bulge
                    pts[i * 2] = xPos
                    pts[i * 2 + 1] = y
                }
                listOf(MOp.Poly(pts, t))
            }
            "[" , "]" -> listOf(
                MOp.Poly(floatArrayOf(x + w, top, x + t, top, x + t, bottom, x + w, bottom), t),
            )
            "{" , "}" -> {
                val mid = (top + bottom) / 2f
                val pts = floatArrayOf(
                    x + w, top,
                    x + t * 1.5f, top + h * 0.06f,
                    x + t * 1.5f, mid - h * 0.12f,
                    x, mid,
                    x + t * 1.5f, mid + h * 0.12f,
                    x + t * 1.5f, bottom - h * 0.06f,
                    x + w, bottom,
                )
                listOf(MOp.Poly(pts, t))
            }
            "|" -> listOf(MOp.Line(x + w / 2f, top, x + w / 2f, bottom, t))
            "‖" -> listOf(
                MOp.Line(x + w * 0.25f, top, x + w * 0.25f, bottom, t),
                MOp.Line(x + w * 0.75f, top, x + w * 0.75f, bottom, t),
            )
            "⟨" , "⟩" -> listOf(
                MOp.Poly(floatArrayOf(x + w, top, x + t, top + h * 0.2f, x + t, bottom - h * 0.2f, x + w, bottom), t),
            )
            "⌊" , "⌋" -> listOf(
                if (d == "⌊") MOp.Poly(floatArrayOf(x + w, top, x + t, top, x + t, bottom, x + w, bottom), t)
                else MOp.Poly(floatArrayOf(x, top, x + w - t, top, x + w - t, bottom, x, bottom), t),
            )
            "⌈" , "⌉" -> listOf(
                if (d == "⌈") MOp.Poly(floatArrayOf(x + t, bottom, x + t, top, x + w, top), t)
                else MOp.Poly(floatArrayOf(x + w - t, bottom, x + w - t, top, x, top), t),
            )
            else -> emptyList()
        }
    }

    private fun layoutAccent(node: MNode.Accent): MathBox {
        val body = layout(node.body)
        val t = max(1.1f, baseSize * 0.05f)
        val padV = baseSize * 0.12f
        val width = body.width
        val ascent = body.ascent + padV + t * 2
        val descent = body.descent
        val baseline = ascent
        val ops = mutableListOf<MOp>()
        ops.addAll(body.ops.shift(0f, ascent - body.ascent))
        val topY = ascent - body.ascent - padV
        when (node.kind) {
            MAccent.BAR -> ops.add(MOp.Line(0f, topY, width, topY, t))
            MAccent.HAT -> ops.add(
                MOp.Poly(
                    floatArrayOf(width * 0.2f, topY + t, width * 0.5f, topY - t, width * 0.8f, topY + t),
                    t,
                ),
            )
            MAccent.VEC -> {
                ops.add(MOp.Line(width * 0.1f, topY, width * 0.9f, topY, t))
                ops.add(
                    MOp.Poly(
                        floatArrayOf(
                            width * 0.9f - t * 3f, topY - t * 2.2f,
                            width * 0.9f, topY,
                            width * 0.9f - t * 3f, topY + t * 2.2f,
                        ),
                        t,
                    ),
                )
            }
            MAccent.TILDE -> ops.add(
                MOp.Poly(
                    floatArrayOf(
                        width * 0.15f, topY + t,
                        width * 0.35f, topY - t,
                        width * 0.65f, topY + t,
                        width * 0.85f, topY - t,
                    ),
                    t,
                ),
            )
            else -> ops.add(
                MOp.Line(width * 0.45f, topY - t, width * 0.55f, topY - t, t * 1.2f),
            )
        }
        return MathBox(width, ascent, descent, ops)
    }

    private fun layoutMatrix(node: MNode.Matrix): MathBox {
        val rows = node.rows
        if (rows.isEmpty()) return MathBox(0f, 0f, 0f, emptyList())
        val cellGap = baseSize * 0.6f
        val rowGap = baseSize * 0.42f
        val grid: List<List<MathBox>> = rows.map { r -> r.map { layout(it) } }
        val cols = grid.maxOf { it.size }
        val colW = FloatArray(cols) { c -> grid.maxOf { r -> if (c < r.size) r[c].width else 0f } }
        val rowH = FloatArray(grid.size) { r -> grid[r].maxOfOrNull { it.height } ?: 0f }

        val contentW = colW.sum() + cellGap * (cols - 1).coerceAtLeast(0)
        val contentH = rowH.sum() + rowGap * (grid.size - 1).coerceAtLeast(0)

        val thickness = max(1.1f, baseSize * 0.05f)
        val bracketW = if (node.open.isEmpty() && node.close.isEmpty()) 0f else baseSize * 0.3f
        val padX = if (bracketW > 0f) baseSize * 0.12f else 0f
        val width = contentW + (bracketW + padX) * 2

        val ascent = max(contentH * 0.62f, baseSize * 0.8f)
        val descent = max(contentH - ascent, baseSize * 0.2f)
        val ops = mutableListOf<MOp>()

        var y = ascent - contentH / 2f
        grid.forEachIndexed { r, cells ->
            var x = bracketW + padX
            cells.forEachIndexed { c, box ->
                val cellCenterX = x + colW[c] / 2f
                val boxTop = y + (rowH[r] - box.height) / 2f
                ops.addAll(box.ops.shift(cellCenterX - box.width / 2f, boxTop))
                x += colW[c] + cellGap
            }
            y += rowH[r] + rowGap
        }

        if (node.open.isNotEmpty()) {
            ops.addAll(delimOps(normalizeDelim(node.open), 0f, 0f, bracketW, ascent + descent, thickness))
        }
        if (node.close.isNotEmpty()) {
            ops.addAll(
                delimOps(normalizeDelim(node.close), width - bracketW, 0f, bracketW, ascent + descent, thickness),
            )
        }
        return MathBox(width, ascent, descent, ops)
    }
}
