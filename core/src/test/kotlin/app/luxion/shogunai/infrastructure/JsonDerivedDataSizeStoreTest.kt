package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.RememberedSize
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonDerivedDataSizeStoreTest {

    private val tempDir = Files.createTempDirectory("json-derived-data-sizes-test")
    private val storagePath = tempDir.resolve("derived-data-sizes.json")
    private val store = JsonDerivedDataSizeStore(storagePath)

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `saved sizes are read back`() {
        val sizes = mapOf(
            "/home/dev/Library/Developer/Xcode/DerivedData/App-hash" to RememberedSize(12_000_000_000L, Instant.parse("2026-10-20T09:30:00Z")),
            "/home/dev/Library/Developer/Xcode/DerivedData/Other-hash" to RememberedSize(0L, Instant.parse("2026-10-21T18:00:00Z")),
        )

        store.save(sizes)

        assertEquals(sizes, JsonDerivedDataSizeStore(storagePath).load())
    }

    @Test
    fun `a missing file has no sizes`() {
        assertEquals(emptyMap(), store.load())
    }

    @Test
    fun `an unreadable file has no sizes`() {
        storagePath.writeText("{ not json")

        assertEquals(emptyMap(), store.load())
    }

    @Test
    fun `an entry with an unreadable date is skipped`() {
        storagePath.writeText("""{"folders":{"/a":{"sizeBytes":1,"lastAccessed":"yesterday"},"/b":{"sizeBytes":2,"lastAccessed":"2026-10-21T18:00:00Z"}}}""")

        assertEquals(mapOf("/b" to RememberedSize(2L, Instant.parse("2026-10-21T18:00:00Z"))), store.load())
    }

    @Test
    fun `packages sizes are read back and missing in entries of earlier versions`() {
        val accessed = Instant.parse("2026-10-20T09:30:00Z")
        store.save(mapOf("/home/dev/Library/Developer/Xcode/DerivedData/App-hash" to RememberedSize(12L, accessed, 5L)))

        assertEquals(5L, JsonDerivedDataSizeStore(storagePath).load().values.single().packagesBytes)

        storagePath.writeText("""{ "folders": { "/home/dev/Library/Developer/Xcode/DerivedData/Old-hash": { "sizeBytes": 7, "lastAccessed": "2026-10-20T09:30:00Z" } } }""")

        assertEquals(RememberedSize(7L, accessed), JsonDerivedDataSizeStore(storagePath).load().values.single())
    }
}
