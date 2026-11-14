package app.luxion.shogunai.domain.model

data class NotificationText(val title: String, val body: String, val openLabel: String)

object AgentNotificationText {

    fun of(notification: AgentNotification, language: AppLanguage): NotificationText {
        val words = if (language == AppLanguage.SPANISH) SPANISH else ENGLISH
        val single = notification.items.singleOrNull()
        val title = if (single != null) words.single(single) else words.counts(notification.blockedCount, notification.doneCount)
        val body = if (single != null) {
            place(single)
        } else {
            notification.items.joinToString("\n") { "${it.agentName} · ${place(it)}" }
        }
        return NotificationText(title, body, words.open)
    }

    private fun place(item: NotificationItem): String = "${item.worktree.worktreeName} · ${item.worktree.projectName}"

    private interface Words {
        val open: String
        fun single(item: NotificationItem): String
        fun blocked(count: Int): String
        fun done(count: Int, alone: Boolean): String

        fun counts(blocked: Int, done: Int): String = when {
            done == 0 -> blocked(blocked)
            blocked == 0 -> done(done, alone = true)
            else -> "${blocked(blocked)}, ${done(done, alone = false)}"
        }
    }

    private val ENGLISH = object : Words {
        override val open = "Open"
        override fun single(item: NotificationItem) =
            if (item.state == AgentState.BLOCKED) "${item.agentName} needs you" else "${item.agentName} is done"
        override fun blocked(count: Int) = if (count == 1) "1 agent needs you" else "$count agents need you"
        override fun done(count: Int, alone: Boolean) = when {
            alone -> "$count agents are done"
            count == 1 -> "1 is done"
            else -> "$count are done"
        }
    }

    private val SPANISH = object : Words {
        override val open = "Abrir"
        override fun single(item: NotificationItem) =
            if (item.state == AgentState.BLOCKED) "${item.agentName} te necesita" else "${item.agentName} ha terminado"
        override fun blocked(count: Int) = if (count == 1) "1 agente te necesita" else "$count agentes te necesitan"
        override fun done(count: Int, alone: Boolean) = when {
            alone -> "$count agentes han terminado"
            count == 1 -> "1 ha terminado"
            else -> "$count han terminado"
        }
    }
}
