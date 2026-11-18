package com.shenliao.deepchat.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenliao.deepchat.ui.math.LatexMath
import com.shenliao.deepchat.ui.math.MathInline
import com.shenliao.deepchat.ui.math.canTypesetLatex

// ------------------------------------------------------------------ 解析

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class ListBlock(val ordered: Boolean, val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Math(val latex: String) : MdBlock
    data object Rule : MdBlock
}

private val BULLET = Regex("^[-*+]\\s+")
private val ORDERED = Regex("^\\d+[.)]\\s+")
private val HEADING = Regex("^#{1,6}\\s+")

private fun parseBlocks(rawSrc: String): List<MdBlock> {
    val (src, formulas) = MathInline.extractDisplay(rawSrc.replace("\r\n", "\n"))
    val lines = src.split("\n")
    val out = mutableListOf<MdBlock>()
    val para = StringBuilder()

    fun flushPara() {
        val t = para.toString().trim()
        if (t.isNotEmpty()) out.add(MdBlock.Paragraph(MathInline.inlineToText(t)))
        para.setLength(0)
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trimStart()
        val marker = MathInline.markerIndex(t)
        when {
            marker >= 0 && marker < formulas.size -> {
                flushPara()
                out.add(MdBlock.Math(formulas[marker]))
                i++
            }

            t.isBlank() -> {
                flushPara()
                i++
            }

            t.startsWith("```") -> {
                flushPara()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                if (i < lines.size) i++ // 吃掉收尾的 ```
                out.add(MdBlock.Code(lang, code.toString().trimEnd('\n')))
            }

            HEADING.containsMatchIn(t) -> {
                flushPara()
                val level = t.takeWhile { it == '#' }.length
                out.add(MdBlock.Heading(level, MathInline.inlineToText(t.drop(level).trim())))
                i++
            }

            t == "---" || t == "***" || t == "___" -> {
                flushPara()
                out.add(MdBlock.Rule)
                i++
            }

            t.startsWith(">") -> {
                flushPara()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote.append(lines[i].trimStart().removePrefix(">").trim()).append('\n')
                    i++
                }
                out.add(MdBlock.Quote(MathInline.inlineToText(quote.toString().trim())))
            }

            BULLET.containsMatchIn(t) || ORDERED.containsMatchIn(t) -> {
                flushPara()
                val ordered = ORDERED.containsMatchIn(t)
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val cur = lines[i].trimStart()
                    val m = if (ordered) ORDERED.find(cur) else BULLET.find(cur)
                    if (m != null) {
                        items.add(MathInline.inlineToText(cur.removeRange(m.range).trim()))
                        i++
                    } else {
                        break
                    }
                }
                if (items.isNotEmpty()) out.add(MdBlock.ListBlock(ordered, items))
            }

            else -> {
                para.append(raw).append('\n')
                i++
            }
        }
    }
    flushPara()
    return out
}

// ------------------------------------------------------------------ 行内样式

private val RE_CODE = Regex("`([^`\\n]+)`")
private val RE_LINK = Regex("\\[([^\\]\\n]+)]\\(([^)\\s]+)\\)")
private val RE_BOLD = Regex("\\*\\*([^*\\n]+)\\*\\*")
private val RE_STRIKE = Regex("~~([^~\\n]+)~~")
private val RE_ITALIC = Regex("\\*([^*\\n]+)\\*")
private val RE_URL = Regex("https?://[^\\s<>()\\[\\]「」]+")

private class Hit(
    val start: Int,
    val end: Int,
    val visStart: Int,
    val visEnd: Int,
    val priority: Int,
    val style: SpanStyle?,
    val url: String?,
)

private fun spansFor(text: String, code: SpanStyle, bold: SpanStyle, italic: SpanStyle, strike: SpanStyle): List<Hit> {
    val hits = mutableListOf<Hit>()

    fun collect(re: Regex, priority: Int, style: SpanStyle?, urlGroup: Int = -1, visGroup: Int = 1) {
        re.findAll(text).forEach { m ->
            val vis = m.groups[visGroup] ?: return@forEach
            hits.add(
                Hit(
                    start = m.range.first,
                    end = m.range.last + 1,
                    visStart = vis.range.first,
                    visEnd = vis.range.last + 1,
                    priority = priority,
                    style = style,
                    url = if (urlGroup >= 0) m.groups[urlGroup]?.value else null,
                ),
            )
        }
    }

    collect(RE_CODE, 0, code)
    collect(RE_LINK, 1, null, urlGroup = 2)
    collect(RE_BOLD, 2, bold)
    collect(RE_STRIKE, 3, strike)
    collect(RE_ITALIC, 4, italic)
    collect(RE_URL, 5, null, urlGroup = 0, visGroup = 0)

    hits.sortWith(compareBy({ it.start }, { it.priority }))

    val picked = mutableListOf<Hit>()
    for (h in hits) {
        if (picked.any { h.start < it.end && h.end > it.start }) continue
        picked.add(h)
    }
    return picked
}

private fun inlineAnnotated(
    text: String,
    base: SpanStyle,
    linkColor: Color,
    codeStyle: SpanStyle,
): AnnotatedString = buildAnnotatedString {
    val hits = spansFor(
        text = text,
        code = codeStyle,
        bold = base.copy(fontWeight = FontWeight.SemiBold),
        italic = base.copy(fontStyle = FontStyle.Italic),
        strike = base.copy(textDecoration = TextDecoration.LineThrough),
    )
    var cursor = 0
    for (h in hits) {
        if (h.start > cursor) {
            withStyle(base) { append(text.substring(cursor, h.start)) }
        }
        val visible = text.substring(h.visStart, h.visEnd)
        val url = h.url
        if (url != null) {
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) {
                withStyle(base) { append(visible) }
            }
        } else {
            withStyle(h.style ?: base) { append(visible) }
        }
        cursor = h.end
    }
    if (cursor < text.length) {
        withStyle(base) { append(text.substring(cursor)) }
    }
}

// ------------------------------------------------------------------ 渲染

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppColors.TextMain,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 23.sp,
) {
    val blocks = remember(text) {
        runCatching { parseBlocks(text) }.getOrElse { listOf(MdBlock.Paragraph(text)) }
    }
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        color = AppColors.CodeAccent,
        background = AppColors.SurfaceHigh,
    )
    val base = SpanStyle(color = color, fontSize = fontSize)

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = inlineAnnotated(block.text, base, AppColors.Primary, codeStyle),
                    style = TextStyle(fontSize = fontSize, lineHeight = lineHeight, color = color),
                )

                is MdBlock.Heading -> {
                    val sizeValue = when (block.level) {
                        1 -> fontSize.value + 6f
                        2 -> fontSize.value + 4f
                        3 -> fontSize.value + 2f
                        else -> fontSize.value + 1f
                    }
                    val size = sizeValue.sp
                    Text(
                        text = inlineAnnotated(
                            block.text,
                            base.copy(fontWeight = FontWeight.Bold, fontSize = size),
                            AppColors.Primary,
                            codeStyle,
                        ),
                        style = TextStyle(
                            fontSize = size,
                            lineHeight = (size.value + 9).sp,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 3.dp),
                    )
                }

                is MdBlock.Code -> CodeBlock(block.lang, block.code)

                is MdBlock.Math -> {
                    if (canTypesetLatex(block.latex)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            LatexMath(
                                latex = block.latex,
                                fontSize = (fontSize.value + 2).sp,
                                color = color,
                                display = true,
                            )
                        }
                    } else {
                        Text(
                            text = block.latex.trim(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = AppColors.CodeAccent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        )
                    }
                }

                is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { i, item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (block.ordered) "${i + 1}." else "•",
                                style = TextStyle(fontSize = fontSize, lineHeight = lineHeight),
                                color = if (block.ordered) AppColors.TextDim else AppColors.Primary,
                                modifier = Modifier.width(if (block.ordered) 22.dp else 14.dp),
                            )
                            Text(
                                text = inlineAnnotated(item, base, AppColors.Primary, codeStyle),
                                style = TextStyle(fontSize = fontSize, lineHeight = lineHeight, color = color),
                            )
                        }
                    }
                }

                is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppColors.Border),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = inlineAnnotated(block.text, base, AppColors.Primary, codeStyle),
                        style = TextStyle(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = AppColors.TextDim,
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                }

                MdBlock.Rule -> HorizontalDivider(color = AppColors.Border)
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.CodeBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lang.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextFaint,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(ctx, "代码已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.height(34.dp),
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    tint = AppColors.TextFaint,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            Text(
                text = code,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = AppColors.CodeText,
                ),
            )
        }
    }
}
