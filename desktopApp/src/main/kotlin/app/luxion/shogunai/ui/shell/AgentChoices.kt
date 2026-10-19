package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AgentChoices(
    initial: Map<String, String> = emptyMap(),
    private val persist: ((Map<String, String>) -> Map<String, String>) -> Unit = {},
) {
    var chosen by mutableStateOf(initial)
        private set

    fun update(transform: (Map<String, String>) -> Map<String, String>) {
        val updated = transform(chosen)
        if (updated == chosen) return
        chosen = updated
        persist(transform)
    }
}
