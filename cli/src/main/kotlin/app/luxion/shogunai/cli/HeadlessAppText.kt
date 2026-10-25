package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.HeadlessAppStart
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.HeadlessAppStatus
import app.luxion.shogunai.domain.model.HeadlessAppStop
import app.luxion.shogunai.domain.model.MacPermission
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal fun headlessStartLine(started: HeadlessAppStart): String {
    val state = if (started.hidden) "running hidden" else "running visible"
    val already = if (started.alreadyRunning) " (already running)" else ""
    return "${started.pid} $state$already"
}

internal fun headlessStatusLines(status: HeadlessAppStatus): List<String> = buildList {
    if (status.state == HeadlessAppState.STOPPED) {
        add("stopped")
    } else {
        val window = when {
            !status.hasWindow -> "no window"
            status.hidden -> "hidden"
            else -> "visible"
        }
        add("${status.pid} running, $window")
    }
    status.paths.homePath?.let { add("home: $it") }
    add("log: ${status.paths.logPath}")
    MacPermission.entries.filterNot { it in status.grantedPermissions }.forEach {
        add("missing permission: ${it.displayName} (System Settings › ${it.settingsPane})")
    }
}

internal fun headlessStartJson(worktree: String, started: HeadlessAppStart): JsonObject = buildJsonObject {
    put("worktree", worktree)
    put("state", "running")
    put("pid", started.pid)
    put("alreadyRunning", started.alreadyRunning)
    put("hidden", started.hidden)
    put("missingPermission", started.missingPermission?.jsonLabel)
    put("home", started.paths.homePath)
    put("log", started.paths.logPath)
}

internal fun headlessStopJson(worktree: String, stopped: HeadlessAppStop): JsonObject = buildJsonObject {
    put("worktree", worktree)
    put("state", "stopped")
    put("wasRunning", stopped.wasRunning)
    put("pid", stopped.pid)
}

internal fun headlessStatusJson(worktree: String, status: HeadlessAppStatus): JsonObject = buildJsonObject {
    put("worktree", worktree)
    put("state", status.state.name.lowercase())
    put("pid", status.pid)
    put("window", status.hasWindow)
    put("hidden", status.hidden)
    put("home", status.paths.homePath)
    put("log", status.paths.logPath)
    putJsonObject("permissions") {
        MacPermission.entries.forEach { put(it.jsonLabel, it in status.grantedPermissions) }
    }
}

internal fun headlessCaptureJson(worktree: String, path: String): JsonObject = buildJsonObject {
    put("worktree", worktree)
    put("capture", path)
}

private val MacPermission.jsonLabel: String
    get() = when (this) {
        MacPermission.SCREEN_RECORDING -> "screenRecording"
        MacPermission.ACCESSIBILITY -> "accessibility"
    }
