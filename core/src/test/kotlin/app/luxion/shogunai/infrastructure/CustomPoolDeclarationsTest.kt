package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.CustomPool
import app.luxion.shogunai.domain.model.WorktreeError
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomPoolDeclarationsTest {

    private val repository: Path = Files.createTempDirectory("main-repo")
    private val configFile = repository.resolve(".shogunai.json")
    private val files = RepositoryProvisioningFiles()

    @AfterTest
    fun cleanUp() {
        repository.toFile().deleteRecursively()
    }

    private fun pools(json: String): List<CustomPool> {
        configFile.writeText(json)
        return files.read(repository.toString()).pools
    }

    private fun failure(json: String): String {
        configFile.writeText(json)
        return assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }.message
    }

    @Test
    fun `reads a declared pool`() {
        val read = pools(
            """{ "env": { "PORT": "${'$'}{port}" }, "pools": { "db": { "max": 3, "acquire": ["./scripts/db-up.sh", "${'$'}{slot}"], """ +
                """"release": ["./scripts/db-down.sh", "${'$'}{slot}"], "env": { "DATABASE_URL": "postgres://localhost/app_${'$'}{slot}" } } } }""",
        ).single()

        assertEquals("db", read.name)
        assertEquals(3, read.max)
        assertEquals(listOf("./scripts/db-up.sh", "2"), read.acquireCommand(2, "TASK-1"))
        assertEquals(listOf("./scripts/db-down.sh", "2"), read.releaseCommand(2, "TASK-1"))
        assertEquals(listOf("DATABASE_URL" to "postgres://localhost/app_2"), read.variablesFor(2, "TASK-1"))
    }

    @Test
    fun `a pool may have no release command and no variables`() {
        val read = pools("""{ "pools": { "gpu": { "max": 1, "acquire": ["true"], "release": null, "env": null } } }""").single()

        assertNull(read.release)
        assertEquals(emptyList(), read.variables)
    }

    @Test
    fun `no pools without the key, with null or without a file`() {
        assertEquals(emptyList(), files.read(repository.toString()).pools)
        assertEquals(emptyList(), pools("""{ "pools": null }"""))
        assertEquals(emptyList(), pools("""{ }"""))
    }

    @Test
    fun `pools must be an object of objects`() {
        assertTrue(failure("""{ "pools": [] }""").contains("\"pools\" must be an object of named pools"))
        assertTrue(failure("""{ "pools": { "db": 1 } }""").contains("\"pools.db\" must be an object"))
    }

    @Test
    fun `an invalid name names the pool`() {
        val message = failure("""{ "pools": { "My DB": { "max": 1, "acquire": ["true"] } } }""")

        assertTrue(message.contains(".shogunai.json"))
        assertTrue(message.contains("\"My DB\" isn't a valid pool name"))
    }

    @Test
    fun `max must be a whole number from 1 to 64`() {
        listOf("0", "65", "\"2\"", "1.5", "null", "{}").forEach { max ->
            assertTrue(failure("""{ "pools": { "db": { "max": $max, "acquire": ["true"] } } }""").contains("\"pools.db.max\" must be a whole number from 1 to 64"), max)
        }
        assertTrue(failure("""{ "pools": { "db": { "acquire": ["true"] } } }""").contains("pools.db.max"))
        assertEquals(64, pools("""{ "pools": { "db": { "max": 64, "acquire": ["true"] } } }""").single().max)
    }

    @Test
    fun `commands must be non-empty lists of strings`() {
        listOf("\"./scripts/db-up.sh 1\"", "[]", "[1]", "[[\"a\"]]").forEach { command ->
            assertTrue(
                failure("""{ "pools": { "db": { "max": 1, "acquire": $command } } }""")
                    .contains("\"pools.db.acquire\" must be one command as a non-empty list of strings"),
                command,
            )
        }
        assertTrue(failure("""{ "pools": { "db": { "max": 1, "acquire": ["true"], "release": "x" } } }""").contains("pools.db.release"))
        assertTrue(failure("""{ "pools": { "db": { "max": 1 } } }""").contains("\"pools.db.acquire\" is required"))
        assertTrue(failure("""{ "pools": { "db": { "max": 1, "acquire": null } } }""").contains("\"pools.db.acquire\" is required"))
    }

    @Test
    fun `unknown placeholders are rejected in commands and variables`() {
        assertTrue(failure("""{ "pools": { "db": { "max": 1, "acquire": ["up", "${'$'}{port}"] } } }""").contains("\"pools.db.acquire\": unknown placeholder"))
        assertTrue(
            failure("""{ "pools": { "db": { "max": 1, "acquire": ["up"], "env": { "URL": "http://localhost:${'$'}{port}" } } } }""")
                .contains("\"pools.db.env.URL\": unknown placeholder \${port}"),
        )
    }

    @Test
    fun `variables follow the env rules`() {
        val declare = { env: String -> failure("""{ "pools": { "db": { "max": 1, "acquire": ["up"], "env": $env } } }""") }

        assertTrue(declare("[]").contains("\"pools.db.env\" must be an object"))
        assertTrue(declare("""{ "1X": "a" }""").contains("\"1X\" isn't a valid variable name"))
        assertTrue(declare("""{ "PATH": "a" }""").contains("\"PATH\" is a reserved variable name"))
        assertTrue(declare("""{ "URL": 1 }""").contains("\"pools.db.env.URL\" must be a string"))
        assertTrue(declare("""{ "URL": [] }""").contains("\"pools.db.env.URL\" must be a string"))
        assertTrue(declare("""{ "URL": "a\u0007" }""").contains("can't contain line breaks"))
    }

    @Test
    fun `a variable can't repeat a name of env or of another pool`() {
        val inEnv = failure(
            """{ "env": { "DATABASE_URL": "x" }, "pools": { "db": { "max": 1, "acquire": ["up"], "env": { "DATABASE_URL": "y" } } } }""",
        )
        val inPools = failure(
            """{ "pools": { "db": { "max": 1, "acquire": ["up"], "env": { "URL": "a" } }, "cache": { "max": 1, "acquire": ["up"], "env": { "URL": "b" } } } }""",
        )

        assertTrue(inEnv.contains("\"DATABASE_URL\" is already declared"))
        assertTrue(inPools.contains("\"pools.cache.env\": \"URL\" is already declared"))
    }

    @Test
    fun `the approval form sorts pools and variables and keeps the templates`() {
        val read = pools(
            """{ "pools": { "gpu": { "max": 1, "acquire": ["claim"] }, "db": { "max": 2, "acquire": ["up", "${'$'}{slot}"], "release": ["down"], """ +
                """"env": { "B": "${'$'}{slot}", "A": "x" } } } }""",
        )

        assertNull(poolsJsonOf(emptyList()))
        assertEquals(
            """[{"name":"db","max":2,"acquire":["up","${'$'}{slot}"],"release":["down"],"env":[["A","x"],["B","${'$'}{slot}"]]},""" +
                """{"name":"gpu","max":1,"acquire":["claim"],"release":null,"env":[]}]""",
            poolsJsonOf(read).toString(),
        )
    }
}
