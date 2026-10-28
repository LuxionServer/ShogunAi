package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.SharedVariablesDeclaration
import app.luxion.shogunai.domain.model.UnresolvedReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultirepoSharedEnvironmentTest {

    private val world = MultirepoTestWorld()
    private val appTask = "${world.worktreesRoot}/app/TASK-7"
    private val apiTask = "${world.worktreesRoot}/api/TASK-7"
    private val shared = MultirepoSharedEnvironment(world.resolver, world.git)

    private fun sharing(vararg variables: Pair<String, String>, alias: String = "app") {
        val env = SharedVariablesDeclaration(mapOf(alias to variables.toMap()))
        world.source.declarations[world.folder] = world.declaration.copy(env = env)
    }

    @Test
    fun `a worktree in a group refers to its partner on the same branch`() = runTest {
        sharing("API_URL" to "http://localhost:\${port:api.http}")
        with(world.git) {
            world.app.worktree(appTask, "feature/TASK-7")
            world.api.worktree(apiTask, "feature/TASK-7")
        }

        val environment = shared.forWorktree(world.appBase, appTask)!!

        assertEquals(listOf("API_URL"), environment.variables.map { it.name })
        assertEquals(mapOf("api" to SharedTarget.Located(world.apiBase, apiTask)), environment.targets)
    }

    @Test
    fun `a loose worktree and a main checkout refer to the main checkout`() = runTest {
        sharing("API_URL" to "http://localhost:\${port:api.http}")
        with(world.git) {
            world.app.worktree(appTask, "feature/TASK-8")
            world.api.worktree(apiTask, "feature/TASK-7")
        }

        assertEquals(SharedTarget.Located(world.apiBase, world.apiBase), shared.forWorktree(world.appBase, appTask)!!.targets["api"])
        assertEquals(SharedTarget.Located(world.apiBase, world.apiBase), shared.forWorktree(world.appBase, world.appBase)!!.targets["api"])
    }

    @Test
    fun `a partner created later is found on the next read`() = runTest {
        sharing("API_URL" to "http://localhost:\${port:api.http}")
        with(world.git) { world.app.worktree(appTask, "feature/TASK-7") }
        assertEquals(SharedTarget.Located(world.apiBase, world.apiBase), shared.forWorktree(world.appBase, appTask)!!.targets["api"])

        with(world.git) { world.api.worktree(apiTask, "feature/TASK-7") }

        assertEquals(SharedTarget.Located(world.apiBase, apiTask), shared.forWorktree(world.appBase, appTask)!!.targets["api"])
    }

    @Test
    fun `repositories without shared variables and unapproved multirepos get nothing`() = runTest {
        sharing("API_URL" to "http://localhost:\${port:api.http}")
        assertNull(shared.forWorktree(world.apiBase, world.apiBase))

        world.source.approveAll = false

        assertNull(shared.forWorktree(world.appBase, world.appBase))
    }

    @Test
    fun `an invalid declaration names the variables of the repository and the problem`() = runTest {
        sharing("API_URL" to "\${port:billing.http}")

        val environment = shared.forWorktree(world.appBase, world.appBase)!!

        assertEquals(listOf("API_URL"), environment.invalidNames)
        val reason = assertIs<UnresolvedReason.InvalidDeclaration>(environment.invalid)
        assertTrue(reason.declarationPath.endsWith(".shogunai-multirepo.json"))
        assertTrue(reason.detail.contains("billing"))
    }
}
