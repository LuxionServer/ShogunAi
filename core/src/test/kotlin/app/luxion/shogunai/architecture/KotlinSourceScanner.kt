package app.luxion.shogunai.architecture

data class SourceComment(val line: Int, val text: String)

data class SourceLiteral(val line: Int, val text: String)

data class ScannedSource(val code: List<String>, val comments: List<SourceComment>, val literals: List<SourceLiteral>)

class KotlinSourceScanner private constructor(private val text: String) {

    private var index = 0
    private var line = 1
    private val code = StringBuilder()
    private val comments = mutableListOf<SourceComment>()
    private val literals = mutableListOf<SourceLiteral>()

    private fun scan(): ScannedSource {
        scanCode(insideTemplate = false)
        return ScannedSource(code.toString().split('\n'), comments, literals)
    }

    private fun scanCode(insideTemplate: Boolean) {
        var depth = 0
        while (index < text.length) {
            val char = text[index]
            when {
                startsWith("//") -> lineComment()
                startsWith("/*") -> blockComment()
                startsWith("\"\"\"") -> string(raw = true)
                char == '"' -> string(raw = false)
                char == '\'' -> character()
                char == '`' -> backtickName()
                insideTemplate && char == '{' -> {
                    depth++
                    keep()
                }
                insideTemplate && char == '}' -> {
                    if (depth == 0) return
                    depth--
                    keep()
                }
                else -> keep()
            }
        }
    }

    private fun lineComment() {
        val start = index
        while (index < text.length && text[index] != '\n') blank()
        comments += SourceComment(line, text.substring(start, index))
    }

    private fun blockComment() {
        val startLine = line
        val start = index
        var depth = 0
        while (index < text.length) {
            when {
                startsWith("/*") -> {
                    depth++
                    blank(2)
                }
                startsWith("*/") -> {
                    depth--
                    blank(2)
                    if (depth == 0) break
                }
                else -> blank()
            }
        }
        comments += SourceComment(startLine, text.substring(start, index))
    }

    private fun string(raw: Boolean) {
        val startLine = line
        val content = StringBuilder()
        blank(if (raw) 3 else 1)
        while (index < text.length) {
            when {
                raw && startsWith("\"\"\"\"") -> {
                    content.append('"')
                    blank()
                }
                raw && startsWith("\"\"\"") -> {
                    blank(3)
                    break
                }
                !raw && text[index] == '"' -> {
                    blank()
                    break
                }
                !raw && text[index] == '\\' -> {
                    content.append(text, index, minOf(index + 2, text.length))
                    blank(2)
                }
                startsWith("\${") -> template()
                else -> {
                    content.append(text[index])
                    blank()
                }
            }
        }
        literals += SourceLiteral(startLine, content.toString())
    }

    private fun template() {
        blank(2)
        scanCode(insideTemplate = true)
        if (index < text.length) blank()
    }

    private fun character() {
        blank()
        while (index < text.length && text[index] != '\'') {
            if (text[index] == '\\') blank(2) else blank()
        }
        if (index < text.length) blank()
    }

    private fun backtickName() {
        keep()
        while (index < text.length && text[index] != '`' && text[index] != '\n') keep()
        if (index < text.length && text[index] == '`') keep()
    }

    private fun startsWith(prefix: String): Boolean = text.startsWith(prefix, index)

    private fun keep() {
        val char = text[index++]
        code.append(char)
        if (char == '\n') line++
    }

    private fun blank(count: Int = 1) {
        repeat(count) {
            if (index >= text.length) return
            val char = text[index++]
            if (char == '\n') {
                code.append('\n')
                line++
            } else {
                code.append(' ')
            }
        }
    }

    companion object {
        fun scan(text: String): ScannedSource = KotlinSourceScanner(text).scan()
    }
}
