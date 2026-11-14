package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant

const val MAX_POOL_SLOTS = 64

private val POOL_NAME = Regex("[a-z0-9][a-z0-9-]{0,31}")

fun isValidPoolName(name: String): Boolean = POOL_NAME.matches(name)

sealed interface PoolTemplatePart {
    data class Literal(val text: String) : PoolTemplatePart
    data object Slot : PoolTemplatePart
    data object Worktree : PoolTemplatePart
}

data class PoolTemplate(val source: String, val parts: List<PoolTemplatePart>) {
    fun render(slot: Int, worktreeName: String): String = parts.joinToString("") { part ->
        when (part) {
            is PoolTemplatePart.Literal -> part.text
            PoolTemplatePart.Slot -> slot.toString()
            PoolTemplatePart.Worktree -> worktreeName
        }
    }
}

class InvalidPoolTemplate(override val message: String) : Exception(message)

fun parsePoolTemplate(value: String): PoolTemplate {
    val parts = mutableListOf<PoolTemplatePart>()
    val literal = StringBuilder()
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith("$$", index) -> {
                literal.append('$')
                index += 2
            }
            value.startsWith("\${", index) -> {
                val end = value.indexOf('}', index)
                if (end < 0) throw InvalidPoolTemplate("unclosed placeholder in \"$value\"")
                if (literal.isNotEmpty()) parts += PoolTemplatePart.Literal(literal.toString()).also { literal.clear() }
                parts += when (val content = value.substring(index + 2, end)) {
                    "slot" -> PoolTemplatePart.Slot
                    "worktree" -> PoolTemplatePart.Worktree
                    else -> throw InvalidPoolTemplate("unknown placeholder \${$content} in \"$value\"; pools accept \${slot}, \${worktree} and $$")
                }
                index = end + 1
            }
            else -> {
                literal.append(value[index])
                index++
            }
        }
    }
    if (literal.isNotEmpty()) parts += PoolTemplatePart.Literal(literal.toString())
    return PoolTemplate(value, parts)
}

data class CustomPoolVariable(val name: String, val template: PoolTemplate)

data class CustomPool(
    val name: String,
    val max: Int,
    val acquire: List<PoolTemplate>,
    val release: List<PoolTemplate>? = null,
    val variables: List<CustomPoolVariable> = emptyList(),
) {
    val acquireSource: List<String> get() = acquire.map { it.source }

    val releaseSource: List<String>? get() = release?.map { it.source }

    fun acquireCommand(slot: Int, worktreeName: String): List<String> = acquire.map { it.render(slot, worktreeName) }

    fun releaseCommand(slot: Int, worktreeName: String): List<String>? = release?.map { it.render(slot, worktreeName) }

    fun variablesFor(slot: Int, worktreeName: String): List<Pair<String, String>> =
        variables.map { it.name to it.template.render(slot, worktreeName) }
}

data class CustomPoolLease(
    val repository: String,
    val repositoryPath: String,
    val pool: String,
    val slot: Int,
    val adminDirectory: String,
    val worktreePath: String,
    val acquiredAt: Long,
    val renewedAt: Long,
    val runningBy: ProcessIdentity? = null,
) {
    val displayName: String get() = poolDisplayName(adminDirectory, worktreePath)

    fun expiresAt(duration: Duration): Instant = Instant.ofEpochMilli(renewedAt).plus(duration)

    fun belongsTo(repository: String, pool: String): Boolean = this.repository == repository && this.pool == pool

    fun heldBy(adminDirectory: String): Boolean = normalizePath(this.adminDirectory) == normalizePath(adminDirectory)
}

data class CustomPoolLoss(
    val repository: String,
    val pool: String,
    val slot: Int,
    val adminDirectory: String,
    val worktreePath: String,
    val at: Long,
    val forWorktree: String,
)

data class CustomPoolLeases(
    val leases: List<CustomPoolLease> = emptyList(),
    val losses: List<CustomPoolLoss> = emptyList(),
)

data class CustomPoolHolder(
    val lease: CustomPoolLease,
    val expiresAt: Instant,
    val agentState: AgentState?,
    val running: Boolean,
) {
    val displayName: String get() = lease.displayName

    val note: CustomPoolHolderNote
        get() = CustomPoolHolderNote(displayName, lease.slot, expiresAt.toEpochMilli(), agentState == AgentState.WORKING)
}

data class CustomPoolState(
    val pool: CustomPool,
    val holders: List<CustomPoolHolder>,
    val waiting: List<WaitTicket>,
    val losses: List<CustomPoolLoss>,
) {
    fun heldBy(adminDirectory: String): CustomPoolHolder? = holders.firstOrNull { it.lease.heldBy(adminDirectory) }

    fun waitingOf(adminDirectory: String): Pair<Int, WaitTicket>? {
        val index = waiting.indexOfFirst { normalizePath(it.adminDirectory) == normalizePath(adminDirectory) }
        return if (index < 0) null else (index + 1) to waiting[index]
    }

    fun lossOf(adminDirectory: String): CustomPoolLoss? =
        losses.lastOrNull { normalizePath(it.adminDirectory) == normalizePath(adminDirectory) }
}

data class CustomPoolAcquisition(
    val pool: String,
    val slot: Int,
    val expiresAt: Instant,
    val renewed: Boolean,
    val takenFrom: String?,
    val waitedSeconds: Long?,
    val variables: List<Pair<String, String>>,
)

data class CustomPoolRelease(val pool: String, val slot: Int?)

data class CustomPoolWaitProgress(
    val pool: String,
    val position: Int,
    val waiting: Int,
    val max: Int,
    val holders: List<CustomPoolHolderNote>,
    val nextLeaseExpiry: Instant?,
)

data class CustomPoolWait(
    val timeout: Duration = Duration.ofMinutes(DEFAULT_DEVICE_WAIT_MINUTES),
    val onProgress: (CustomPoolWaitProgress) -> Unit = {},
)

fun customPoolTicketRequest(repository: String, pool: String): String = "$repository\n$pool"
