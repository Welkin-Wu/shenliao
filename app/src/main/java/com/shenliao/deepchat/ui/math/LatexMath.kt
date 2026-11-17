package com.shenliao.deepchat.ui.math

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenliao.deepchat.ui.AppColors

private class ComposeMeter(
    private val measurer: androidx.compose.ui.text.TextMeasurer,
    private val density: Density,
    private val family: FontFamily,
) : TextMeter {
    override fun measure(text: String, sizePx: Float, italic: Boolean, bold: Boolean): TextBox {
        val style = TextStyle(
            fontSize = with(density) { sizePx.toSp() },
            fontFamily = family,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        val result = measurer.measure(
            text = AnnotatedString(text),
            style = style,
            maxLines = 1,
            softWrap = false,
        )
        val baseline = result.firstBaseline.takeIf { it.isFinite() } ?: (result.size.height * 0.8f)
        return TextBox(text, result.size.width.toFloat(), baseline, result.size.height - baseline)
    }
}

/**
 * 渲染一段 LaTeX（display 模式）。解析失败时返回 false，由调用方回退成纯文本。
 */
@Composable
fun LatexMath(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    color: Color = AppColors.TextMain,
    display: Boolean = true,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val mathFamily = LocalMathFont.current
    val sizePx = with(density) { fontSize.toPx() }

    val parsed = remember(latex) { LatexParser.parse(latex) }
    val node = parsed.node
    if (node == null) return

    val box = remember(node, sizePx, display, mathFamily) {
        MathTypesetter(ComposeMeter(measurer, density, mathFamily), sizePx, display).layout(node)
    }

    val widthDp = with(density) { box.width.toDp() } + 2.dp
    val heightDp = with(density) { box.height.toDp() } + 2.dp

    Box(
        modifier = modifier
            .size(width = widthDp, height = heightDp)
            .drawBehind {
                val pad = 1.dp.toPx()
                for (op in box.ops) {
                    when (op) {
                        is MOp.Txt -> {
                            val style = TextStyle(
                                fontSize = with(density) { op.size.toSp() },
                                fontFamily = mathFamily,
                                fontStyle = if (op.italic) FontStyle.Italic else FontStyle.Normal,
                                fontWeight = if (op.bold) FontWeight.Bold else FontWeight.Normal,
                                color = color,
                            )
                            val layout = measurer.measure(
                                text = AnnotatedString(op.text),
                                style = style,
                                maxLines = 1,
                                softWrap = false,
                            )
                            val baseline = layout.firstBaseline.takeIf { it.isFinite() }
                                ?: (layout.size.height * 0.8f)
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(op.x + pad, op.y - baseline + pad),
                                color = color,
                            )
                        }

                        is MOp.Line -> drawLine(
                            color = color,
                            start = Offset(op.x1 + pad, op.y1 + pad),
                            end = Offset(op.x2 + pad, op.y2 + pad),
                            strokeWidth = op.w,
                            cap = StrokeCap.Round,
                        )

                        is MOp.Poly -> {
                            if (op.pts.size >= 4) {
                                val path = Path()
                                path.moveTo(op.pts[0] + pad, op.pts[1] + pad)
                                var i = 2
                                while (i + 1 < op.pts.size) {
                                    path.lineTo(op.pts[i] + pad, op.pts[i + 1] + pad)
                                    i += 2
                                }
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(
                                        width = op.w,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
    )
}

/** 供外部判断“这段能不能排” */
fun canTypesetLatex(latex: String): Boolean = LatexParser.parse(latex).node != null
