package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.ThemeMode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * The user preferences file (`~/.shogunai/settings.json`), shared by every
 * preference repository. [update] rewrites the whole file atomically from the
 * current contents, so saving one preference keeps the others as they were.
 */
class JsonSettingsFile(
    private val storagePath: Path = Path.of(System.getProperty("user.home"), ".shogunai", "settings.json"),
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Current settings; defaults when the file is missing, empty or can't be parsed. */
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

    // Defaults also cover files written before a field existed (e.g. no `language`).
    @Serializable
    data class Settings(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val language: AppLanguage = AppLanguage.ENGLISH,
    )
}
