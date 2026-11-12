package app.luxion.shogunai.domain.model

import kotlin.coroutines.cancellation.CancellationException

inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
