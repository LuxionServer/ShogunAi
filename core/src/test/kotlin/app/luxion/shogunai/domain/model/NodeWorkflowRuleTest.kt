package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeWorkflowRuleTest {

    private val worktree = WorkflowContext(inMainCheckout = false, runnerAvailable = true, emulatorProvider = false, simulatorProvider = false)

    @Test
    fun `a repository that declares node gets the rule with its manager at session start`() {
        val context = worktree.copy(nodeProvider = true, nodeProject = NodeProject(NodePackageManager.PNPM, "pnpm-lock.yaml"))

        val rule = WorkflowRules.sessionStartRules(context).single { it.id == "resources.node" }

        assertEquals(WorkflowTopic.RESOURCES, rule.topic)
        assertTrue("`pnpm install` after `package.json` or `pnpm-lock.yaml` change" in rule.text)
    }

    @Test
    fun `the rule applies only with the provider`() {
        assertTrue("resources.node" in WorkflowRules.rulesFor(worktree).inapplicable.map { it.id })
        assertTrue("resources.node" !in WorkflowRules.sessionStartRules(worktree).map { it.id })
    }

    @Test
    fun `the guide shows the rule with the repository's manager`() {
        val rules = WorkflowRules.rulesFor(worktree.copy(nodeProvider = true), WorkflowTopic.RESOURCES).applicable

        assertTrue("the package manager's install" in rules.single { it.id == "resources.node" }.text)
    }
}
