package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MultirepoApprovalTest {

    private val folder = "/projects/product"
    private val declaration = MultirepoDeclaration(
        repos = mapOf("app" to "product-app", "api" to "product-api"),
        worktreesRoot = "../product-worktrees",
        defaultRepos = listOf("app"),
    )
    private val subject = Multirepo(folder, declaration).approvalSubject

    @Test
    fun `default repositories and a declared agent don't change the subject`() {
        val changed = declaration.copy(defaultRepos = listOf("api"), agent = AgentLaunchConfig(agentCommand = "touch /tmp/marker; claude"))

        assertEquals(subject, Multirepo(folder, changed).approvalSubject)
    }

    @Test
    fun `the order of the repositories doesn't change the subject`() {
        val reordered = declaration.copy(repos = mapOf("api" to "product-api", "app" to "product-app"))

        assertEquals(subject, Multirepo(folder, reordered).approvalSubject)
    }

    @Test
    fun `where the multirepo acts changes the subject`() {
        listOf(
            declaration.copy(repos = mapOf("app" to "product-app", "api" to "/home/user/other-repo")),
            declaration.copy(repos = mapOf("app" to "product-app", "backend" to "product-api")),
            declaration.copy(worktreesRoot = "/tmp/elsewhere"),
            declaration.copy(workspace = WorkspaceSetting(enabled = false)),
            declaration.copy(workspace = WorkspaceSetting(alias = "root")),
        ).forEach { changed ->
            assertNotEquals(subject, Multirepo(folder, changed).approvalSubject)
        }
        assertNotEquals(subject, Multirepo("/projects/copy", declaration).approvalSubject)
    }

    @Test
    fun `the agent comes only from the local setting`() {
        val declared = declaration.copy(agent = AgentLaunchConfig(agentCommand = "touch /tmp/marker; claude"))

        assertEquals("claude", Multirepo(folder, declared).agent.resolvedCommand())
        assertTrue(Multirepo(folder, declared).ignoresDeclaredAgent)
        val local = AgentLaunchConfig(name = "Codex", agentCommand = "codex")
        assertEquals(local, Multirepo(folder, declared, localAgent = local).agent)
    }

    @Test
    fun `paths outside the folder's parent are flagged`() {
        val multirepo = Multirepo(folder, declaration)

        assertFalse(multirepo.isOutsideParent("/projects/product-api"))
        assertFalse(multirepo.isOutsideParent("/projects/product-worktrees/app"))
        assertTrue(multirepo.isOutsideParent("/srv/other/api"))
        assertTrue(multirepo.isOutsideParent("/projects-evil/api"))
    }

    @Test
    fun `control and formatting characters are unsafe in a path`() {
        assertTrue(hasUnsafePathCharacters("product-api\u001b[2K\r"))
        assertTrue(hasUnsafePathCharacters("product‮ipa"))
        assertTrue(hasUnsafePathCharacters("product​api"))
        assertFalse(hasUnsafePathCharacters("../product api/ñ"))
    }
}
