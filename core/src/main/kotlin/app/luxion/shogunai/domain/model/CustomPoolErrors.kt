package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant

class CustomPoolFailure(val problem: CustomPoolProblem) : WorktreeError(problem.message)

data class CustomPoolHolderNote(val holder: String, val slot: Int, val expiresAt: Long, val agentWorking: Boolean) {
    val text: String
        get() = "$holder holds slot $slot, " +
            if (agentWorking) "its agent is working" else "leased until ${Instant.ofEpochMilli(expiresAt).toLocalClockTime()}"
}

enum class CustomPoolCommandKind(val field: String) { ACQUIRE("acquire"), RELEASE("release") }

sealed interface CustomPoolProblem {
    val message: String

    data class UnknownPool(val pool: String, val declared: List<String>) : CustomPoolProblem {
        override val message: String
            get() = "The repository declares no pool \"$pool\" in .shogunai.json" +
                if (declared.isEmpty()) "; it declares no pools" else "; it declares ${declared.joinToString()}"
    }

    data class NotApproved(val pool: String, val repositoryPath: String) : CustomPoolProblem {
        override val message: String
            get() = "The commands of pool \"$pool\" aren't approved for $repositoryPath; review and approve them with `shogun trust`"
    }

    data class Full(val pool: String, val max: Int, val holders: List<CustomPoolHolderNote>, val waiting: Int) : CustomPoolProblem {
        val summary: String
            get() = "Pool \"$pool\" is full (${holders.size} of $max in use)" +
                holders.joinToString("") { "; ${it.text}" } +
                when (waiting) {
                    0 -> ""
                    1 -> "; 1 worktree is waiting"
                    else -> "; $waiting worktrees are waiting"
                }

        override val message: String
            get() = "$summary. Wait for a slot with --wait, or free one with `shogun pool release $pool` in the worktree that holds it"
    }

    data class WaitTimedOut(val waited: Duration, val blocked: Full) : CustomPoolProblem {
        override val message: String
            get() = "Waited ${waited.toWaitText()} for a slot of pool \"${blocked.pool}\": ${blocked.summary}"
    }

    data class CommandFailed(
        val pool: String,
        val kind: CustomPoolCommandKind,
        val worktreePath: String,
        val command: List<String>,
        val exitCode: Int?,
        val errorOutput: String,
    ) : CustomPoolProblem {
        override val message: String
            get() = "The ${kind.field} command of pool \"$pool\" failed" + (if (exitCode != null) " (exit code $exitCode)" else "") +
                " for ${worktreePath.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')}: ${command.joinToString(" ")}" +
                if (errorOutput.isNotBlank()) "\n$errorOutput" else ""
    }
}
