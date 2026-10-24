package app.luxion.shogunai.domain.model

enum class WorkflowTopic(val id: String) {
    WORKTREES("worktrees"),
    PARALLEL("parallel"),
    RESOURCES("resources"),
    CLEANUP("cleanup"),
    ;

    companion object {
        fun parse(id: String): WorkflowTopic? = entries.firstOrNull { it.id == id }
    }
}

enum class WorkflowCondition(val label: String) {
    ALWAYS("always"),
    MAIN_CHECKOUT("in the main checkout"),
    WORKTREE("in a worktree"),
    HERDR("when Herdr is available"),
    EMULATOR_PROVIDER("when the repository declares the emulator provider"),
    SIMULATOR_PROVIDER("when the repository declares the ios-simulator provider"),
    COMPOSE_PROVIDER("when the repository declares the docker-compose provider"),
}

data class WorkflowRule(
    val id: String,
    val topic: WorkflowTopic,
    val text: String,
    val condition: WorkflowCondition = WorkflowCondition.ALWAYS,
    val inSessionStart: Boolean = false,
)

data class WorkflowContext(
    val inMainCheckout: Boolean,
    val herdrAvailable: Boolean,
    val emulatorProvider: Boolean,
    val simulatorProvider: Boolean,
    val composeProvider: Boolean = false,
) {
    fun holds(condition: WorkflowCondition): Boolean = when (condition) {
        WorkflowCondition.ALWAYS -> true
        WorkflowCondition.MAIN_CHECKOUT -> inMainCheckout
        WorkflowCondition.WORKTREE -> !inMainCheckout
        WorkflowCondition.HERDR -> herdrAvailable
        WorkflowCondition.EMULATOR_PROVIDER -> emulatorProvider
        WorkflowCondition.SIMULATOR_PROVIDER -> simulatorProvider
        WorkflowCondition.COMPOSE_PROVIDER -> composeProvider
    }
}

data class WorkflowSelection(val applicable: List<WorkflowRule>, val inapplicable: List<WorkflowRule>)

object WorkflowRules {

    const val PRECEDENCE =
        "ShogunAi's workflow defaults; the project's AGENTS.md and the person's instructions take precedence over them."

    const val GUIDE_POINTER = "More detail: run 'shogun guide'."

    val all: List<WorkflowRule> = listOf(
        WorkflowRule(
            "worktrees.main-checkout",
            WorkflowTopic.WORKTREES,
            "This is the main checkout: create a worktree with `shogun new <id>` for each change and never change this checkout's branch.",
            WorkflowCondition.MAIN_CHECKOUT,
            inSessionStart = true,
        ),
        WorkflowRule(
            "worktrees.keep-branch",
            WorkflowTopic.WORKTREES,
            "Never change the branch of the worktree a session starts in: no `git switch`, no `git checkout -b`.",
            WorkflowCondition.WORKTREE,
            inSessionStart = true,
        ),
        WorkflowRule(
            "worktrees.create",
            WorkflowTopic.WORKTREES,
            "Every change goes in its own worktree created with `shogun new <id>`, `shogun new <id> --fix` for a fix/ branch, " +
                "`shogun new <id> --fetch` to refresh origin first, or `shogun new --branch <branch>` for an existing branch.",
        ),
        WorkflowRule(
            "worktrees.base",
            WorkflowTopic.WORKTREES,
            "`shogun new <id>` branches from the current worktree's HEAD; use `shogun new <id> --from <ref>` to branch from something else, " +
                "such as an epic's branch, and check the new worktree's `git log -1`.",
            inSessionStart = true,
        ),
        WorkflowRule(
            "worktrees.handoff",
            WorkflowTopic.WORKTREES,
            "Give the person the path of the new worktree so they open a session there, and consult `shogun status` " +
                "before saying which worktrees exist.",
        ),
        WorkflowRule(
            "parallel.open",
            WorkflowTopic.PARALLEL,
            "Open another session with `shogun open <id> --herdr`.",
            WorkflowCondition.HERDR,
            inSessionStart = true,
        ),
        WorkflowRule(
            "parallel.open-path",
            WorkflowTopic.PARALLEL,
            "Open another session in the path `shogun new <id>` prints, from the person's own terminal.",
        ),
        WorkflowRule(
            "parallel.messages",
            WorkflowTopic.PARALLEL,
            "Talk to an open session with messages, never by typing into its terminal.",
            WorkflowCondition.HERDR,
            inSessionStart = true,
        ),
        WorkflowRule(
            "resources.own",
            WorkflowTopic.RESOURCES,
            "Each worktree uses its own emulator and simulator; stop them when done with `shogun emulator stop` and `shogun simulator stop`.",
        ),
        WorkflowRule(
            "resources.emulator",
            WorkflowTopic.RESOURCES,
            "Run `shogun emulator start` before every use of the worktree's Android emulator (installing the app, running instrumented tests): " +
                "it may have been released to another worktree since the session began, and the command re-acquires one and prints the ANDROID_SERIAL to use. " +
                "`shogun emulator status` shows it.",
            WorkflowCondition.EMULATOR_PROVIDER,
            inSessionStart = true,
        ),
        WorkflowRule(
            "resources.adb",
            WorkflowTopic.RESOURCES,
            "The adb server is shared with other worktrees' agents: never run 'adb kill-server' or 'adb start-server', " +
                "and don't pass -s, -e or -d to adb, so every command goes to ANDROID_SERIAL.",
            WorkflowCondition.EMULATOR_PROVIDER,
            inSessionStart = true,
        ),
        WorkflowRule(
            "resources.simulator",
            WorkflowTopic.RESOURCES,
            "Boot the worktree's iOS simulator with `shogun simulator start` and build with " +
                "xcodebuild -destination \"id=\$IOS_SIMULATOR_UDID\"; `shogun simulator status` shows it.",
            WorkflowCondition.SIMULATOR_PROVIDER,
            inSessionStart = true,
        ),
        WorkflowRule(
            "resources.compose",
            WorkflowTopic.RESOURCES,
            "Start and build the worktree's Docker Compose services with `shogun compose up` (`--build` to rebuild) and `shogun compose build`, " +
                "never with a hand-written project name, so they use its COMPOSE_PROJECT_NAME and ports; " +
                "take them down with `shogun compose down <worktree>`.",
            WorkflowCondition.COMPOSE_PROVIDER,
        ),
        WorkflowRule(
            "resources.caches",
            WorkflowTopic.RESOURCES,
            "Run `shogun gradle stop` and `shogun xcode clean` showing what they free, and ask first.",
        ),
        WorkflowRule(
            "cleanup.ask",
            WorkflowTopic.CLEANUP,
            "Ask before running `shogun clean --yes`; plain `shogun clean` only lists what it would remove.",
            inSessionStart = true,
        ),
        WorkflowRule(
            "cleanup.options",
            WorkflowTopic.CLEANUP,
            "`shogun clean` also takes --stop-emulators, --stop-gradle-daemons, --delete-simulators, --delete-derived-data " +
                "and --delete-branches; each removes more than the worktrees, so say which ones you will pass.",
        ),
    )

    fun rulesFor(context: WorkflowContext, topic: WorkflowTopic? = null): WorkflowSelection {
        val (applicable, inapplicable) = all
            .filter { topic == null || it.topic == topic }
            .partition { context.holds(it.condition) }
        return WorkflowSelection(applicable, inapplicable)
    }

    fun sessionStartRules(context: WorkflowContext): List<WorkflowRule> =
        rulesFor(context).applicable.filter { it.inSessionStart }

    fun sessionStartLines(context: WorkflowContext): List<String> =
        listOf(PRECEDENCE) + sessionStartRules(context).map { it.text } + GUIDE_POINTER
}
