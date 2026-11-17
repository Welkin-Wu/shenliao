package com.shenliao.deepchat.ui.math

/**
 * 行内公式：把 LaTeX 压成 Unicode 纯文本（x^{2} → x²），这样能直接混在正文里。
 * 同时提供从 Markdown 里抽取 $$...$$ / \[...\] 的工具。
 */
object MathInline {

    private val SUP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', '°' to '°',
    )

    private val SUB = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'h' to 'ₕ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'p' to 'ₚ',
        's' to 'ₛ', 't' to 'ₜ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'r' to 'ᵣ',
        'u' to 'ᵤ', 'v' to 'ᵥ',
    )

    fun render(latex: String): String {
        val node = LatexParser.parse(latex).node ?: return latex.trim()
        return linear(node)
    }

    private fun supOf(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder()
        for (c in text) {
            val m = SUP[c] ?: return "^(${text})"
            sb.append(m)
        }
        return sb.toString()
    }

    private fun subOf(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder()
        for (c in text) {
            val m = SUB[c] ?: return "_(${text})"
            sb.append(m)
        }
        return sb.toString()
    }

    private fun linear(node: MNode): String = when (node) {
        MNode.Empty -> ""
        is MNode.Sym -> node.text
        is MNode.TextRun -> node.text
        is MNode.Space -> if (node.em <= 0f) "" else " "
        is MNode.Row -> node.items.joinToString("") { linear(it) }
        is MNode.Script -> {
            val base = linear(node.base)
            val sup = node.sup?.let { supOf(linear(it)) } ?: ""
            val sub = node.sub?.let { subOf(linear(it)) } ?: ""
            base + sup + sub
        }
        is MNode.Frac -> {
            val n = linear(node.num)
            val d = linear(node.den)
            val simple = n.length <= 6 && d.length <= 6 && !n.contains(' ') && !d.contains(' ')
            if (simple) "$n/$d" else "($n)/($d)"
        }
        is MNode.Sqrt -> {
            val idx = node.index?.let { supOf(linear(it)) } ?: ""
            "$idx√(${linear(node.body)})"
        }
        is MNode.BigOp -> {
            val sub = node.sub?.let { subOf(linear(it)) } ?: ""
            val sup = node.sup?.let { supOf(linear(it)) } ?: ""
            node.symbol + sub + sup
        }
        is MNode.Delim -> {
            val open = when (node.open) {
                "\\{", "{" -> "{"
                "\\langle" -> "⟨"
                "\\lfloor" -> "⌊"
                "\\lceil" -> "⌈"
                "\\|" -> "‖"
                "." -> ""
                else -> node.open
            }
            val close = when (node.close) {
                "\\}", "}" -> "}"
                "\\rangle" -> "⟩"
                "\\rfloor" -> "⌋"
                "\\rceil" -> "⌉"
                "\\|" -> "‖"
                "." -> ""
                else -> node.close
            }
            "$open${linear(node.body)}$close"
        }
        is MNode.Accent -> linear(node.body)
        is MNode.Matrix -> {
            val open = if (node.open.isEmpty()) "" else node.open
            val close = if (node.close.isEmpty()) "" else node.close
            val rows = node.rows.joinToString("; ") { r -> r.joinToString(", ") { linear(it) } }
            "$open$rows$close"
        }
    }

    // ------------------------------------------------------------ Markdown 抽取

    private const val MARK = "@@MATHPH@@"

    /**
     * 把 $$...$$ 与 \[...\] 抽成独立的占位行（代码块里的不动，支持跨行公式）。
     * 返回新文本和公式列表，占位行形如 @@MATHPH@@3。
     */
    fun extractDisplay(src: String): Pair<String, List<String>> {
        val formulas = mutableListOf<String>()
        val out = StringBuilder()
        val buffer = StringBuilder()
        var inFence = false

        fun flushBuffer() {
            if (buffer.isEmpty()) return
            out.append(replaceInBuffer(buffer.toString(), formulas))
            buffer.setLength(0)
        }

        for (line in src.split("\n")) {
            if (line.trimStart().startsWith("```")) {
                flushBuffer()
                out.append(line).append('\n')
                inFence = !inFence
                continue
            }
            if (inFence) out.append(line).append('\n') else buffer.append(line).append('\n')
        }
        flushBuffer()
        return out.toString() to formulas
    }

    private fun replaceInBuffer(s: String, formulas: MutableList<String>): String {
        val sb = StringBuilder()
        var i = 0
        val dollar = "\$\$"
        val openBracket = "\\["
        val closeBracket = "\\]"
        while (i < s.length) {
            val d = s.indexOf(dollar, i)
            val b = s.indexOf(openBracket, i)
            val start = when {
                d >= 0 && b >= 0 -> minOf(d, b)
                d >= 0 -> d
                b >= 0 -> b
                else -> -1
            }
            if (start < 0) {
                sb.append(s, i, s.length)
                break
            }
            val isDollar = start == d
            val close = if (isDollar) s.indexOf(dollar, start + 2) else s.indexOf(closeBracket, start + 2)
            if (close < 0) {
                sb.append(s, i, start + 2)
                i = start + 2
                continue
            }
            sb.append(s, i, start)
            formulas.add(s.substring(start + 2, close))
            sb.append('\n').append(MARK).append(formulas.size - 1).append('\n')
            i = close + 2
        }
        return sb.toString()
    }

    /** 占位行 → 公式下标，非占位行返回 -1 */
    fun markerIndex(line: String): Int {
        val t = line.trim()
        if (!t.startsWith(MARK)) return -1
        val n = t.removePrefix(MARK).toIntOrNull() ?: return -1
        return n
    }

    private val INLINE_DOLLAR = Regex("\\$(?=\\S)([^$\\n]{1,200}?)(?<=\\S)\\$")
    private val INLINE_PAREN = Regex("\\\\\\((.{1,200}?)\\\\\\)")

    /** 行内公式 → Unicode 文本（不处理代码块，调用方保证只在正文里用） */
    fun inlineToText(text: String): String {
        if (!text.contains('$') && !text.contains("\\(")) return text
        var out = text
        out = INLINE_PAREN.replace(out) { m -> render(m.groupValues[1]) }
        out = INLINE_DOLLAR.replace(out) { m ->
            val body = m.groupValues[1]
            // 太像普通文本就不当公式，避免把 "$100 和 $200" 误伤
            if (body.count { it == ' ' } > 6) m.value else render(body)
        }
        return out
    }
}
