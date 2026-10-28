package app.luxion.shogunai.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedEnvironmentTest {

    private val aliases = setOf("app", "api")

    private fun env(text: String): SharedVariablesDeclaration = Json.decodeFromString(SharedVariablesDeclaration.serializer(), text)

    private fun parse(text: String) = parseSharedEnvironment(env(text), aliases)

    private fun rejection(text: String): String = assertFailsWith<InvalidSharedEnvironment> { parse(text) }.message.orEmpty()

    @Test
    fun `variables are declared per repository`() {
        val shared = parse("""{"app": {"API_URL": "http://localhost:${'$'}{port:api.http}"}}""")

        val variable = shared.getValue("app").single()
        assertEquals("API_URL", variable.name)
        assertEquals(listOf(TemplatePart.RepositoryPort("api", "port:http")), variable.repositoryPorts)
        assertEquals(setOf("app"), shared.keys)
    }

    @Test
    fun `a cross-repository port renders the referenced port`() {
        val variable = parse("""{"app": {"API_URL": "http://localhost:${'$'}{port:api.http}"}}""").getValue("app").single()

        assertEquals(
            "http://localhost:20003",
            variable.render("TASK-7", emptyMap(), mapOf(TemplatePart.RepositoryPort("api", "port:http") to 20003)),
        )
    }

    @Test
    fun `the worktree name and a literal dollar resolve`() {
        val variable = parse("""{"app": {"APP_SUFFIX": "-${'$'}{worktree}$$"}}""").getValue("app").single()

        assertEquals("-TASK-7$", variable.render("TASK-7", emptyMap()))
    }

    @Test
    fun `no env declares nothing`() {
        assertEquals(emptyMap(), parseSharedEnvironment(null, aliases))
    }

    @Test
    fun `an unknown alias is rejected naming it`() {
        assertTrue(rejection("""{"web": {"API_URL": "x"}}""").contains("'web'"))
    }

    @Test
    fun `a non-object entry and a non-string value are rejected naming them`() {
        assertTrue(rejection("""{"app": "API_URL"}""").contains("env.app"))
        assertTrue(rejection("""{"app": {"PORT": 3000}}""").contains("env.app.PORT"))
    }

    @Test
    fun `reserved and invalid names are rejected naming the repository and the variable`() {
        val reserved = rejection("""{"app": {"NODE_OPTIONS": "x"}}""")
        assertTrue(reserved.contains("app") && reserved.contains("NODE_OPTIONS"))
        assertTrue(rejection("""{"app": {"1PORT": "x"}}""").contains("1PORT"))
    }

    @Test
    fun `hidden characters are rejected`() {
        assertTrue(rejection("""{"app": {"APP_NAME": "demo\nexport OTHER=1"}}""").contains("APP_NAME"))
    }

    @Test
    fun `ports of its own are rejected`() {
        listOf("${'$'}{port}", "${'$'}{port:debug}").forEach { template ->
            val message = rejection("""{"app": {"DEBUG_PORT": "$template"}}""")
            assertTrue(message.contains("DEBUG_PORT") && message.contains(".shogunai.json"), message)
        }
    }

    @Test
    fun `a reference to an unknown repository is rejected naming it`() {
        val message = rejection("""{"app": {"API_URL": "${'$'}{port:billing.http}"}}""")

        assertTrue(message.contains("API_URL") && message.contains("billing"))
    }

    @Test
    fun `other placeholders are rejected`() {
        assertTrue(rejection("""{"app": {"API_URL": "${'$'}{branch}"}}""").contains("branch"))
    }

    @Test
    fun `a repository's own env doesn't accept cross-repository ports`() {
        assertFailsWith<InvalidEnvironmentTemplate> { parseEnvironmentVariable("API_URL", "${'$'}{port:api.http}") }
    }
}
