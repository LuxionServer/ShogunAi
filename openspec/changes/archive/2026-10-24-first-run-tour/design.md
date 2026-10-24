# Design

## Context

See proposal.md for the motivation and `specs/first-run-tour/spec.md` for the behavior. What shapes the approach:

- The first launch shows `EmptyState` from `AppShell.ShellContent` when `ShellViewModel.destination` is `null`. Help lives in `AppMenuBar`.
- The command-line tool already has a port and a state holder: `CommandLineTool` (`status`, `install`, `remove`) and `ClaudeHooksState` in the desktop app, which Preferences and the hooks notice use. Its notice-dismissed flag is a field of `JsonSettingsFile.Settings`.
- Projects are registered with `RegisterProjectUseCase` and removed through `ProjectRepository.delete`; worktrees are removed with `RemoveWorktreeUseCase`, which also releases environment reservations and provider resources.
- The app's worktree states come from Git (`ReadWorktreeStatusUseCase`) and agents. The activity date uses the committer date (`git log -1 --format=%ct`), and "Inactive" depends on `inactiveAfterDays`, at most 90 (`INACTIVE_AFTER_DAYS_RANGE`).
- `ShellCommandExecutor` runs argv with a working directory, but can't set environment variables for one command.
- Launchers (`OpenWorktreeInTerminalUseCase`, `OpenWorktreeInHerdrUseCase`, Claude Desktop, copy launch command) all take the project's `AgentLaunchConfig`.

## Goals / Non-Goals

**Goals:**
- Everything the tour creates lives under one folder, `~/.shogunai/tour`, and one project, so ending it can't touch anything else.
- The sample is built with the user's own Git, offline, in a few seconds, on macOS, Windows and Linux.
- The tour reuses the existing launchers, agents and command-line tool flows instead of new ones.

**Non-Goals:**
- No `shogun tour` command: the tour is a desktop app entry point. The agent uses the CLI the tour teaches.
- Agent states that need hooks or Herdr ("Agent working", "Needs you" from an agent) aren't faked in the sample: the tour's own agent produces them live in the main worktree, and the script points at that.
- No providers with real resources (Gradle, emulators, Docker) in the sample: the script explains them and runs `shogun stacks`, so the tour needs no SDKs.
- No tracking of which step the user reached.

## Decisions

### A core use case builds the sample, Git does the work
`CreateTourUseCase` (core) takes the tour root (`~/.shogunai/tour`), the app language and the inactivity bound, and runs `git` through `ShellCommandExecutor`:

1. `git init --bare origin.git`; `git init -b main sample-repo`; write `README.md`, `.gitignore`, `.shogunai.json`, `AGENTS.md`, `CLAUDE.md`, `.env.local`(ignored, copied by `copy`); commit with a fixed local identity (`git -c user.name=ShogunAi -c user.email=tour@shogunai.invalid`) so it works without a global Git identity; add `origin`, push `main`.
2. Worktrees with `git worktree add -b <branch> ../worktrees/<name> main`, each brought to its state:
   - `new-idea`: nothing more ("New").
   - `needs-you`: an uncommitted edit ("Needs you").
   - `pushed-work`: a commit pushed with `-u`, then a later commit on `main` pushed, so it's behind ("Stopped").
   - `merged-fix`: a commit merged into `main` with `--no-ff` and pushed ("Merged").
   - `old-experiment`: a commit dated 120 days ago and pushed ("Inactive").
   - `locked-task`: `git worktree lock --reason "Locked by the ShogunAi tour"` ("locked" label).
   - `folder-gone`: created, then its folder deleted ("Folder missing").
3. `RegisterProjectUseCase` on `sample-repo`, then save the project with name "ShogunAi tour", `worktreesRoot` `~/.shogunai/tour/worktrees` and the tour's agents.

The order matters for "behind": the extra `main` commit is made after `pushed-work` branches.

Alternatives: creating worktrees with `CreateWorktreeUseCase` would provision them and run the post-creation command, which needs trust approval and is slower; the tour needs raw Git states, so plain `git worktree add` is closer. Shipping a prebuilt repository as a zip would avoid Git calls but hide machine-specific paths in Git's administrative files and break the relative dates.

### Committer dates through an optional environment on the executor
"Inactive" needs a committer date in the past, which only `GIT_COMMITTER_DATE` sets. `ShellCommandExecutor` gains `executeWithEnvironment(command, environment, workingDirectory)`, whose default delegates to `execute`, and which `ProcessBuilderShellCommandExecutor` implements by adding the variables on top of its own. Changing `execute`'s signature would have touched every fake (over sixty); a new method with a default keeps them compiling. Alternative: `env VAR=… git` isn't available on Windows; `git fast-import` needs stdin, which the port doesn't offer.

### Failure leaves nothing behind
If any step fails, the use case deletes `~/.shogunai/tour` and returns a typed `WorktreeError.TourCreationFailed(command, stderr)`; the project is registered only at the very end. If the folder already exists when creating (a previous tour, or a leftover), the use case refuses unless asked to replace it, which the restart path does after ending the old tour.

### The script is Kotlin text, rendered with the language
`AGENTS.md` comes from `TourScript` (core has no resources folder, and other generated texts such as the workflow guide are Kotlin strings too), in English, with one line naming the language to speak (from `AppLanguage`). `CLAUDE.md` is `@AGENTS.md`. The script's steps, in order: the overview and states (each sample worktree, and the agent's own main worktree turning "Agent working"); a worktree's details; cleanup (the app's Clean up on the merged and missing worktrees); provisioning (`.shogunai.json`, creating a worktree from the app, which meets the post-creation approval, and checking `.env.local` was copied); the command line (`shogun status`, `shogun status --json`, `shogun guide`, after checking `shogun --version` works); hooks (`shogun hooks status`, installing from the app); environment (`shogun env`, the reserved port, `.env.shogunai`); providers (`shogun stacks`, the providers guide); and the end (adding their own project, "End tour"). It says to wait for the user after each step and to change nothing outside `~/.shogunai/tour`.

The post-creation command in the sample is `["git", "status", "--short"]`: harmless, cross-platform, and it is not pre-approved, so creating a worktree shows the trust prompt that the script explains.

### Tour agents are plain project agents
The tour project gets `AgentLaunchConfig(name = "Claude Code", agentCommand = "claude \"Start the ShogunAi tour\"", useHeadroom = false)` as default and `Codex` (`codex "Start the ShogunAi tour"`) as additional agent. The launchers already run the agent command in a shell, so the prompt reaches the agent with no new launcher code. Claude Desktop doesn't take a command: it opens a session in the folder, and `CLAUDE.md` makes the agent start the tour on the user's first message; the dialog says to write "Start the tour".

### The tour is identified by its folder
No flag is added to `Project`: the tour project is the one whose `baseRepositoryPath` is `~/.shogunai/tour/sample-repo`. A small `TourLocation` (core) resolves the paths from the user's home and answers `isTour(project)`. The banner and "End tour" use it.

The tool step reuses the shared `ClaudeHooksState` and its `CommandLineToolPrerequisite` view, so installing the tool from the tour also updates Settings and the hooks notice.

### Ending the tour
`EndTourUseCase` deletes every project whose repository is the tour's, then deletes `~/.shogunai/tour`. Port reservations live in each worktree's Git administrative folder, inside the sample's `.git`, and the machine index drops entries whose folder is gone, so deleting the folder frees them without removing worktrees one by one. Every step tolerates what's already gone. Agents still running in the sample aren't stopped: the confirmation says to close them first, and a folder in use is reported as cleanup already does.

### Desktop app pieces
- `TourState` (shell) holds the dialog's step, the creation progress and errors, using `CommandLineTool` for the tool step like `ClaudeHooksState` does (sharing the instance, so installing in the tour updates the hooks section and notice too).
- `TourDialog` shows welcome → tool (skipped when `CommandLineToolState.AVAILABLE`) → creating → open agent. The last step shows the tour project behind the dialog and `TourAgentChoices` lists, for its main worktree, the terminal and Claude Desktop actions the holder's `openActions` already compute, Herdr and copying the launch command. The choices are a composable slot of the dialog and the banner, so both are tested without a `WorktreeBoard`. A launch closes the dialog; a launch error appears in the project view like for any worktree.
- `EmptyState` gains "Take the tour"; `AppMenuBar` Help gains "ShogunAi Tour"; a `TourNotice` above the content follows the hooks notice pattern, with `tourOffered: Boolean` in `Settings` set when the tour starts or the notice is dismissed. New users never see it because starting from the empty state sets it.
- The tour banner sits at the top of the project view (and the "All worktrees" view filtered to it) when `isTour(project)`.
- Strings in `AppStrings` for English and Spanish.

## Risks / Trade-offs

- [Git missing or too old for `git init -b`] → `TourCreationFailed` shows the command and its output; `git init` followed by `git symbolic-ref HEAD refs/heads/main` avoids needing Git 2.28.
- [Claude Code or Codex not installed] → the terminal shows "command not found"; the dialog's last step names both agents and says the tour needs one of them, and the copy option lets the user run another agent by hand.
- [Ports reserved by the tour's worktrees] → they're stored in the sample's `.git`, so deleting the folder, by "End tour" or by hand, frees them.
- [The tour's agent edits outside the sample] → the script forbids it and every command it suggests runs inside `~/.shogunai/tour`; it can't be enforced beyond the agent's own permission prompts.
- [Inactivity bound changes] → 120 days stays above the 90-day maximum; if the range grows, the date comes from the range's upper bound plus 30 days, not a constant.

## Migration Plan

Additive. `Settings.tourOffered` defaults to `false`, so existing users see the notice once. Nothing to roll back beyond the app version; a tour folder left by a newer version is ended like any other.
