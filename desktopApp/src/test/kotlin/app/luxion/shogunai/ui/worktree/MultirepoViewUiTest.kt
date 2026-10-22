package app.luxion.shogunai.ui.worktree

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.usecase.GroupRepositoryOutcome
import app.luxion.shogunai.domain.usecase.GroupRepositoryResult
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.multirepo.MultirepoMembership
import app.luxion.shogunai.ui.multirepo.NewChange
import app.luxion.shogunai.ui.multirepo.NewChangeDialog
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MultirepoViewUiTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private fun model(alias: String? = null, groupTag: String? = null) = worktreeRowModel(
        worktree = Worktree(path = "/home/dev/projects/TASK-12", branch = "feature/TASK-12"),
        status = WorktreeStatusState.Loaded(WorktreeStatus(uncommittedChanges = 0, upstream = Divergence(0, 0), mainBranch = Divergence(2, 0), lastCommitAt = now)),
        mainBranchName = "develop",
        agents = emptyList(),
        emulator = null,
        simulator = null,
        origin = null,
        projectName = null,
        inactiveAfter = Duration.ofDays(3),
        now = now,
        strings = EnglishStrings,
        alias = alias,
        groupTag = groupTag,
    )

    @Test
    fun `a row of a group shows its tag first and following it opens the group`() = runComposeUiTest {
        var opened = 0
        setContent {
            MaterialTheme {
                WorktreeRow(
                    model = model(groupTag = "TASK-12 · +api"),
                    selected = false,
                    isCopied = false,
                    isMain = false,
                    onSelect = {},
                    onCopyLaunchCommand = {},
                    onCopyPath = {},
                    onOpenInHerdr = {},
                    onOpenGroupTag = { opened++ },
                    onRemove = {},
                )
            }
        }

        onNodeWithText("TASK-12 · +api").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `a row of the multirepo view shows the repository alias in its first column`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                WorktreeRow(
                    model = model(alias = "api"),
                    selected = false,
                    isCopied = false,
                    isMain = false,
                    onSelect = {},
                    onCopyLaunchCommand = {},
                    onCopyPath = {},
                    onOpenInHerdr = {},
                    onRemove = {},
                )
            }
        }

        onNodeWithTag(AliasTag, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("api").assertIsDisplayed()
    }

    private fun group(state: StateBadgeKind) = ChangeGroup(
        branch = "feature/TASK-1",
        entries = emptyList(),
        state = state,
        members = emptyList(),
    )

    @Test
    fun `a group header shows the title, the branch and the combined state`() = runComposeUiTest {
        var cleaned = 0
        setContent {
            MaterialTheme {
                ChangeGroupHeaderView(
                    group = group(StateBadgeKind.NEEDS_YOU),
                    cleanUpBlocker = "api isn't merged",
                    canCleanUp = false,
                    addableAliases = listOf("docs"),
                    isAdding = false,
                    onToggle = {},
                    onOpenAll = {},
                    onAddRepository = {},
                    onCleanUp = { cleaned++ },
                )
            }
        }

        onNodeWithText("TASK-1").assertIsDisplayed()
        onNode(hasText("feature/TASK-1", substring = true)).assertIsDisplayed()
        onNodeWithText("Needs you").assertIsDisplayed()
        onNodeWithText("Clean up group").assertIsNotEnabled()
        onNodeWithText("Open all in Herdr").assertIsEnabled()
        assertEquals(0, cleaned)
    }

    @Test
    fun `add repository offers the repositories not yet in the group`() = runComposeUiTest {
        val picked = mutableListOf<String>()
        setContent {
            MaterialTheme {
                ChangeGroupHeaderView(
                    group = group(StateBadgeKind.MERGED),
                    cleanUpBlocker = null,
                    canCleanUp = true,
                    addableAliases = listOf("docs"),
                    isAdding = false,
                    onToggle = {},
                    onOpenAll = {},
                    onAddRepository = { picked += it },
                    onCleanUp = {},
                )
            }
        }

        onNodeWithText("Merged").assertIsDisplayed()
        onNodeWithText("Clean up group").assertIsEnabled()
        onNodeWithText("Add repository").performClick()
        onNodeWithText("docs").performClick()
        assertEquals(listOf("docs"), picked)
    }

    @Test
    fun `the new change dialog reports each repository's result`() = runComposeUiTest {
        val membership = MultirepoMembership(
            Multirepo("/home/dev/projects/product", MultirepoDeclaration(mapOf("app" to "app", "api" to "api"), "../w", listOf("app", "api"))),
            mapOf("app" to "app", "api" to "api"),
        )
        val change = NewChange(membership).apply {
            updateTaskId("TASK-20")
            results = listOf(
                GroupRepositoryResult("app", GroupRepositoryOutcome.CREATED),
                GroupRepositoryResult("api", GroupRepositoryOutcome.FAILED, error = IllegalStateException("boom")),
            )
        }
        setContent {
            MaterialTheme { NewChangeDialog(change, DialogPostCreateApproval(), onConfirm = {}, onDismiss = {}) }
        }

        onNodeWithText("created").assertIsDisplayed()
        onNode(hasText("failed:", substring = true)).assertIsDisplayed()
        onNodeWithText("Close").assertIsDisplayed()
    }
}
