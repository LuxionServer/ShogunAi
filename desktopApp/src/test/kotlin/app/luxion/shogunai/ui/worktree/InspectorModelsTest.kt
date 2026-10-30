package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.ProvisionDrift
import app.luxion.shogunai.domain.model.ProvisionDriftState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.ui.components.Property
import app.luxion.shogunai.ui.components.SectionState
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.worktree.inspector.agentSectionState
import app.luxion.shogunai.ui.worktree.inspector.environmentProperties
import app.luxion.shogunai.ui.worktree.inspector.environmentSectionState
import app.luxion.shogunai.ui.worktree.inspector.environmentSummary
import app.luxion.shogunai.ui.worktree.inspector.provisioningSectionState
import app.luxion.shogunai.ui.worktree.inspector.resourcesSectionState
import app.luxion.shogunai.ui.worktree.inspector.runsWithLabel
import app.luxion.shogunai.ui.worktree.inspector.statusProperties
import app.luxion.shogunai.ui.worktree.inspector.statusSectionState
import app.luxion.shogunai.ui.worktree.inspector.statusSummary
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InspectorModelsTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val worktree = Worktree(path = "/home/dev/projects/TASK-123", branch = "feature/TASK-123")
    private val status = WorktreeStatus(
        uncommittedChanges = 4,
        upstream = Divergence(ahead = 2, behind = 0),
        mainBranch = Divergence(ahead = 7, behind = 0),
        lastCommitAt = now.minus(Duration.ofHours(3)),
    )

    @Test
    fun `status properties list changes, divergences and the last commit`() {
        assertEquals(
            listOf(
                Property("Changes", "4"),
                Property("Upstream", "↑2 ↓0"),
                Property("vs develop", "↑7 ↓0"),
                Property("Last commit", "3 hours ago", isData = false),
            ),
            statusProperties(status, "develop", now, EnglishStrings),
        )
    }

    @Test
    fun `a merged branch says so against the main branch`() {
        val merged = status.copy(mainBranch = Divergence(ahead = 0, behind = 5))

        assertEquals(Property("vs develop", "merged", isData = false), statusProperties(merged, "develop", now, EnglishStrings)[2])
    }

    @Test
    fun `the divergence is against the recorded base, naming it`() {
        val stacked = status.copy(base = Divergence(ahead = 3, behind = 0), baseName = "epic/redesign")

        assertEquals(
            listOf(
                Property("Changes", "4"),
                Property("Upstream", "↑2 ↓0"),
                Property("vs epic/redesign", "↑3 ↓0"),
                Property("Last commit", "3 hours ago", isData = false),
            ),
            statusProperties(stacked, "develop", now, EnglishStrings),
        )
    }

    @Test
    fun `a branch merged into its recorded base says so naming the base`() {
        val merged = status.copy(mainBranch = Divergence(ahead = 4, behind = 0), base = Divergence(ahead = 0, behind = 1), baseName = "epic/redesign")

        assertEquals(Property("vs epic/redesign", "merged", isData = false), statusProperties(merged, "develop", now, EnglishStrings)[2])
    }

    @Test
    fun `a branch merged into the main branch but not its base is merged into the main branch`() {
        val merged = status.copy(mainBranch = Divergence(ahead = 0, behind = 3), base = Divergence(ahead = 2, behind = 0), baseName = "epic/redesign")

        assertEquals(Property("vs develop", "merged", isData = false), statusProperties(merged, "develop", now, EnglishStrings)[2])
    }

    @Test
    fun `the status summary counts changes and commits ahead`() {
        assertEquals("4 changes · ↑2", statusSummary(status, EnglishStrings))
        assertNull(statusSummary(status.copy(uncommittedChanges = 0, upstream = Divergence(0, 0)), EnglishStrings))
    }

    private val runsWith = runsWithLabel("Claude Code", "Herdr")

    @Test
    fun `provisioning is drawn only when a file is stale or the check failed`() {
        val stale = ProvisioningState.Checked(listOf(ProvisionDrift(".env", ProvisionDriftState.DIFFERS)))

        assertEquals(SectionState.NotApplicable, provisioningSectionState(worktree, ProvisioningState.Checking, EnglishStrings))
        assertEquals(SectionState.NotApplicable, provisioningSectionState(worktree, ProvisioningState.Checked(emptyList()), EnglishStrings))
        assertEquals(SectionState.Content, provisioningSectionState(worktree, stale, EnglishStrings))
        assertEquals(
            SectionState.Empty(EnglishStrings.provisioningUnavailable),
            provisioningSectionState(worktree, ProvisioningState.Unavailable, EnglishStrings),
        )
        assertEquals(SectionState.NotApplicable, provisioningSectionState(worktree.copy(isPrunable = true), stale, EnglishStrings))
    }

    @Test
    fun `runs with names the agent and the runner`() {
        assertEquals("Claude Code · Herdr", runsWith)
    }

    @Test
    fun `section states distinguish loading, empty and not applicable`() {
        assertEquals(SectionState.Loading, statusSectionState(null, EnglishStrings))
        assertEquals(SectionState.Empty("Status unavailable"), statusSectionState(WorktreeStatusState.Unavailable, EnglishStrings))
        assertEquals(SectionState.Content, statusSectionState(WorktreeStatusState.Loaded(status), EnglishStrings))
        assertEquals(SectionState.Loading, agentSectionState(null, worktree, runsWith, EnglishStrings))
        assertEquals(
            SectionState.Empty("No agent running · runs with Claude Code · Herdr"),
            agentSectionState(emptyMap(), worktree, runsWith, EnglishStrings),
        )
        assertEquals(
            SectionState.Content,
            agentSectionState(mapOf(worktree.path to listOf(RunningAgent("claude"))), worktree, runsWith, EnglishStrings),
        )
        assertEquals(SectionState.NotApplicable, environmentSectionState(null, emptyMap()))
        assertEquals(SectionState.NotApplicable, provisioningSectionState(worktree.copy(isMain = true), null, EnglishStrings))
        assertEquals(SectionState.NotApplicable, provisioningSectionState(worktree, null, EnglishStrings))
        assertEquals(SectionState.Loading, resourcesSectionState(applies = false, size = null, worktree = worktree))
        assertEquals(SectionState.NotApplicable, resourcesSectionState(false, null, worktree.copy(isPrunable = true)))
        assertEquals(SectionState.Content, resourcesSectionState(false, 1024L, worktree))
    }

    @Test
    fun `environment properties list ports by number, then variables, all copyable`() {
        val environment = WorktreeEnvironment(variables = listOf("PORT" to "4123", "APP_SUFFIX" to "-TASK-123"))
        val ports = mapOf("API_PORT" to 4124, "PORT" to 4123, EMULATOR_PORT_KEY to 5554)

        assertEquals(
            listOf("PORT 4123", "API_PORT 4124", "PORT 4123", "APP_SUFFIX -TASK-123"),
            environmentProperties(environment, ports).map { "${it.key} ${it.value}" },
        )
        assertEquals(true, environmentProperties(environment, ports).all { it.copyable })
        assertEquals("2 ports · 2 vars", environmentSummary(environment, ports, EnglishStrings))
    }
}
