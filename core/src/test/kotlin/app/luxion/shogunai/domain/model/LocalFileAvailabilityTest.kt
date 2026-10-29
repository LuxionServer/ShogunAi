package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFileAvailabilityTest {

    private val base = "/home/dev/projects/main-repo"

    @Test
    fun `the message lists every file with its reason, full path and fix`() {
        val error = WorktreeError.LocalFilesUnavailable(
            base,
            listOf(
                UnavailableLocalFile("local.properties", "$base/local.properties", LocalFileOrigin.PROJECT, UnavailableReason.Missing),
                UnavailableLocalFile("keys.properties", "$base/keys.properties", LocalFileOrigin.REPOSITORY, UnavailableReason.BrokenLink("/missing/keys.properties")),
                UnavailableLocalFile("keys/private", "$base/keys/private", LocalFileOrigin.PROJECT, UnavailableReason.Unreadable(null)),
            ),
        )

        assertEquals(
            """
            These local files can't be copied or linked from $base, so nothing was changed:
              local.properties: not found at $base/local.properties. Create it there, or remove it from the project's local files
              keys.properties: $base/keys.properties is a link to /missing/keys.properties, which doesn't exist. Restore /missing/keys.properties or replace the link with the file, or stop matching it in .shogunai.json or .worktreeinclude
              keys/private: $base/keys/private can't be read. Make it readable for your user, or remove it from the project's local files
            """.trimIndent(),
            error.message,
        )
    }
}
