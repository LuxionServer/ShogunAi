package app.luxion.shogunai.ui.i18n

import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EveryTextTest {

    private val languages = listOf(EnglishStrings, SpanishStrings)

    private val describedByEveryErrorTest = setOf("errorTexts")

    private val emptySummaries = setOf("environmentSummary" to listOf<Any?>(0, 0))

    @Test
    fun `every text property has content in every language`() {
        val blank = Strings::class.declaredMemberProperties.flatMap { property ->
            languages.filter { strings -> (property.get(strings) as? String)?.isBlank() == true }
                .map { "${it.language} ${property.name}" }
        }

        assertEquals(emptyList(), blank)
    }

    @Test
    fun `every text function has content in every language and shows the values it is given`() {
        val problems = sampledFunctions().flatMap { function ->
            TextSamples.combinations(function.valueParameters()).flatMap { arguments -> problemsOf(function, arguments) }
        }

        assertEquals(emptyList(), problems)
    }

    @Test
    fun `every text function can be sampled`() {
        val unsampled = Strings::class.declaredMemberFunctions
            .filter { function -> function.valueParameters().any { TextSamples.of(it) == null } }
            .map { it.name }

        assertEquals(emptyList(), unsampled)
    }

    @Test
    fun `the summary of nothing is empty in every language`() {
        languages.forEach { assertEquals("", it.environmentSummary(0, 0)) }
        assertTrue(languages.all { it.environmentSummary(1, 0).isNotBlank() })
    }

    @Test
    fun `every repository configuration issue has a text in every language that names what it is about`() {
        val issues = TextSamples.of(typeOf<RepositoryConfigIssue>()).orEmpty().filterIsInstance<RepositoryConfigIssue>()
        val problems = issues.flatMap { issue ->
            languages.mapNotNull { strings ->
                val text = strings.repositoryConfigIssue(issue)
                val named = listOfNotNull(
                    (issue as? RepositoryConfigIssue.UnknownPlaceholder)?.placeholder,
                    (issue as? RepositoryConfigIssue.UnknownMultirepoRepository)?.alias,
                )
                "${strings.language} $issue: $text".takeIf { text.isBlank() || named.any { it !in text } }
            }
        }

        assertEquals(RepositoryConfigIssue::class.sealedSubclasses.size, issues.size)
        assertEquals(emptyList(), problems)
    }

    private fun sampledFunctions() =
        Strings::class.declaredMemberFunctions.filter { it.name !in describedByEveryErrorTest }

    private fun problemsOf(function: KFunction<*>, arguments: List<Any?>): List<String> = languages.flatMap { strings ->
        val text = function.call(strings, *arguments.toTypedArray()) as String?
        val label = "${strings.language} ${function.name}$arguments"
        when {
            function.name to arguments in emptySummaries -> emptyList()
            text == null -> listOfNotNull("$label has no text".takeUnless { function.returnType.isMarkedNullable })
            text.isBlank() -> listOf("$label is blank")
            else -> TextSamples.shownValues(arguments).filterNot { it in text }.map { "$label doesn't show $it: $text" }
        }
    }

    private fun KFunction<*>.valueParameters(): List<KType> = parameters.drop(1).map { it.type }
}
