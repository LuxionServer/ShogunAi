package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AppWindow
import app.luxion.shogunai.domain.model.HeadlessLaunch
import app.luxion.shogunai.domain.model.HeadlessLaunchResult
import app.luxion.shogunai.domain.model.HideOutcome
import app.luxion.shogunai.domain.model.MacPermission

interface HeadlessAppLauncher {

    suspend fun launch(launch: HeadlessLaunch): HeadlessLaunchResult

    fun isAlive(pid: Long): Boolean

    suspend fun stop(pid: Long)
}

interface AppWindowService {

    val isSupported: Boolean

    suspend fun mainWindow(pid: Long): AppWindow?

    suspend fun hide(pid: Long): HideOutcome

    suspend fun capture(window: AppWindow, outputPath: String)

    suspend fun isGranted(permission: MacPermission): Boolean
}
