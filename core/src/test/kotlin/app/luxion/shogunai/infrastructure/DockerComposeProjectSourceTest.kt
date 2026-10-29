package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DockerComposeProjectSourceTest {

    private val output = """[{"Name":"main-repo-task-9","Status":"running(2)","ConfigFiles":"/a/compose.yaml,/a/override.yaml"},{"Name":"other","Status":"exited(1)","ConfigFiles":""}]"""

    @Test
    fun `lists projects with status and compose files`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, output) }

        val projects = DockerComposeProjectSource(executor).list()

        assertEquals(listOf("main-repo-task-9", "other"), projects.map { it.name })
        assertEquals(listOf("/a/compose.yaml", "/a/override.yaml"), projects[0].configFiles)
        assertEquals(listOf("docker", "compose", "ls", "--all", "--format", "json"), executor.executedCommands.single())
    }

    @Test
    fun `no output means no projects`() = runTest {
        assertEquals(emptyList(), DockerComposeProjectSource(FakeShellCommandExecutor()).list())
    }

    @Test
    fun `a failing docker is reported as unavailable with its first error line`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "Cannot connect to the Docker daemon\nmore") }

        val error = assertFailsWith<WorktreeError.DockerUnavailable> { DockerComposeProjectSource(executor).list() }

        assertEquals("Cannot connect to the Docker daemon", error.reason)
    }

    @Test
    fun `a missing docker binary is reported as unavailable`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, RuntimeException("no such file")) }

        assertFailsWith<WorktreeError.DockerUnavailable> { DockerComposeProjectSource(executor).list() }
    }

    @Test
    fun `takes a project down with its name and only removes volumes on request`() = runTest {
        val executor = FakeShellCommandExecutor()
        val source = DockerComposeProjectSource(executor)

        source.down("main-repo-task-9", removeVolumes = false).getOrThrow()
        source.down("main-repo-task-9", removeVolumes = true).getOrThrow()

        assertEquals(listOf("docker", "compose", "-p", "main-repo-task-9", "down"), executor.executedCommands[0])
        assertEquals(listOf("docker", "compose", "-p", "main-repo-task-9", "down", "--volumes"), executor.executedCommands[1])
    }

    @Test
    fun `a failing take down carries the reason`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "network has active endpoints") }

        val result = DockerComposeProjectSource(executor).down("x", removeVolumes = false)

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("network has active endpoints"))
    }

    @Test
    fun `starts a project detached in the worktree with its environment and extra arguments`() = runTest {
        val executor = FakeShellCommandExecutor()
        val environment = mapOf("COMPOSE_PROJECT_NAME" to "main-repo-task-123", "DB_PORT" to "20004")

        DockerComposeProjectSource(executor)
            .run("main-repo-task-123", ComposeAction.Up(build = true, extraArguments = listOf("api", "--wait")), "/w/TASK-123", environment)
            .getOrThrow()

        val command = listOf("docker", "compose", "up", "--detach", "--build", "api", "--wait")
        assertEquals(command, executor.executedCommands.single())
        assertEquals("/w/TASK-123", executor.workingDirectories.single())
        assertEquals(environment, executor.environments[command])
    }

    @Test
    fun `builds a project with its extra arguments`() = runTest {
        val executor = FakeShellCommandExecutor()

        DockerComposeProjectSource(executor).run("p", ComposeAction.Build(listOf("api")), "/w", emptyMap()).getOrThrow()

        assertEquals(listOf("docker", "compose", "build", "api"), executor.executedCommands.single())
    }

    @Test
    fun `a failing up carries the last error line`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "pulling\nport is already allocated\n") }

        val error = DockerComposeProjectSource(executor).run("p", ComposeAction.Up(), "/w", emptyMap()).exceptionOrNull()

        assertEquals("port is already allocated", (error as WorktreeError.ComposeCommandFailed).detail)
    }

    @Test
    fun `lists volumes, networks and built images by their project label`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            when {
                command.take(3) == listOf("docker", "volume", "ls") -> FakeShellCommandExecutor.success(command, "main-repo-task-9_db-data|main-repo-task-9\n")
                command.take(3) == listOf("docker", "network", "ls") -> FakeShellCommandExecutor.success(command, "ab12|main-repo-task-9_default|main-repo-task-9")
                command.take(3) == listOf("docker", "image", "ls") -> FakeShellCommandExecutor.success(command, "sha256:aa\nsha256:bb\nsha256:aa")
                command.take(3) == listOf("docker", "image", "inspect") ->
                    FakeShellCommandExecutor.success(command, "sha256:aa|main-repo-task-9-api:latest|main-repo-task-9\nsha256:bbbbbbbbbbbbbbbb||main-repo-task-9")
                else -> FakeShellCommandExecutor.failure(command)
            }
        }

        val resources = DockerComposeProjectSource(executor).listResources()

        assertEquals(
            listOf(
                ComposeResource(ComposeResourceKind.VOLUME, "main-repo-task-9_db-data", "main-repo-task-9_db-data", "main-repo-task-9"),
                ComposeResource(ComposeResourceKind.NETWORK, "ab12", "main-repo-task-9_default", "main-repo-task-9"),
                ComposeResource(ComposeResourceKind.IMAGE, "sha256:aa", "main-repo-task-9-api:latest", "main-repo-task-9"),
                ComposeResource(ComposeResourceKind.IMAGE, "sha256:bbbbbbbbbbbbbbbb", "bbbbbbbbbbbb", "main-repo-task-9"),
            ),
            resources,
        )
        assertEquals(listOf("sha256:aa", "sha256:bb"), executor.executedCommands.last().takeLast(2))
        assertTrue(executor.executedCommands.first().containsAll(listOf("--filter", "label=com.docker.compose.project")))
    }

    @Test
    fun `no labelled image means no inspect`() = runTest {
        val executor = FakeShellCommandExecutor()

        assertEquals(emptyList(), DockerComposeProjectSource(executor).listResources())
        assertTrue(executor.executedCommands.none { it.take(3) == listOf("docker", "image", "inspect") })
    }

    @Test
    fun `listing resources without docker is reported as unavailable`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "Cannot connect to the Docker daemon") }

        assertFailsWith<WorktreeError.DockerUnavailable> { DockerComposeProjectSource(executor).listResources() }
    }

    @Test
    fun `removes each kind without forcing and reports the reason of a failure`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command[1] == "image") FakeShellCommandExecutor.failure(command, stderr = "image is being used by running container") else FakeShellCommandExecutor.success(command)
        }
        val source = DockerComposeProjectSource(executor)

        source.remove(ComposeResource(ComposeResourceKind.NETWORK, "ab12", "n", "p")).getOrThrow()
        source.remove(ComposeResource(ComposeResourceKind.VOLUME, "v", "v", "p")).getOrThrow()
        val image = source.remove(ComposeResource(ComposeResourceKind.IMAGE, "sha256:aa", "i", "p"))

        assertEquals(listOf("docker", "network", "rm", "ab12"), executor.executedCommands[0])
        assertEquals(listOf("docker", "volume", "rm", "v"), executor.executedCommands[1])
        assertEquals(listOf("docker", "image", "rm", "sha256:aa"), executor.executedCommands[2])
        assertEquals("image is being used by running container", image.exceptionOrNull()?.message)
    }

    @Test
    fun `output that isn't a JSON list is reported as unavailable`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "not json") }

        assertFailsWith<WorktreeError.DockerUnavailable> { DockerComposeProjectSource(executor).list() }
    }

    @Test
    fun `running or listing resources without a docker binary is reported as unavailable`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, RuntimeException("no such file")) }
        val source = DockerComposeProjectSource(executor)

        assertIs<WorktreeError.DockerUnavailable>(source.run("p", ComposeAction.Up(), "/w", emptyMap()).exceptionOrNull())
        assertFailsWith<WorktreeError.DockerUnavailable> { source.listResources() }
    }
}
