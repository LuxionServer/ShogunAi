package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowRulesTest {

    private val worktree = WorkflowContext(inMainCheckout = false, runnerAvailable = true, emulatorProvider = true, simulatorProvider = true, composeProvider = true, desktopAppProvider = true, flutterProvider = true)
    private val bare = WorkflowContext(inMainCheckout = true, runnerAvailable = false, emulatorProvider = false, simulatorProvider = false)

    private fun ids(rules: List<WorkflowRule>) = rules.map { it.id }

    @Test
    fun `every rule appears exactly where its condition holds`() {
        listOf(worktree, bare, worktree.copy(runnerAvailable = false), bare.copy(emulatorProvider = true)).forEach { context ->
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
            listOf("resources.emulator", "resources.simulator", "resources.flutter", "resources.compose", "resources.desktop-app"),
            ids(selection.inapplicable),
        )
    }

    @Test
    fun `the session start subset fits twelve lines in the richest context`() {
        val lines = WorkflowRules.sessionStartLines(worktree)

        assertTrue(lines.size <= 12, lines.joinToString("\n"))
        assertEquals(WorkflowRules.PRECEDENCE, lines.first())
        assertEquals(WorkflowRules.GUIDE_POINTER, lines.last())
    }

    private val backendWorktree = WorkflowContext(inMainCheckout = false, runnerAvailable = true, emulatorProvider = false, simulatorProvider = false, composeProvider = true)

    @Test
    fun `neither the desktop turn nor the worktree base rule is in the session start`() {
        listOf(worktree, bare, backendWorktree).forEach { context ->
            val sessionIds = ids(WorkflowRules.sessionStartRules(context))
            assertFalse("resources.desktop-turn" in sessionIds, sessionIds.toString())
            assertFalse("worktrees.base" in sessionIds, sessionIds.toString())
            val text = WorkflowRules.sessionStartLines(context).joinToString("\n")
            assertFalse("--from" in text, text)
            assertFalse("git log -1" in text, text)
        }
    }

    @Test
    fun `the guide keeps the desktop turn and worktree base rules`() {
        val turn = WorkflowRules.rulesFor(bare, WorkflowTopic.RESOURCES).applicable.single { it.id == "resources.desktop-turn" }
        listOf("shogun desktop acquire --wait", "shogun desktop release", "background", "`orca computer`").forEach {
            assertTrue(it in turn.text, it)
        }
        val base = WorkflowRules.rulesFor(worktree, WorkflowTopic.WORKTREES).applicable.single { it.id == "worktrees.base" }
        assertTrue("--from <ref>" in base.text && "git log -1" in base.text)
    }

    @Test
    fun `a backend session start has no desktop, emulator or simulator rules`() {
        val text = WorkflowRules.sessionStartLines(backendWorktree).joinToString("\n").lowercase()

        listOf("desktop", "computer use", "emulator", "simulator", "headless").forEach { assertFalse(it in text, it) }
    }

    private fun flutterRule(context: WorkflowContext): String =
        WorkflowRules.sessionStartRules(context).single { it.id == "resources.flutter" }.text

    @Test
    fun `the flutter rule names both devices when both providers are declared`() {
        val text = flutterRule(worktree)

        assertTrue("`flutter pub get`" in text)
        assertTrue("`shogun emulator start` and then `flutter run -d \"\$ANDROID_SERIAL\"`" in text, text)
        assertTrue("`shogun simulator start` and then `flutter run -d \"\$IOS_SIMULATOR_UDID\"`" in text, text)
        assertTrue("`flutter test integration_test`" in text && "`flutter drive`" in text)
        assertTrue("`flutter emulators --launch`" in text)
    }

    @Test
    fun `the flutter rule names only the declared devices`() {
        val text = flutterRule(worktree.copy(simulatorProvider = false))

        assertTrue("ANDROID_SERIAL" in text)
        assertFalse("IOS_SIMULATOR_UDID" in text || "simulator" in text, text)

        val withoutDevices = flutterRule(worktree.copy(emulatorProvider = false, simulatorProvider = false))
        assertFalse(" -d " in withoutDevices, withoutDevices)
    }

    @Test
    fun `the flutter rule uses fvm when the repository pins its version`() {
        val text = flutterRule(worktree.copy(flutterUsesFvm = true))

        assertTrue("`fvm flutter pub get`" in text)
        assertTrue("`fvm flutter run -d \"\$ANDROID_SERIAL\"`" in text)
        assertTrue("use `fvm flutter` for every Flutter command" in text)
    }

    @Test
    fun `the flutter rule applies only with the provider`() {
        assertTrue("resources.flutter" in ids(WorkflowRules.rulesFor(worktree.copy(flutterProvider = false)).inapplicable))
    }

    @Test
    fun `the compose rule applies only with the provider`() {
        assertTrue("resources.compose" in ids(WorkflowRules.rulesFor(worktree, WorkflowTopic.RESOURCES).applicable))
        assertTrue("resources.compose" in ids(WorkflowRules.rulesFor(worktree.copy(composeProvider = false)).inapplicable))
    }

    @Test
    fun `the desktop app rule has a one line session start form and the full text in the guide`() {
        val lines = WorkflowRules.sessionStartLines(worktree)
        val line = lines.single { "shogun headless start" in it }
        assertTrue("shogun guide resources" in line, line)
        assertFalse("shogun headless capture" in line, line)

        val rule = WorkflowRules.rulesFor(worktree, WorkflowTopic.RESOURCES).applicable.single { it.id == "resources.desktop-app" }
        listOf("shogun headless start", "shogun headless capture", "take_screenshot", "restart", "menu bar", "shogun desktop acquire --wait").forEach {
            assertTrue(it in rule.text, it)
        }
        assertFalse("resources.desktop-app" in ids(WorkflowRules.sessionStartRules(worktree.copy(desktopAppProvider = false))))
    }

    @Test
    fun `session start without a runner has no rule about opening or messaging sessions`() {
        val text = WorkflowRules.sessionStartLines(worktree.copy(runnerAvailable = false)).joinToString("\n")

        assertFalse("--herdr" in text)
        assertFalse("messages" in text)
    }

    @Test
    fun `with Orca in use the open rule names --orca everywhere`() {
        val orca = worktree.copy(runner = RunnerId.ORCA)
        val text = WorkflowRules.sessionStartLines(orca).joinToString("\n")

        assertTrue("`shogun open <id> --orca`" in text, text)
        assertFalse("--herdr" in text)
        assertTrue(WorkflowRules.forRunner(RunnerId.ORCA).none { "--herdr" in it.text })
        assertEquals(WorkflowRules.all, WorkflowRules.forRunner(RunnerId.HERDR))
    }

    @Test
    fun `an unavailable runner leaves its rules marked with the condition`() {
        val inapplicable = WorkflowRules.rulesFor(worktree.copy(runner = RunnerId.ORCA, runnerAvailable = false), WorkflowTopic.PARALLEL).inapplicable

        assertEquals(listOf("parallel.open", "parallel.messages"), ids(inapplicable))
        assertTrue(inapplicable.all { it.condition == WorkflowCondition.RUNNER })
        assertTrue("--orca" in inapplicable.first().text)
    }

    @Test
    fun `every topic has rules and no rule is about personal preferences`() {
        WorkflowTopic.entries.forEach { topic -> assertTrue(WorkflowRules.all.any { it.topic == topic }, topic.id) }
        val forbidden = listOf("commit message", "pull request format", "reviewer", "code style", "ticket", "delegat")
        WorkflowRules.all.forEach { rule -> forbidden.forEach { assertFalse(it in rule.text.lowercase(), rule.id) } }
    }

    @Test
    fun `rule ids are unique and start with their topic`() {
        assertEquals(WorkflowRules.all.size, ids(WorkflowRules.all).toSet().size)
        WorkflowRules.all.forEach { assertTrue(it.id.startsWith(it.topic.id + "."), it.id) }
    }
}
