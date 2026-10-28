package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AppWindowService
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.HeadlessAppLauncher
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.AppWindow
import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.HeadlessAppPaths
import app.luxion.shogunai.domain.model.HeadlessAppProcess
import app.luxion.shogunai.domain.model.HeadlessAppStart
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.HeadlessAppStatus
import app.luxion.shogunai.domain.model.HeadlessAppStop
import app.luxion.shogunai.domain.model.HeadlessLaunch
import app.luxion.shogunai.domain.model.HideOutcome
import app.luxion.shogunai.domain.model.MacPermission
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.gradleModuleDirectory
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class HeadlessAppWorktrees(
    private val provisioningSource: RepositoryProvisioningSource,
    private val adminDirectoryOf: suspend (String) -> String,
    private val launcher: HeadlessAppLauncher,
    private val windows: AppWindowService,
    private val fileManager: FileManager,
) {
    internal data class Located(val worktreePath: String, val provider: DesktopAppProvider, val paths: HeadlessAppPaths)

    internal suspend fun locate(worktreePath: String): Located {
        if (!windows.isSupported) throw WorktreeError.HeadlessAppsNotSupported
        val provider = provisioningSource.read(worktreePath).providers.desktopApp
            ?: throw WorktreeError.DesktopAppProviderNotDeclared
        return Located(worktreePath, provider, pathsOf(worktreePath, provider))
    }

    internal suspend fun declaresApp(worktreePath: String): Boolean =
        windows.isSupported && runCatching { provisioningSource.read(worktreePath).providers.desktopApp }.getOrNull() != null

    private suspend fun pathsOf(worktreePath: String, provider: DesktopAppProvider): HeadlessAppPaths {
        val state = "${adminDirectoryOf(worktreePath).trimEnd('/')}/$STATE_DIRECTORY"
        val run = "${worktreePath.trimEnd('/')}/${gradleModuleDirectory(provider.module)}/$RUN_DIRECTORY"
        return HeadlessAppPaths(
            stateDirectory = state,
            homePath = if (provider.isolatedHome) "$state/home" else null,
            logPath = "$state/launch.log",
            appLogPath = "$run/hotRun.stderr.txt",
            pidFilePath = "$run/main.pid",
            capturesDirectory = "$state/captures",
        )
    }

    internal fun runningPid(paths: HeadlessAppPaths): Long? =
        fileManager.readProperties(paths.pidFilePath)["pid"]?.trim()?.toLongOrNull()?.takeIf(launcher::isAlive)

    internal fun tailOf(path: String): String =
        fileManager.readText(path)?.lines()?.dropLastWhile { it.isBlank() }?.takeLast(LOG_TAIL_LINES)?.joinToString("\n").orEmpty()

    internal suspend fun grantedPermissions(): Set<MacPermission> =
        MacPermission.entries.filter { windows.isGranted(it) }.toSet()

    companion object {
        const val STATE_DIRECTORY = "shogunai/headless"
        const val RUN_DIRECTORY = "build/run/main"
        const val LOG_TAIL_LINES = 20
    }
}

class StartHeadlessAppUseCase(
    private val worktrees: HeadlessAppWorktrees,
    private val launcher: HeadlessAppLauncher,
    private val windows: AppWindowService,
    private val fileManager: FileManager,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val timeoutMs: Long = WINDOW_TIMEOUT_MS,
) {
    suspend operator fun invoke(worktreePath: String, visible: Boolean = false, fresh: Boolean = false): Result<HeadlessAppStart> =
        runCatching {
            val located = worktrees.locate(worktreePath)
            val paths = located.paths
            worktrees.runningPid(paths)?.let { pid ->
                val window = windows.mainWindow(pid)
                return@runCatching finish(pid, alreadyRunning = true, window, visible, paths)
            }
            if (fresh) paths.homePath?.let(fileManager::deleteRecursively)
            val launched = launcher.launch(HeadlessLaunch(worktreePath, located.provider.module, paths.homePath, paths.logPath))
            if (launched.exitCode != 0) throw WorktreeError.HeadlessAppLaunchFailed(paths.logPath, worktrees.tailOf(paths.logPath))
            val pid = launched.pid
                ?: worktrees.runningPid(paths)
                ?: throw WorktreeError.HeadlessAppLaunchFailed(paths.logPath, worktrees.tailOf(paths.logPath))
            finish(pid, alreadyRunning = false, awaitWindow(pid, paths), visible, paths)
        }

    private suspend fun awaitWindow(pid: Long, paths: HeadlessAppPaths): AppWindow {
        var waited = 0L
        while (true) {
            windows.mainWindow(pid)?.let { return it }
            if (!launcher.isAlive(pid)) throw WorktreeError.HeadlessAppExitedEarly(paths.appLogPath, worktrees.tailOf(paths.appLogPath))
            if (waited >= timeoutMs) throw WorktreeError.HeadlessAppWindowTimeout(pid, timeoutMs / 1000)
            pause(pollIntervalMs)
            waited += pollIntervalMs
        }
    }

    private suspend fun finish(pid: Long, alreadyRunning: Boolean, window: AppWindow?, visible: Boolean, paths: HeadlessAppPaths): HeadlessAppStart {
        if (visible || window == null) {
            return HeadlessAppStart(pid, alreadyRunning, hidden = window?.isOnScreen == false, missingPermission = null, paths = paths)
        }
        val outcome = windows.hide(pid)
        val missing = if (outcome == HideOutcome.DENIED) MacPermission.ACCESSIBILITY else null
        return HeadlessAppStart(pid, alreadyRunning, hidden = outcome == HideOutcome.HIDDEN, missingPermission = missing, paths = paths)
    }

    companion object {
        const val POLL_INTERVAL_MS = 500L
        const val WINDOW_TIMEOUT_MS = 120_000L
    }
}

class StopHeadlessAppUseCase(
    private val worktrees: HeadlessAppWorktrees,
    private val launcher: HeadlessAppLauncher,
) {
    suspend operator fun invoke(worktreePath: String): Result<HeadlessAppStop> = runCatching {
        val pid = worktrees.runningPid(worktrees.locate(worktreePath).paths)
            ?: return@runCatching HeadlessAppStop(wasRunning = false, pid = null)
        launcher.stop(pid)
        HeadlessAppStop(wasRunning = true, pid = pid)
    }

    suspend fun beforeRemoving(worktreePath: String): WorktreeError? {
        if (!worktrees.declaresApp(worktreePath)) return null
        return invoke(worktreePath).exceptionOrNull()?.let { it as? WorktreeError ?: WorktreeError.HeadlessAppStopFailed(it.message) }
    }
}

class ReadHeadlessAppUseCase(
    private val worktrees: HeadlessAppWorktrees,
    private val windows: AppWindowService,
) {
    suspend operator fun invoke(worktreePath: String): Result<HeadlessAppStatus> = runCatching {
        val paths = worktrees.locate(worktreePath).paths
        val pid = worktrees.runningPid(paths)
        val window = pid?.let { windows.mainWindow(it) }
        HeadlessAppStatus(
            state = if (pid == null) HeadlessAppState.STOPPED else HeadlessAppState.RUNNING,
            pid = pid,
            hasWindow = window != null,
            hidden = window?.isOnScreen == false,
            paths = paths,
            grantedPermissions = worktrees.grantedPermissions(),
        )
    }

    suspend fun process(worktreePath: String): Result<HeadlessAppProcess?> = runCatching {
        if (!worktrees.declaresApp(worktreePath)) return@runCatching null
        val paths = worktrees.locate(worktreePath).paths
        val pid = worktrees.runningPid(paths)
        HeadlessAppProcess(if (pid == null) HeadlessAppState.STOPPED else HeadlessAppState.RUNNING, pid, paths.logPath)
    }
}

class CaptureHeadlessAppUseCase(
    private val worktrees: HeadlessAppWorktrees,
    private val windows: AppWindowService,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend operator fun invoke(worktreePath: String, outputPath: String? = null): Result<String> = runCatching {
        val paths = worktrees.locate(worktreePath).paths
        val pid = worktrees.runningPid(paths) ?: throw WorktreeError.HeadlessAppNotRunning
        if (!windows.isGranted(MacPermission.SCREEN_RECORDING)) throw WorktreeError.MacPermissionMissing(MacPermission.SCREEN_RECORDING)
        val window = windows.mainWindow(pid) ?: throw WorktreeError.HeadlessAppHasNoWindow
        val output = outputPath ?: "${paths.capturesDirectory}/capture-${CAPTURE_STAMP.format(clock.instant())}.png"
        windows.capture(window, output)
        output
    }

    private companion object {
        val CAPTURE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
    }
}
