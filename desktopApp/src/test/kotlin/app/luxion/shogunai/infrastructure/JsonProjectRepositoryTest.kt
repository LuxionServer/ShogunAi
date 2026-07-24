package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonProjectRepositoryTest {

    private val tempDir = createTempDirectory("json-project-repository-test")
    private val storagePath = tempDir.resolve("projects.json")
    private val repository = JsonProjectRepository(storagePath)

    @AfterTest
    fun tearDown() {
        storagePath.deleteIfExists()
        tempDir.deleteExisting()
    }

    @Test
    fun `ignores a stale terminalPreference key from a previously persisted project`() {
        Files.writeString(
            storagePath,
            """
            [
                {
                    "id": "project-1",
                    "name": "Main",
                    "config": {
                        "baseRepositoryPath": "/home/dev/projects/main-repo",
                        "worktreesRoot": "/home/dev/projects",
                        "secretFiles": ["local.properties"],
                        "terminalPreference": {
                            "mode": "AUTO_DETECT",
                            "emulator": null,
                            "customCommandTemplate": null
                        },
                        "agentLaunchConfig": {
                            "agentCommand": "claude",
                            "useHeadroom": true
                        }
                    }
                }
            ]
            """.trimIndent(),
        )

        val projects = repository.list()

        assertEquals(1, projects.size)
        assertEquals("project-1", projects.single().id)
    }
}
