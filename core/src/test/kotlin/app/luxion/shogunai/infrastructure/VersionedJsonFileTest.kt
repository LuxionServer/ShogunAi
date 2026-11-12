package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionedJsonFileTest {

    private val root = createTempDirectory("versioned-json-file-test")
    private val path = root.resolve(".shogunai").resolve("store.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file = VersionedJsonFile(path, Document.serializer(), json, Document())

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `a missing or blank file reads as empty and an unchanged update writes nothing`() {
        assertEquals(Document(), file.read())
        assertNull(file.update { it })
        assertFalse(path.exists())

        stored(" ")
        assertEquals(Document(), file.read())
    }

    @Test
    fun `a write puts the schema version first`() {
        file.update { it.copy(title = "main") }

        val written = parsed()
        assertEquals(listOf("schemaVersion", "title"), written.keys.toList())
        assertEquals(JsonPrimitive(1), written["schemaVersion"])
        assertEquals(Document(title = "main"), file.read())
    }

    @Test
    fun `an unchanged value isn't rewritten`() {
        stored("""{"title":   "main"}""")

        file.update { it }

        assertEquals("""{"title":   "main"}""", path.readText())
    }

    @Test
    fun `a file without a version is version 1 and is upgraded in order before use`() {
        val steps = mutableListOf<String>()
        val upgraded = VersionedJsonFile(path, Document.serializer(), json, Document(), migratingSchema(steps))
        stored("""{"name": "old"}""")

        assertEquals(Document(title = "old", tags = mapOf("added" to Item("added"))), upgraded.read())
        assertEquals(listOf("1 to 2", "2 to 3", "normalize"), steps)

        upgraded.update { it.copy(child = Child(1)) }
        assertEquals(JsonPrimitive(3), parsed()["schemaVersion"])
        assertFalse("name" in parsed())
    }

    @Test
    fun `a file at a middle version runs only the later steps`() {
        val steps = mutableListOf<String>()
        stored("""{"schemaVersion": 2, "title": "middle"}""")

        VersionedJsonFile(path, Document.serializer(), json, Document(), migratingSchema(steps)).read()

        assertEquals(listOf("2 to 3", "normalize"), steps)
    }

    @Test
    fun `a schema version that isn't a positive whole number makes the file unreadable`() {
        listOf("\"2\"", "{}", "1.5", "0").forEach { version ->
            stored("""{"schemaVersion": $version}""")

            val problem = assertFailsWith<WorktreeError.LocalFileUnreadable> { file.read() }

            assertEquals(path.toString(), problem.path)
            assertTrue(problem.reason!!.startsWith("schemaVersion must be a positive whole number"))
        }
    }

    @Test
    fun `unreadable contents are reported and kept before an update replaces them`() {
        listOf("{ not json", """{"title": [1]}""").forEach { contents ->
            stored(contents)

            assertFailsWith<WorktreeError.LocalFileUnreadable> { file.read() }
            val problem = file.update { it.copy(title = "fresh") }

            assertEquals(path.toString(), problem?.path)
            assertFalse(problem!!.reason!!.startsWith("written by a newer version"))
            val kept = path.parent.listDirectoryEntries().single { it.name.startsWith("store.json.unreadable-") }
            assertEquals(contents, kept.readText())
            assertEquals(Document(title = "fresh"), file.read())
            Files.delete(kept)
        }
    }

    @Test
    fun `a path that can't be read as text is kept aside before an update`() {
        path.createDirectories()

        assertFailsWith<WorktreeError.LocalFileUnreadable> { file.read() }
        assertTrue(file.update { it.copy(title = "fresh") } is WorktreeError.LocalFileUnreadable)

        assertEquals(Document(title = "fresh"), file.read())
        assertTrue(path.parent.listDirectoryEntries().any { it.name.startsWith("store.json.unreadable-") })
    }

    @Test
    fun `a newer file is read as far as understood and never written`() {
        val contents = """{"schemaVersion": 3, "title": "newer", "shape": "changed"}"""
        stored(contents)

        assertEquals(Document(title = "newer"), file.read())
        val refused = assertFailsWith<WorktreeError.LocalFileFromNewerVersion> { file.update { it.copy(title = "older") } }

        assertEquals(path.toString(), refused.path)
        assertEquals(3, refused.version)
        assertEquals(1, refused.supportedVersion)
        assertTrue(refused.message.contains("written by a newer version of ShogunAi"))
        assertEquals(contents, path.readText())
    }

    @Test
    fun `a newer file that can't be decoded is reported as newer and isn't moved aside`() {
        val contents = """{"schemaVersion": 2, "title": [1]}"""
        stored(contents)

        val problem = assertFailsWith<WorktreeError.LocalFileUnreadable> { file.read() }
        assertFailsWith<WorktreeError.LocalFileFromNewerVersion> { file.update { it } }

        assertTrue(problem.reason!!.startsWith("written by a newer version of ShogunAi (schema 2; this version supports up to 1): "))
        assertEquals(listOf("store.json"), path.parent.listDirectoryEntries().map { it.name }.filterNot { it.endsWith(".lock") })
        assertEquals(contents, path.readText())
    }

    @Test
    fun `fields this version doesn't know are kept in objects, map entries and list items with the same id`() {
        stored(
            """
            {
              "title": "old",
              "future": {"kept": true},
              "child": {"value": 1, "future": "child"},
              "tags": {
                "kept": {"id": "kept", "future": "tag"},
                "removed": {"id": "removed", "future": "gone"}
              },
              "items": [
                {"id": "first", "future": "first"},
                {"id": "second", "future": "second"},
                {"name": "no id", "future": "lost"}
              ],
              "children": [{"value": 1, "future": "lost"}],
              "names": ["a"]
            }
            """.trimIndent(),
        )

        file.update { document ->
            document.copy(
                title = "new",
                child = Child(2),
                tags = document.tags - "removed",
                items = listOf(Item("second", "renamed"), Item("third")),
                children = listOf(Child(2)),
                names = listOf("b"),
                raw = JsonObject(mapOf("free" to JsonPrimitive(1))),
            )
        }

        val written = parsed()
        assertEquals(JsonPrimitive("new"), written["title"])
        assertEquals(json.parseToJsonElement("""{"kept": true}"""), written["future"])
        assertEquals(json.parseToJsonElement("""{"value": 2, "future": "child"}"""), written["child"])
        assertEquals(json.parseToJsonElement("""{"kept": {"id": "kept", "future": "tag"}}"""), written["tags"])
        assertEquals(
            json.parseToJsonElement("""[{"id": "second", "name": "renamed", "future": "second"}, {"id": "third"}]"""),
            written["items"],
        )
        assertEquals(json.parseToJsonElement("""[{"value": 2}]"""), written["children"])
        assertEquals(json.parseToJsonElement("""["b"]"""), written["names"])
    }

    @Test
    fun `free JSON fields are written as they are`() {
        stored("""{"raw": {"old": 1}, "items": []}""")
        file.update { it.copy(raw = JsonObject(mapOf("new" to JsonPrimitive(2)))) }
        assertEquals(json.parseToJsonElement("""{"new": 2}"""), parsed()["raw"])

        stored("""{"raw": [{"id": "a", "old": 1}]}""")
        file.update { it.copy(raw = json.parseToJsonElement("""[{"id": "a"}]""")) }
        assertEquals(json.parseToJsonElement("""[{"id": "a"}]"""), parsed()["raw"])
    }

    @Test
    fun `a list file has no version and keeps unknown fields of items with the same id`() {
        val list = VersionedJsonFile(path, ListSerializer(Item.serializer()), json, emptyList())
        stored("""[{"id": "a", "future": 1}, {"id": "b"}]""")

        list.update { items -> items.filterNot { it.id == "b" } + Item("c") }

        assertEquals(json.parseToJsonElement("""[{"id": "a", "future": 1}, {"id": "c"}]"""), json.parseToJsonElement(path.readText()))
    }

    @Test
    fun `a private file is readable and writable only by the user and a custom decoder is used`() {
        val private = VersionedJsonFile(path, Document.serializer(), json, Document(), privateFile = true, decode = { Document(title = "decoded") })
        stored("""{"title": "stored"}""")

        assertEquals(Document(title = "decoded"), private.read())
        private.update { it.copy(title = "written") }

        val permissions = runCatching { Files.getPosixFilePermissions(path) }.getOrNull() ?: return
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions)
    }

    private fun migratingSchema(steps: MutableList<String>) = JsonSchema(
        migrations = listOf(
            { element ->
                steps += "1 to 2"
                val document = element.jsonObject
                JsonObject(document - "name" + ("title" to document.getValue("name")))
            },
            { element ->
                steps += "2 to 3"
                JsonObject(element.jsonObject + ("tags" to json.parseToJsonElement("""{"added": {"id": "added"}}""")))
            },
        ),
        normalize = { element -> element.also { steps += "normalize" } },
    )

    private fun stored(text: String) {
        path.parent.createDirectories()
        path.writeText(text)
    }

    private fun parsed(): JsonObject = json.parseToJsonElement(path.readText()).jsonObject

    @Serializable
    data class Document(
        val title: String = "",
        val child: Child? = null,
        val tags: Map<String, Item> = emptyMap(),
        val items: List<Item> = emptyList(),
        val children: List<Child> = emptyList(),
        val names: List<String> = emptyList(),
        val raw: JsonElement? = null,
    )

    @Serializable
    data class Item(val id: String = "", val name: String? = null)

    @Serializable
    data class Child(val value: Int = 0)
}
