package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.HerdrPluginState
import app.luxion.shogunai.domain.model.HerdrPluginStatus
import app.luxion.shogunai.domain.model.HerdrPluginStep
import app.luxion.shogunai.domain.usecase.HerdrPluginHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HerdrPluginSettings(private val health: HerdrPluginHealth) {
    var status by mutableStateOf<HerdrPluginStatus?>(null)
        private set
    var pending by mutableStateOf<List<HerdrPluginStep>?>(null)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var running by mutableStateOf(false)
        private set

    val action: HerdrPluginState? get() = status?.state.takeIf { it == HerdrPluginState.BROKEN || it == HerdrPluginState.MISSING }

    suspend fun refresh() {
        status = withContext(Dispatchers.IO) { health.status() }
    }

    fun request() {
        val current = status ?: return
        pending = health.reinstallSteps(current).takeIf { it.isNotEmpty() }
    }

    fun cancel() {
        pending = null
    }

    suspend fun confirm() {
        val steps = pending ?: return
        pending = null
        running = true
        val result = withContext(Dispatchers.IO) { health.reinstall(steps) }
        error = result.exceptionOrNull()
        status = result.getOrNull() ?: withContext(Dispatchers.IO) { health.status() }
        running = false
    }

    fun dismissError() {
        error = null
    }
}
