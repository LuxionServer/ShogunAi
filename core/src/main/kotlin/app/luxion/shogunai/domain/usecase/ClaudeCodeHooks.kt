package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ClaudeComputerUse
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.putJsonArray

enum class HooksInstallState { INSTALLED, OUTDATED, MISSING }

data class HookEntry(val event: String, val command: String)

data class HookReplacement(val event: String, val from: List<String>, val to: String)

data class HooksPreview(
    val added: List<HookEntry> = emptyList(),
    val replaced: List<HookReplacement> = emptyList(),
    val removed: List<HookEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = added.isEmpty() && replaced.isEmpty() && removed.isEmpty()
    val events: List<String> get() = (added.map { it.event } + replaced.map { it.event } + removed.map { it.event }).distinct()
}

data class DesiredHook(val event: String, val command: String, val matcher: String? = null, val condition: String? = null)

private data class InstalledHook(val command: String, val matcher: String?, val condition: String?)

data class HooksStatus(val path: String, val state: HooksInstallState, val events: List<String>)

class ClaudeCodeHooks(private val fileManager: FileManager) {

    fun status(path: String): Result<HooksStatus> = runCatching {
        val preview = diff(readSettings(path), install = true)
        val state = when {
            preview.isEmpty -> HooksInstallState.INSTALLED
            preview.added.size == DESIRED.size && preview.removed.isEmpty() -> HooksInstallState.MISSING
            else -> HooksInstallState.OUTDATED
        }
        HooksStatus(path, state, preview.events)
    }

    fun previewInstall(path: String): Result<HooksPreview> = runCatching { diff(readSettings(path), install = true) }

    fun previewUninstall(path: String): Result<HooksPreview> = runCatching { diff(readSettings(path), install = false) }

    fun install(path: String): Result<HooksPreview> = runCatching {
        val settings = readSettings(path)
        val preview = diff(settings, install = true)
        if (!preview.isEmpty) fileManager.writeText(path, render(applyInstall(settings)))
        preview
    }

    fun uninstall(path: String): Result<HooksPreview> = runCatching {
        val settings = readSettings(path)
        val preview = diff(settings, install = false)
        if (!preview.isEmpty) fileManager.writeText(path, render(applyUninstall(settings)))
        preview
    }

    private fun readSettings(path: String): JsonObject {
        val text = fileManager.readText(path)?.takeIf { it.isNotBlank() } ?: return JsonObject(emptyMap())
        val parsed = try {
            Json.parseToJsonElement(text)
        } catch (error: SerializationException) {
            throw WorktreeError.InvalidClaudeSettings(path, error.message!!.lineSequence().first())
        }
        val settings = parsed as? JsonObject ?: throw WorktreeError.InvalidClaudeSettings(path, "the top level isn't an object")
        if (settings[HOOKS] != null && settings[HOOKS] !is JsonObject) {
            throw WorktreeError.InvalidClaudeSettings(path, "\"hooks\" isn't an object")
        }
        return settings
    }

    private fun hooksOf(settings: JsonObject): JsonObject = settings[HOOKS] as? JsonObject ?: JsonObject(emptyMap())

    private fun diff(settings: JsonObject, install: Boolean): HooksPreview {
        val hooks = hooksOf(settings)
        val added = mutableListOf<HookEntry>()
        val replaced = mutableListOf<HookReplacement>()
        val removed = mutableListOf<HookEntry>()
        (DESIRED.map { it.event } + hooks.keys).distinct().forEach { event ->
            val found = installedHooks(hooks[event])
            val wanted = if (install) desiredFor(event) else emptyList()
            if (isCurrent(found, wanted)) return@forEach
            val remaining = found.toMutableList()
            val missing = wanted.filterNot { remaining.remove(it.installed()) }
            val paired = missing.associateWith { wish -> remaining.filter { it.matcher == wish.matcher }.also { remaining.removeAll(it) } }
            val unpaired = missing.firstOrNull { paired.getValue(it).isEmpty() }
            missing.forEach { wish ->
                val previous = paired.getValue(wish) + if (wish == unpaired) remaining.toList() else emptyList()
                if (previous.isEmpty()) added += HookEntry(event, wish.command) else replaced += HookReplacement(event, previous.map { it.command }, wish.command)
            }
            if (unpaired == null) remaining.forEach { removed += HookEntry(event, it.command) }
        }
        return HooksPreview(added, replaced, removed)
    }

    private fun desiredFor(event: String): List<DesiredHook> = DESIRED.filter { it.event == event }

    private fun DesiredHook.installed(): InstalledHook = InstalledHook(command, matcher, condition)

    private fun isCurrent(found: List<InstalledHook>, wanted: List<DesiredHook>): Boolean =
        found.groupingBy { it }.eachCount() == wanted.map { it.installed() }.groupingBy { it }.eachCount()

    private fun installedHooks(groups: JsonElement?): List<InstalledHook> =
        (groups as? JsonArray).orEmpty().flatMap { group ->
            val matcher = (group as? JsonObject)?.get(MATCHER)?.string()
            shogunHooks(group).map { InstalledHook(commandOf(it), matcher, it[IF]?.string()) }
        }

    private fun shogunHooks(group: JsonElement): List<JsonObject> =
        ((group as? JsonObject)?.get(HOOKS) as? JsonArray).orEmpty().filter(::isShogunHook).map { it.jsonObject }

    private fun isShogunHook(hook: JsonElement): Boolean =
        hook is JsonObject && hook["type"]?.string() == "command" && commandOf(hook).startsWith(SHOGUN_PREFIX)

    private fun commandOf(hook: JsonObject): String = hook["command"]?.string().orEmpty()

    private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun applyInstall(settings: JsonObject): JsonObject {
        val hooks = hooksOf(settings)
        val rebuilt = LinkedHashMap<String, JsonElement>(hooks)
        DESIRED.map { it.event }.distinct().forEach { event ->
            val wanted = desiredFor(event)
            if (isCurrent(installedHooks(hooks[event]), wanted)) return@forEach
            rebuilt[event] = JsonArray(stripped(hooks[event]) + wanted.map(::group))
        }
        hooks.keys.filter { event -> DESIRED.none { it.event == event } }.forEach { event -> restoreStripped(rebuilt, hooks, event) }
        return withHooks(settings, JsonObject(rebuilt))
    }

    private fun applyUninstall(settings: JsonObject): JsonObject {
        val hooks = hooksOf(settings)
        val rebuilt = LinkedHashMap<String, JsonElement>(hooks)
        hooks.keys.forEach { event -> restoreStripped(rebuilt, hooks, event) }
        return withHooks(settings, JsonObject(rebuilt))
    }

    private fun restoreStripped(rebuilt: MutableMap<String, JsonElement>, hooks: JsonObject, event: String) {
        if (installedHooks(hooks[event]).isEmpty()) return
        val kept = stripped(hooks[event])
        if (kept.isEmpty()) rebuilt.remove(event) else rebuilt[event] = JsonArray(kept)
    }

    private fun stripped(groups: JsonElement?): List<JsonElement> =
        (groups as? JsonArray).orEmpty().mapNotNull { group ->
            val inner = (group as? JsonObject)?.get(HOOKS) as? JsonArray ?: return@mapNotNull group
            val remaining = inner.filterNot(::isShogunHook)
            when {
                remaining.size == inner.size -> group
                remaining.isEmpty() -> null
                else -> JsonObject(group.toMutableMap().apply { put(HOOKS, JsonArray(remaining)) })
            }
        }

    private fun group(hook: DesiredHook): JsonElement = buildJsonObject {
        hook.matcher?.let { put(MATCHER, it) }
        putJsonArray(HOOKS) {
            addJsonObject {
                put("type", "command")
                put("command", hook.command)
                hook.condition?.let { put(IF, it) }
            }
        }
    }

    private fun withHooks(settings: JsonObject, hooks: JsonObject): JsonObject {
        val result = LinkedHashMap<String, JsonElement>(settings)
        if (hooks.isEmpty()) result.remove(HOOKS) else result[HOOKS] = hooks
        return JsonObject(result)
    }

    private fun render(settings: JsonObject): String = PRETTY.encodeToString(JsonObject.serializer(), settings) + "\n"

    companion object {
        const val SHOGUN_PREFIX = "shogun hook "
        private const val HOOKS = "hooks"
        private const val MATCHER = "matcher"
        private const val IF = "if"
        private val PRETTY = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        val DESIRED: List<DesiredHook> = listOf(
            DesiredHook("WorktreeCreate", "shogun hook worktree-create"),
            DesiredHook("WorktreeRemove", "shogun hook worktree-remove"),
            DesiredHook("SessionStart", "shogun hook session-start"),
            DesiredHook("UserPromptSubmit", "shogun hook agent-state"),
            DesiredHook("Notification", "shogun hook agent-state"),
            DesiredHook("Stop", "shogun hook agent-state"),
            DesiredHook("SessionEnd", "shogun hook agent-state"),
            DesiredHook("PreToolUse", "shogun hook desktop-turn", matcher = "Bash", condition = "Bash(*computer *)"),
            DesiredHook("PreToolUse", "shogun hook desktop-turn", matcher = ClaudeComputerUse.hookMatcher),
        )

        private val AGENT_STATE_EVENTS: List<String> = listOf("UserPromptSubmit", "Notification", "Stop", "SessionEnd")

        fun userSettingsPath(configDirectory: String?, userHome: String): String =
            claudeConfigDirectory(configDirectory, userHome) + "/settings.json"

        fun claudeSessionsDirectory(configDirectory: String?, userHome: String): String =
            claudeConfigDirectory(configDirectory, userHome) + "/sessions"

        private fun claudeConfigDirectory(configDirectory: String?, userHome: String): String =
            (configDirectory?.takeIf { it.isNotBlank() } ?: "$userHome/.claude").trimEnd('/')

        fun projectSettingsPath(repository: String): String = repository.trimEnd('/') + "/.claude/settings.json"
    }
}
