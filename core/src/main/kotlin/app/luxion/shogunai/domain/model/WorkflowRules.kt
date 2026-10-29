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
    DESKTOP_APP_PROVIDER("when the repository declares the desktop-app provider, on macOS"),
    FLUTTER_PROVIDER("when the repository declares the flutter provider"),
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
    val desktopAppProvider: Boolean = false,
    val flutterProvider: Boolean = false,
    val flutterUsesFvm: Boolean = false,
) {
    fun holds(condition: WorkflowCondition): Boolean = when (condition) {
        WorkflowCondition.ALWAYS -> true
        WorkflowCondition.MAIN_CHECKOUT -> inMainCheckout
        WorkflowCondition.WORKTREE -> !inMainCheckout
        WorkflowCondition.HERDR -> herdrAvailable
        WorkflowCondition.EMULATOR_PROVIDER -> emulatorProvider
        WorkflowCondition.SIMULATOR_PROVIDER -> simulatorProvider
        WorkflowCondition.COMPOSE_PROVIDER -> composeProvider
        WorkflowCondition.DESKTOP_APP_PROVIDER -> desktopAppProvider
        WorkflowCondition.FLUTTER_PROVIDER -> flutterProvider
    }
}

data class WorkflowSelection(val applicable: List<WorkflowRule>, val inapplicable: List<WorkflowRule>)

object WorkflowRules {

    const val PRECEDENCE =
        "ShogunAi's workflow defaults; the project's AGENTS.md and the person's instructions take precedence over them."

    const val GUIDE_POINTER = "More detail: run 'shogun guide'."

    private const val FLUTTER_RULE_ID = "resources.flutter"

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
            "worktrees.pull-request-base",
            WorkflowTopic.WORKTREES,
            "Open a pull request against the branch `shogun base` prints, passing it explicitly " +
                "(`gh pr create --base \"\$(shogun base)\"`); if none is recorded, set it with `shogun base --set <branch>` " +
                "once the base is clear, or ask the person.",
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
                "If it fails because the emulator pool is full, retry with `shogun emulator start --wait` run in the background, " +
                "since it can wait up to 30 minutes for one. `shogun emulator status` shows it. " +
                "The adb server is shared with other worktrees' agents: never run 'adb kill-server' or 'adb start-server', " +
                "and don't pass -s, -e or -d to adb, so every command goes to ANDROID_SERIAL.",
            WorkflowCondition.EMULATOR_PROVIDER,
            inSessionStart = true,
        ),
        WorkflowRule(
            "resources.simulator",
            WorkflowTopic.RESOURCES,
            "Boot the worktree's iOS simulator with `shogun simulator start` and build with " +
                "xcodebuild -destination \"id=\$IOS_SIMULATOR_UDID\"; `shogun simulator status` shows it. " +
                "If it fails because the simulator pool is full, retry with `shogun simulator start --wait` run in the background, " +
                "since it can wait up to 30 minutes for one.",
            WorkflowCondition.SIMULATOR_PROVIDER,
            inSessionStart = true,
        ),
        WorkflowRule(
            FLUTTER_RULE_ID,
            WorkflowTopic.RESOURCES,
            flutterRuleText(usesFvm = false, android = true, ios = true),
            WorkflowCondition.FLUTTER_PROVIDER,
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
            "resources.desktop-app",
            WorkflowTopic.RESOURCES,
            "Check the desktop app without computer use: `shogun headless start` runs it hidden, drive it with the Compose Hot Reload MCP " +
                "(re-read the semantic tree after each view change; run `shogun headless start` again after its `restart` to hide it), " +
                "and see it with `shogun headless capture`, never the MCP's take_screenshot. " +
                "Keyboard shortcuts, native menus, the menu bar and system dialogs still need the desktop: take its turn first with `shogun desktop acquire --wait`.",
            WorkflowCondition.DESKTOP_APP_PROVIDER,
            inSessionStart = true,
        ),
        WorkflowRule(
            "resources.desktop-turn",
            WorkflowTopic.RESOURCES,
            "Before driving the desktop with computer use, take the machine's desktop turn with `shogun desktop acquire --wait`, " +
                "run in the background or with a long enough shell timeout, since it can wait up to 30 minutes; " +
                "run it again to renew the turn during a long check, before it expires, and give it back with `shogun desktop release` as soon as you are done.",
            inSessionStart = true,
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
        return WorkflowSelection(applicable.map { it.adaptedTo(context) }, inapplicable)
    }

    private fun WorkflowRule.adaptedTo(context: WorkflowContext): WorkflowRule =
        if (id == FLUTTER_RULE_ID) {
            copy(text = flutterRuleText(context.flutterUsesFvm, context.emulatorProvider, context.simulatorProvider))
        } else {
            this
        }

    private fun flutterRuleText(usesFvm: Boolean, android: Boolean, ios: Boolean): String {
        val flutter = if (usesFvm) "fvm flutter" else "flutter"
        val devices = listOfNotNull(
            "`shogun emulator start` and then `$flutter run -d \"\$ANDROID_SERIAL\"`".takeIf { android },
            "`shogun simulator start` and then `$flutter run -d \"\$IOS_SIMULATOR_UDID\"`".takeIf { ios },
        )
        return listOfNotNull(
            "Run `$flutter pub get` after `pubspec.yaml` or `pubspec.lock` change" +
                if (usesFvm) ", and use `fvm flutter` for every Flutter command, since .fvmrc pins the version." else ".",
            devices.takeIf { it.isNotEmpty() }?.let {
                "Flutter doesn't pick the worktree's device by itself and other worktrees' devices are online too: start the worktree's device first " +
                    "and always pass it with -d to `$flutter run`, `$flutter test integration_test` and `$flutter drive`: " +
                    it.joinToString("; ") + "."
            },
            "Never launch emulators with `$flutter emulators --launch`, which starts one outside ShogunAi's pool.",
        ).joinToString(" ")
    }

    fun sessionStartRules(context: WorkflowContext): List<WorkflowRule> =
        rulesFor(context).applicable.filter { it.inSessionStart }

    fun sessionStartLines(context: WorkflowContext): List<String> =
        listOf(PRECEDENCE) + sessionStartRules(context).map { it.text } + GUIDE_POINTER
}
