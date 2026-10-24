package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.InvalidEnvironmentTemplate
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MavenAndComposeContributionsTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = "/home/dev/projects/worktrees/TASK-123"
    private val other = "/home/dev/projects/worktrees/TASK-456"
    private val admin = mapOf(
        repository to common,
        task to "$common/worktrees/TASK-123",
        other to "$common/worktrees/TASK-456",
    )

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) =
            FakeShellCommandExecutor.success(command, "${admin.getValue(workingDirectory!!)}\n$common\n")
    }

    private fun resolver(providers: RepositoryProviders, vararg env: Pair<String, String>, store: FakeEnvironmentStateStore = FakeEnvironmentStateStore()) =
        ResolveWorktreeEnvironmentUseCase(
            executor,
            FakeRepositoryProvisioningSource(
                byRepository = mapOf(
                    repository to RepositoryProvisioning(
                        providers = providers,
                        environment = EnvironmentDeclaration(env.map { (name, value) -> parseEnvironmentVariable(name, value) }),
                    ),
                ),
            ),
            store,
            FakePortProbe(),
            MachineProviderContributions(FakeAppleSimulators(), { SimulatorSettings() }),
            trust = FakeRepositoryTrust(approvesEverything = true),
        )

    @Test
    fun `the maven provider bounds the heap in every worktree`() = runTest {
        val resolve = resolver(RepositoryProviders(maven = MavenProvider("1g")))

        listOf(repository, task, other).forEach { worktree ->
            assertEquals(listOf("MAVEN_OPTS" to "-Xmx1g"), resolve(repository, worktree).getOrThrow().variables)
        }
    }

    @Test
    fun `the maven provider without a heap adds nothing`() = runTest {
        assertEquals(emptyList(), resolver(RepositoryProviders(maven = MavenProvider()))(repository, task).getOrThrow().variables)
    }

    @Test
    fun `MAVEN_OPTS can't be declared to replace the maven provider`() {
        assertFailsWith<InvalidEnvironmentTemplate> { parseEnvironmentVariable("MAVEN_OPTS", "-Xmx9g") }
    }

    @Test
    fun `a repository without the maven provider gets no maven variable`() = runTest {
        assertEquals(emptyList(), resolver(RepositoryProviders())(repository, task).getOrThrow().variables)
    }

    @Test
    fun `the compose project name joins the repository and worktree folders`() = runTest {
        val resolve = resolver(RepositoryProviders(dockerCompose = DockerComposeProvider()))

        assertEquals(listOf("COMPOSE_PROJECT_NAME" to "main-repo-task-123"), resolve(repository, task).getOrThrow().variables)
    }

    @Test
    fun `a declared COMPOSE_PROJECT_NAME wins`() = runTest {
        val resolve = resolver(RepositoryProviders(dockerCompose = DockerComposeProvider()), "COMPOSE_PROJECT_NAME" to "mine")

        assertEquals(listOf("COMPOSE_PROJECT_NAME" to "mine"), resolve(repository, task).getOrThrow().variables)
    }

    @Test
    fun `compose project names only use supported characters`() {
        val name = composeProjectName("main-repo", "fix login (2)")

        assertTrue(Regex("[a-z0-9][a-z0-9_-]*").matches(name), name)
    }

    @Test
    fun `compose project names never collide for folders that normalise alike`() {
        val names = listOf("Fix-A", "fix-a", "fix a", "fix.a", "fix-a ").map { composeProjectName("main-repo", it) }

        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `compose project names stay within the length limit`() {
        val name = composeProjectName("main-repo", "x".repeat(120))

        assertTrue(name.length <= 50, name)
        assertNotEquals(composeProjectName("main-repo", "x".repeat(121)), name)
    }

    @Test
    fun `compose project names do not start with a dash or underscore`() {
        assertTrue(composeProjectName(".repo", "task").startsWith("repo-task"))
        assertTrue(!composeProjectName("_repo", "task").startsWith("_"))
    }

    @Test
    fun `compose port variables get distinct stable ports in every worktree`() = runTest {
        val store = FakeEnvironmentStateStore()
        val resolve = resolver(RepositoryProviders(dockerCompose = DockerComposeProvider(listOf("DB_PORT", "API_PORT"))), store = store)

        val first = resolve(repository, task).getOrThrow().variables.toMap()
        val again = resolve(repository, task).getOrThrow().variables.toMap()
        val second = resolve(repository, other).getOrThrow().variables.toMap()

        val ports = listOf("DB_PORT", "API_PORT").flatMap { listOf(first.getValue(it), second.getValue(it)) }
        assertEquals(4, ports.distinct().size)
        assertTrue(ports.all { it.toInt() in 20000..29999 })
        assertEquals(first, again)
    }

    @Test
    fun `a port variable also declared in env gets no port from the provider`() = runTest {
        val resolve = resolver(RepositoryProviders(dockerCompose = DockerComposeProvider(listOf("DB_PORT"))), "DB_PORT" to "5432")

        val environment = resolve(repository, task).getOrThrow()

        assertEquals("5432", environment.variables.toMap().getValue("DB_PORT"))
        assertTrue(environment.ports.isEmpty())
    }
}
