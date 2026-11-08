package app.luxion.shogunai.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultirepoTest {

    private val multirepo = Multirepo(
        folder = "/projects/product",
        declaration = MultirepoDeclaration(repos = mapOf("api" to "api", "app" to "app")),
        folderIsRepository = true,
    )

    @Test
    fun `a detected workspace isn't declared`() {
        assertTrue(multirepo.workspaceDetected)
        assertFalse(multirepo.workspaceDeclared)
        assertTrue(multirepo.copy(declaration = multirepo.declaration.copy(workspace = WorkspaceSetting())).workspaceDeclared)
    }

    @Test
    fun `parent folder is the folder above the multirepo`() {
        assertEquals("/projects", multirepo.parentFolder)
        assertEquals("/", Multirepo("/", MultirepoDeclaration()).parentFolder)
    }

    @Test
    fun `repositories are found by alias and by path`() {
        assertEquals(MultirepoRepository("api", "/projects/product/api"), multirepo.repository("api"))
        assertNull(multirepo.repository("web"))
        assertEquals("app", multirepo.aliasOf("/projects/product/app"))
        assertNull(multirepo.aliasOf("/projects/other"))
    }
    private fun declaration(text: String): MultirepoDeclaration = Json.decodeFromString(text)

    @Test
    fun `a workspace object fills in a missing or null alias and default`() {
        assertEquals(WorkspaceSetting(), declaration("""{"workspace":{}}""").workspace)
        assertEquals(WorkspaceSetting(), declaration("""{"workspace":{"alias":null,"default":"yes"}}""").workspace)
        assertEquals(WorkspaceSetting(alias = "root", default = true), declaration("""{"workspace":{"alias":"root","default":true}}""").workspace)
    }

    @Test
    fun `workspace is only false or an object`() {
        assertEquals(WorkspaceSetting(enabled = false), declaration("""{"workspace":false}""").workspace)
        listOf("true", "\"off\"", "[]").forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) { declaration("""{"workspace":$value}""") }
        }
    }

    @Test
    fun `a declaration reads missing fields as empty and writes only what is set`() {
        val full = MultirepoDeclaration(
            repos = mapOf("api" to "api"),
            worktreesRoot = "../worktrees",
            defaultRepos = listOf("api"),
            agent = AgentLaunchConfig(agentCommand = "codex"),
            workspace = WorkspaceSetting(alias = "root", default = true),
            name = "Product",
            env = SharedVariablesDeclaration(mapOf("api" to mapOf("PORT" to "8080"))),
        )
        val formats = listOf(Json, Json { encodeDefaults = true })

        assertEquals(MultirepoDeclaration(), declaration("{}"))
        assertEquals("{}", Json.encodeToString(MultirepoDeclaration()))
        formats.forEach { json ->
            listOf(
                MultirepoDeclaration(),
                full,
                MultirepoDeclaration(repos = mapOf("a" to "a"), defaultRepos = listOf("a"), workspace = WorkspaceSetting(enabled = false), env = SharedVariablesDeclaration()),
                MultirepoDeclaration(worktreesRoot = "w", agent = AgentLaunchConfig(), name = "N"),
            ).forEach { value -> assertEquals(value, json.decodeFromString<MultirepoDeclaration>(json.encodeToString(value))) }
        }
    }

    @Test
    fun `shared variables report everything that isn't a string template`() {
        assertEquals(listOf("env must be an object"), declaration("""{"env":[]}""").env?.problems)
        assertEquals(
            listOf("env.api must be an object", "env.app.OBJECT must be a string", "env.app.NUMBER must be a string", "env.app.NULL must be a string"),
            declaration("""{"env":{"api":"x","app":{"OBJECT":{},"NUMBER":1,"NULL":null,"PORT":"80"}}}""").env?.problems,
        )
        assertEquals(mapOf("PORT" to "80"), declaration("""{"env":{"app":{"PORT":"80"}}}""").env?.byAlias?.get("app"))
    }

    @Test
    fun `removing a variable of an alias without variables leaves it empty`() {
        assertEquals(mapOf("api" to emptyMap<String, String>()), SharedVariablesDeclaration().withoutVariable("api", "PORT").byAlias)
    }

    @Test
    fun `the name is the declared one when it is valid and the folder's otherwise`() {
        assertEquals("Product", multirepo.copy(declaration = multirepo.declaration.copy(name = " Product ")).name)
        assertEquals("product", multirepo.copy(declaration = multirepo.declaration.copy(name = "  ")).name)
        assertEquals("product", multirepo.copy(declaration = multirepo.declaration.copy(name = "x".repeat(MAX_MULTIREPO_NAME_LENGTH + 1))).name)
    }

    @Test
    fun `a workspace is neither detected nor declared when the folder isn't a repository`() {
        val plain = multirepo.copy(folderIsRepository = false)

        assertFalse(plain.workspaceDetected)
        assertFalse(plain.workspaceDeclared)
    }

    @Test
    fun `a declared or listed workspace isn't detected`() {
        val listed = multirepo.copy(declaration = multirepo.declaration.copy(repos = mapOf("root" to ".")))

        assertFalse(listed.workspaceDetected)
        assertTrue(listed.workspaceDeclared)
    }

    @Test
    fun `a default workspace joins the default repositories once`() {
        val joining = multirepo.declaration.copy(workspace = WorkspaceSetting(default = true), defaultRepos = listOf("api"))

        assertEquals(listOf("api", DEFAULT_WORKSPACE_ALIAS), multirepo.copy(declaration = joining).defaultRepos)
        assertEquals(listOf("api"), multirepo.copy(declaration = joining, folderIsRepository = false).defaultRepos)
        assertEquals(listOf("api"), multirepo.copy(declaration = joining.copy(workspace = WorkspaceSetting())).defaultRepos)
        assertEquals(listOf("api"), multirepo.copy(declaration = joining.copy(workspace = null)).defaultRepos)
        assertEquals(
            listOf(DEFAULT_WORKSPACE_ALIAS),
            multirepo.copy(declaration = joining.copy(defaultRepos = listOf(DEFAULT_WORKSPACE_ALIAS))).defaultRepos,
        )
    }

    @Test
    fun `a declared agent is ignored in favour of the local one`() {
        assertTrue(multirepo.copy(declaration = multirepo.declaration.copy(agent = AgentLaunchConfig())).ignoresDeclaredAgent)
        assertFalse(multirepo.ignoresDeclaredAgent)
    }

    @Test
    fun `a multirepo at the root has no parent to stay inside`() {
        val atRoot = Multirepo("/", MultirepoDeclaration())

        assertFalse(atRoot.isOutsideParent("/elsewhere"))
        assertTrue(multirepo.isOutsideParent("/elsewhere"))
        assertTrue(atRoot.approvalSubject.startsWith("folder\t/\n"))
    }

    @Test
    fun `paths are valid without control characters`() {
        assertTrue(isValidMultirepoPath("../api"))
        assertFalse(isValidMultirepoPath("api\u0000"))
        assertFalse(isValidMultirepoPath("api\u202E"))
    }

    @Test
    fun `paths resolve against the folder and stay absolute at the root`() {
        assertEquals("/", resolveMultirepoPath("/projects/product", "/"))
        assertEquals("/projects/api", resolveMultirepoPath("/projects/product", "../api"))
    }

    @Test
    fun `relative paths are written from the folder when they are close`() {
        assertEquals(".", relativeMultirepoPath("/projects/product", "/projects/product/"))
        assertEquals("api", relativeMultirepoPath("/projects/product", "/projects/product/api"))
        assertEquals("../api", relativeMultirepoPath("/projects/product", "/projects/api"))
        assertEquals("/elsewhere/api", relativeMultirepoPath("/projects/product", "/elsewhere/api"))
        assertEquals("api", relativeMultirepoPath("/", "api"))
    }
}
