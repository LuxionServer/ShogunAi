package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.NodeProvider
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
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

class NodeProviderConfigTest {

    private val repository: Path = Files.createTempDirectory("main-repo")
    private val files = RepositoryProvisioningFiles()
    private val configFile = repository.resolve(".shogunai.json")

    @AfterTest
    fun cleanUp() {
        repository.toFile().deleteRecursively()
    }

    @Test
    fun `reads the node provider ignoring unknown options`() {
        configFile.writeText("""{ "providers": { "node": {} } }""")
        assertEquals(NodeProvider, files.read(repository.toString()).providers.node)

        configFile.writeText("""{ "providers": { "node": { "future": true } } }""")
        assertEquals(NodeProvider, files.read(repository.toString()).providers.node)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on a node provider that isn't an object`() {
        configFile.writeText("""{ "providers": { "node": true } }""")

        val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        assertTrue(".shogunai.json" in error.path)
        assertTrue("providers.node" in error.detail.orEmpty())
    }

    @Test
    fun `writing other providers keeps the node provider and its unknown options`() {
        configFile.writeText("""{ "providers": { "node": { "future": 1 } } }""")

        write(RepositoryProviders(gradle = GradleProvider(), node = NodeProvider))

        val providers = written().getValue("providers").jsonObject
        assertEquals(listOf("node", "gradle"), providers.keys.toList())
        assertEquals("1", providers.getValue("node").jsonObject.getValue("future").jsonPrimitive.content)
    }

    @Test
    fun `turns the node provider on and off`() {
        configFile.writeText("""{ "providers": { "gradle": {} } }""")

        write(RepositoryProviders(gradle = GradleProvider(), node = NodeProvider))
        assertEquals(emptySet(), written().getValue("providers").jsonObject.getValue("node").jsonObject.keys)

        write(RepositoryProviders(gradle = GradleProvider()))
        assertEquals(setOf("gradle"), written().getValue("providers").jsonObject.keys)
    }

    private fun write(providers: RepositoryProviders) =
        files.write(repository.toString(), RepositoryConfigEdit(providers = providers), files.fingerprint(repository.toString()))

    private fun written() = Json.parseToJsonElement(configFile.readText()).jsonObject
}
