package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.DEFAULT_INACTIVE_AFTER_DAYS
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.RecentWorktree
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.SidebarOrder
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.domain.model.WindowBounds
import app.luxion.shogunai.domain.model.WorktreeSort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.luxion.shogunai.domain.model.WorktreeError
import java.nio.file.Path

class JsonSettingsFile(
    storagePath: Path,
) {
    private val file = VersionedJsonFile(
        path = storagePath,
        serializer = Settings.serializer(),
        json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
        empty = Settings(),
    )

    @Volatile
    var lastReadProblem: WorktreeError.LocalFileUnreadable? = null
        private set

    fun acknowledgeReadProblem() {
        lastReadProblem = null
    }

    fun read(): Settings = try {
        file.read()
    } catch (problem: WorktreeError.LocalFileUnreadable) {
        lastReadProblem = problem
        Settings()
    }

    fun update(transform: (Settings) -> Settings) {
        val problem = try {
            file.update(transform)
        } catch (_: WorktreeError.LocalFileFromNewerVersion) {
            null
        }
        if (problem != null) lastReadProblem = problem
    }

    @Serializable
    data class Settings(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val language: AppLanguage = AppLanguage.ENGLISH,
        val lastDestination: String? = null,
        val windowBounds: WindowBounds? = null,
        val sidebarCollapsed: Boolean = false,
        val collapsedProjects: List<String> = emptyList(),
        val collapsedMultirepos: List<String> = emptyList(),
        val sidebarOrder: SidebarOrder = SidebarOrder(),
        val lastCreateProject: String? = null,
        val recentPaletteItems: List<String> = emptyList(),
        val grouping: GroupingSettings = GroupingSettings(),
        val sorting: SortingSettings = SortingSettings(),
        val inactiveAfterDays: Int = DEFAULT_INACTIVE_AFTER_DAYS,
        val terminal: TerminalId? = null,
        val editor: EditorId? = null,
        val runner: RunnerId? = null,
        val recentProjects: List<String> = emptyList(),
        val recentWorktrees: List<RecentWorktree> = emptyList(),
        val showTrayIcon: Boolean = true,
        val keepRunningInTray: Boolean = false,
        val checkForUpdates: Boolean = true,
        val skippedVersion: String? = null,
        val worktreeAgents: Map<String, String> = emptyMap(),
        val claudeHooksNoticeDismissed: Boolean = false,
        val tourOffered: Boolean = false,
        val resources: ResourceSettings = ResourceSettings(),
    )

    @Serializable
    data class GroupingSettings(
        val active: Grouping = Grouping.BY_STATE,
        val all: Grouping = Grouping.BY_PROJECT,
        val multirepo: Grouping = Grouping.BY_CHANGE,
    )

    @Serializable
    data class SortingSettings(
        val active: WorktreeSort = WorktreeSort.NAME,
        val all: WorktreeSort = WorktreeSort.NAME,
        val project: WorktreeSort = WorktreeSort.NAME,
    )
}
