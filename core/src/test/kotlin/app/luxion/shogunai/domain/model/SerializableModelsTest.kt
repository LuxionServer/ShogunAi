package app.luxion.shogunai.domain.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SerializableModelsTest {

    private val formats = listOf(Json, Json { encodeDefaults = true })

    private inline fun <reified T> assertRoundTrips(vararg values: T) {
        formats.forEach { json ->
            values.forEach { value -> assertEquals(value, json.decodeFromString<T>(json.encodeToString(value))) }
        }
    }

    @Test
    fun `resource limits read missing fields as unlimited and keep every set limit`() {
        assertEquals(ResourceLimits.UNLIMITED, Json.decodeFromString<ResourceLimits>("{}"))
        assertEquals(
            ResourceLimits(emulators = 1, simulators = 2, gradleDaemons = 3),
            Json.decodeFromString<ResourceLimits>("""{"emulators":1,"simulators":2,"gradleDaemons":3}"""),
        )
        assertRoundTrips(ResourceLimits.UNLIMITED, ResourceLimits(emulators = 1), ResourceLimits(simulators = 2, gradleDaemons = 3))
    }

    @Test
    fun `resource settings read missing fields as their defaults and keep every changed one`() {
        val changed = ResourceSettings(
            profile = CapacityProfile.CUSTOM,
            custom = ResourceLimits.BALANCED,
            derivedDataQuotaGigabytes = 40,
            leaseMinutes = 5,
            derivedDataQuotaMode = DerivedDataQuotaMode.FIXED,
            desktopTurnMinutes = 3,
        )

        assertEquals(ResourceSettings(), Json.decodeFromString<ResourceSettings>("{}"))
        assertRoundTrips(
            ResourceSettings(),
            changed,
            ResourceSettings(profile = CapacityProfile.LIGHT, leaseMinutes = 7),
            ResourceSettings(custom = ResourceLimits.UNLIMITED, derivedDataQuotaGigabytes = 1, derivedDataQuotaMode = DerivedDataQuotaMode.NONE, desktopTurnMinutes = 1),
        )
    }

    @Test
    fun `agent launch config reads missing fields as claude without headroom and always writes headroom`() {
        assertEquals(AgentLaunchConfig(), Json.decodeFromString<AgentLaunchConfig>("{}"))
        assertEquals("""{"useHeadroom":false}""", Json.encodeToString(AgentLaunchConfig()))
        assertRoundTrips(
            AgentLaunchConfig(),
            AgentLaunchConfig(name = "Work", agentCommand = "codex", useHeadroom = true),
            AgentLaunchConfig(name = "Work"),
            AgentLaunchConfig(agentCommand = "codex", useHeadroom = true),
        )
    }

    @Test
    fun `app request needs a creation time and an action`() {
        assertFailsWith<SerializationException> { Json.decodeFromString<AppRequest>("""{"action":"open-app"}""") }
        assertFailsWith<SerializationException> { Json.decodeFromString<AppRequest>("""{"createdAt":1}""") }
        assertRoundTrips(
            AppRequest(createdAt = 1, action = AppRequest.OPEN_APP),
            AppRequest(version = 2, createdAt = 1, action = AppRequest.OPEN_PROJECT, projectId = "project-1", settings = true),
        )
    }

    @Test
    fun `window bounds need every coordinate and default to not maximized`() {
        assertEquals(WindowBounds(1, 2, 3, 4), Json.decodeFromString<WindowBounds>("""{"x":1,"y":2,"width":3,"height":4}"""))
        listOf("x", "y", "width", "height").forEach { missing ->
            val fields = mapOf("x" to 1, "y" to 2, "width" to 3, "height" to 4) - missing
            val text = fields.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":$value" }
            assertFailsWith<SerializationException> { Json.decodeFromString<WindowBounds>(text) }
        }
        assertRoundTrips(WindowBounds(1, 2, 3, 4), WindowBounds(1, 2, 3, 4, maximized = true))
    }

    @Test
    fun `recent worktree needs its project and path`() {
        assertFailsWith<SerializationException> { Json.decodeFromString<RecentWorktree>("""{"path":"/tmp/a"}""") }
        assertFailsWith<SerializationException> { Json.decodeFromString<RecentWorktree>("""{"projectId":"p"}""") }
        assertRoundTrips(RecentWorktree("p", "/tmp/a"))
    }

    @Test
    fun `emulator and simulator settings read missing fields as no preference`() {
        assertEquals(EmulatorSettings(), Json.decodeFromString<EmulatorSettings>("{}"))
        assertEquals(SimulatorSettings(), Json.decodeFromString<SimulatorSettings>("{}"))
        assertRoundTrips(EmulatorSettings(), EmulatorSettings(avd = "Pixel"), EmulatorSettings(noWindow = true))
        assertRoundTrips(SimulatorSettings(), SimulatorSettings(device = "iPhone 16"), SimulatorSettings(runtime = "iOS 18.0"))
    }

    @Test
    fun `sidebar order reads missing fields as empty`() {
        assertEquals(SidebarOrder(), Json.decodeFromString<SidebarOrder>("{}"))
        assertRoundTrips(SidebarOrder(), SidebarOrder(topLevel = listOf("a")), SidebarOrder(multirepos = mapOf("m" to listOf("b"))))
    }

    @Test
    fun `project config needs its paths and secret files and defaults the rest`() {
        val minimal = ProjectConfig("/repo", "/worktrees", listOf(".env"))
        val changed = ProjectConfig(
            baseRepositoryPath = "/repo",
            worktreesRoot = "/worktrees",
            secretFiles = listOf(".env"),
            agentLaunchConfig = AgentLaunchConfig(agentCommand = "codex"),
            additionalAgents = listOf(AgentLaunchConfig(name = "Other")),
            mainBranch = "develop",
            emulator = EmulatorSettings(avd = "Pixel"),
            simulator = SimulatorSettings(device = "iPhone 16"),
            editorOverride = EditorId.XCODE,
            fetchBeforeCreate = true,
            linkedSecretFiles = listOf(".env"),
        )

        assertEquals(minimal, Json.decodeFromString<ProjectConfig>("""{"baseRepositoryPath":"/repo","worktreesRoot":"/worktrees","secretFiles":[".env"]}"""))
        listOf("baseRepositoryPath", "worktreesRoot", "secretFiles").forEach { missing ->
            val fields = mapOf("baseRepositoryPath" to "\"/repo\"", "worktreesRoot" to "\"/worktrees\"", "secretFiles" to "[]") - missing
            val text = fields.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":$value" }
            assertFailsWith<SerializationException> { Json.decodeFromString<ProjectConfig>(text) }
        }
        assertRoundTrips(
            minimal,
            changed,
            minimal.copy(mainBranch = "main", fetchBeforeCreate = true),
            ProjectConfig("/repo", "/worktrees", emptyList(), additionalAgents = listOf(AgentLaunchConfig()), editorOverride = EditorId.VS_CODE),
            ProjectConfig("/repo", "/worktrees", emptyList(), emulator = EmulatorSettings(noWindow = true), simulator = SimulatorSettings(runtime = "iOS 18.0"), linkedSecretFiles = listOf("a")),
        )
    }

    @Test
    fun `project needs its id, name and config`() {
        val config = ProjectConfig("/repo", "/worktrees", emptyList())

        listOf("id", "name", "config").forEach { missing ->
            val fields = mapOf("id" to "\"p\"", "name" to "\"P\"", "config" to Json.encodeToString(config)) - missing
            val text = fields.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":$value" }
            assertFailsWith<SerializationException> { Json.decodeFromString<Project>(text) }
        }
        assertRoundTrips(Project("p", "P", config))
    }

    @Test
    fun `enums in settings are written by name and reject unknown names`() {
        assertEquals("\"SPANISH\"", Json.encodeToString(AppLanguage.SPANISH))
        assertEquals(ThemeMode.DARK, Json.decodeFromString<ThemeMode>("\"DARK\""))
        assertEquals(Grouping.BY_CHANGE, Json.decodeFromString<Grouping>("\"BY_CHANGE\""))
        assertEquals(WorktreeSort.ACTIVITY, Json.decodeFromString<WorktreeSort>("\"ACTIVITY\""))
        assertEquals(TerminalId.ZELLIJ, Json.decodeFromString<TerminalId>("\"ZELLIJ\""))
        assertEquals(RunnerId.ORCA, Json.decodeFromString<RunnerId>("\"ORCA\""))
        assertFailsWith<SerializationException> { Json.decodeFromString<EditorId>("\"NOTEPAD\"") }
    }
}
