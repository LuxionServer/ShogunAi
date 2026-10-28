package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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

data class HooksStatus(val path: String, val state: HooksInstallState, val events: List<String>)

class ClaudeCodeHooks(private val fileManager: FileManager) {

    fun status(path: String): Result<HooksStatus> = runCatching {
        val preview = diff(readSettings(path), install = true)
        val state = when {
            preview.isEmpty -> HooksInstallState.INSTALLED
            preview.added.size == DESIRED.size && preview.replaced.isEmpty() && preview.removed.isEmpty() -> HooksInstallState.MISSING
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
            throw WorktreeError.InvalidClaudeSettings(path, error.message?.lineSequence()?.firstOrNull().orEmpty())
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
        val desired = if (install) DESIRED.toMap() else emptyMap()
        (DESIRED.map { it.first } + hooks.keys).distinct().forEach { event ->
            val found = shogunCommands(hooks[event])
            val wanted = desired[event]
            when {
                wanted != null && found == listOf(wanted) -> Unit
                wanted != null && found.isEmpty() -> added += HookEntry(event, wanted)
                wanted != null -> replaced += HookReplacement(event, found, wanted)
                else -> found.forEach { removed += HookEntry(event, it) }
            }
        }
        return HooksPreview(added, replaced, removed)
    }

    private fun shogunCommands(groups: JsonElement?): List<String> =
        (groups as? JsonArray).orEmpty().flatMap { group -> shogunHooks(group).map { commandOf(it) } }

    private fun shogunHooks(group: JsonElement): List<JsonElement> =
        ((group as? JsonObject)?.get(HOOKS) as? JsonArray).orEmpty().filter(::isShogunHook)

    private fun isShogunHook(hook: JsonElement): Boolean =
        (hook as? JsonObject)?.get("type")?.string() == "command" && commandOf(hook).startsWith(SHOGUN_PREFIX)

    private fun commandOf(hook: JsonElement): String = (hook as? JsonObject)?.get("command")?.string().orEmpty()

    private fun JsonElement.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun applyInstall(settings: JsonObject): JsonObject {
        val hooks = hooksOf(settings)
        val rebuilt = LinkedHashMap<String, JsonElement>(hooks)
        DESIRED.forEach { (event, command) ->
            if (shogunCommands(hooks[event]) == listOf(command)) return@forEach
            val kept = stripped(hooks[event])
            rebuilt[event] = JsonArray(kept + group(command))
        }
        hooks.keys.filter { event -> DESIRED.none { it.first == event } }.forEach { event -> restoreStripped(rebuilt, hooks, event) }
        return withHooks(settings, JsonObject(rebuilt))
    }

    private fun applyUninstall(settings: JsonObject): JsonObject {
        val hooks = hooksOf(settings)
        val rebuilt = LinkedHashMap<String, JsonElement>(hooks)
        hooks.keys.forEach { event -> restoreStripped(rebuilt, hooks, event) }
        return withHooks(settings, JsonObject(rebuilt))
    }

    private fun restoreStripped(rebuilt: MutableMap<String, JsonElement>, hooks: JsonObject, event: String) {
        if (shogunCommands(hooks[event]).isEmpty()) return
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

    private fun group(command: String): JsonElement = buildJsonObject {
        putJsonArray(HOOKS) {
            addJsonObject {
                put("type", "command")
                put("command", command)
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
        private val PRETTY = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        val DESIRED: List<Pair<String, String>> = listOf(
            "WorktreeCreate" to "shogun hook worktree-create",
            "WorktreeRemove" to "shogun hook worktree-remove",
            "SessionStart" to "shogun hook session-start",
            "UserPromptSubmit" to "shogun hook agent-state",
            "Notification" to "shogun hook agent-state",
            "Stop" to "shogun hook agent-state",
            "SessionEnd" to "shogun hook agent-state",
        )

        val AGENT_STATE_EVENTS: List<String> = listOf("UserPromptSubmit", "Notification", "Stop", "SessionEnd")

        fun userSettingsPath(configDirectory: String?, userHome: String): String =
            claudeConfigDirectory(configDirectory, userHome) + "/settings.json"

        fun claudeSessionsDirectory(configDirectory: String?, userHome: String): String =
            claudeConfigDirectory(configDirectory, userHome) + "/sessions"

        private fun claudeConfigDirectory(configDirectory: String?, userHome: String): String =
            (configDirectory?.takeIf { it.isNotBlank() } ?: "$userHome/.claude").trimEnd('/')

        fun projectSettingsPath(repository: String): String = repository.trimEnd('/') + "/.claude/settings.json"
    }
}
