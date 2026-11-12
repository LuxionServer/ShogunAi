package app.luxion.shogunai.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinSourceScannerTest {

    private fun comments(text: String): List<SourceComment> = KotlinSourceScanner.scan(text).comments

    private fun literals(text: String): List<String> = KotlinSourceScanner.scan(text).literals.map { it.text }

    @Test
    fun `a line comment is found with its line`() {
        assertEquals(listOf(SourceComment(2, "// enough for CI")), comments("val a = 1\nval retries = 3 // enough for CI\n"))
    }

    @Test
    fun `block, nested block and KDoc comments are found where they start`() {
        val text = "/** Docs */\nfun a() = 1\n/* outer /* inner */ still outer */\nval b = 2\n"
        assertEquals(listOf(1, 3), comments(text).map { it.line })
        assertEquals("/* outer /* inner */ still outer */", comments(text)[1].text)
        assertTrue(KotlinSourceScanner.scan(text).code[3].contains("val b = 2"))
    }

    @Test
    fun `slashes inside strings are not comments`() {
        val text = "val url = \"https://herdr.dev\"\nval raw = \"\"\"a // b /* c\"\"\"\n"
        assertEquals(emptyList(), comments(text))
        assertEquals(listOf("https://herdr.dev", "a // b /* c"), literals(text))
    }

    @Test
    fun `escaped quotes and character literals don't end a string early`() {
        val text = "val a = \"say \\\"hi\\\" // not\"\nval b = '\"'\nval c = '\\''\nval d = '/'\n// real\n"
        assertEquals(listOf(SourceComment(5, "// real")), comments(text))
    }

    @Test
    fun `templates are code and may hold strings and braces`() {
        val text = "val a = \"x \${map[\"k\"]?.let { \"}\" } ?: \"y\"} z\" // after\n"
        val scanned = KotlinSourceScanner.scan(text)
        assertEquals(listOf(SourceComment(1, "// after")), scanned.comments)
        assertEquals(listOf("k", "}", "y", "x  z"), scanned.literals.map { it.text })
        assertTrue(scanned.code.first().contains("map["))
    }

    @Test
    fun `raw strings may end with extra quotes`() {
        assertEquals(listOf("a\""), literals("val a = \"\"\"a\"\"\"\"\n"))
    }

    @Test
    fun `backtick names may hold quotes and slashes`() {
        val text = "fun `it's a // name`() = 1\n"
        assertEquals(emptyList(), comments(text))
        assertEquals(text.trimEnd('\n'), KotlinSourceScanner.scan(text).code.first())
    }

    @Test
    fun `code keeps its lines with literals and comments blanked`() {
        val text = "val a = \"two\nlines\" /* c\n*/ val b = 1"
        val code = KotlinSourceScanner.scan(text).code
        assertEquals(3, code.size)
        assertEquals("val a =     ", code[0])
        assertTrue(code[2].endsWith("val b = 1"))
        assertEquals(2, KotlinSourceScanner.scan("a\n\"x\ny\"").literals.single().line)
    }

    @Test
    fun `unterminated constructs end at the end of the file`() {
        assertEquals(listOf("/* open"), comments("/* open").map { it.text })
        assertEquals(listOf("abc"), literals("\"abc"))
        assertEquals(listOf("ab\\"), literals("\"ab\\"))
        assertEquals(listOf(""), literals("\"\${a"))
        assertEquals(emptyList(), comments("val c = '\\"))
        assertEquals("fun `open", KotlinSourceScanner.scan("fun `open").code.single())
        assertEquals("fun `open", KotlinSourceScanner.scan("fun `open\nb").code.first())
    }
}
