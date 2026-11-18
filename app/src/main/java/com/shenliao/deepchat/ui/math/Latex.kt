package com.shenliao.deepchat.ui.math

/**
 * 极简 LaTeX 子集 → 排版树。
 * 纯 Kotlin，不依赖 Android，便于单独跑几何测试。
 */
sealed interface MNode {
    data object Empty : MNode
    data class Sym(val text: String, val italic: Boolean = false, val bold: Boolean = false) : MNode
    data class Row(val items: List<MNode>) : MNode
    data class Script(val base: MNode, val sub: MNode? = null, val sup: MNode? = null) : MNode
    data class Frac(val num: MNode, val den: MNode) : MNode
    data class Sqrt(val index: MNode?, val body: MNode) : MNode
    data class BigOp(val symbol: String, val sub: MNode?, val sup: MNode?, val limits: Boolean) : MNode
    data class Delim(val open: String, val close: String, val body: MNode) : MNode
    data class TextRun(val text: String, val bold: Boolean = false) : MNode
    data class Space(val em: Float) : MNode
    data class Accent(val body: MNode, val kind: Int) : MNode
    data class Matrix(val rows: List<List<MNode>>, val open: String, val close: String) : MNode
}

object MAccent {
    const val BAR = 0
    const val HAT = 1
    const val VEC = 2
    const val TILDE = 3
    const val DOT = 4
}

object LatexTable {

    /** 命令 → Unicode 符号 */
    val SYM: Map<String, String> = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
        "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ",
        "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ",
        "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "ϕ",
        "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
        "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷", "cdot" to "·", "ast" to "∗",
        "star" to "⋆", "circ" to "∘", "bullet" to "•", "oplus" to "⊕", "otimes" to "⊗",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡", "sim" to "∼",
        "simeq" to "≃", "cong" to "≅", "propto" to "∝", "ll" to "≪", "gg" to "≫",
        "infty" to "∞", "partial" to "∂", "nabla" to "∇", "prime" to "′", "degree" to "°",
        "in" to "∈", "notin" to "∉", "ni" to "∋", "subset" to "⊂", "subseteq" to "⊆",
        "supset" to "⊃", "supseteq" to "⊇", "cup" to "∪", "cap" to "∩",
        "setminus" to "∖", "emptyset" to "∅", "varnothing" to "∅",
        "forall" to "∀", "exists" to "∃", "neg" to "¬", "lnot" to "¬",
        "land" to "∧", "wedge" to "∧", "lor" to "∨", "vee" to "∨",
        "vdash" to "⊢", "dashv" to "⊣", "models" to "⊨", "perp" to "⊥", "parallel" to "∥",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "gets" to "←",
        "leftrightarrow" to "↔", "Rightarrow" to "⇒", "implies" to "⇒",
        "Leftarrow" to "⇐", "Leftrightarrow" to "⇔", "iff" to "⇔",
        "mapsto" to "↦", "uparrow" to "↑", "downarrow" to "↓",
        "longrightarrow" to "⟶", "Longrightarrow" to "⟹", "hookrightarrow" to "↪",
        "cdots" to "⋯", "ldots" to "…", "dots" to "…", "vdots" to "⋮", "ddots" to "⋱",
        "angle" to "∠", "triangle" to "△", "square" to "□", "because" to "∵", "therefore" to "∴",
        "hbar" to "ℏ", "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ",
        "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋",
        "lceil" to "⌈", "rceil" to "⌉", "checkmark" to "✓", "dagger" to "†",
        "lbrace" to "{", "rbrace" to "}", "vert" to "|", "Vert" to "‖",
        "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
    )

    /** 小写希腊字母走斜体 */
    val ITALIC_GREEK: Set<String> = setOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta",
        "vartheta", "iota", "kappa", "lambda", "mu", "nu", "xi", "pi", "varpi", "rho", "varrho",
        "sigma", "varsigma", "tau", "upsilon", "phi", "varphi", "chi", "psi", "omega",
    )

    /** 函数名：直立体 */
    val FUNCS: Set<String> = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "log", "ln", "lg", "exp", "lim", "limsup", "liminf",
        "max", "min", "sup", "inf", "det", "dim", "ker", "gcd", "deg", "arg", "mod", "bmod",
    )

    val BIG_OPS: Map<String, String> = mapOf(
        "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
    )

    /** 左定界符 → 右定界符 */
    val DELIM_PAIRS: Map<String, String> = mapOf(
        "(" to ")", "[" to "]", "\\{" to "}", "{" to "}", "|" to "|", "\\|" to "\\|",
        "\\langle" to "\\rangle", "\\lfloor" to "\\rfloor", "\\lceil" to "\\rceil",
        "." to ".",
    )
}

/** 解析结果：可能解析失败，失败时回退为纯文本渲染 */
data class LatexParseResult(val node: MNode?, val error: String? = null)

class LatexParser(private val src: String) {

    private var i = 0
    private var depth = 0

    private fun guardDepth() {
        depth++
        if (depth > 80) throw IllegalStateException("公式嵌套太深")
    }

    companion object {
        fun parse(src: String): LatexParseResult {
            val text = src.trim().removePrefix("$").let {
                if (it.startsWith("$") && it.endsWith("$") && it.length >= 2) it.substring(0, it.length) else it
            }
            if (text.isEmpty()) return LatexParseResult(MNode.Empty)
            return try {
                val p = LatexParser(text)
                val node = p.parseRow(stopAtBrace = false)
                LatexParseResult(node)
            } catch (t: Throwable) {
                LatexParseResult(null, t.message ?: "解析失败")
            }
        }
    }

    private fun eof(): Boolean = i >= src.length
    private fun peek(): Char = if (i < src.length) src[i] else '\u0000'
    private fun skipWs() {
        while (i < src.length && (src[i] == ' ' || src[i] == '\n' || src[i] == '\t' || src[i] == '\r')) i++
    }

    private fun parseRow(stopAtBrace: Boolean): MNode {
        val items = mutableListOf<MNode>()
        while (true) {
            skipWs()
            if (eof()) break
            if (src.startsWith("\\right", i)) break
            if (src.startsWith("\\end", i)) break
            if (src[i] == '}' && stopAtBrace) break
            if (src[i] == '&' || src.startsWith("\\\\", i)) break

            var atom = parseAtom() ?: continue
            atom = attachScripts(atom)
            items.add(atom)
        }
        return when {
            items.isEmpty() -> MNode.Empty
            items.size == 1 -> items[0]
            else -> MNode.Row(items)
        }
    }

    private fun attachScripts(start: MNode): MNode {
        var node = start
        while (true) {
            skipWs()
            when {
                peek() == '^' -> {
                    i++
                    val sup = parseScriptArg()
                    node = when (node) {
                        is MNode.Script -> node.copy(sup = sup)
                        is MNode.BigOp -> node.copy(sup = sup)
                        else -> MNode.Script(node, sup = sup)
                    }
                }

                peek() == '_' -> {
                    i++
                    val sub = parseScriptArg()
                    node = when (node) {
                        is MNode.Script -> node.copy(sub = sub)
                        is MNode.BigOp -> node.copy(sub = sub)
                        else -> MNode.Script(node, sub = sub)
                    }
                }
                peek() == '\'' -> {
                    i++
                    node = if (node is MNode.Script) node.copy(sup = MNode.Sym("′"))
                    else MNode.Script(node, sup = MNode.Sym("′"))
                }
                else -> return node
            }
        }
    }

    private fun parseScriptArg(): MNode {
        skipWs()
        if (eof()) return MNode.Empty
        return if (peek() == '{') parseGroup() else (parseAtom() ?: MNode.Empty)
    }

    private fun parseGroup(): MNode {
        skipWs()
        if (peek() != '{') return MNode.Empty
        guardDepth()
        i++ // {
        val body = parseRow(stopAtBrace = true)
        skipWs()
        if (peek() == '}') i++
        depth--
        return body
    }

    private fun readRawGroup(): String {
        skipWs()
        if (peek() != '{') {
            if (eof()) return ""
            val c = src[i]
            i++
            return c.toString()
        }
        i++
        var depth = 1
        val sb = StringBuilder()
        while (i < src.length) {
            val c = src[i]
            when {
                c == '{' -> {
                    depth++
                    sb.append(c)
                    i++
                }
                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        i++
                        break
                    }
                    sb.append(c)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun readCommandName(): String {
        if (peek() == '\\') i++
        val sb = StringBuilder()
        while (i < src.length && src[i].isLetter()) {
            sb.append(src[i])
            i++
        }
        if (sb.isEmpty() && i < src.length) {
            sb.append(src[i])
            i++
        }
        return sb.toString()
    }

    private fun parseAtom(): MNode? {
        skipWs()
        if (eof()) return null
        val c = peek()
        return when {
            c == '{' -> parseGroup()
            c == '\\' -> parseCommand()
            c == '^' || c == '_' -> null
            c == '&' -> { i++; MNode.Space(0.6f) }
            c == '~' -> { i++; MNode.Space(0.5f) }
            c.isDigit() -> {
                val sb = StringBuilder()
                while (i < src.length && (src[i].isDigit() || (src[i] == '.' && i + 1 < src.length && src[i + 1].isDigit()))) {
                    sb.append(src[i]); i++
                }
                MNode.Sym(sb.toString())
            }
            c.isLetter() -> {
                // 连续多字母（如 sin、max）识别为函数名
                val sb = StringBuilder()
                var j = i
                while (j < src.length && src[j].isLetter()) {
                    sb.append(src[j]); j++
                }
                val word = sb.toString()
                if (word.length > 1 && LatexTable.FUNCS.contains(word)) {
                    i = j
                    MNode.TextRun(word)
                } else {
                    i++
                    MNode.Sym(c.toString(), italic = true)
                }
            }
            c == ' ' -> { i++; MNode.Space(0.25f) }
            else -> {
                i++
                when (c) {
                    '-' -> MNode.Sym("−")
                    '*' -> MNode.Sym("∗")
                    else -> MNode.Sym(c.toString())
                }
            }
        }
    }

    private fun parseCommand(): MNode {
        val name = readCommandName()
        return when {
            name == "frac" || name == "dfrac" || name == "tfrac" ->
                MNode.Frac(parseGroup(), parseGroup())

            name == "sqrt" -> {
                skipWs()
                var index: MNode? = null
                if (peek() == '[') {
                    i++
                    val sb = StringBuilder()
                    while (i < src.length && src[i] != ']') {
                        sb.append(src[i]); i++
                    }
                    if (peek() == ']') i++
                    index = LatexParser(sb.toString()).parseRow(stopAtBrace = false)
                }
                MNode.Sqrt(index, parseGroup())
            }

            name == "text" || name == "textrm" || name == "mathrm" || name == "mathsf" ||
                name == "operatorname" -> MNode.TextRun(readRawGroup())

            name == "mathbf" || name == "textbf" || name == "boldsymbol" || name == "bm" ->
                MNode.TextRun(readRawGroup(), bold = true)

            name == "mathit" || name == "mathcal" || name == "mathfrak" ->
                MNode.TextRun(readRawGroup())

            name == "overline" || name == "bar" -> MNode.Accent(parseGroup(), MAccent.BAR)
            name == "underline" -> MNode.Accent(parseGroup(), MAccent.BAR)
            name == "hat" || name == "widehat" -> MNode.Accent(parseGroup(), MAccent.HAT)
            name == "vec" || name == "overrightarrow" -> MNode.Accent(parseGroup(), MAccent.VEC)
            name == "tilde" || name == "widetilde" -> MNode.Accent(parseGroup(), MAccent.TILDE)
            name == "dot" -> MNode.Accent(parseGroup(), MAccent.DOT)

            name == "left" -> parseDelimited()
            name == "right" -> {
                skipDelimiter()
                MNode.Empty
            }

            name == "begin" -> parseEnvironment(readRawGroup())
            name == "end" -> {
                readRawGroup()
                MNode.Empty
            }

            name == "," || name == ":" || name == ";" || name == " " ->
                MNode.Space(if (name == ";") 0.6f else 0.3f)
            name == "!" -> MNode.Space(-0.15f)
            name == "quad" -> MNode.Space(1f)
            name == "qquad" -> MNode.Space(2f)

            name == "limits" || name == "nolimits" || name == "displaystyle" ||
                name == "textstyle" || name == "scriptstyle" -> MNode.Empty

            LatexTable.BIG_OPS.containsKey(name) ->
                MNode.BigOp(LatexTable.BIG_OPS[name]!!, null, null, limits = !isIntegral(name))

            LatexTable.FUNCS.contains(name) -> MNode.TextRun(name)

            LatexTable.SYM.containsKey(name) ->
                MNode.Sym(LatexTable.SYM[name]!!, italic = LatexTable.ITALIC_GREEK.contains(name))

            else -> MNode.TextRun(name)
        }
    }

    private fun isIntegral(name: String): Boolean =
        name == "int" || name == "iint" || name == "iiint" || name == "oint"

    private fun skipDelimiter(): String {
        skipWs()
        if (eof()) return "."
        return when {
            peek() == '\\' -> "\\" + readCommandName()
            else -> {
                val c = peek(); i++; c.toString()
            }
        }
    }

    private fun parseDelimited(): MNode {
        val open = skipDelimiter()
        val body = parseRow(stopAtBrace = false)
        skipWs()
        if (src.startsWith("\\right", i)) {
            i += "\\right".length
            val close = skipDelimiter()
            return MNode.Delim(open, close, body)
        }
        return MNode.Delim(open, ")", body)
    }

    private fun parseEnvironment(envRaw: String): MNode {
        val env = envRaw.trim()
        // 找到配对的 \end{env}
        val endTag = "\\end{$env}"
        var depth = 1
        var j = i
        while (j < src.length) {
            if (src.startsWith("\\begin{$env}", j)) {
                depth++
                j += "\\begin{$env}".length
            } else if (src.startsWith(endTag, j)) {
                depth--
                if (depth == 0) break
                j += endTag.length
            } else {
                j++
            }
        }
        val body = if (j <= src.length) src.substring(i, j.coerceAtMost(src.length)) else ""
        i = if (j + endTag.length <= src.length) j + endTag.length else src.length

        val rows = splitCells(body)
        return when {
            env.endsWith("matrix") || env == "array" -> {
                val open = when {
                    env.startsWith("p") -> "("
                    env.startsWith("b") -> "["
                    env.startsWith("v") -> "|"
                    env.startsWith("V") -> "‖"
                    else -> ""
                }
                val close = when (open) {
                    "(" -> ")"
                    "[" -> "]"
                    "|" -> "|"
                    "‖" -> "‖"
                    else -> ""
                }
                MNode.Matrix(rows.map { r -> r.map { c -> parseCell(c) } }, open, close)
            }
            env == "cases" -> MNode.Matrix(rows.map { r -> r.map { c -> parseCell(c) } }, "{", "")
            env == "aligned" || env == "align" || env == "gather" || env == "split" || env == "eqnarray" ->
                MNode.Matrix(rows.map { r -> r.map { c -> parseCell(c) } }, "", "")
            else -> MNode.Row(rows.flatten().map { parseCell(it) })
        }
    }

    private fun splitCells(body: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var k = 0
        while (k < body.length) {
            val c = body[k]
            when {
                c == '\\' && k + 1 < body.length && body[k + 1] == '\\' && depth == 0 -> {
                    cells.add(cur.toString()); cur.setLength(0)
                    rows.add(cells.toList()); cells.clear()
                    k += 2
                    // 吃掉换行后的可选 [..]
                    if (k < body.length && body[k] == '[') {
                        val close = body.indexOf(']', k)
                        if (close > 0) k = close + 1
                    }
                }
                c == '{' -> { depth++; cur.append(c); k++ }
                c == '}' -> { depth--; cur.append(c); k++ }
                c == '&' && depth == 0 -> { cells.add(cur.toString()); cur.setLength(0); k++ }
                else -> { cur.append(c); k++ }
            }
        }
        if (cur.isNotBlank() || cells.isNotEmpty()) {
            cells.add(cur.toString())
            rows.add(cells.toList())
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun parseCell(raw: String): MNode {
        val t = raw.trim()
        if (t.isEmpty()) return MNode.Empty
        return try {
            LatexParser(t).parseRow(stopAtBrace = false)
        } catch (_: Throwable) {
            MNode.TextRun(t)
        }
    }
}
