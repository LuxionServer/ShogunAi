package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeCodeHooksTest {

    private val path = "/home/dev/.claude/settings.json"
    private val files = FakeFileManager()
    private val hooks = ClaudeCodeHooks(files)

    private fun settings(text: String) = files.writeText(path, text)

    private fun written(): JsonObject = Json.parseToJsonElement(files.writtenTexts.getValue(path)).jsonObject

    private fun commands(settings: JsonObject, event: String): List<String> =
        settings.getValue("hooks").jsonObject[event]?.jsonArray.orEmpty()
            .flatMap { it.jsonObject.getValue("hooks").jsonArray }
            .map { it.jsonObject.getValue("command").jsonPrimitive.content }

    private val allEvents = ClaudeCodeHooks.DESIRED.map { it.event }

    @Test
    fun `installing keeps other settings and hooks and adds ShogunAi's`() {
        settings(
            """{"theme":"dark","hooks":{"Stop":[{"hooks":[{"type":"command","command":"say done"}]}]}}""",
        )

        hooks.install(path).getOrThrow()

        val result = written()
        assertEquals("dark", result.getValue("theme").jsonPrimitive.content)
        assertEquals(listOf("say done", "shogun hook agent-state"), commands(result, "Stop"))
        assertEquals(listOf("shogun hook worktree-create"), commands(result, "WorktreeCreate"))
        assertEquals(allEvents.toSet(), result.getValue("hooks").jsonObject.keys)
    }

    @Test
    fun `installing creates a missing settings file`() {
        hooks.install(path).getOrThrow()

        assertEquals(allEvents.toSet(), written().getValue("hooks").jsonObject.keys)
        assertTrue(files.writtenTexts.getValue(path).endsWith("\n"))
    }

    @Test
    fun `installing twice leaves each hook once and does not rewrite the file`() {
        hooks.install(path).getOrThrow()
        val first = files.writtenTexts.getValue(path)

        val second = hooks.install(path).getOrThrow()

        assertTrue(second.isEmpty)
        assertEquals(first, files.writtenTexts.getValue(path))
        allEvents.distinct().forEach { event -> assertEquals(allEvents.count { it == event }, commands(written(), event).size, event) }
    }

    @Test
    fun `an older installation gets the missing hook and keeps a single session-start hook`() {
        settings(
            """{"hooks":{"SessionStart":[{"hooks":[{"type":"command","command":"shogun hook session-start"}]}]}}""",
        )

        val preview = hooks.install(path).getOrThrow()

        assertFalse(preview.added.any { it.event == "SessionStart" })
        assertTrue(preview.added.any { it.event == "Stop" })
        assertEquals(listOf("shogun hook session-start"), commands(written(), "SessionStart"))
        assertEquals(listOf("shogun hook agent-state"), commands(written(), "Stop"))
    }

    @Test
    fun `a ShogunAi hook that differs from the current one is replaced`() {
        settings(
            """{"hooks":{"Stop":[{"hooks":[{"type":"command","command":"shogun hook old-name"},{"type":"command","command":"say done"}]}]}}""",
        )

        val preview = hooks.install(path).getOrThrow()

        assertEquals(listOf(HookReplacement("Stop", listOf("shogun hook old-name"), "shogun hook agent-state")), preview.replaced)
        assertEquals(listOf("say done", "shogun hook agent-state"), commands(written(), "Stop"))
    }

    @Test
    fun `a ShogunAi hook on an event no longer used is removed`() {
        settings("""{"hooks":{"PostToolUse":[{"hooks":[{"type":"command","command":"shogun hook agent-state"}]}]}}""")

        val preview = hooks.install(path).getOrThrow()

        assertEquals(listOf(HookEntry("PostToolUse", "shogun hook agent-state")), preview.removed)
        assertNull(written().getValue("hooks").jsonObject["PostToolUse"])
    }

    @Test
    fun `the desktop turn hook runs only for shell commands mentioning computer`() {
        hooks.install(path).getOrThrow()

        val group = preToolUseGroups().single { it.getValue("matcher").jsonPrimitive.content == "Bash" }
        val hook = group.getValue("hooks").jsonArray.single().jsonObject
        assertEquals("Bash", group.getValue("matcher").jsonPrimitive.content)
        assertEquals("shogun hook desktop-turn", hook.getValue("command").jsonPrimitive.content)
        assertEquals("Bash(*computer *)", hook.getValue("if").jsonPrimitive.content)
    }

    private fun preToolUseGroups(): List<JsonObject> =
        written().getValue("hooks").jsonObject.getValue("PreToolUse").jsonArray.map { it.jsonObject }

    @Test
    fun `the desktop turn hook also runs for Claude's acting computer-use tools`() {
        hooks.install(path).getOrThrow()

        val group = preToolUseGroups().single { it.getValue("matcher").jsonPrimitive.content != "Bash" }
        val hook = group.getValue("hooks").jsonArray.single().jsonObject
        val matcher = Regex(group.getValue("matcher").jsonPrimitive.content)
        assertEquals("shogun hook desktop-turn", hook.getValue("command").jsonPrimitive.content)
        assertNull(hook["if"])
        assertTrue(matcher.containsMatchIn("mcp__computer-use__left_click"))
        assertTrue(matcher.containsMatchIn("mcp__remote-devices__computer_type"))
        assertFalse(matcher.containsMatchIn("mcp__computer-use__screenshot"))
    }

    @Test
    fun `the previous version's PreToolUse hook is outdated and gets the computer-use hook`() {
        hooks.install(path).getOrThrow()
        val previous = written().getValue("hooks").jsonObject.let { all ->
            val bashOnly = JsonArray(preToolUseGroups().filter { it.getValue("matcher").jsonPrimitive.content == "Bash" })
            JsonObject(mapOf("hooks" to JsonObject(all + ("PreToolUse" to bashOnly))))
        }
        settings(previous.toString())

        val status = hooks.status(path).getOrThrow()
        val preview = hooks.install(path).getOrThrow()

        assertEquals(HooksInstallState.OUTDATED, status.state)
        assertEquals(listOf("PreToolUse"), status.events)
        assertEquals(listOf(HookEntry("PreToolUse", "shogun hook desktop-turn")), preview.added)
        assertTrue(preview.replaced.isEmpty() && preview.removed.isEmpty())
        assertEquals(1, preToolUseGroups().count { it.getValue("matcher").jsonPrimitive.content == "Bash" })
        assertEquals(2, preToolUseGroups().size)
        assertEquals(HooksInstallState.INSTALLED, hooks.status(path).getOrThrow().state)
    }

    @Test
    fun `uninstalling removes both PreToolUse hooks and keeps the others`() {
        settings("""{"hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"lint-check"}]}]}}""")
        hooks.install(path).getOrThrow()

        val preview = hooks.uninstall(path).getOrThrow()

        assertEquals(2, preview.removed.count { it.event == "PreToolUse" })
        assertEquals(listOf("lint-check"), commands(written(), "PreToolUse"))
    }

    @Test
    fun `an installation without the desktop turn hook is outdated and gets it`() {
        hooks.install(path).getOrThrow()
        val previous = written().let { settings ->
            JsonObject(mapOf("hooks" to JsonObject(settings.getValue("hooks").jsonObject.filterKeys { it != "PreToolUse" })))
        }
        settings(previous.toString())

        val status = hooks.status(path).getOrThrow()
        val preview = hooks.install(path).getOrThrow()

        assertEquals(HooksInstallState.OUTDATED, status.state)
        assertEquals(listOf("PreToolUse"), status.events)
        assertEquals(List(2) { HookEntry("PreToolUse", "shogun hook desktop-turn") }, preview.added)
        assertEquals(List(2) { "shogun hook desktop-turn" }, commands(written(), "PreToolUse"))
    }

    @Test
    fun `a desktop turn hook without its filter is replaced by the filtered one`() {
        hooks.install(path).getOrThrow()
        val hooksObject = written().getValue("hooks").jsonObject
        val unfiltered = Json.parseToJsonElement(
            """[{"hooks":[{"type":"command","command":"shogun hook desktop-turn"}]},{"matcher":"Bash","hooks":[{"type":"command","command":"lint-check"}]}]""",
        )
        settings(JsonObject(mapOf("hooks" to JsonObject(hooksObject + ("PreToolUse" to unfiltered)))).toString())

        assertEquals(HooksInstallState.OUTDATED, hooks.status(path).getOrThrow().state)
        val preview = hooks.install(path).getOrThrow()

        assertEquals(listOf(HookReplacement("PreToolUse", listOf("shogun hook desktop-turn"), "shogun hook desktop-turn")), preview.replaced)
        assertEquals(listOf("lint-check", "shogun hook desktop-turn", "shogun hook desktop-turn"), commands(written(), "PreToolUse"))
        val shogunHook = preToolUseGroups()[1].getValue("hooks").jsonArray.single().jsonObject
        assertEquals("Bash(*computer *)", shogunHook.getValue("if").jsonPrimitive.content)
        assertEquals(HooksInstallState.INSTALLED, hooks.status(path).getOrThrow().state)
    }

    @Test
    fun `invalid JSON is reported and never overwritten`() {
        settings("""{"hooks": {""")

        val install = hooks.install(path)
        val uninstall = hooks.uninstall(path)

        assertIs<WorktreeError.InvalidClaudeSettings>(install.exceptionOrNull())
        assertIs<WorktreeError.InvalidClaudeSettings>(uninstall.exceptionOrNull())
        assertTrue(install.exceptionOrNull()!!.message!!.contains(path))
        assertEquals("""{"hooks": {""", files.writtenTexts[path])
    }

    @Test
    fun `settings whose top level or hooks are not objects are invalid`() {
        settings("[]")
        assertIs<WorktreeError.InvalidClaudeSettings>(hooks.status(path).exceptionOrNull())
        settings("""{"hooks":[]}""")
        assertIs<WorktreeError.InvalidClaudeSettings>(hooks.status(path).exceptionOrNull())
    }

    @Test
    fun `uninstalling removes only ShogunAi hooks and empty entries`() {
        settings("""{"theme":"dark","hooks":{"Stop":[{"hooks":[{"type":"command","command":"say done"}]}]}}""")
        hooks.install(path).getOrThrow()

        hooks.uninstall(path).getOrThrow()

        val result = written()
        assertEquals(setOf("Stop"), result.getValue("hooks").jsonObject.keys)
        assertEquals(listOf("say done"), commands(result, "Stop"))
        assertEquals("dark", result.getValue("theme").jsonPrimitive.content)
    }

    @Test
    fun `uninstalling after installing in an empty file leaves no hooks entry`() {
        hooks.install(path).getOrThrow()

        hooks.uninstall(path).getOrThrow()

        assertEquals(JsonObject(emptyMap()), written())
    }

    @Test
    fun `uninstalling without ShogunAi hooks changes nothing`() {
        settings("""{"hooks":{"Stop":[{"hooks":[{"type":"command","command":"say done"}]}]}}""")

        val preview = hooks.uninstall(path).getOrThrow()

        assertTrue(preview.isEmpty)
        assertEquals("""{"hooks":{"Stop":[{"hooks":[{"type":"command","command":"say done"}]}]}}""", files.writtenTexts[path])
    }

    @Test
    fun `status tells missing, installed and outdated apart`() {
        assertEquals(HooksInstallState.MISSING, hooks.status(path).getOrThrow().state)

        hooks.install(path).getOrThrow()
        assertEquals(HooksInstallState.INSTALLED, hooks.status(path).getOrThrow().state)

        val withoutNotification = written().let { settings ->
            val remaining = settings.getValue("hooks").jsonObject.filterKeys { it != "Notification" }
            JsonObject(mapOf("hooks" to JsonObject(remaining)))
        }
        settings(withoutNotification.toString())
        val outdated = hooks.status(path).getOrThrow()
        assertEquals(HooksInstallState.OUTDATED, outdated.state)
        assertEquals(listOf("Notification"), outdated.events)
    }

    @Test
    fun `status of other people's hooks only is missing`() {
        settings("""{"hooks":{"Stop":[{"hooks":[{"type":"command","command":"say done"}]}]}}""")

        assertEquals(HooksInstallState.MISSING, hooks.status(path).getOrThrow().state)
    }

    @Test
    fun `previewing does not write`() {
        val preview = hooks.previewInstall(path).getOrThrow()

        assertEquals(allEvents, preview.added.map { it.event })
        assertNull(files.writtenTexts[path])
    }

    @Test
    fun `settings paths follow CLAUDE_CONFIG_DIR and the repository`() {
        assertEquals("/home/dev/.claude/settings.json", ClaudeCodeHooks.userSettingsPath(null, "/home/dev"))
        assertEquals("/home/dev/.claude/settings.json", ClaudeCodeHooks.userSettingsPath(" ", "/home/dev"))
        assertEquals("/custom/claude/settings.json", ClaudeCodeHooks.userSettingsPath("/custom/claude/", "/home/dev"))
        assertEquals("/home/dev/projects/main-repo/.claude/settings.json", ClaudeCodeHooks.projectSettingsPath("/home/dev/projects/main-repo"))
    }

    @Test
    fun `the sessions directory follows the Claude config directory`() {
        assertEquals("/home/dev/.claude/sessions", ClaudeCodeHooks.claudeSessionsDirectory(null, "/home/dev"))
        assertEquals("/home/dev/.claude/sessions", ClaudeCodeHooks.claudeSessionsDirectory("", "/home/dev"))
        assertEquals("/custom/claude/sessions", ClaudeCodeHooks.claudeSessionsDirectory("/custom/claude/", "/home/dev"))
    }

    @Test
    fun `hooks of an event stay an array`() {
        hooks.install(path).getOrThrow()

        assertIs<JsonArray>(written().getValue("hooks").jsonObject.getValue("Stop"))
    }
}
