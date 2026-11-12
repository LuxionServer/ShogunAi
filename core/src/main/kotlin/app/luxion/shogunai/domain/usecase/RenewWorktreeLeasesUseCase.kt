package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.io.PhysicalDeviceLeaseStore
import app.luxion.shogunai.domain.runSuspendCatching
import java.time.Instant

class RenewWorktreeLeasesUseCase(
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val stateStore: EnvironmentStateStore,
    private val clock: () -> Instant = Instant::now,
    private val deviceLeases: PhysicalDeviceLeaseStore? = null,
) {
    suspend operator fun invoke(worktreePath: String): Result<Unit> = runSuspendCatching {
        val admin = resolveEnvironment.directoriesOf(worktreePath).admin
        stateStore.withLock {
            val held = buildSet {
                if (EMULATOR_PORT_KEY in stateStore.readPorts(admin)) add(EMULATOR_PORT_KEY)
                if (IOS_SIMULATOR_KEY in stateStore.readResources(admin)) add(IOS_SIMULATOR_KEY)
            }
            if (held.isNotEmpty()) {
                val now = clock().toEpochMilli()
                stateStore.writeLeases(admin, stateStore.readLeases(admin) + held.associateWith { now })
            }
            deviceLeases?.let { renewDevices(it, admin) }
        }
    }

    private fun renewDevices(store: PhysicalDeviceLeaseStore, admin: String) {
        val current = store.read()
        if (current.leases.none { normalizePath(it.adminDirectory) == normalizePath(admin) }) return
        val now = clock().toEpochMilli()
        store.write(current.copy(leases = current.leases.map { if (normalizePath(it.adminDirectory) == normalizePath(admin)) it.copy(renewedAt = now) else it }))
    }
}
