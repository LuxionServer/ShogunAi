package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AppWindowService
import app.luxion.shogunai.domain.model.AppWindow
import app.luxion.shogunai.domain.model.HideOutcome
import app.luxion.shogunai.domain.model.MacPermission
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path

class MacAppWindowService(
    private val executor: ShellCommandExecutor,
    private val scriptDirectory: String,
    private val osName: String = System.getProperty("os.name").orEmpty(),
) : AppWindowService {

    override val isSupported: Boolean get() = osName.startsWith("Mac", ignoreCase = true)

    override suspend fun mainWindow(pid: Long): AppWindow? = parseMainWindow(helper(ACTION_WINDOWS, pid.toString()))

    override suspend fun hide(pid: Long): HideOutcome = parseHide(helper(ACTION_HIDE, pid.toString()))

    override suspend fun capture(window: AppWindow, outputPath: String) {
        withContext(Dispatchers.IO) { Path.of(outputPath).toAbsolutePath().parent?.let(Files::createDirectories) }
        val command = captureCommand(window, outputPath)
        val result = executor.execute(command)
        if (!result.isSuccess) throw WorktreeError.MacWindowToolFailed(result.stderr.ifBlank { result.stdout }.trim())
    }

    override suspend fun isGranted(permission: MacPermission): Boolean {
        val permissions = Json.parseToJsonElement(helper(ACTION_PERMISSIONS, "0")).jsonObject
        val key = when (permission) {
            MacPermission.SCREEN_RECORDING -> "screenRecording"
            MacPermission.ACCESSIBILITY -> "accessibility"
        }
        return permissions[key]?.jsonPrimitive?.booleanOrNull == true
    }

    private suspend fun helper(action: String, pid: String): String {
        val result = executor.execute(helperCommand(writeScript(), action, pid))
        if (!result.isSuccess) throw WorktreeError.MacWindowToolFailed(result.stderr.trim())
        return result.stdout.trim()
    }

    private suspend fun writeScript(): Path = withContext(Dispatchers.IO) {
        val script = Path.of(scriptDirectory, SCRIPT_NAME)
        Files.createDirectories(script.parent)
        if (!Files.exists(script) || Files.readString(script) != SCRIPT) Files.writeString(script, SCRIPT)
        script
    }

    companion object {
        const val SCRIPT_NAME = "shogunai-windows.js"
        const val ACTION_WINDOWS = "windows"
        const val ACTION_HIDE = "hide"
        const val ACTION_PERMISSIONS = "permissions"

        fun helperCommand(script: Path, action: String, pid: String): List<String> =
            listOf("osascript", "-l", "JavaScript", script.toString(), action, pid)

        fun captureCommand(window: AppWindow, outputPath: String): List<String> =
            listOf("screencapture", "-x", "-o", "-l", window.id.toString(), outputPath)

        fun parseMainWindow(output: String): AppWindow? =
            (Json.parseToJsonElement(output) as? JsonArray).orEmpty()
                .map { it.jsonObject }
                .maxByOrNull { it.long("width") * it.long("height") }
                ?.let { AppWindow(id = it.long("id"), isOnScreen = it["onScreen"]?.jsonPrimitive?.booleanOrNull == true) }

        fun parseHide(output: String): HideOutcome {
            val code = Json.parseToJsonElement(output).jsonObject["code"]?.jsonPrimitive?.intOrNull
            return if (code == 0) HideOutcome.HIDDEN else HideOutcome.DENIED
        }

        private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L

        val SCRIPT = """
            ObjC.import('AppKit');
            ObjC.import('CoreGraphics');
            ObjC.import('ApplicationServices');
            ObjC.bindFunction('CGPreflightScreenCaptureAccess', ['bool', []]);
            ObjC.bindFunction('AXIsProcessTrusted', ['bool', []]);
            ObjC.bindFunction('AXUIElementCreateApplication', ['id', ['int']]);
            ObjC.bindFunction('AXUIElementSetAttributeValue', ['int', ['id', 'id', 'id']]);
            function windows(pid) {
                const list = ObjC.castRefToObject(${'$'}.CGWindowListCopyWindowInfo(${'$'}.kCGWindowListOptionAll, ${'$'}.kCGNullWindowID));
                const result = [];
                for (let i = 0; i < list.count; i++) {
                    const window = list.objectAtIndex(i);
                    if (window.objectForKey('kCGWindowOwnerPID').js !== pid || window.objectForKey('kCGWindowLayer').js !== 0) continue;
                    const bounds = window.objectForKey('kCGWindowBounds');
                    const onScreen = window.objectForKey('kCGWindowIsOnscreen');
                    result.push({
                        id: window.objectForKey('kCGWindowNumber').js,
                        onScreen: onScreen ? onScreen.js === true : false,
                        width: bounds.objectForKey('Width').js,
                        height: bounds.objectForKey('Height').js,
                    });
                }
                return result;
            }
            function run(argv) {
                const pid = parseInt(argv[1], 10);
                if (argv[0] === 'windows') return JSON.stringify(windows(pid));
                if (argv[0] === 'hide') {
                    const code = ${'$'}.AXUIElementSetAttributeValue(${'$'}.AXUIElementCreateApplication(pid), ${'$'}('AXHidden'), ${'$'}.NSNumber.numberWithBool(true));
                    return JSON.stringify({ code: code });
                }
                if (argv[0] === 'permissions') {
                    return JSON.stringify({ screenRecording: ${'$'}.CGPreflightScreenCaptureAccess(), accessibility: ${'$'}.AXIsProcessTrusted() });
                }
                throw new Error('unknown action');
            }

        """.trimIndent()
    }
}
