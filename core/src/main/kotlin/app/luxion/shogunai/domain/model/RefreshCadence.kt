package app.luxion.shogunai.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class RefreshCadence(
    val focusedCheck: Duration,
    val unfocusedCheck: Duration,
    val devices: Duration,
    val machineView: Duration,
    val summaries: Duration,
) {
    companion object {
        val LIGHT = RefreshCadence(
            focusedCheck = 10.seconds,
            unfocusedCheck = 60.seconds,
            devices = 30.seconds,
            machineView = 15.seconds,
            summaries = 120.seconds,
        )
        val BALANCED = RefreshCadence(
            focusedCheck = 3.seconds,
            unfocusedCheck = 15.seconds,
            devices = 10.seconds,
            machineView = 5.seconds,
            summaries = 30.seconds,
        )
        val PER_WORKTREE = RefreshCadence(
            focusedCheck = 3.seconds,
            unfocusedCheck = 5.seconds,
            devices = 10.seconds,
            machineView = 5.seconds,
            summaries = 30.seconds,
        )
    }
}
