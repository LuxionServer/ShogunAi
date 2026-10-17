package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.EmulatorSettings
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorSettings
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `loads a project stored without a main branch`() {
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
                        "secretFiles": []
                    }
                }
            ]
            """.trimIndent(),
        )

        val project = repository.list().single()

        assertNull(project.config.mainBranch)
        assertEquals(EmulatorSettings(), project.config.emulator)
        assertEquals(SimulatorSettings(), project.config.simulator)
    }

    @Test
    fun `keeps the emulator settings of a project`() {
        val project = Project(
            id = "project-1",
            name = "Main",
            config = ProjectConfig(
                baseRepositoryPath = "/home/dev/projects/main-repo",
                worktreesRoot = "/home/dev/projects",
                secretFiles = emptyList(),
                emulator = EmulatorSettings(avd = "Medium_Phone_API_35", noWindow = true),
                simulator = SimulatorSettings(device = "iPhone 16", runtime = "iOS 18.5"),
            ),
        )

        repository.save(project)

        val loaded = JsonProjectRepository(storagePath).list().single().config
        assertEquals(project.config.emulator, loaded.emulator)
        assertEquals(project.config.simulator, loaded.simulator)
    }
}
