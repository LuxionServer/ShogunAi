package app.luxion.shogunai.domain.usecase

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalDirectoryTest {

    @Test
    fun `a directory resolves to its real path and a missing one stays as given`() {
        val folder = Files.createTempDirectory("canonical")
        try {
            assertEquals(normalizePath(folder.toRealPath().toString()), canonicalDirectory("$folder/."))
            assertEquals("/projects/missing", canonicalDirectory("/projects/missing"))
        } finally {
            Files.delete(folder)
        }
    }
}
