# Using with agents

AI agents increasingly create their own worktrees. These recipes make the worktrees they create ready to build, with the files your repository declares in `.shogunai.json` (and `.worktreeinclude`) and your project's local files, and keep them visible and cleanable in ShogunAi. They all rely on the [`shogun` command](guide/cli.md) being on your `PATH` (see [Installation](guide/installation.md#the-shogun-command)).

## Per-worktree environment

A worktree isolates files, not ports. When the repository declares variables in `.shogunai.json`, each worktree gets its own values:

```json
{
  "env": {
    "PORT": "${port}",
    "API_URL": "http://localhost:${port:api}",
    "APP_SUFFIX": "-${worktree}"
  },
  "envFile": ".env.shogunai"
}
```

- `${port}` reserves a port for that variable and `${port:<key>}` one shared by every variable using the key. Ports start at 20000, are unique among the repository's worktrees, are free when reserved and never change afterwards. They're kept in Git's own folder for the worktree, so removing the worktree releases them.
- `${worktree}` is the worktree's folder name; `$$` is a literal `$`.
- `shogun env` prints the current worktree's variables as `export` lines (`--format dotenv` or `json` for other tools), so `eval "$(shogun env)"` sets them in a shell.
- `envFile` (optional) is written into each worktree when it's created or provisioned, in dotenv format, for tools that read one. It's never written over a file Git tracks, so add it to `.gitignore`.

For Android repositories, `"providers": { "emulator": {} }` adds `ANDROID_SERIAL=emulator-<port>` to every worktree's environment, so `./gradlew installDebug`, `connectedAndroidTest` and plain `adb` commands only use that worktree's emulator. The adb server itself is shared by every worktree, so an agent must never restart it (`adb kill-server`) or force a device with `-s`, `-e` or `-d`; the `SessionStart` hook tells Claude so. Agents start it with `shogun emulator start` (it waits until the emulator has booted); see [Providers › Android emulator](guide/providers.md#android-emulator) for the AVD and window settings. Likewise, `"providers": { "ios-simulator": {} }` gives each worktree its own iOS simulator and `IOS_SIMULATOR_UDID`; agents boot it with `shogun simulator start` and build with `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`, and KMP tests use it once the build script (or the machine-wide init script) passes it to `device` ([Providers › KMP tests](guide/providers.md#kmp-tests)). On macOS, `"providers": { "desktop-app": { "module": ":desktopApp" } }` lets agents check a Compose Desktop app without computer use: `shogun headless start` runs the worktree's app hidden, the Compose Hot Reload MCP server drives it and `shogun headless capture` shows it ([Checking a desktop app in the background](guide/headless-app.md)). What still needs the real desktop is driven with computer use one agent at a time: in any repository, an agent takes the machine's desktop turn with `shogun desktop acquire --wait` first and gives it back with `shogun desktop release` ([`desktop`](guide/cli.md#desktop)).

For Gradle repositories, settings in the Gradle provider (`"providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 } }`) add the same `GRADLE_OPTS` to every worktree's environment, so the daemons that agents' builds start use that heap and idle timeout instead of the ones in `gradle.properties`, and can be shared between worktrees. It replaces a `GRADLE_OPTS` already set in the shell, and doesn't reach builds started from an IDE ([Providers › Daemon memory and lifetime per worktree](guide/providers.md#daemon-memory-and-lifetime-per-worktree)).

For Xcode repositories, `"providers": { "xcode": { "compilationCache": true } }` adds `XCODE_XCCONFIG_FILE` to every worktree's environment, pointing to a machine-wide `.xcconfig` that turns on compilation caching with prefix mapping, so an agent's first `xcodebuild` in a new worktree reuses what other worktrees already compiled. It replaces an `XCODE_XCCONFIG_FILE` already set in the shell, doesn't reach Xcode opened from the Dock and doesn't help Swift packages. With `"cocoaPods": true`, a new worktree gets the base repository's CocoaPods pods cloned when its `Podfile.lock` matches, and `shogun new` and `shogun provision` name the folders that still need `pod install`. Agents can check DerivedData with `shogun xcode` and delete the orphaned folders with `shogun xcode clean` ([Providers › Xcode](guide/providers.md#xcode)).

For Flutter repositories, `"providers": { "flutter": {} }` runs `flutter pub get` (`fvm flutter pub get` with `.fvmrc`) in every new or provisioned worktree, so it builds at once. Flutter doesn't read `ANDROID_SERIAL`, so the `SessionStart` hook tells Claude to start the worktree's device first and always pass it with `flutter run -d "$ANDROID_SERIAL"` or `-d "$IOS_SIMULATOR_UDID"`, and never to launch emulators with `flutter emulators --launch` ([Providers › Flutter](guide/providers.md#flutter)).

The recipes below show how each agent gets these variables.

## Approving a repository

Agent hooks and the Herdr plugin can't ask you anything, so they never run a repository's `hooks.postCreate` commands, nor deliver the variables it declares in `env` or write its `envFile`, until you have approved them on this machine: they provision the worktree, skip them and print a note on standard error that names `shogun trust`. Run it once in each repository you trust:

```bash
shogun trust        # show the commands, variables and environment file, and ask
shogun trust --yes  # approve without asking
```

After that the hooks run and deliver them, until the repository's commands or variables change; then you approve again. Details: [Approving a repository](guide/configuration.md#approving-a-repository).

## Claude Code

Claude Code's `WorktreeCreate` hook **replaces** its own worktree creation for `claude --worktree`, `EnterWorktree`, subagents with `isolation: "worktree"` and background sessions. One command points it at `shogun`, together with `WorktreeRemove` so cleanup goes through ShogunAi too, and with the hooks that report the agent's state ([below](#agent-states-outside-herdr)):

```bash
shogun hooks install
```

It shows what it will add to your `~/.claude/settings.json` and asks before writing; `--yes` skips the question. Your other settings and hooks are kept, running it again changes nothing, and after upgrading ShogunAi it adds or replaces only the hooks that changed. `shogun hooks install --project` writes the repository's `.claude/settings.json` instead (shared with the team), `shogun hooks status` says whether they are installed, outdated or missing, and `shogun hooks uninstall` removes them. The desktop app does the same from Settings › Claude Code, with "Install", "Update" and "Remove", and suggests it once when it finds a Claude agent without a known state. `CLAUDE_CONFIG_DIR` is honored. A settings file that isn't valid JSON is never overwritten.

The hooks it installs, each one a `command` hook:

| Event | Command |
|---|---|
| `WorktreeCreate` | `shogun hook worktree-create` |
| `WorktreeRemove` | `shogun hook worktree-remove` |
| `SessionStart` | `shogun hook session-start` |
| `UserPromptSubmit`, `Notification`, `Stop`, `SessionEnd` | `shogun hook agent-state` |

The hooks run `shogun`, so it must be on the `PATH` of the shell you run Claude Code from. In the desktop app, Preferences and the board's hooks notice show this prerequisite next to "Install", keep it disabled until the command is available and offer "Install command-line tool" (see [The `shogun` command](guide/installation.md#the-shogun-command)).

What happens then:

- `claude -w TASK-123` asks `shogun` for the worktree. It's created on `feature/TASK-123` in your ShogunAi project's worktrees folder, or in `.claude/worktrees/` (Claude Code's usual place) when the repository has no project, and provisioned. Claude Code starts the session inside it.
- Running `claude -w TASK-123` again reuses that worktree.
- When Claude Code removes the worktree (you choose to remove it when exiting, or it had no changes), `shogun` removes it and deletes its branch only if it's merged; a branch with work is kept.
- With this hook Claude Code no longer processes `.worktreeinclude` itself; ShogunAi reads it instead, so its files are still copied.
- `SessionStart` appends the worktree's `env` variables to the session's `CLAUDE_ENV_FILE`, so every Bash command Claude runs sees them (`$PORT`), and tells Claude which variables this worktree has, without their values. It only does so in a repository registered as a project, and only delivers the declared variables once the repository is approved; while they await approval, it names them and tells Claude to ask you to review them with `shogun trust` instead of approving them itself. It also gives the session [ShogunAi's workflow rules](#shogunais-workflow-rules): a short list adapted to where it runs, including how to start the emulator or the iOS simulator when the repository uses those providers. Its workflow rules reach any session started inside a worktree or the main checkout of a Git repository, also without `--worktree` and also when the repository declares no `env` (then there is no variables line and nothing is written to `CLAUDE_ENV_FILE`).

Claude Code only runs a repository's project hooks once you've trusted its folder. If the hooks live in the repository's `.claude/settings.json` and you've never run `claude` there, `claude -w` stops with "Workspace trust not yet accepted": run `claude` once in the repository, accept the trust dialog, and retry. Hooks in `~/.claude/settings.json` don't need this for repositories you already trust.

The new worktree starts from the HEAD of the worktree Claude Code runs in (the main checkout's current commit when you start there), like `shogun new`, and Claude Code's `worktree.baseRef` setting doesn't apply. Checked with Claude Code 2.1.306.

### Agent states outside Herdr

The `agent-state` hooks record whether each Claude session is working (`UserPromptSubmit`), blocked (`Notification`, for example a permission prompt), done (`Stop`, or a `Notification` saying the prompt has been idle) or over (`SessionEnd`). The desktop app and `shogun status` show that state for Claude agents without Herdr, and a blocked or done agent puts its worktree under "Needs you".

- Each session is a small file in the worktree's own Git folder (`<git-dir>/shogunai/agent-sessions/`), so it never shows up as a change, isn't copied to other worktrees and goes away with the worktree.
- A record counts only while its Claude process runs; records left by a crash are ignored and deleted.
- Granting a permission fires no hook. A blocked session whose transcript is written to afterwards reads as working, so the state follows the permission being granted.
- When Herdr or [Orca](#orca) also reports the agent, their state is used.
- Other agents (Codex, Gemini…) have no such hooks and show without a state unless Herdr reports them.
- When the session that holds the [desktop turn](guide/cli.md#desktop) ends its response (`Stop`) or ends (`SessionEnd`), the turn is released for the next agent waiting.
- These recorded sessions also protect a worktree's emulator and simulator: a working session renews the device lease and is never released from under it, while a blocked one is released last. Herdr's states aren't used for this. See [Providers](guide/providers.md).

## Herdr

[Herdr](https://herdr.dev) is a terminal multiplexer for AI agents: it keeps their sessions running in the background and shows which one is working, blocked or done. Herdr runs the agents and ShogunAi creates and prepares the worktrees they work in.

**Recommended flow.** Create the worktree from ShogunAi (the desktop app or `shogun new`), so it lands in the project's worktrees folder, provisioned, with its ports and environment. Then use the "Open in Herdr" action of its row in the desktop app: Herdr opens it as a worktree of its repository, grouped under the repository's space like its own "Open worktree…", focused, with a single pane whose shell already has the worktree's environment (`PORT`, `ANDROID_SERIAL`, …, everything `shogun env` prints), and runs the project's agent command there, the same command "Copy command" copies. If Herdr already has that worktree open with an agent running in it, the action only focuses that agent's pane and doesn't type anything; if the workspace is open but its agent is gone, it starts the agent in a new pane of that workspace with the same environment and leaves the other panes alone. Each time it types the agent command it waits up to 10 seconds for Herdr to detect the agent, and reports an error naming the pane and the command if it doesn't. Herdr's server must be running (`herdr`); the action calls `herdr` directly and doesn't need `shogun` on any `PATH`. From a terminal, or from an agent, `shogun open <task id> --herdr` does the same (`--agent <name>` picks another of the project's agents).

**Agent states.** While Herdr is running (for Claude without Herdr, see [Agent states outside Herdr](#agent-states-outside-herdr)), the desktop app and `shogun status` show the state Herdr reports for each agent in a worktree: working, blocked (waiting for you, for example on a permission prompt), done or idle. A worktree whose agent is blocked or done is listed under "Needs you", and "Open in Herdr" focuses that agent's pane instead of only opening the workspace. Agents Herdr sees count as running even if the process list can't, so cleanup keeps their worktrees. If Herdr isn't installed or its server isn't running, agents are shown without a state and nothing reports an error; ShogunAi asks Herdr at most once every 30 seconds after a failure.

Don't use Herdr's "New worktree" (or `herdr worktree create`) for ShogunAi projects: Herdr always places those checkouts in its own folder (`~/.herdr/worktrees/<repo>/<branch>`, or the `[worktrees] directory` of its `config.toml`), outside the project's worktrees folder, and ShogunAi lists them with the Herdr origin.

**Rescue path: the ShogunAi plugin for Herdr.** For worktrees created from Herdr anyway, by mistake or by an agent through Herdr's API, install the plugin once:

```bash
herdr plugin install LuxionServer/ShogunAi/integrations/herdr
```

The plugin is installed from the repository's default branch, `main`, which only receives released code, so it matches the released `shogun`. The plugin's manifest and notes live in [`integrations/herdr/`](https://github.com/LuxionServer/ShogunAi/tree/main/integrations/herdr) in the repository. On every worktree Herdr creates, it runs `shogun hook herdr`, which provisions it like `shogun provision` does: files from `.shogunai.json` and `.worktreeinclude`, your project's local files, reserved ports and `envFile`. When the repository has a ShogunAi project and the worktree is outside its worktrees folder, it also shows a Herdr notification saying so and recommending creating worktrees from ShogunAi.

The plugin also listens to `worktree.removed`: when you remove a worktree from Herdr ("Remove worktree" or `herdr worktree remove`) in a repository with a ShogunAi project, `shogun hook herdr` deletes its branch if Git considers it merged, and keeps it otherwise, the same as removing from ShogunAi does. Run `herdr plugin update` to get this on an existing install.

The other way round needs no plugin: removing a worktree from ShogunAi (the row's remove action, "Clean up", `shogun clean --yes` or Claude Code's removal) also closes its Herdr workspace, and the agent running there, once the worktree is removed. The dialogs say beforehand which worktrees are open in Herdr; if Herdr isn't installed or running, nothing changes, and if closing the workspace fails, the removal still counts and a warning shows Herdr's message.

- The plugin runs `shogun`, so it must be on the `PATH` of the shell you start Herdr from (Herdr's background server keeps that shell's environment; restart it with `herdr server stop` after installing `shogun`). `herdr plugin log` shows each run with the files it copied, or the error.
- Herdr opens the new workspace a moment before the plugin runs; an agent you start instantly could find the files still being copied.
- The plugin doesn't type anything into the new workspace's shell. Its variables reach agents the usual way: Claude Code's `SessionStart` hook above, the `envFile`, or `direnv` (see below).

## Orca

[Orca](https://onorca.dev) is a desktop app for running coding agents in worktrees: it lists each repository's worktrees, opens terminals in them and shows which agent is working, waiting or done. Orca is optional: without it nothing changes. ShogunAi works with Orca 1.4.243 or newer, found as `orca` (`~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`, the app bundle on macOS, then the `PATH`) or, on Linux, only as `orca-ide`, since `orca` there is usually GNOME's screen reader.

**Recommended flow.** Create the worktree from ShogunAi, then use "Open in Orca" in its detail or its row's context menu, or `shogun open <task id> --orca` from a terminal or an agent (`--agent <name>` picks another of the project's agents). ShogunAi:

- starts Orca when it isn't running and waits up to 30 seconds for it;
- registers the repository with Orca when Orca doesn't know it, and, when Orca would hide worktrees it didn't create for that repository, sets that repository's "non-Orca worktrees" visibility to show them (no other repository's setting changes);
- focuses the worktree's terminal when Orca already reports a live agent in it (a finished turn counts), without typing anything;
- otherwise opens a new focused terminal in the worktree that runs the project's agent command with the worktree's environment (`env 'PORT=…' … claude -n <session name>`, or `$env:NAME = '…'; …` on Windows) and waits up to 10 seconds for Orca to report the agent, failing with the terminal and the command if it doesn't. The terminal stays open either way.

ShogunAi runs the project's own command rather than Orca's agent launcher, whose default runs Claude with full access, and Claude sessions get the same `-n` name as in Herdr, so inter-agent messages reach them.

**Agent states.** While Orca is running, the desktop app and `shogun status` show the state Orca reports for each agent: working, blocked (Orca's "waiting", for example on a permission prompt) or done, with `stateSource` `orca`. Orca's state wins over the one Claude Code's hooks record. ShogunAi asks Orca once per refresh with `orca worktree ps`, never starts it for this, and after a failure or a closed Orca waits 30 seconds before asking again. Orca reports these states through its own agent hooks in `~/.claude/settings.json`; if you turn them off (`orca agent hooks off`), Orca only sees an agent while it shows a prompt such as Claude's folder trust, and ShogunAi shows the agents without an Orca state; "Open in Orca" may then report that the agent didn't start even though it did.

**What the agent is doing.** With the states, Orca reports what each agent waits on, what it was asked and what it last said. The worktree's detail shows, under each agent Orca reports, the tool it waits on with its input (only while it's blocked, for example `Waiting on Bash: npm test` on a permission prompt), its prompt and its last message, each shortened to three lines, and `shogun status --json` gives them in each agent's `activity`.

**Ports Orca sees.** Orca ties the ports listening on the machine to the worktree their process runs in, also for worktrees it only shows, such as ShogunAi's. With the states, ShogunAi reads them with one `orca ports --all` per refresh, under the same rules: the worktree's detail lists them after the ports ShogunAi reserves, with the process and the address a dev server printed in an Orca terminal, and marks the reserved ports Orca sees listening; `shogun status` adds `listening=<ports>` and its JSON `listeningPorts`.

**Origin.** Worktrees Orca created are labelled "Orca". A ShogunAi worktree opened in Orca keeps its ShogunAi label. While Orca can't be asked, worktrees inside the folder where Orca last created one, or `~/orca/workspaces`, are labelled "Orca".

**Worktrees created from Orca.** Orca creates its worktrees in its own folder with branches named `<git user>/<name>`. Prefer creating them from ShogunAi. For the ones created from Orca anyway, add ShogunAi to the repository's `orca.yaml`, as described in [Configuration](guide/configuration.md#orca): `shogun hook orca setup` provisions them like `shogun provision` and notes when they're outside the project's worktrees folder, and `shogun hook orca archive` frees their emulator, simulator and idle Gradle daemons before Orca removes them.

**Removal.** Removing a worktree from ShogunAi (the remove action, "Clean up", `shogun remove`, `shogun clean --yes` or Claude Code's removal) also closes its Orca terminals, and the agents in them, once the worktree is removed; a folder locked by those shells gets its terminals closed and the removal retried once. The dialogs and `shogun clean`'s dry run say beforehand which worktrees have Orca terminals. If Orca isn't installed or running, nothing changes and Orca isn't started; if closing fails, the removal still counts and a warning shows Orca's message.

**Emulators.** In a repository with the [emulator provider](guide/providers.md#android-emulator), starting a worktree's emulator (`shogun emulator start`, or Start in the app) while that worktree is open in a running Orca also shows the emulator in the worktree's emulator view in Orca, with live video, even when it runs without a window. Opening a worktree in Orca while its emulator runs shows it there too, and the agent's terminal stays in front. ShogunAi never starts Orca or the emulator for this, and if Orca can't show it the start or the open still succeeds (`shogun emulator start` says why on standard error). Stopping the emulator needs nothing from Orca: its view keeps the last frame until the next start shows it live again. Close Orca's emulator tab rather than using it to stop the emulator. A tab opened from Orca's own "+" menu, where you pick the device by hand, shuts the emulator down when you close it. ShogunAi then shows it as stopped, and `shogun emulator start` boots it again.

**Multirepos.** In a multirepo folder, `shogun open <id> --orca` and the group header's "Open all in Orca" open every worktree of the group under its own Orca repository, and `shogun open --orca` and "Open multirepo agent in Orca" run the multirepo agent in Orca's workspace for the multirepo folder. A folder that isn't a Git repository needs a one-time setup in Orca's app first; see [Multirepos and groups](guide/multirepo.md#groups). While Orca reports an agent in that workspace, the multirepo view shows its state as for a worktree's agent.

## ShogunAi's workflow rules

Agents that work in ShogunAi's worktrees tend to break its flow: they switch the branch of the worktree a session starts in, create worktrees outside `shogun`, type into another session's terminal or clean worktrees without asking. The rules that follow from how `shogun` works are kept in one catalog, versioned with the binary, in four topics: `worktrees`, `parallel`, `resources` and `cleanup`. They are defaults: every output says that the project's `AGENTS.md` and your own instructions take precedence. Personal or team preferences (code style, commits, pull request format or reviewers) are not part of it; the one rule about pull requests points to a fact ShogunAi records: open them against the branch [`shogun base`](guide/cli.md#base) prints.

- **Claude Code** gets the short subset, adapted to the session, from the [`SessionStart` hook](#claude-code) (at most eleven lines, ending with a pointer to the rest): the rule about the main checkout or the worktree, branching with `--from`, opening sessions with `shogun open --herdr` and messaging them only when Herdr is available, the emulator and simulator rules only when the repository declares those providers, taking the desktop turn before computer use, and asking before `shogun clean --yes`.
- **Any agent** can read all of it with `shogun guide [<topic>]`: the rules that apply where it runs first, then the ones that don't, each marked with what it needs. `shogun guide --plain` prints every rule without looking at the directory, so it also works outside a repository.
- **Agents without hooks** (Codex, Gemini CLI, Cursor) get a minimal block in the `AGENTS.md`-style file they read, which only says to run `shogun guide`, so the rules always come from the installed binary:

    ```bash
    shogun guide install --agent codex      # ~/.codex/AGENTS.md (or $CODEX_HOME/AGENTS.md)
    shogun guide install --agent gemini     # ~/.gemini/GEMINI.md
    shogun guide install --project          # the repository's AGENTS.md, which Cursor reads
    shogun guide install --file <path>      # any other file
    shogun guide status                     # installed, outdated or missing in each of them
    shogun guide uninstall --agent codex
    ```

    The block sits between `<!-- shogun:guide v1 -->` and `<!-- /shogun:guide -->`; everything else in the file is kept byte for byte, installing again never duplicates it and an older block is replaced. It shows what it will write and asks `[y/N]` unless `--yes` is given. Cursor keeps user rules in its settings, not in a file, so for it use `--project` or `--file`. Claude Code isn't an `--agent`: it has the hook, and a block in the project's `AGENTS.md` (which Claude Code reads through `CLAUDE.md`) would give it the rules twice, which `shogun guide status` warns about.

### Migrating your own instructions

If your `AGENTS.md` already repeats these rules, run `shogun guide --migrate --file <path>` and give its output to your agent. It prints every rule with its id and asks the agent to list what the file repeats, propose removing it and show the diff without applying anything until you confirm. The command doesn't read or write the file. `shogun guide install` and `status` also warn when the file mentions `shogun new`, `shogun open` or `shogun clean` outside the block, and never change that text. For a repository's `AGENTS.md`, do the same through a pull request.

## Cursor

Cursor's parallel agents run the setup commands in `.cursor/worktrees.json` inside each new worktree. Provision it there:

```json
{
  "setup-worktree": ["shogun provision"]
}
```

Add your other setup commands (`npm ci`, …) after it. Cursor creates and names the worktree itself; ShogunAi lists it as created elsewhere. Setup commands don't leave variables in the agent's shell, so for the per-worktree environment declare an `envFile` (provisioning writes it) or start commands with `eval "$(shogun env)" && …`.

## Codex

Codex's local environments run a setup script when it creates a worktree for a new thread. Use `shogun provision` as (or at the start of) that script. Codex's documentation doesn't say which directory the script runs in; if it isn't the new worktree, pass its path: `shogun provision <worktree path>`. This recipe hasn't been verified with Codex yet. As with Cursor, use an `envFile` or `eval "$(shogun env)"` for the per-worktree environment.

## Any other agent or tool

- **Let ShogunAi create the worktree, then start the agent in it:**

    ```bash
    cd "$(shogun new TASK-123)" && codex
    ```

- **The tool creates the worktree and can run a command afterwards:** run `shogun provision <worktree path>` (or plain `shogun provision` from inside it).
- **Per-worktree environment in your own shell:** with [direnv](https://direnv.net), commit an `.envrc` containing `eval "$(shogun env)"`; every worktree loads its own values when you `cd` into it. Without direnv, run `eval "$(shogun env)"` yourself.
- **Clean up whatever accumulated, from any tool:** `shogun clean` lists merged worktrees and worktrees whose folder is gone; `shogun clean --yes` removes them.
