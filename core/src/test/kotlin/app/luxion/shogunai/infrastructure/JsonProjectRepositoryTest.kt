package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.EmulatorSettings
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.newProject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorSettings
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.concurrent.thread
import app.luxion.shogunai.domain.model.WorktreeError

class JsonProjectRepositoryTest {

    private val tempDir = createTempDirectory("json-project-repository-test")
    private val storagePath = tempDir.resolve("projects.json")
    private val repository = JsonProjectRepository(storagePath)

    @AfterTest
    fun tearDown() {
        tempDir.listDirectoryEntries().forEach { it.deleteIfExists() }
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
        assertFalse(project.config.fetchBeforeCreate)
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

    @Test
    fun `keeps the IDE override of a project and reads projects saved without one`() {
        val config = ProjectConfig(
            baseRepositoryPath = "/home/dev/projects/main-repo",
            worktreesRoot = "/home/dev/projects",
            secretFiles = emptyList(),
        )
        repository.save(Project("with", "With", config.copy(editorOverride = EditorId.ANDROID_STUDIO)))
        Files.writeString(
            tempDir.resolve("old.json"),
            """[{"id":"old","name":"Old","config":{"baseRepositoryPath":"/a","worktreesRoot":"/b","secretFiles":[]}}]""",
        )

        assertEquals(EditorId.ANDROID_STUDIO, JsonProjectRepository(storagePath).list().single().config.editorOverride)
        assertNull(JsonProjectRepository(tempDir.resolve("old.json")).list().single().config.editorOverride)
    }

    private fun project(id: String) = Project(
        id = id,
        name = id,
        config = ProjectConfig(baseRepositoryPath = "/home/dev/projects/$id", worktreesRoot = "/home/dev/projects", secretFiles = emptyList()),
    )

    @Test
    fun `reports a corrupted file with its path instead of a stack trace`() {
        Files.writeString(storagePath, "{ not json")

        val error = assertFailsWith<WorktreeError.LocalFileUnreadable> { repository.list() }

        assertEquals(storagePath.toString(), error.path)
        assertTrue(Files.readString(storagePath) == "{ not json", "listing never touches the file")
    }

    @Test
    fun `keeps the unreadable file before saving over it`() {
        Files.writeString(storagePath, "{ not json")

        repository.save(project("project-1"))

        val kept = tempDir.listDirectoryEntries().single { it.name.startsWith("projects.json.unreadable-") }
        assertEquals("{ not json", Files.readString(kept))
        assertEquals(listOf("project-1"), repository.list().map { it.id })
    }

    @Test
    fun `concurrent writers don't lose each other's projects`() {
        val writers = (1..2).map { writer ->
            thread {
                val own = JsonProjectRepository(storagePath)
                repeat(15) { own.save(project("writer-$writer-$it")) }
            }
        }
        writers.forEach { it.join() }

        assertEquals(30, repository.list().size)
    }

    @Test
    fun `a project stored with one agent has a single agent`() {
        Files.writeString(
            storagePath,
            """
            [{"id": "p", "name": "Main", "config": {
                "baseRepositoryPath": "/home/dev/projects/main-repo",
                "worktreesRoot": "/home/dev/projects",
                "secretFiles": [],
                "agentLaunchConfig": {"agentCommand": "claude", "useHeadroom": false}
            }}]
            """.trimIndent(),
        )

        val config = repository.list().single().config

        assertEquals(listOf(AgentLaunchConfig(agentCommand = "claude", useHeadroom = false)), config.agents)
        assertTrue(config.additionalAgents.isEmpty())
    }

    @Test
    fun `agents stored without the headroom option by an earlier version keep headroom`() {
        Files.writeString(
            storagePath,
            """
            [{"id": "p", "name": "Main", "config": {
                "baseRepositoryPath": "/home/dev/projects/main-repo",
                "worktreesRoot": "/home/dev/projects",
                "secretFiles": [],
                "agentLaunchConfig": {"agentCommand": "claude"},
                "additionalAgents": [{"name": "Codex", "agentCommand": "codex"}, {"agentCommand": "aider", "useHeadroom": false}]
            }}]
            """.trimIndent(),
        )

        val config = repository.list().single().config

        assertEquals(
            listOf(
                AgentLaunchConfig(agentCommand = "claude", useHeadroom = true),
                AgentLaunchConfig(name = "Codex", agentCommand = "codex", useHeadroom = true),
                AgentLaunchConfig(agentCommand = "aider", useHeadroom = false),
            ),
            config.agents,
        )
    }

    @Test
    fun `a project stored without an agent by an earlier version runs claude with headroom`() {
        Files.writeString(
            storagePath,
            """[{"id":"old","name":"Old","config":{"baseRepositoryPath":"/a","worktreesRoot":"/b","secretFiles":[]}}]""",
        )

        assertEquals(AgentLaunchConfig(agentCommand = "claude", useHeadroom = true), repository.list().single().config.agentLaunchConfig)
    }

    @Test
    fun `a new project's agent is written without headroom and reads back the same`() {
        val project = newProject("p", "/home/dev/projects/main-repo")

        repository.save(project)

        val agent = writtenConfig()["agentLaunchConfig"]!!.jsonObject
        assertEquals(JsonPrimitive(false), agent["useHeadroom"])
        assertEquals(AgentLaunchConfig(), JsonProjectRepository(storagePath).list().single().config.agentLaunchConfig)
    }

    @Test
    fun `saving a project from an earlier version writes its headroom explicitly`() {
        Files.writeString(
            storagePath,
            """[{"id":"old","name":"Old","config":{"baseRepositoryPath":"/a","worktreesRoot":"/b","secretFiles":[]}}]""",
        )
        val legacy = repository.list().single()

        repository.save(legacy.copy(name = "Renamed"))

        assertEquals(JsonPrimitive(true), writtenConfig()["agentLaunchConfig"]!!.jsonObject["useHeadroom"])
    }

    private fun writtenConfig() =
        Json.parseToJsonElement(Files.readString(storagePath)).jsonArray.single().jsonObject["config"]!!.jsonObject

    @Test
    fun `keeps several agents of a project in order`() {
        val agents = listOf(
            AgentLaunchConfig(name = "Claude", agentCommand = "claude"),
            AgentLaunchConfig(name = "Codex", agentCommand = "codex --full-auto", useHeadroom = false),
        )
        val project = Project(
            id = "p",
            name = "Main",
            config = ProjectConfig(
                baseRepositoryPath = "/home/dev/projects/main-repo",
                worktreesRoot = "/home/dev/projects",
                secretFiles = emptyList(),
                agentLaunchConfig = agents.first(),
                additionalAgents = agents.drop(1),
            ),
        )

        repository.save(project)

        assertEquals(agents, repository.list().single().config.agents)
    }

    @Test
    fun `adding and removing a project leaves the other entries and the file's keys unchanged`() {
        val existing = Project(
            id = "existing",
            name = "Main",
            config = ProjectConfig(
                baseRepositoryPath = "/home/dev/projects/main-repo",
                worktreesRoot = "/home/dev/custom-worktrees",
                secretFiles = listOf(".env"),
                mainBranch = "develop",
                fetchBeforeCreate = true,
            ),
        )
        val added = newProject("added", "/home/dev/projects/other-repo")
        repository.save(existing)

        repository.save(added)
        assertEquals(listOf(existing, added), repository.list())
        repository.delete("added")

        assertEquals(listOf(existing), repository.list())
        val written = Json.parseToJsonElement(Files.readString(storagePath)).jsonArray.single().jsonObject
        assertEquals(setOf("id", "name", "config"), written.keys)
    }
}
