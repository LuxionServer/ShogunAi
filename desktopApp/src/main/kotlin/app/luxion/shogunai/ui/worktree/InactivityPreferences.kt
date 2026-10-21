package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.INACTIVE_AFTER_DAYS_RANGE
import java.time.Duration

class InactivityPreferences(private val repository: ShellPreferenceRepository) {
    var days by mutableStateOf(repository.loadInactiveAfterDays())
        private set

    val threshold: Duration get() = Duration.ofDays(days.toLong())

    fun change(days: Int): Boolean {
        if (days !in INACTIVE_AFTER_DAYS_RANGE) return false
        this.days = days
        repository.saveInactiveAfterDays(days)
        return true
    }
}
