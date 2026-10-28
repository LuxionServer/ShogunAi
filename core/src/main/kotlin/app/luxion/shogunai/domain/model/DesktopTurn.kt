package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant

data class DesktopTurn(
    val adminDirectory: String,
    val worktreePath: String,
    val acquiredAt: Long,
    val expiresAt: Long,
) {
    val displayName: String
        get() = poolDisplayName(adminDirectory, worktreePath)

    val repositoryPath: String
        get() = repositoryPathOf(adminDirectory)

    fun isExpiredAt(now: Instant): Boolean = now.toEpochMilli() >= expiresAt
}

data class DesktopTurnState(
    val holder: DesktopTurn?,
    val waiting: List<WaitTicket>,
)

data class DesktopTurnAcquisition(
    val turn: DesktopTurn,
    val renewed: Boolean,
    val waitedSeconds: Long?,
    val waiting: List<WaitTicket>,
)

data class DesktopWait(
    val timeout: Duration = Duration.ofMinutes(DEFAULT_DEVICE_WAIT_MINUTES),
    val onProgress: (DesktopWaitProgress) -> Unit = {},
)

data class DesktopWaitProgress(
    val position: Int,
    val waiting: Int,
    val holder: DesktopTurn?,
)

sealed interface DesktopTurnRelease {
    data class Released(val turn: DesktopTurn) : DesktopTurnRelease

    data object NobodyHolds : DesktopTurnRelease
}

fun repositoryPathOf(adminDirectory: String): String = adminDirectory.trimEnd('/').substringBefore("/.git")

val WaitTicket.repositoryPath: String
    get() = repositoryPathOf(adminDirectory)
