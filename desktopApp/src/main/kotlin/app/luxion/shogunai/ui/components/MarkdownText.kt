package app.luxion.shogunai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.luxion.shogunai.ui.theme.ShogunTheme

sealed interface MarkdownSpan {
    data class Plain(val text: String) : MarkdownSpan
    data class Bold(val text: String) : MarkdownSpan
    data class Code(val text: String) : MarkdownSpan
    data class Link(val text: String, val url: String) : MarkdownSpan
}

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val spans: List<MarkdownSpan>) : MarkdownBlock
    data class Bullet(val depth: Int, val spans: List<MarkdownSpan>) : MarkdownBlock
    data class Paragraph(val spans: List<MarkdownSpan>) : MarkdownBlock
}

object Markdown {
    private const val MaxHeadingLevel = 3
    private const val NestedIndent = 2

    private val HeadingLine = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val BulletLine = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val InlineToken = Regex(
        """\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\((https?://[^)\s]+)\)|(https?://[^\s<>()]*[^\s<>().,;:!?'"])""",
    )

    fun parse(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        var pending: PendingBlock? = null
        fun flush() {
            pending?.let { blocks += it.toBlock() }
            pending = null
        }
        text.replace("\r\n", "\n").lines().forEach { line ->
            val heading = HeadingLine.matchEntire(line)
            val bullet = BulletLine.matchEntire(line)
            val current = pending
            when {
                line.isBlank() -> flush()
                heading != null -> {
                    flush()
                    val (hashes, content) = heading.destructured
                    blocks += MarkdownBlock.Heading(hashes.length.coerceAtMost(MaxHeadingLevel), inline(content))
                }
                bullet != null -> {
                    flush()
                    val (indent, content) = bullet.destructured
                    pending = PendingBlock(bulletDepth = if (indent.length >= NestedIndent) 1 else 0, text = content.trim())
                }
                current != null -> current.text += " " + line.trim()
                else -> pending = PendingBlock(bulletDepth = null, text = line.trim())
            }
        }
        flush()
        return blocks
    }

    fun inline(text: String): List<MarkdownSpan> {
        val spans = mutableListOf<MarkdownSpan>()
        var position = 0
        InlineToken.findAll(text).forEach { match ->
            if (match.range.first > position) spans += MarkdownSpan.Plain(text.substring(position, match.range.first))
            val (bold, code, linkText, linkUrl, bareUrl) = match.destructured
            spans += when {
                bold.isNotEmpty() -> MarkdownSpan.Bold(bold)
                code.isNotEmpty() -> MarkdownSpan.Code(code)
                linkUrl.isNotEmpty() -> MarkdownSpan.Link(linkText, linkUrl)
                else -> MarkdownSpan.Link(bareUrl, bareUrl)
            }
            position = match.range.last + 1
        }
        if (position < text.length) spans += MarkdownSpan.Plain(text.substring(position))
        return spans
    }

    private class PendingBlock(val bulletDepth: Int?, var text: String) {
        fun toBlock(): MarkdownBlock =
            bulletDepth?.let { MarkdownBlock.Bullet(it, inline(text)) } ?: MarkdownBlock.Paragraph(inline(text))
    }
}

@Composable
fun MarkdownText(text: String, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(text) { Markdown.parse(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> MarkdownLine(
                    block.spans,
                    headingStyle(block.level),
                    onOpenUrl,
                    Modifier.padding(top = Spacing.xs),
                )
                is MarkdownBlock.Bullet -> Row(
                    modifier = Modifier.padding(start = Spacing.md * block.depth),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(if (block.depth == 0) "•" else "◦", style = MaterialTheme.typography.bodyMedium)
                    MarkdownLine(block.spans, MaterialTheme.typography.bodyMedium, onOpenUrl)
                }
                is MarkdownBlock.Paragraph -> MarkdownLine(block.spans, MaterialTheme.typography.bodyMedium, onOpenUrl)
            }
        }
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.titleMedium
    2 -> MaterialTheme.typography.titleSmall
    else -> MaterialTheme.typography.labelLarge
}

@Composable
private fun MarkdownLine(spans: List<MarkdownSpan>, style: TextStyle, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val linkColor = ShogunTheme.colors.accent
    val codeStyle = ShogunTheme.typography.dataSmall
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(spans, linkColor, codeStyle, codeBackground, onOpenUrl) {
        spans.toAnnotatedString(linkColor, codeStyle, codeBackground, onOpenUrl)
    }
    Text(annotated, style = style, modifier = modifier)
}

private fun List<MarkdownSpan>.toAnnotatedString(
    linkColor: Color,
    codeStyle: TextStyle,
    codeBackground: Color,
    onOpenUrl: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    forEach { span ->
        when (span) {
            is MarkdownSpan.Plain -> append(span.text)
            is MarkdownSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
            is MarkdownSpan.Code -> withStyle(codeStyle.toSpanStyle().copy(background = codeBackground)) { append(span.text) }
            is MarkdownSpan.Link -> withLink(LinkAnnotation.Clickable(span.url, linkStyles) { onOpenUrl(span.url) }) {
                append(span.text)
            }
        }
    }
}
