package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelSummariesTest {

    @Test
    fun `provisioning is empty only without copy or include patterns`() {
        assertTrue(RepositoryProvisioning().isEmpty)
        assertFalse(RepositoryProvisioning(worktreeIncludePatterns = listOf(".env")).isEmpty)
    }

    @Test
    fun `mobile secrets report is empty only without secrets or warnings`() {
        assertTrue(MobileSecretsReport().isEmpty)
        assertFalse(MobileSecretsReport(warnings = listOf(KeystoreReferenceWarning("key.properties", "storeFile", "../release.jks"))).isEmpty)
    }

    @Test
    fun `pods preparation is empty only without clones, pending installs or an error`() {
        assertTrue(PodsPreparation().isEmpty)
        assertFalse(PodsPreparation(error = "clone failed").isEmpty)
    }

    @Test
    fun `docker compose and flutter providers count as declared`() {
        val declared = RepositoryProviders(dockerCompose = DockerComposeProvider(), flutter = FlutterProvider)

        assertTrue(declared.declares(SuggestedProvider.DOCKER_COMPOSE))
        assertTrue(declared.declares(SuggestedProvider.FLUTTER))
        assertFalse(RepositoryProviders().declares(SuggestedProvider.DOCKER_COMPOSE))
    }

    @Test
    fun `compose state keeps the project and its status`() {
        assertEquals(WorktreeComposeState("app", null), WorktreeComposeState("app", null).copy())
    }

    @Test
    fun `workflow topics parse by id`() {
        assertEquals(WorkflowTopic.CLEANUP, WorkflowTopic.parse("cleanup"))
        assertNull(WorkflowTopic.parse("unknown"))
    }

    @Test
    fun `host os is read from the os name`() {
        assertEquals(HostOs.MACOS, HostOs.of("Mac OS X"))
        assertEquals(HostOs.WINDOWS, HostOs.of("Windows 11"))
        assertEquals(HostOs.LINUX, HostOs.of("Linux"))
        assertEquals(HostOs.of(System.getProperty("os.name")), HostOs.current())
        assertEquals(HostArchitecture.of(System.getProperty("os.arch")), HostArchitecture.current())
    }

    @Test
    fun `maven daemon pid is its process pid`() {
        assertEquals(42L, MavenDaemon(MavenDaemonProcess(42L, 0L, null), owner = null).pid)
    }

    @Test
    fun `gradle daemons have no wait queue`() {
        assertEquals(WaitQueueName.EMULATOR, ResourceKind.EMULATOR.queueName)
        assertFailsWith<IllegalArgumentException> { ResourceKind.GRADLE_DAEMON.queueName }
    }

    @Test
    fun `wait ticket names its repository and worktree`() {
        val ticket = WaitTicket("1", "/projects/main-repo/.git/worktrees/task-123", "/projects/worktrees/task-123", ProcessIdentity(1L, null), 0L)

        assertEquals("main-repo · task-123", ticket.displayName)
        assertEquals("/projects/main-repo", ticket.repositoryPath)
    }

    @Test
    fun `posix command quotes only the words that need it`() {
        assertEquals("git commit -m 'a message'", ShellQuoting.posixCommand(listOf("git", "commit", "-m", "a message")))
    }

    @Test
    fun `duplicate variable name is its own issue`() {
        assertEquals("DuplicateVariableName", RepositoryConfigIssue.DuplicateVariableName.toString())
    }
}
