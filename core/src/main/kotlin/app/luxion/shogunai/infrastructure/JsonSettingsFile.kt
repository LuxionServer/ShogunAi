package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.DEFAULT_INACTIVE_AFTER_DAYS
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.RecentWorktree
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.SidebarOrder
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.domain.model.WindowBounds
import app.luxion.shogunai.domain.model.WorktreeSort
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonSettingsFile(
    private val storagePath: Path = Path.of(System.getProperty("user.home"), ".shogunai", "settings.json"),
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    var lastReadProblem: WorktreeError.LocalFileUnreadable? = null
        private set

    fun acknowledgeReadProblem() {
        lastReadProblem = null
    }

    fun read(): Settings = LocalFileLock.withLock(storagePath) {
        try {
            decode()
        } catch (problem: WorktreeError.LocalFileUnreadable) {
            lastReadProblem = problem
            Settings()
        }
    }

    fun update(transform: (Settings) -> Settings) = LocalFileLock.withLock(storagePath) {
        val current = try {
            decode()
        } catch (problem: WorktreeError.LocalFileUnreadable) {
            lastReadProblem = problem
            keepUnreadableCopy(storagePath)
            Settings()
        }
        Files.createDirectories(storagePath.parent)
        val tempFile = Files.createTempFile(storagePath.parent, "settings", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(transform(current)))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun decode(): Settings {
        if (!storagePath.exists()) return Settings()
        val text = try {
            storagePath.readText()
        } catch (error: IOException) {
            throw WorktreeError.LocalFileUnreadable(storagePath.toString(), error.message)
        }
        if (text.isBlank()) return Settings()
        return try {
            json.decodeFromString<Settings>(text)
        } catch (error: SerializationException) {
            throw WorktreeError.LocalFileUnreadable(storagePath.toString(), error.message?.lineSequence()?.firstOrNull())
        } catch (error: IllegalArgumentException) {
            throw WorktreeError.LocalFileUnreadable(storagePath.toString(), error.message?.lineSequence()?.firstOrNull())
        }
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
        val recentProjects: List<String> = emptyList(),
        val recentWorktrees: List<RecentWorktree> = emptyList(),
        val showTrayIcon: Boolean = true,
        val keepRunningInTray: Boolean = false,
        val checkForUpdates: Boolean = true,
        val skippedVersion: String? = null,
        val worktreeAgents: Map<String, String> = emptyMap(),
        val claudeHooksNoticeDismissed: Boolean = false,
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
