package app.luxion.shogunai.domain.model

data class HeadlessLaunch(
    val worktreePath: String,
    val module: String,
    val homePath: String?,
    val logPath: String,
)

data class HeadlessLaunchResult(val exitCode: Int, val pid: Long?)

data class AppWindow(val id: Long, val isOnScreen: Boolean)

enum class HideOutcome { HIDDEN, DENIED }

enum class MacPermission(val displayName: String, val settingsPane: String) {
    SCREEN_RECORDING("Screen Recording", "Privacy & Security › Screen Recording"),
    ACCESSIBILITY("Accessibility", "Privacy & Security › Accessibility"),
}

enum class HeadlessAppState { RUNNING, STOPPED }

data class HeadlessAppPaths(
    val stateDirectory: String,
    val homePath: String?,
    val logPath: String,
    val appLogPath: String,
    val pidFilePath: String,
    val capturesDirectory: String,
)

data class HeadlessAppStart(
    val pid: Long,
    val alreadyRunning: Boolean,
    val hidden: Boolean,
    val missingPermission: MacPermission?,
    val paths: HeadlessAppPaths,
)

data class HeadlessAppStatus(
    val state: HeadlessAppState,
    val pid: Long?,
    val hasWindow: Boolean,
    val hidden: Boolean,
    val paths: HeadlessAppPaths,
    val grantedPermissions: Set<MacPermission>,
)

data class HeadlessAppProcess(val state: HeadlessAppState, val pid: Long?, val logPath: String)

data class HeadlessAppStop(val wasRunning: Boolean, val pid: Long?)

fun gradleModuleDirectory(module: String): String = module.trim(':').replace(':', '/')
