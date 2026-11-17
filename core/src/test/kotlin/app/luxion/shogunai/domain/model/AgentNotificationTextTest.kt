package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentNotificationTextTest {
    private val login = NotifiedWorktree("api", "api", "/home/dev/projects/TASK-1", "feature/login")
    private val search = NotifiedWorktree("web", "web", "/home/dev/projects/TASK-2", "feature/search")

    private fun notification(vararg states: AgentState) =
        AgentNotification(states.mapIndexed { index, state -> NotificationItem(if (index % 2 == 0) login else search, "Claude", state) })

    private fun title(language: AppLanguage, vararg states: AgentState) = AgentNotificationText.of(notification(*states), language).title

    @Test
    fun `one agent names its state, worktree and project`() {
        val blocked = AgentNotificationText.of(notification(AgentState.BLOCKED), AppLanguage.ENGLISH)

        assertEquals(NotificationText("Claude needs you", "feature/login · api", "Open"), blocked)
        assertEquals("Claude is done", title(AppLanguage.ENGLISH, AgentState.DONE))
        assertEquals("Claude te necesita", title(AppLanguage.SPANISH, AgentState.BLOCKED))
        assertEquals("Claude ha terminado", title(AppLanguage.SPANISH, AgentState.DONE))
        assertEquals("Abrir", AgentNotificationText.of(notification(AgentState.DONE), AppLanguage.SPANISH).openLabel)
    }

    @Test
    fun `several agents are counted and listed`() {
        val text = AgentNotificationText.of(notification(AgentState.BLOCKED, AgentState.BLOCKED, AgentState.DONE), AppLanguage.ENGLISH)

        assertEquals("2 agents need you, 1 is done", text.title)
        assertEquals("Claude · feature/login · api\nClaude · feature/search · web\nClaude · feature/login · api", text.body)
    }

    @Test
    fun `counts agree in number in English`() {
        assertEquals("2 agents need you", title(AppLanguage.ENGLISH, AgentState.BLOCKED, AgentState.BLOCKED))
        assertEquals("2 agents are done", title(AppLanguage.ENGLISH, AgentState.DONE, AgentState.DONE))
        assertEquals("1 agent needs you, 2 are done", title(AppLanguage.ENGLISH, AgentState.BLOCKED, AgentState.DONE, AgentState.DONE))
    }

    @Test
    fun `counts agree in number in Spanish`() {
        assertEquals("2 agentes te necesitan", title(AppLanguage.SPANISH, AgentState.BLOCKED, AgentState.BLOCKED))
        assertEquals("2 agentes han terminado", title(AppLanguage.SPANISH, AgentState.DONE, AgentState.DONE))
        assertEquals("2 agentes te necesitan, 1 ha terminado", title(AppLanguage.SPANISH, AgentState.BLOCKED, AgentState.BLOCKED, AgentState.DONE))
        assertEquals("1 agente te necesita, 2 han terminado", title(AppLanguage.SPANISH, AgentState.BLOCKED, AgentState.DONE, AgentState.DONE))
    }

    @Test
    fun `the preferences allow only the switched states`() {
        val defaults = NotificationPreferences()

        assertTrue(defaults.allows(AgentState.BLOCKED))
        assertFalse(defaults.allows(AgentState.DONE))
        assertFalse(defaults.allows(AgentState.WORKING))
        assertTrue(defaults.anyOn)
        assertTrue(NotificationPreferences(blocked = false, done = true).anyOn)
        assertFalse(NotificationPreferences(blocked = false, done = false).anyOn)
    }
}
