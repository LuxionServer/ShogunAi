package app.luxion.shogunai.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactorTest {

    private fun redactor(projects: List<String> = listOf("main-repo"), branches: List<String> = listOf("feature/TASK-123")) =
        Redactor(
            home = "/Users/jane",
            userName = "jane",
            projectNames = { projects },
            branchNames = { branches },
        )

    @Test
    fun `replaces a path and a project name and keeps the rest of the message`() {
        val text = redactor().redact("Couldn't read /Users/jane/projects/main-repo/.shogunai.json while loading main-repo")

        assertEquals("Couldn't read <path> while loading <project>", text)
        listOf("/Users/jane", "jane", "main-repo").forEach { assertFalse(text.contains(it)) }
    }

    @Test
    fun `replaces branch names`() {
        val text = redactor().redact("Couldn't switch to feature/TASK-123 in the worktree")

        assertEquals("Couldn't switch to <branch> in the worktree", text)
        assertFalse(text.contains("TASK-123"))
    }

    @Test
    fun `replaces home relative and Windows paths`() {
        val redactor = redactor()

        assertEquals("a <path> b", redactor.redact("a ~/Library/Logs/x.log b"))
        assertEquals("a <path> b", redactor.redact("a C:\\Users\\jane\\repo\\x.txt b"))
        assertEquals("a <path> b", redactor.redact("a D:/work/repo b"))
    }

    @Test
    fun `replaces the user name and home outside paths`() {
        assertEquals("owner <user> on <path>", redactor().redact("owner jane on /Users/jane"))
    }

    @Test
    fun `keeps stack frames untouched`() {
        val frame = "\tat app.luxion.shogunai.main-repo.Foo.bar(Foo.kt:12)"

        assertEquals(frame, redactor(projects = listOf("shogunai")).redact(frame))
    }

    @Test
    fun `keeps exception class names and numbers`() {
        assertEquals("java.io.IOException: disk full (12 of 30)", redactor().redact("java.io.IOException: disk full (12 of 30)"))
    }

    @Test
    fun `a project with a common name is only replaced inside paths`() {
        val redactor = redactor(projects = listOf("app"))

        assertEquals("The app crashed reading <path>", redactor.redact("The app crashed reading /home/jane/work/app/src"))
    }

    @Test
    fun `does not touch URLs scheme`() {
        assertTrue(redactor().redact("GET https://example.com/a failed").startsWith("GET https:"))
    }

    @Test
    fun `a root home, a short user name and short project names aren't replaced`() {
        val redactor = Redactor(home = "/", userName = "jo", projectNames = { listOf("ab") }, branchNames = { emptyList() })

        assertEquals("jo opened ab", redactor.redact("jo opened ab"))
    }
}
