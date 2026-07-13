package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ThemePreferenceRepository
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

class JsonThemePreferenceRepository(
    private val storagePath: Path = Path.of(System.getProperty("user.home"), ".shogunai", "settings.json"),
) : ThemePreferenceRepository {

    private val json = Json { prettyPrint = true }

    override fun load(): ThemeMode {
        if (!storagePath.exists()) return ThemeMode.SYSTEM
        val text = storagePath.readText()
        if (text.isBlank()) return ThemeMode.SYSTEM
        return try {
            json.decodeFromString<Settings>(text).themeMode
        } catch (_: SerializationException) {
            ThemeMode.SYSTEM
        }
    }

    override fun save(mode: ThemeMode) {
        Files.createDirectories(storagePath.parent)
        val tempFile = Files.createTempFile(storagePath.parent, "settings", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(Settings(mode)))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Serializable
    private data class Settings(val themeMode: ThemeMode)
}
