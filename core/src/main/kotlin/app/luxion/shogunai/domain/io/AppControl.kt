package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.AppRequest

interface AppRequestChannel {
    fun send(request: AppRequest)
    fun receive(nowMillis: Long): List<AppRequest>
}

interface AppInstanceLock {
    fun isHeldByAnyInstance(): Boolean
    fun acquire(): Boolean
    fun release()
}

interface AppLauncher {
    fun isAvailable(): Boolean
    fun launch()
}
