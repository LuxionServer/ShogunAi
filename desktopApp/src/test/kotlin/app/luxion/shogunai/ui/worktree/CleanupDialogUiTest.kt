package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ComposeUseCases
import app.luxion.shogunai.EmulatorUseCases
import app.luxion.shogunai.GradleUseCases
import app.luxion.shogunai.HerdrUseCases
import app.luxion.shogunai.MavenUseCases
import app.luxion.shogunai.OrcaUseCases
import app.luxion.shogunai.SimulatorUseCases
import app.luxion.shogunai.XcodeUseCases
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.domain.usecase.CleanUpWorktreesUseCase
import app.luxion.shogunai.domain.usecase.DeleteOrphanedDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListDerivedDataUseCase
import app.luxion.shogunai.domain.usecase.ListGradleDaemonsUseCase
import app.luxion.shogunai.domain.usecase.StopGradleDaemonsUseCase
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.multirepo.multirepoMemberships
import app.luxion.shogunai.ui.shell.LocalAppMessages
import app.luxion.shogunai.ui.shell.rememberAppMessages
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CleanupDialogUiTest {

    private val strings = EnglishStrings
    private val api = FakeWorktreeRepository("api", listOf("/home/dev/projects/api-TASK-1" to "feature/TASK-1", "/home/dev/projects/api-TASK-2" to "feature/TASK-2"))
    private val docs = FakeWorktreeRepository("docs", listOf("/home/dev/projects/docs-TASK-1" to "feature/TASK-1"))

    private fun ComposeUiTest.show(board: WorktreeBoard) {
        setContent {
            val messages = rememberAppMessages()
            CompositionLocalProvider(LocalAppMessages provides messages) {
                MaterialTheme {
                    Box(Modifier.size(1000.dp, 900.dp)) {
                        WorktreeScreen(board, onOpenProjectSettings = {}, onSeeAll = {}, onOpenMachine = {})
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) { board.isCatalogLoaded }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun everyProvider(repository: FakeWorktreeRepository, useCases: app.luxion.shogunai.WorktreeUseCases, gate: CompletableDeferred<Unit>) =
        if (repository.id != "api") {
            useCases
        } else {
            useCases.copy(
                detectAgents = { Result.success(mapOf("/home/dev/projects/api-TASK-2" to listOf(RunningAgent("claude")))) },
                gradle = GradleUseCases(
                    provider = { GradleProvider() },
                    listDaemons = ListGradleDaemonsUseCase(FakeGradleDaemonSource(), FakeFileManager()),
                    stopDaemons = StopGradleDaemonsUseCase(FakeGradleDaemonSource(), repository.executor),
                    isBuildCacheEnabled = { true },
                ),
                maven = MavenUseCases(provider = { MavenProvider() }),
                compose = ComposeUseCases(provider = { DockerComposeProvider() }),
                emulator = EmulatorUseCases.NONE.copy(declared = { true }),
                simulator = SimulatorUseCases.NONE.copy(
                    declared = { true },
                    orphans = { Result.success(listOf(SimulatorDevice("SIM-9", "ShogunAi · api · gone", SimulatorState.SHUTDOWN))) },
                ),
                xcode = XcodeUseCases(
                    provider = { XcodeProvider() },
                    supported = { true },
                    list = ListDerivedDataUseCase(
                        FakeDerivedDataSource(
                            existing = listOf(
                                DerivedDataFolder("/home/dev/DerivedData/App-a", "/home/dev/gone/App.xcodeproj", 2048L, null),
                                DerivedDataFolder("/home/dev/DerivedData/App-b", "/home/dev/gone/Other.xcodeproj", null, null),
                            ),
                        ),
                    ),
                    deleteOrphans = DeleteOrphanedDerivedDataUseCase(FakeDerivedDataSource()),
                ),
                herdr = HerdrUseCases.NONE.copy(openWorkspaces = { paths -> paths.associateWith { "w1" } }),
                orca = OrcaUseCases.NONE.copy(withTerminals = { paths -> paths.toSet() }),
                cleanUp = CleanUpWorktreesUseCase(
                    repository.config,
                    object : ScriptedShellCommandExecutor() {
                        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
                            gate.await()
                            return repository.executor.respond(command, workingDirectory)
                        }
                    },
                ),
            )
        }

    @Test
    fun `the cleanup dialog lists every project's candidates and offers every provider's options`() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val board = uiBoard(listOf(api, docs)) { repository, useCases -> everyProvider(repository, useCases, gate) }
        show(board)
        waitUntil(timeoutMillis = 5_000) { board.holder("api")!!.isXcodeRepository && board.holder("api")!!.derivedDataOrphans.isNotEmpty() }

        runOnIdle { board.openCleanup() }
        waitForText(strings.alsoDeleteDerivedData)
        waitForText(strings.herdrCleanupNotice)
        val checkboxes = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        listOf(0, 4, 3, 7, 1, 2, 5, 6).forEach { index -> onAllNodes(checkboxes)[index].performSemanticsAction(SemanticsActions.OnClick) }
        runOnIdle {
            assertTrue(board.cleanupDeleteBranches)
            assertFalse(board.cleanupDownComposeProjects)
            assertFalse(board.cleanupRemoveComposeVolumes)
            assertFalse(board.cleanupDeleteDerivedData)
        }

        runOnIdle { board.confirmCleanup() }
        waitUntil { board.isCleaningUp }
        waitForIdle()
        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { !board.isCleaningUp }
    }

    @Test
    fun `a group cleanup leaves out the derived data and the orphans`() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        gate.complete(Unit)
        val board = uiBoard(listOf(api, docs)) { repository, useCases -> everyProvider(repository, useCases, gate) }
        val product = Multirepo(
            "/home/dev/projects/product",
            MultirepoDeclaration(repos = mapOf("api" to api.base, "docs" to docs.base), worktreesRoot = "../product-worktrees"),
            approved = true,
        )
        board.syncMultirepos(multirepoMemberships(listOf(product), board.projects))
        board.showMultirepo(product.folder)
        show(board)
        waitUntil(timeoutMillis = 5_000) { board.changeView.groups.isNotEmpty() }

        runOnIdle { board.openGroupCleanup(board.changeView.groups.single()) }
        waitForText(strings.cleanUpGroupTitle)

        runOnIdle { assertTrue(onAllNodesWithText(strings.alsoDeleteDerivedData).fetchSemanticsNodes().isEmpty()) }
    }

    @Test
    fun `a single project's cleanup of orphans only has no headings and no candidates`() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        gate.complete(Unit)
        api.respond = { command ->
            if (command == listOf("git", "rev-list", "--left-right", "--count", "origin/main...HEAD")) {
                app.luxion.shogunai.domain.FakeShellCommandExecutor.success(command, "0\t1\n")
            } else {
                null
            }
        }
        val board = uiBoard(listOf(api)) { repository, useCases ->
            everyProvider(repository, useCases, gate).copy(
                xcode = XcodeUseCases(
                    provider = { XcodeProvider() },
                    supported = { true },
                    list = ListDerivedDataUseCase(FakeDerivedDataSource()),
                    deleteOrphans = DeleteOrphanedDerivedDataUseCase(FakeDerivedDataSource()),
                ),
            )
        }
        board.show("api")
        show(board)
        waitUntil(timeoutMillis = 5_000) { board.holder("api")!!.orphanedSimulators.isNotEmpty() }

        runOnIdle { board.openCleanup() }
        waitForText(strings.alsoDeleteSimulators)

        runOnIdle { assertTrue(board.cleanupProjects.single().worktrees.isEmpty()) }
    }
}
