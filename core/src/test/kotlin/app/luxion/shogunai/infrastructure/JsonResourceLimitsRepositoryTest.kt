package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceLimits
import app.luxion.shogunai.domain.model.ResourceSettings
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonResourceLimitsRepositoryTest {

    private val tempDir = createTempDirectory("json-resource-limits-test")
    private val storagePath = tempDir.resolve("settings.json")
    private val repository = JsonResourceLimitsRepository(JsonSettingsFile(storagePath))

    @AfterTest
    fun tearDown() {
        tempDir.listDirectoryEntries().forEach { it.deleteIfExists() }
        tempDir.deleteIfExists()
    }

    @Test
    fun `a missing file reads as Automatic`() {
        assertEquals(CapacityProfile.AUTOMATIC, repository.load().profile)
    }

    @Test
    fun `settings without resources read as Automatic`() {
        storagePath.writeText("""{"language":"ENGLISH"}""")
        assertEquals(ResourceSettings(), repository.load())
    }

    @Test
    fun `an unreadable file reads as Automatic`() {
        storagePath.writeText("{ not json")
        assertEquals(CapacityProfile.AUTOMATIC, repository.load().profile)
    }

    @Test
    fun `saved custom limits are read back`() {
        val settings = ResourceSettings(CapacityProfile.CUSTOM, ResourceLimits(emulators = 3, simulators = null, gradleDaemons = 2))
        repository.save(settings)
        assertEquals(settings, JsonResourceLimitsRepository(JsonSettingsFile(storagePath)).load())
    }
}
