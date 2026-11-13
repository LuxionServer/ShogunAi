package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.DEFAULT_DEVICE_WAIT_MINUTES
import java.time.Duration

enum class PoolSlotAction(val key: String) { ACQUIRE("acquire"), RELEASE("release") }

data class PoolSlotCommand(
    val action: PoolSlotAction,
    val pool: String,
    val path: String? = null,
    val wait: Duration? = null,
    val json: Boolean = false,
) : CliCommand, JsonOutput {
    override fun withJson() = copy(json = true)
}

internal val POOL_SLOT_ACTIONS = PoolSlotAction.entries.map { it.key }.toSet()

internal fun parsePoolSlot(rest: List<String>): PoolSlotCommand {
    val action = PoolSlotAction.entries.first { it.key == rest.first() }
    val positional = mutableListOf<String>()
    var waits = false
    var minutes = DEFAULT_DEVICE_WAIT_MINUTES
    val remaining = ArrayDeque(rest.drop(1))
    while (remaining.isNotEmpty()) {
        when (val argument = remaining.removeFirst()) {
            "--wait" -> waits = true
            "--wait-timeout" -> {
                val value = remaining.removeFirstOrNull()
                minutes = value?.toLongOrNull()?.takeIf { it > 0 }
                    ?: throw UsageException("--wait-timeout needs a positive whole number of minutes, got '${value.orEmpty()}'")
                waits = true
            }
            else -> {
                if (argument.startsWith("-")) throw UsageException("unknown option '$argument' for 'pool ${action.key}'")
                positional += argument
            }
        }
    }
    if (waits && action != PoolSlotAction.ACQUIRE) throw UsageException("--wait and --wait-timeout only apply to 'pool acquire'")
    val pool = positional.firstOrNull() ?: throw UsageException("'pool ${action.key}' needs the name of a pool declared in .shogunai.json")
    if (positional.size > 2) throw UsageException("'pool ${action.key}' takes a pool name and at most one path")
    return PoolSlotCommand(action, pool, positional.getOrNull(1), Duration.ofMinutes(minutes).takeIf { waits })
}
