package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.AppWindowService
import app.luxion.shogunai.domain.io.HeadlessAppLauncher
import app.luxion.shogunai.domain.model.AppWindow
import app.luxion.shogunai.domain.model.HeadlessLaunch
import app.luxion.shogunai.domain.model.HeadlessLaunchResult
import app.luxion.shogunai.domain.model.HideOutcome
import app.luxion.shogunai.domain.model.MacPermission

class FakeHeadlessAppLauncher(
    private val fileManager: FakeFileManager,
    private val pidFilePath: String,
    var nextPid: Long = 4242,
    var exitCode: Int = 0,
    var startsApp: Boolean = true,
) : HeadlessAppLauncher {

    val alive = mutableSetOf<Long>()
    val launches = mutableListOf<HeadlessLaunch>()
    val stopped = mutableListOf<Long>()
    var failStop: Exception? = null

    var writesPidFile: Boolean = true

    override suspend fun launch(launch: HeadlessLaunch): HeadlessLaunchResult {
        launches += launch
        if (exitCode != 0 || !startsApp) return HeadlessLaunchResult(exitCode, null)
        if (writesPidFile) fileManager.writtenProperties[pidFilePath] = mapOf("pid" to nextPid.toString(), "orchestration.port" to "54609")
        alive += nextPid
        return HeadlessLaunchResult(exitCode, nextPid)
    }

    override fun isAlive(pid: Long): Boolean = pid in alive

    override suspend fun stop(pid: Long) {
        failStop?.let { throw it }
        stopped += pid
        alive -= pid
    }
}

class FakeAppWindowService(
    override val isSupported: Boolean = true,
    var granted: Set<MacPermission> = MacPermission.entries.toSet(),
) : AppWindowService {

    val windows = mutableMapOf<Long, AppWindow>()
    var windowAfterPolls = 0
    private var polls = 0
    val hidden = mutableListOf<Long>()
    val captures = mutableListOf<Pair<AppWindow, String>>()
    var queries = 0

    override suspend fun mainWindow(pid: Long): AppWindow? {
        queries++
        polls++
        if (polls <= windowAfterPolls) return null
        return windows[pid]
    }

    override suspend fun hide(pid: Long): HideOutcome {
        if (MacPermission.ACCESSIBILITY !in granted) return HideOutcome.DENIED
        hidden += pid
        windows[pid]?.let { windows[pid] = it.copy(isOnScreen = false) }
        return HideOutcome.HIDDEN
    }

    override suspend fun capture(window: AppWindow, outputPath: String) {
        captures += window to outputPath
    }

    override suspend fun isGranted(permission: MacPermission): Boolean {
        queries++
        return permission in granted
    }
}
