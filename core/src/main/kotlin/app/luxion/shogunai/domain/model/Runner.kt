package app.luxion.shogunai.domain.model

val RunnerId.flag: String get() = name.lowercase()

enum class RunnerOutcome { OPENED, STARTED_AGENT, FOCUSED }

data class RunnerOpening(
    val runner: RunnerId,
    val outcome: RunnerOutcome,
    val handle: String,
    val agent: String?,
    val sessionName: String? = null,
    val emulator: String? = null,
    val simulator: String? = null,
    val devicesSkipped: Boolean = false,
)

data class GroupRunnerOpening(val alias: String, val path: String, val result: Result<RunnerOpening>)

fun runnerInUse(chosen: RunnerId?, installed: (RunnerId) -> Boolean): RunnerId =
    chosen?.takeIf(installed) ?: RunnerId.entries.firstOrNull(installed) ?: chosen ?: RunnerId.HERDR

fun runnersInUseOrder(inUse: RunnerId): List<RunnerId> = listOf(inUse) + RunnerId.entries.filter { it != inUse }
