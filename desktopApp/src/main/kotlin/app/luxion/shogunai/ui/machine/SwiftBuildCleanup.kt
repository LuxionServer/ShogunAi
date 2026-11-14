package app.luxion.shogunai.ui.machine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.SwiftBuildDeletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SwiftBuildCleanup(
    private val scope: CoroutineScope,
    private val deleteUnused: suspend () -> Result<SwiftBuildDeletion>,
    private val afterDeletion: suspend () -> Unit,
    private val onFailure: (Throwable, retry: () -> Unit) -> Unit,
) {
    var isDeleting by mutableStateOf(false)
        private set

    var report by mutableStateOf<SwiftBuildDeletion?>(null)
        private set

    fun deleteUnused() {
        if (isDeleting) return
        isDeleting = true
        scope.launch {
            try {
                deleteUnused.invoke()
                    .onSuccess { report = it }
                    .onFailure { onFailure(it, ::deleteUnused) }
                afterDeletion()
            } finally {
                isDeleting = false
            }
        }
    }

    fun dismissReport() {
        report = null
    }
}
