package app.luxion.shogunai

import app.luxion.shogunai.engine.ShogunEngine
import app.luxion.shogunai.ui.machine.SimulatorStorageActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun simulatorStorageActions(engine: ShogunEngine): SimulatorStorageActions = SimulatorStorageActions(
    supported = engine.ports.appleSimulators.isSupported(),
    list = { withContext(Dispatchers.IO) { engine.listSimulatorStorage() } },
    deleteRuntime = { identifier -> withContext(Dispatchers.IO) { engine.deleteSimulatorRuntime(identifier) } },
    deleteUnavailable = { withContext(Dispatchers.IO) { engine.deleteUnavailableSimulators() } },
)
