package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryProvisioningFilesTest {

    private val repository: Path = Files.createTempDirectory("main-repo")
    private val files = RepositoryProvisioningFiles()
    private val configFile = repository.resolve(".shogunai.json")
    private val worktreeInclude = repository.resolve(".worktreeinclude")

    @AfterTest
    fun cleanUp() {
        repository.toFile().deleteRecursively()
    }

    @Test
    fun `reads nothing when neither file exists`() {
        assertEquals(RepositoryProvisioning(), files.read(repository.toString()))
    }

    @Test
    fun `reads copy patterns and ignores unknown fields`() {
        configFile.writeText("""{ "copy": ["local.properties", ".env*"], "hooks": { "postCreate": ["npm", "install"] } }""")

        val provisioning = files.read(repository.toString())

        assertEquals(listOf("local.properties", ".env*"), provisioning.copyPatterns)
    }

    @Test
    fun `reads worktreeinclude skipping comments and blank lines`() {
        worktreeInclude.writeText("# secrets\n\n.env.local\n  config/keys/  \n")

        val provisioning = files.read(repository.toString())

        assertEquals(listOf(".env.local", "config/keys/"), provisioning.worktreeIncludePatterns)
    }

    @Test
    fun `merges the patterns of both files without duplicates`() {
        configFile.writeText("""{ "copy": [".env", "local.properties"] }""")
        worktreeInclude.writeText(".env\n.idea/\n")

        val provisioning = files.read(repository.toString())

        assertEquals(listOf(".env", "local.properties", ".idea/"), provisioning.allPatterns)
    }

    @Test
    fun `treats an empty file or a file without copy as no patterns`() {
        configFile.writeText("   ")
        assertEquals(emptyList(), files.read(repository.toString()).copyPatterns)

        configFile.writeText("{}")
        assertEquals(emptyList(), files.read(repository.toString()).copyPatterns)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on malformed json`() {
        configFile.writeText("""{ "copy": [".env", }""")

        val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        assertTrue(error.path.endsWith(".shogunai.json"))
        assertFalse(error.detail.orEmpty().contains("\n"), "keeps only the first line of the parser's message")
    }

    @Test
    fun `fails with InvalidRepositoryConfig when copy is not a list of strings`() {
        configFile.writeText("""{ "copy": ".env" }""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        configFile.writeText("""{ "copy": [".env", 3] }""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        configFile.writeText("""[".env"]""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }
    }

    @Test
    fun `reads the gradle provider and ignores unknown providers`() {
        configFile.writeText("""{ "providers": { "gradle": {}, "xcode": {} } }""")

        assertEquals(RepositoryProviders(gradle = GradleProvider()), files.read(repository.toString()).providers)
    }

    @Test
    fun `reads the gradle provider settings`() {
        configFile.writeText(
            """{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1024M" } } }""",
        )

        val gradle = files.read(repository.toString()).providers.gradle

        assertEquals(GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30, kotlinDaemonMaxHeap = "1024M"), gradle)
        assertEquals(true, gradle?.hasSettings)
    }

    @Test
    fun `declares the gradle provider without settings`() {
        configFile.writeText("""{ "providers": { "gradle": {} } }""")

        assertEquals(false, files.read(repository.toString()).providers.gradle?.hasSettings)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on invalid gradle provider settings`() {
        listOf(
            """{ "providers": { "gradle": { "daemonMaxHeap": "2 GB" } } }""",
            """{ "providers": { "gradle": { "daemonMaxHeap": "" } } }""",
            """{ "providers": { "gradle": { "daemonMaxHeap": "2048" } } }""",
            """{ "providers": { "gradle": { "kotlinDaemonMaxHeap": 1 } } }""",
            """{ "providers": { "gradle": { "idleTimeoutMinutes": 0 } } }""",
            """{ "providers": { "gradle": { "idleTimeoutMinutes": -5 } } }""",
            """{ "providers": { "gradle": { "idleTimeoutMinutes": 1.5 } } }""",
            """{ "providers": { "gradle": { "idleTimeoutMinutes": "30" } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
            assertEquals(true, error.message.orEmpty().contains("providers.gradle."), content)
        }
    }

    @Test
    fun `reads the emulator provider with and without a preferred AVD`() {
        configFile.writeText("""{ "providers": { "emulator": { "avd": "Pixel_8_API_35" } } }""")
        assertEquals(EmulatorProvider("Pixel_8_API_35"), files.read(repository.toString()).providers.emulator)

        configFile.writeText("""{ "providers": { "emulator": {} } }""")
        assertEquals(EmulatorProvider(), files.read(repository.toString()).providers.emulator)
    }

    @Test
    fun `reads the iOS simulator provider with and without preferences`() {
        configFile.writeText("""{ "providers": { "ios-simulator": { "device": "iPhone 16", "runtime": "iOS 18.5" } } }""")
        assertEquals(IosSimulatorProvider("iPhone 16", "iOS 18.5"), files.read(repository.toString()).providers.iosSimulator)

        configFile.writeText("""{ "providers": { "ios-simulator": {} } }""")
        assertEquals(IosSimulatorProvider(), files.read(repository.toString()).providers.iosSimulator)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on an invalid iOS simulator provider`() {
        listOf(
            """{ "providers": { "ios-simulator": true } }""",
            """{ "providers": { "ios-simulator": { "device": 16 } } }""",
            """{ "providers": { "ios-simulator": { "runtime": "" } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
        }
    }

    @Test
    fun `fails with InvalidRepositoryConfig on an invalid emulator provider`() {
        listOf(
            """{ "providers": { "emulator": true } }""",
            """{ "providers": { "emulator": { "avd": 35 } } }""",
            """{ "providers": { "emulator": { "avd": "" } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
        }
    }

    @Test
    fun `declares no provider without the field or the file`() {
        assertEquals(RepositoryProviders(), files.read(repository.toString()).providers)

        configFile.writeText("""{ "copy": [".env"] }""")
        assertEquals(RepositoryProviders(), files.read(repository.toString()).providers)

        configFile.writeText("""{ "providers": { "xcode": true } }""")
        assertEquals(RepositoryProviders(), files.read(repository.toString()).providers)
    }

    @Test
    fun `fails with InvalidRepositoryConfig when providers or a known provider is not an object`() {
        configFile.writeText("""{ "providers": ["gradle"] }""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        configFile.writeText("""{ "providers": { "gradle": true } }""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }
    }

    @Test
    fun `reads the environment and its file in declaration order`() {
        configFile.writeText("""{ "env": { "PORT": "${'$'}{port}", "APP_SUFFIX": "-${'$'}{worktree}" }, "envFile": ".env.shogunai" }""")

        val environment = files.read(repository.toString()).environment

        assertEquals(listOf("PORT", "APP_SUFFIX"), environment.variables.map { it.name })
        assertEquals(".env.shogunai", environment.envFile)
    }

    @Test
    fun `declares an empty environment without the field`() {
        configFile.writeText("""{ "copy": [".env"] }""")

        assertTrue(files.read(repository.toString()).environment.isEmpty)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on an invalid environment`() {
        listOf(
            """{ "env": ["PORT"] }""",
            """{ "env": { "PORT": 3000 } }""",
            """{ "env": { "1PORT": "x" } }""",
            """{ "env": { "A": "${'$'}{branch}" } }""",
            """{ "envFile": "/tmp/shared.env" }""",
            """{ "envFile": "../shared.env" }""",
            """{ "envFile": 3 }""",
        ).forEach { content ->
            configFile.writeText(content)
            assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
        }
    }

    @Test
    fun `keeps the environment when adding patterns`() {
        configFile.writeText("""{ "env": { "PORT": "${'$'}{port}" }, "envFile": ".env.shogunai" }""")

        files.addCopyPatterns(repository.toString(), listOf("/local.properties"))

        val environment = files.read(repository.toString()).environment
        assertEquals(listOf("PORT"), environment.variables.map { it.name })
        assertEquals(".env.shogunai", environment.envFile)
    }

    @Test
    fun `keeps providers when adding patterns`() {
        configFile.writeText("""{ "providers": { "gradle": {} } }""")

        files.addCopyPatterns(repository.toString(), listOf("/local.properties"))

        val provisioning = files.read(repository.toString())
        assertEquals(RepositoryProviders(gradle = GradleProvider()), provisioning.providers)
        assertEquals(listOf("/local.properties"), provisioning.copyPatterns)
    }

    @Test
    fun `creates the file when adding patterns to a repository without one`() {
        files.addCopyPatterns(repository.toString(), listOf("/local.properties"))

        assertEquals(listOf("/local.properties"), files.read(repository.toString()).copyPatterns)
        assertTrue(configFile.readText().endsWith("\n"))
    }

    @Test
    fun `appends patterns without duplicates and keeps other fields`() {
        configFile.writeText("""{ "copy": [".env"], "hooks": { "postCreate": ["npm", "install"] } }""")

        files.addCopyPatterns(repository.toString(), listOf(".env", "/local.properties"))

        val written = Json.parseToJsonElement(configFile.readText()).jsonObject
        assertEquals(listOf(".env", "/local.properties"), written.getValue("copy").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf("npm", "install"),
            written.getValue("hooks").jsonObject.getValue("postCreate").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `refuses to overwrite a file it can't read`() {
        val malformed = """{ "copy": [".env", }"""
        configFile.writeText(malformed)

        assertFailsWith<WorktreeError.InvalidRepositoryConfig> {
            files.addCopyPatterns(repository.toString(), listOf("/local.properties"))
        }

        assertEquals(malformed, configFile.readText())
    }

    @Test
    fun `leaves no temporary files behind`() {
        files.addCopyPatterns(repository.toString(), listOf("/local.properties"))

        assertTrue(configFile.exists())
        assertFalse(Files.list(repository).use { stream -> stream.anyMatch { it.fileName.toString().endsWith(".tmp") } })
    }
}
