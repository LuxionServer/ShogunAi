package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OverlapDeclarationTest {

    private val repository: Path = Files.createTempDirectory("main-repo")
    private val files = RepositoryProvisioningFiles()
    private val configFile = repository.resolve(".shogunai.json")

    @AfterTest
    fun cleanUp() {
        repository.toFile().deleteRecursively()
    }

    private fun overlapIgnore() = files.read(repository.toString()).overlapIgnore

    @Test
    fun `reads the declared patterns skipping blank ones`() {
        configFile.writeText("""{ "overlap": { "ignore": ["docs/roadmap*.md", " ", "openspec/specs/**"] } }""")

        assertEquals(listOf("docs/roadmap*.md", "openspec/specs/**"), overlapIgnore())
    }

    @Test
    fun `declares nothing without the field, the list or the file`() {
        assertEquals(emptyList(), overlapIgnore())

        configFile.writeText("""{ "copy": [".env"] }""")
        assertEquals(emptyList(), overlapIgnore())

        configFile.writeText("""{ "overlap": null }""")
        assertEquals(emptyList(), overlapIgnore())

        configFile.writeText("""{ "overlap": {} }""")
        assertEquals(emptyList(), overlapIgnore())

        configFile.writeText("""{ "overlap": { "ignore": null } }""")
        assertEquals(emptyList(), overlapIgnore())
    }

    @Test
    fun `fails naming the file and the field when the declaration has the wrong shape`() {
        val cases = mapOf(
            """{ "overlap": ["docs/roadmap.md"] }""" to "\"overlap\" must be an object",
            """{ "overlap": { "ignore": "docs/roadmap.md" } }""" to "\"overlap.ignore\" must be a list of strings",
            """{ "overlap": { "ignore": ["docs/roadmap.md", 3] } }""" to "\"overlap.ignore\" must be a list of strings",
            """{ "overlap": { "ignore": [["docs/roadmap.md"]] } }""" to "\"overlap.ignore\" must be a list of strings",
        )
        cases.forEach { (content, detail) ->
            configFile.writeText(content)

            val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig> { overlapIgnore() }

            assertTrue(error.path.endsWith(".shogunai.json"))
            assertEquals(detail, error.detail)
        }
    }

    @Test
    fun `writing providers from the settings keeps the declaration`() {
        configFile.writeText("""{ "overlap": { "ignore": ["docs/roadmap.md"] } }""")

        files.write(
            repository.toString(),
            RepositoryConfigEdit(providers = RepositoryProviders(gradle = GradleProvider())),
            files.fingerprint(repository.toString()),
        )

        val written = Json.parseToJsonElement(configFile.readText()).jsonObject
        val ignore = written.getValue("overlap").jsonObject.getValue("ignore").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("docs/roadmap.md"), ignore)
        assertEquals(listOf("docs/roadmap.md"), overlapIgnore())
    }
}
