package app.luxion.shogunai.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelPredicatesTest {

    private val holder = PoolHolder(ResourceKind.EMULATOR, "/projects/worktrees/task-123", null, PoolHolderState.RUNNING, idle = true, lastUsed = null)

    @Test
    fun `gradle settings exist when any of them is set`() {
        assertFalse(GradleProvider().hasSettings)
        assertTrue(GradleProvider(daemonMaxHeap = "2g").hasSettings)
        assertTrue(GradleProvider(idleTimeoutMinutes = 5).hasSettings)
        assertTrue(GradleProvider(kotlinDaemonMaxHeap = "1g").hasSettings)
    }

    @Test
    fun `a repository declares exactly the providers it configures`() {
        val all = RepositoryProviders(
            gradle = GradleProvider(),
            emulator = EmulatorProvider(),
            iosSimulator = IosSimulatorProvider(),
            xcode = XcodeProvider(),
            maven = MavenProvider(),
            dockerCompose = DockerComposeProvider(),
            flutter = FlutterProvider,
        )

        SuggestedProvider.entries.forEach { provider ->
            assertTrue(all.declares(provider), provider.id)
            assertFalse(RepositoryProviders().declares(provider), provider.id)
        }
    }

    @Test
    fun `a herdr pane is at its prompt when the shell runs in the foreground`() {
        assertTrue(HerdrPaneProcesses(shellPid = 10, foregroundProcessGroupId = 10).atPrompt)
        assertFalse(HerdrPaneProcesses(shellPid = 10, foregroundProcessGroupId = 11).atPrompt)
        assertFalse(HerdrPaneProcesses(shellPid = null, foregroundProcessGroupId = 10).atPrompt)
        assertFalse(HerdrPaneProcesses(shellPid = 10, foregroundProcessGroupId = null).atPrompt)
    }

    @Test
    fun `hidden characters are controls, formats and line or paragraph separators`() {
        listOf("a\u0007", "a\u200B", "a\u2028", "a\u2029").forEach { assertTrue(hasHiddenCharacters(it), it) }
        assertFalse(hasHiddenCharacters("plain text"))
    }

    @Test
    fun `headroom only counts when it wraps claude`() {
        assertEquals("headroom wrap claude -n task", namedClaudeCommand("headroom wrap claude", "task"))
        assertNull(namedClaudeCommand("headroom run claude", "task"))
        assertNull(namedClaudeCommand(" ", "task"))
    }

    @Test
    fun `only a positive fixed quota has bytes`() {
        assertEquals(2 * BYTES_PER_GIGABYTE, ResourceSettings(derivedDataQuotaGigabytes = 2).fixedDerivedDataQuotaBytes)
        assertNull(ResourceSettings(derivedDataQuotaGigabytes = 0).fixedDerivedDataQuotaBytes)
        assertNull(ResourceSettings().fixedDerivedDataQuotaBytes)
    }

    @Test
    fun `a lease protects a holder that has finished booting and isn't ranked for release`() {
        val leased = holder.copy(leaseExpiresAt = Instant.EPOCH)

        assertEquals(HolderProtection.LEASED, leased.protection)
        assertNull(leased.copy(releaseRank = 1).protection)
        assertNull(leased.copy(state = PoolHolderState.BOOTING).protection)
        assertNull(holder.protection)
    }

    @Test
    fun `a pool is over its limit only when it has one and exceeds it`() {
        assertTrue(PoolUsage(ResourceKind.EMULATOR, 1, 1, listOf(holder, holder)).isOverLimit)
        assertFalse(PoolUsage(ResourceKind.EMULATOR, 2, 2, listOf(holder, holder)).isOverLimit)
        assertFalse(PoolUsage(ResourceKind.EMULATOR, null, null, listOf(holder)).isOverLimit)
    }

    @Test
    fun `holders are named after their repository and worktree`() {
        assertEquals("repo · task-123", poolDisplayName("/projects/repo/.git/worktrees/task-123", "/projects/worktrees/task-123/"))
        assertEquals("task-123", poolDisplayName(null, "/projects/worktrees/task-123"))
        assertEquals("repo · task-123", poolDisplayName("/projects/repo/.git/worktrees/task-123", null))
        assertEquals("", poolDisplayName(null, null))
    }

    @Test
    fun `an unreadable file explains why only when it knows`() {
        fun text(detail: String?) = UnavailableLocalFile(".env", "/repo/.env", LocalFileOrigin.PROJECT, UnavailableReason.Unreadable(detail)).problemText()

        assertEquals("/repo/.env can't be read (denied). Make it readable for your user", text("denied"))
        assertEquals("/repo/.env can't be read. Make it readable for your user", text(" "))
        assertEquals("/repo/.env can't be read. Make it readable for your user", text(null))
    }

    @Test
    fun `derived data sizes count unknown sizes as zero`() {
        val known = DerivedDataFolder("/dd/a", "/w/a.xcworkspace", 10, null)
        val unknown = known.copy(sizeBytes = null)

        assertEquals(10, DerivedDataDeletion(deleted = listOf(known, unknown)).bytesFreed)
        assertEquals(10, DerivedDataTrim(null, 0, 0, released = listOf(AttributedDerivedData(known, DerivedDataOwner.Orphaned), AttributedDerivedData(unknown, DerivedDataOwner.Orphaned))).bytesFreed)
    }

    @Test
    fun `an automatic quota without bytes is unknown`() {
        assertTrue(DerivedDataQuota(DerivedDataQuotaMode.AUTOMATIC, null).isUnknown)
        assertFalse(DerivedDataQuota(DerivedDataQuotaMode.AUTOMATIC, 1).isUnknown)
        assertFalse(DerivedDataQuota(DerivedDataQuotaMode.FIXED, null).isUnknown)
    }

    @Test
    fun `an orca update is empty only without any field`() {
        assertTrue(OrcaWorktreeUpdate().isEmpty)
        assertFalse(OrcaWorktreeUpdate(comment = "c").isEmpty)
        assertFalse(OrcaWorktreeUpdate(workspaceStatus = "s").isEmpty)
        assertFalse(OrcaWorktreeUpdate(pullRequest = 1).isEmpty)
        assertFalse(OrcaWorktreeUpdate(issue = 1).isEmpty)
    }

    @Test
    fun `orca is recognised with and without a windows suffix`() {
        assertTrue(OrcaComputerUse.drivesDesktop("orca.exe computer click --app a"))
        assertTrue(OrcaComputerUse.drivesDesktop("C:\\tools\\orca.cmd computer click --app a"))
        assertTrue(OrcaComputerUse.drivesDesktop("orca computer click --app a"))
        assertFalse(OrcaComputerUse.drivesDesktop("other.exe computer click --app a"))
    }

    @Test
    fun `mobile secrets are recognised by name`() {
        assertEquals(MobileSecretKind.KEYSTORE, MobileSecretKind.of("release.jks"))
        assertEquals(MobileSecretKind.KEYSTORE, MobileSecretKind.of("debug.keystore"))
        assertNull(MobileSecretKind.of("notes.txt"))
        assertTrue(MobileSecretsReport().isEmpty)
        assertFalse(MobileSecretsReport(secrets = listOf(MobileSecret("a.jks", MobileSecretKind.KEYSTORE))).isEmpty)
        assertFalse(MobileSecretsReport(warnings = listOf(KeystoreReferenceWarning("f", "k", "v"))).isEmpty)
    }

    @Test
    fun `an approval declares an environment with variables or an env file`() {
        assertFalse(RepositoryApprovalSubject().declaresEnvironment)
        assertTrue(RepositoryApprovalSubject(variables = listOf("A" to "1")).declaresEnvironment)
        assertTrue(RepositoryApprovalSubject(envFile = ".env").declaresEnvironment)
    }

    @Test
    fun `pods preparation is empty only without clones, installs or errors`() {
        assertTrue(PodsPreparation().isEmpty)
        assertFalse(PodsPreparation(cloned = listOf("ios")).isEmpty)
        assertFalse(PodsPreparation(installNeeded = listOf(PodsInstallNeeded("ios", PodsInstallReason.BASE_HAS_NO_PODS))).isEmpty)
        assertFalse(PodsPreparation(error = "boom").isEmpty)
    }

    @Test
    fun `a worktree path drops only a known branch prefix`() {
        val config = ProjectConfig("/repo", "/worktrees", emptyList())

        assertEquals("/worktrees/task-123", config.worktreePathForBranch("feature/task-123"))
        assertEquals("/worktrees/wip-task-123", config.worktreePathForBranch("wip/task-123"))
    }

    @Test
    fun `an agent with an empty command has an empty name`() {
        assertEquals("", AgentLaunchConfig(agentCommand = "").displayName)
    }

    @Test
    fun `avd requests and settings must not be blank`() {
        assertEquals("Pixel", chooseAvd(" ", EmulatorSettings(avd = " "), null, listOf("Pixel")))
        assertEquals("Tablet", chooseAvd(null, EmulatorSettings(avd = "Tablet"), null, listOf("Pixel")))
    }
    @Test
    fun `only windows is windows`() {
        assertTrue(HostOs.WINDOWS.isWindows)
        assertFalse(HostOs.MACOS.isWindows)
    }
}
