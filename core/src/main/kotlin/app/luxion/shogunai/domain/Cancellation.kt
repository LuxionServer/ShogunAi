package app.luxion.shogunai.domain

import kotlin.coroutines.cancellation.CancellationException

inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
