package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant

const val DEFAULT_DEVICE_WAIT_MINUTES = 30L

data class ProcessIdentity(val pid: Long, val startedAt: Long?)

data class WaitTicket(
    val id: String,
    val adminDirectory: String,
    val worktreePath: String,
    val process: ProcessIdentity,
    val since: Long,
) {
    val displayName: String
        get() = poolDisplayName(adminDirectory, worktreePath)
}

data class DeviceWait(
    val timeout: Duration = Duration.ofMinutes(DEFAULT_DEVICE_WAIT_MINUTES),
    val onProgress: (DeviceWaitProgress) -> Unit = {},
)

data class DeviceWaitProgress(
    val kind: ResourceKind,
    val position: Int,
    val waiting: Int,
    val limit: Int,
    val holders: List<PoolHolder>,
    val nextLeaseExpiry: Instant?,
)

fun poolDisplayName(adminDirectory: String?, worktreePath: String?): String {
    val repository = adminDirectory?.substringBefore("/.git")?.substringAfterLast('/').orEmpty()
    val worktree = (worktreePath ?: adminDirectory)?.trimEnd('/')?.substringAfterLast('/').orEmpty()
    return if (repository.isEmpty()) worktree else "$repository · $worktree"
}
