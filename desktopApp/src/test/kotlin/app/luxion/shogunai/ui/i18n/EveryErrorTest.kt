package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.GitFailureCause
import java.io.IOException
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EveryErrorTest {

    private val languages = listOf(EnglishStrings, SpanishStrings)

    private val gitCauses = listOf(
        GitFailureCause.LockFileExists("/projects/main-repo/.git/index.lock"),
        GitFailureCause.LockFileExists(null),
        GitFailureCause.PermissionDenied("/projects/main-repo/.git/objects"),
        GitFailureCause.PermissionDenied(null),
        GitFailureCause.NotARepository,
        GitFailureCause.BranchExists("feature/TASK-123"),
        GitFailureCause.LocalChanges,
        GitFailureCause.Unknown,
    )

    private val impliedByWhereTheErrorShows = setOf("worktreePath")

    @Test
    fun `every error is described in every language with the values it carries`() {
        val problems = WorktreeError::class.sealedSubclasses.flatMap { type ->
            samplesOf(type).flatMap { (error, arguments) -> problemsOf(error, arguments, type.simpleName.orEmpty()) }
        }

        assertEquals(emptyList(), problems)
    }

    @Test
    fun `every error that points to its technical details has details to show`() {
        val withoutDetails = WorktreeError::class.sealedSubclasses.flatMap { type ->
            samplesOf(type).map { it.first }
                .filter { error -> "technical details" in EnglishStrings.describe(error).nextStep }
                .filter { error -> EnglishStrings.describe(error).details?.asPlainText().isNullOrBlank() }
                .map { type.simpleName }
        }.distinct()

        assertEquals(emptyList(), withoutDetails)
    }

    @Test
    fun `every cause of a failed Git command is described in every language with its path or branch`() {
        val failure = WorktreeError.GitCommandFailed(listOf("git", "worktree", "add"), 128, "fatal: unexpected")
        val problems = gitCauses.flatMap { cause ->
            languages.flatMap { strings ->
                val texts = strings.errorTexts(failure, cause)
                val shown = texts.summary + texts.nextStep
                val values = listOfNotNull(
                    (cause as? GitFailureCause.LockFileExists)?.path,
                    (cause as? GitFailureCause.PermissionDenied)?.path,
                    (cause as? GitFailureCause.BranchExists)?.name,
                )
                listOfNotNull("${strings.language} $cause is blank".takeIf { texts.summary.isBlank() || texts.nextStep.isBlank() }) +
                    values.filterNot { it in shown }.map { "${strings.language} $cause doesn't show $it" }
            }
        }

        assertEquals(emptyList(), problems)
    }

    @Test
    fun `a command that can't start names the tool and keeps its cause in the details`() {
        val error = CommandExecutionException(listOf("docker", "compose", "up"), IOException("No such file"))

        languages.forEach { strings ->
            val described = strings.describe(error)
            assertTrue(described.summary.contains("docker"))
            assertTrue(described.nextStep.contains("docker"))
            assertEquals(TechnicalDetails(listOf("docker", "compose", "up"), null, "No such file"), described.details)
            assertEquals("", strings.describe(CommandExecutionException(listOf("docker"), IOException())).details?.output)
        }
    }

    @Test
    fun `an unexpected failure keeps its message in the details and has none without one`() {
        languages.forEach { strings ->
            val described = strings.describe(IllegalStateException("broken state"))
            assertTrue(described.summary.isNotBlank())
            assertEquals(TechnicalDetails(null, null, "broken state"), described.details)
            assertNull(strings.describe(IllegalStateException()).details)
        }
    }

    private fun samplesOf(type: KClass<out WorktreeError>): List<Pair<WorktreeError, List<Any?>>> {
        type.objectInstance?.let { return listOf(it to emptyList()) }
        val constructor = type.primaryConstructor!!
        val shown = constructor.parameters.map { it.name !in impliedByWhereTheErrorShows }
        return TextSamples.combinations(constructor.parameters.map { it.type })
            .map { arguments -> constructor.call(*arguments.toTypedArray()) to arguments.filterIndexed { index, _ -> shown[index] } }
    }

    private fun problemsOf(error: WorktreeError, arguments: List<Any?>, name: String): List<String> = languages.flatMap { strings ->
        val described = strings.describe(error)
        val label = "${strings.language} $name$arguments"
        val shown = described.summary + described.nextStep + described.details?.asPlainText().orEmpty()
        when {
            described.summary.isBlank() || described.nextStep.isBlank() -> listOf("$label is blank")
            else -> TextSamples.shownValues(arguments).filterNot { it in shown }.map { "$label doesn't show $it: ${described.summary}" }
        }
    }
}
