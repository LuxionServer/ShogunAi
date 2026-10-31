package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoredFilesSerializationTest {

    private val formats = listOf(Json, Json { encodeDefaults = true })

    private inline fun <reified T> assertRoundTrips(vararg values: T) {
        formats.forEach { json ->
            values.forEach { value -> assertEquals(value, json.decodeFromString<T>(json.encodeToString(value))) }
        }
    }

    private inline fun <reified T> assertEachFieldRequired(fields: Map<String, String>) {
        fields.keys.forEach { missing ->
            val text = (fields - missing).entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":$value" }
            assertFailsWith<SerializationException>(missing) { Json.decodeFromString<T>(text) }
        }
    }

    @Test
    fun `a Claude session file reads every field as optional`() {
        assertEquals(FileClaudeSessionRegistry.SessionFile(), Json.decodeFromString<FileClaudeSessionRegistry.SessionFile>("{}"))
        assertRoundTrips(
            FileClaudeSessionRegistry.SessionFile(),
            FileClaudeSessionRegistry.SessionFile(pid = 1, name = "task", status = JsonPrimitive("busy")),
            FileClaudeSessionRegistry.SessionFile(cwd = "/w", startedAt = 2, statusUpdatedAt = 3),
        )
    }

    @Test
    fun `the compose registry needs each entry's project and worktree`() {
        assertEquals(JsonComposeProjectRegistry.Store(), Json.decodeFromString<JsonComposeProjectRegistry.Store>("{}"))
        assertEachFieldRequired<JsonComposeProjectRegistry.StoredEntry>(mapOf("project" to "\"p\"", "worktreePath" to "\"/w\""))
        assertRoundTrips(JsonComposeProjectRegistry.Store(), JsonComposeProjectRegistry.Store(mapOf("/repo" to listOf(JsonComposeProjectRegistry.StoredEntry("p", "/w")))))
    }

    @Test
    fun `a repository approval needs its digest and commands`() {
        assertEquals(JsonRepositoryTrust.Store(), Json.decodeFromString<JsonRepositoryTrust.Store>("{}"))
        assertEachFieldRequired<JsonRepositoryTrust.Approval>(mapOf("sha256" to "\"abc\"", "commands" to "[]"))
        assertRoundTrips(JsonRepositoryTrust.Store(), JsonRepositoryTrust.Store(mapOf("/repo" to JsonRepositoryTrust.Approval("abc", listOf(listOf("make"))))))
        assertRoundTrips(JsonRepositoryTrust.Approval("abc", emptyList(), env = mapOf("A" to "1")), JsonRepositoryTrust.Approval("abc", emptyList(), envFile = ".env"))
    }

    @Test
    fun `the multirepo registry reads missing fields as empty`() {
        assertEquals(JsonMultirepoSource.Registry(), Json.decodeFromString<JsonMultirepoSource.Registry>("{}"))
        assertEquals(JsonMultirepoSource.LocalSettings(), Json.decodeFromString<JsonMultirepoSource.LocalSettings>("{}"))
        assertRoundTrips(
            JsonMultirepoSource.Registry(),
            JsonMultirepoSource.Registry(multirepos = listOf("/m")),
            JsonMultirepoSource.Registry(local = mapOf("/m" to JsonMultirepoSource.LocalSettings(approved = "abc"))),
        )
        assertRoundTrips(JsonMultirepoSource.LocalSettings(), JsonMultirepoSource.LocalSettings(agent = AgentLaunchConfig(agentCommand = "codex")))
    }

    @Test
    fun `a desktop turn needs every field`() {
        assertEachFieldRequired<NioDesktopTurnStore.TurnEntry>(mapOf("adminDirectory" to "\"/a\"", "worktreePath" to "\"/w\"", "acquiredAt" to "1", "expiresAt" to "2"))
        assertRoundTrips(NioDesktopTurnStore.TurnEntry("/a", "/w", 1, 2))
    }

    @Test
    fun `the Orca workspace store reads missing fields as empty`() {
        assertEquals(JsonOrcaWorkspaceStore.Store(), Json.decodeFromString<JsonOrcaWorkspaceStore.Store>("{}"))
        assertRoundTrips(
            JsonOrcaWorkspaceStore.Store(),
            JsonOrcaWorkspaceStore.Store(workspacesRoot = "/o"),
            JsonOrcaWorkspaceStore.Store(boardStatuses = mapOf("/w" to "todo")),
        )
    }

    @Test
    fun `a remembered derived data size needs its size and last access`() {
        assertEquals(JsonDerivedDataSizeStore.Store(), Json.decodeFromString<JsonDerivedDataSizeStore.Store>("{}"))
        assertEachFieldRequired<JsonDerivedDataSizeStore.Entry>(mapOf("sizeBytes" to "1", "lastAccessed" to "\"2026-01-01T00:00:00Z\""))
        assertRoundTrips(JsonDerivedDataSizeStore.Store(), JsonDerivedDataSizeStore.Store(mapOf("/dd" to JsonDerivedDataSizeStore.Entry(1, "2026-01-01T00:00:00Z"))))
        assertRoundTrips(JsonDerivedDataSizeStore.Entry(1, "2026-01-01T00:00:00Z", packagesBytes = 2))
    }

    @Test
    fun `a wait ticket needs everything but its process start`() {
        assertEquals(NioDeviceWaitQueue.QueueFile(), Json.decodeFromString<NioDeviceWaitQueue.QueueFile>("{}"))
        assertEachFieldRequired<NioDeviceWaitQueue.TicketEntry>(
            mapOf("id" to "\"t\"", "adminDirectory" to "\"/a\"", "worktreePath" to "\"/w\"", "pid" to "1", "since" to "2"),
        )
        assertRoundTrips(NioDeviceWaitQueue.QueueFile(), NioDeviceWaitQueue.QueueFile(mapOf("emulator" to listOf(NioDeviceWaitQueue.TicketEntry("t", "/a", "/w", 1, since = 2)))))
        assertRoundTrips(NioDeviceWaitQueue.TicketEntry("t", "/a", "/w", 1, processStartedAt = 3, since = 2))
    }
}
