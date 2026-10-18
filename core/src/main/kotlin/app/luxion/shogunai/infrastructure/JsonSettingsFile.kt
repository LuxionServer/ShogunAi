package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.ThemeMode
import app.luxion.shogunai.domain.model.WindowBounds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    fun read(): Settings {
        if (!storagePath.exists()) return Settings()
        val text = storagePath.readText()
        if (text.isBlank()) return Settings()
        return try {
            json.decodeFromString<Settings>(text)
        } catch (_: SerializationException) {
            Settings()
        } catch (_: IllegalArgumentException) {
            Settings()
        }
    }

    fun update(transform: (Settings) -> Settings) {
        val settings = transform(read())
        Files.createDirectories(storagePath.parent)
        val tempFile = Files.createTempFile(storagePath.parent, "settings", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(settings))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Serializable
    data class Settings(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val language: AppLanguage = AppLanguage.ENGLISH,
        val lastDestination: String? = null,
        val windowBounds: WindowBounds? = null,
        val sidebarCollapsed: Boolean = false,
        val collapsedProjects: List<String> = emptyList(),
        val lastCreateProject: String? = null,
        val recentPaletteItems: List<String> = emptyList(),
        val grouping: GroupingSettings = GroupingSettings(),
    )

    @Serializable
    data class GroupingSettings(
        val active: Grouping = Grouping.BY_STATE,
        val all: Grouping = Grouping.BY_PROJECT,
    )
}
