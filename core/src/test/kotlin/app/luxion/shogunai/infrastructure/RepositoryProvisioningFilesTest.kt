package app.luxion.shogunai.infrastructure

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
