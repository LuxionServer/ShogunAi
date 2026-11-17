package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.FlutterPubGet
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.NodeDependencies
import app.luxion.shogunai.domain.model.NodePackageManager
import app.luxion.shogunai.domain.model.NodeProvider
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.ProviderToolProblem
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Stack
import app.luxion.shogunai.domain.model.SuggestedProvider
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.suggestProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeProviderFlowTest {

    private val repository = "/home/dev/projects/main-repo"
    private val worktree = "/home/dev/projects/worktrees/TASK-123"
    private val nodeProvisioning = RepositoryProvisioning(providers = RepositoryProviders(node = NodeProvider))

    private fun detect(vararg files: String, directories: Set<String> = emptySet()): List<DetectedStack> =
        DetectRepositoryStacksUseCase(
            FakeFileManager(existing = files.map { "$repository/$it" }, directories = directories.map { "$repository/$it" }.toSet()),
        )(repository)

    @Test
    fun `detects node from the main folder's package json with its lockfiles as evidence`() {
        assertEquals(
            listOf(DetectedStack(Stack.DOCKER_COMPOSE, listOf("compose.yaml")), DetectedStack(Stack.NODE, listOf("package.json", "pnpm-lock.yaml"))),
            detect("package.json", "pnpm-lock.yaml", "compose.yaml"),
        )
        assertEquals(listOf(DetectedStack(Stack.NODE, listOf("package.json"))), detect("package.json"))
    }

    @Test
    fun `a node project only in a subfolder or a lone lockfile isn't detected`() {
        assertTrue(detect("web/package.json", "yarn.lock", directories = setOf("web")).isEmpty())
    }

    @Test
    fun `suggests node on every system unless it is declared`() {
        val stacks = detect("package.json", "package-lock.json", "compose.yaml")

        val suggested = suggestProviders(stacks, RepositoryProviders(), isMac = false).suggestions.map { it.provider }
        val declared = suggestProviders(stacks, RepositoryProviders(node = NodeProvider, dockerCompose = DockerComposeProvider()), isMac = false)

        assertEquals(listOf(SuggestedProvider.DOCKER_COMPOSE, SuggestedProvider.NODE), suggested)
        assertTrue(declared.suggestions.isEmpty())
    }

    @Test
    fun `the environment passes the repository's approval to the node step after flutter`() = runTest {
        val source = FakeRepositoryProvisioningSource(byRepository = mapOf(repository to nodeProvisioning))
        val order = mutableListOf<String>()
        val approvals = mutableListOf<Boolean>()

        suspend fun prepare(trust: FakeRepositoryTrust) = PrepareWorktreeEnvironmentUseCase(
            ResolveWorktreeEnvironmentUseCase(FakeShellCommandExecutor(), source, FakeEnvironmentStateStore(), FakePortProbe(), trust = trust),
            WriteEnvironmentFileUseCase(FakeShellCommandExecutor(), FakeFileManager(), source),
            flutterPackages = { _, _ -> order += "flutter"; FlutterPubGet.Resolved },
            nodeDependencies = { _, _, approved -> order += "node"; approvals += approved; NodeDependencies.Installed(NodePackageManager.NPM) },
        ).prepare(repository, worktree)

        val prepared = prepare(FakeRepositoryTrust())
        prepare(FakeRepositoryTrust(mapOf(repository to nodeProvisioning.approvalSubject)))

        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), prepared.node)
        assertEquals(listOf("flutter", "node", "flutter", "node"), order)
        assertEquals(listOf(false, true), approvals)
    }

    private suspend fun doctor(files: List<String>, executables: Map<String, String>): List<InstallationCheck> =
        CheckProviderToolsUseCase(
            FakeShellCommandExecutor(),
            { executables[it] },
            FakeFileManager(existing = files.map { "$repository/$it" }),
            FakeAndroidSdk(),
            FakeAppleSimulators(),
            { null },
            HostOs.MACOS,
            "/home/dev",
        )("main-repo", repository, RepositoryProviders(node = NodeProvider))

    @Test
    fun `the doctor checks node and the manager found from the lockfile`() = runTest {
        val checks = doctor(listOf("pnpm-lock.yaml"), mapOf("node" to "/opt/homebrew/bin/node", "pnpm" to "/opt/homebrew/bin/pnpm"))

        assertEquals(listOf(CheckState.PASSED, CheckState.PASSED), checks.map { it.state })
        assertEquals(
            listOf(ProviderToolId.NODE to "/opt/homebrew/bin/node", ProviderToolId.PNPM to "/opt/homebrew/bin/pnpm"),
            checks.map { (it.finding as InstallationFinding.ProviderTool).let { tool -> tool.tool to tool.location } },
        )
    }

    @Test
    fun `a missing manager warns with how to get it`() = runTest {
        val check = doctor(listOf("yarn.lock"), mapOf("node" to "/opt/homebrew/bin/node")).last()

        assertEquals(CheckState.WARNING, check.state)
        assertEquals(ProviderToolId.YARN, (check.finding as InstallationFinding.ProviderTool).tool)
        assertEquals("Enable it with Corepack (corepack enable) or install it, and make sure it's on your PATH", InstallationReportText.fix(check))
    }

    @Test
    fun `node tool fixes`() {
        fun fix(tool: ProviderToolId) = InstallationReportText.fix(
            InstallationCheck(CheckState.WARNING, InstallationFinding.ProviderTool("main-repo", tool, null, ProviderToolProblem.NotFound(emptyList(), listOf(tool.id)))),
        )

        assertEquals("Install Node.js, which includes npm (https://nodejs.org), and make sure it's on your PATH", fix(ProviderToolId.NODE))
        assertEquals(fix(ProviderToolId.NODE), fix(ProviderToolId.NPM))
        assertEquals(fix(ProviderToolId.YARN), fix(ProviderToolId.PNPM))
        assertEquals("Install bun (https://bun.sh) and make sure it's on your PATH", fix(ProviderToolId.BUN))
    }
}
