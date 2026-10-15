package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StringsTest {

    private val allStrings = AppLanguage.entries.map { it.strings() }

    @Test
    fun `each language maps to its own string table`() {
        assertEquals(EnglishStrings, AppLanguage.ENGLISH.strings())
        assertEquals(SpanishStrings, AppLanguage.SPANISH.strings())
    }

    @Test
    fun `domain errors are translated and keep their data`() {
        val errors = listOf(
            WorktreeError.BaseRepositoryNotFound("~/projects/main-repo") to "~/projects/main-repo",
            WorktreeError.WorktreeAlreadyExists("~/projects/TASK-123") to "~/projects/TASK-123",
            WorktreeError.InvalidTaskId("bad..id") to "bad..id",
            WorktreeError.SecretFileNotFound(listOf("local.properties"), "~/projects/main-repo") to "local.properties",
            WorktreeError.BranchNotFound("feature/TASK-123") to "feature/TASK-123",
            WorktreeError.BranchAlreadyCheckedOut("feature/TASK-123", "~/projects/TASK-123") to "~/projects/TASK-123",
        )
        errors.forEach { (error, expectedData) ->
            val english = EnglishStrings.worktreeError(error)
            val spanish = SpanishStrings.worktreeError(error)
            assertTrue(english.contains(expectedData), english)
            assertTrue(spanish.contains(expectedData), spanish)
            assertNotEquals(english, spanish)
        }
    }

    @Test
    fun `git failures include Git's error output verbatim`() {
        val output = "fatal: 'TASK-123' is not a working tree"
        val error = WorktreeError.GitCommandFailed(listOf("git", "worktree", "remove", "TASK-123"), 128, output)

        allStrings.forEach { strings ->
            val message = strings.worktreeError(error)
            assertTrue(message.endsWith("\n$output"), message)
            assertTrue(message.contains("128"), message)
        }
    }

    @Test
    fun `errors wrapping a cause include the cause's message`() {
        val error = WorktreeError.SecretCopyFailed(IOException("disk full"))

        allStrings.forEach { strings ->
            assertTrue(strings.worktreeError(error).contains("disk full"))
        }
    }

    @Test
    fun `commands that can't start are reported in the selected language`() {
        val error = CommandExecutionException(listOf("git", "status"), IOException("No such file or directory"))

        assertEquals("Could not start the command: git status", EnglishStrings.worktreeError(error))
        assertEquals("No se pudo iniciar el comando: git status", SpanishStrings.worktreeError(error))
    }

    @Test
    fun `unexpected throwables use the generic message`() {
        val error = IllegalStateException("boom")

        assertEquals("Unexpected error: boom", EnglishStrings.worktreeError(error))
        assertEquals("Error inesperado: boom", SpanishStrings.worktreeError(error))
    }

    @Test
    fun `each language names itself in its own language`() {
        assertEquals("English", EnglishStrings.languageName)
        assertEquals("Español", SpanishStrings.languageName)
    }
}
