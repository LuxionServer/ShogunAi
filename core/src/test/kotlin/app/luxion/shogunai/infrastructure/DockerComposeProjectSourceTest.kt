package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
