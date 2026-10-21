package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowRulesTest {

    private val worktree = WorkflowContext(inMainCheckout = false, herdrAvailable = true, emulatorProvider = true, simulatorProvider = true)
    private val bare = WorkflowContext(inMainCheckout = true, herdrAvailable = false, emulatorProvider = false, simulatorProvider = false)

    private fun ids(rules: List<WorkflowRule>) = rules.map { it.id }

    @Test
    fun `every rule appears exactly where its condition holds`() {
        listOf(worktree, bare, worktree.copy(herdrAvailable = false), bare.copy(emulatorProvider = true)).forEach { context ->
            val selection = WorkflowRules.rulesFor(context)
            selection.applicable.forEach { assertTrue(context.holds(it.condition), it.id) }
            selection.inapplicable.forEach { assertFalse(context.holds(it.condition), it.id) }
            assertEquals(WorkflowRules.all.size, selection.applicable.size + selection.inapplicable.size)
        }
    }

    @Test
    fun `main checkout and worktree rules are exclusive`() {
        assertTrue("worktrees.main-checkout" in ids(WorkflowRules.rulesFor(bare).applicable))
        assertFalse("worktrees.keep-branch" in ids(WorkflowRules.rulesFor(bare).applicable))
        assertTrue("worktrees.keep-branch" in ids(WorkflowRules.rulesFor(worktree).applicable))
        assertFalse("worktrees.main-checkout" in ids(WorkflowRules.rulesFor(worktree).applicable))
    }

    @Test
    fun `a topic narrows the selection`() {
        val selection = WorkflowRules.rulesFor(worktree, WorkflowTopic.PARALLEL)

        assertTrue((selection.applicable + selection.inapplicable).all { it.topic == WorkflowTopic.PARALLEL })
    }

    @Test
    fun `rules of unavailable resources are inapplicable`() {
        val selection = WorkflowRules.rulesFor(bare, WorkflowTopic.RESOURCES)

        assertEquals(
            listOf("resources.emulator", "resources.adb", "resources.simulator"),
            ids(selection.inapplicable),
        )
    }

    @Test
    fun `the session start subset fits ten lines in the richest context`() {
        val lines = WorkflowRules.sessionStartLines(worktree)

        assertTrue(lines.size <= 10, lines.joinToString("\n"))
        assertEquals(WorkflowRules.PRECEDENCE, lines.first())
        assertEquals(WorkflowRules.GUIDE_POINTER, lines.last())
    }

    @Test
    fun `session start without Herdr has no rule about opening or messaging sessions`() {
        val text = WorkflowRules.sessionStartLines(worktree.copy(herdrAvailable = false)).joinToString("\n")

        assertFalse("--herdr" in text)
        assertFalse("messages" in text)
    }

    @Test
    fun `every topic has rules and no rule is about personal preferences`() {
        WorkflowTopic.entries.forEach { topic -> assertTrue(WorkflowRules.all.any { it.topic == topic }, topic.id) }
        val forbidden = listOf("commit message", "pull request", "code style", "ticket", "delegat")
        WorkflowRules.all.forEach { rule -> forbidden.forEach { assertFalse(it in rule.text.lowercase(), rule.id) } }
    }

    @Test
    fun `rule ids are unique and start with their topic`() {
        assertEquals(WorkflowRules.all.size, ids(WorkflowRules.all).toSet().size)
        WorkflowRules.all.forEach { assertTrue(it.id.startsWith(it.topic.id + "."), it.id) }
    }
}
