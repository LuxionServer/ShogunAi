package app.luxion.shogunai.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {

    @Test
    fun `parses headings, bullets with one nesting level and paragraphs`() {
        val blocks = Markdown.parse(
            """
            # Title
            ## What's new
            #### Deep heading

            - First item
              that wraps
              - Nested item
            * Starred item

            A paragraph
            on two lines.
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, listOf(MarkdownSpan.Plain("Title"))),
                MarkdownBlock.Heading(2, listOf(MarkdownSpan.Plain("What's new"))),
                MarkdownBlock.Heading(3, listOf(MarkdownSpan.Plain("Deep heading"))),
                MarkdownBlock.Bullet(0, listOf(MarkdownSpan.Plain("First item that wraps"))),
                MarkdownBlock.Bullet(1, listOf(MarkdownSpan.Plain("Nested item"))),
                MarkdownBlock.Bullet(0, listOf(MarkdownSpan.Plain("Starred item"))),
                MarkdownBlock.Paragraph(listOf(MarkdownSpan.Plain("A paragraph on two lines."))),
            ),
            blocks,
        )
    }

    @Test
    fun `a bold line is a paragraph, not a bullet`() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph(listOf(MarkdownSpan.Bold("Full Changelog"), MarkdownSpan.Plain(": soon")))),
            Markdown.parse("**Full Changelog**: soon"),
        )
    }

    @Test
    fun `parses bold, code, links and bare URLs inside a line`() {
        assertEquals(
            listOf(
                MarkdownSpan.Bold("Faster."),
                MarkdownSpan.Plain(" Run "),
                MarkdownSpan.Code("shogun status"),
                MarkdownSpan.Plain(", read "),
                MarkdownSpan.Link("the guide", "https://example.test/guide"),
                MarkdownSpan.Plain(" or https-less text and "),
                MarkdownSpan.Link("https://example.test/page", "https://example.test/page"),
                MarkdownSpan.Plain("."),
            ),
            Markdown.inline(
                "**Faster.** Run `shogun status`, read [the guide](https://example.test/guide) or https-less text and https://example.test/page.",
            ),
        )
    }

    @Test
    fun `unclosed markers stay as text`() {
        assertEquals(listOf(MarkdownSpan.Plain("**not bold and `not code")), Markdown.inline("**not bold and `not code"))
    }
}
