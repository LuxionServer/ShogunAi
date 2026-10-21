package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.ResourceKind

class ReadReleaseRecordUseCase(
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val stateStore: EnvironmentStateStore,
) {
    suspend operator fun invoke(worktreePath: String, kind: ResourceKind): ReleaseRecord? {
        val key = when (kind) {
            ResourceKind.EMULATOR -> EMULATOR_PORT_KEY
            ResourceKind.IOS_SIMULATOR -> IOS_SIMULATOR_KEY
            ResourceKind.GRADLE_DAEMON -> return null
        }
        val admin = runCatching { resolveEnvironment.directoriesOf(worktreePath).admin }.getOrNull() ?: return null
        return stateStore.readReleases(admin)[key]
    }
}
