package app.luxion.shogunai.engine

import app.luxion.shogunai.domain.usecase.WorktreeEnvironmentCache
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

data class EngineOptions(
    val clock: () -> Instant = Instant::now,
    val pauseWhileWaiting: suspend (Duration) -> Unit = { delay(it.toMillis()) },
    val pauseWhileStartingApp: suspend (Long) -> Unit = { delay(it) },
    val environmentCache: WorktreeEnvironmentCache? = null,
)
