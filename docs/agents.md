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

For Android repositories, `"providers": { "emulator": {} }` adds `ANDROID_SERIAL=emulator-<port>` to every worktree's environment, so `./gradlew installDebug`, `connectedAndroidTest` and plain `adb` commands only use that worktree's emulator. The adb server itself is shared by every worktree, so an agent must never restart it (`adb kill-server`) or force a device with `-s`, `-e` or `-d`; the `SessionStart` hook tells Claude so. Agents start it with `shogun emulator start` (it waits until the emulator has booted); see [Providers › Android emulator](guide/providers.md#android-emulator) for the AVD and window settings. Likewise, `"providers": { "ios-simulator": {} }` gives each worktree its own iOS simulator and `IOS_SIMULATOR_UDID`; agents boot it with `shogun simulator start` and build with `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`, and KMP tests use it once the build script (or the machine-wide init script) passes it to `device` ([Providers › KMP tests](guide/providers.md#kmp-tests)).

For Gradle repositories, settings in the Gradle provider (`"providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 } }`) add the same `GRADLE_OPTS` to every worktree's environment, so the daemons that agents' builds start use that heap and idle timeout instead of the ones in `gradle.properties`, and can be shared between worktrees. It replaces a `GRADLE_OPTS` already set in the shell, and doesn't reach builds started from an IDE ([Providers › Daemon memory and lifetime per worktree](guide/providers.md#daemon-memory-and-lifetime-per-worktree)).

For Xcode repositories, `"providers": { "xcode": { "compilationCache": true } }` adds `XCODE_XCCONFIG_FILE` to every worktree's environment, pointing to a machine-wide `.xcconfig` that turns on compilation caching with prefix mapping, so an agent's first `xcodebuild` in a new worktree reuses what other worktrees already compiled. It replaces an `XCODE_XCCONFIG_FILE` already set in the shell, doesn't reach Xcode opened from the Dock and doesn't help Swift packages. Agents can check DerivedData with `shogun xcode` and delete the orphaned folders with `shogun xcode clean` ([Providers › Xcode](guide/providers.md#xcode)).

The recipes below show how each agent gets these variables.

## Claude Code

Claude Code's `WorktreeCreate` hook **replaces** its own worktree creation for `claude --worktree`, `EnterWorktree`, subagents with `isolation: "worktree"` and background sessions. Point it at `shogun`, together with `WorktreeRemove` so cleanup goes through ShogunAi too. In the repository's `.claude/settings.json` (shared with the team) or your `~/.claude/settings.json` (every repository):

```json
{
  "hooks": {
    "WorktreeCreate": [
      { "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }
    ],
    "WorktreeRemove": [
      { "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }
    ],
    "SessionStart": [
      { "hooks": [{ "type": "command", "command": "shogun hook session-start" }] }
    ]
  }
}
```

What happens then:

- `claude -w TASK-123` asks `shogun` for the worktree. It's created on `feature/TASK-123` in your ShogunAi project's worktrees folder, or in `.claude/worktrees/` (Claude Code's usual place) when the repository has no project, and provisioned. Claude Code starts the session inside it.
- Running `claude -w TASK-123` again reuses that worktree.
- When Claude Code removes the worktree (you choose to remove it when exiting, or it had no changes), `shogun` removes it and deletes its branch only if it's merged; a branch with work is kept.
- With this hook Claude Code no longer processes `.worktreeinclude` itself; ShogunAi reads it instead, so its files are still copied.
- `SessionStart` appends the worktree's `env` variables to the session's `CLAUDE_ENV_FILE`, so every Bash command Claude runs sees them (`$PORT`), and tells Claude which values this worktree uses, including how to start its emulator or its iOS simulator when the repository uses those providers. It works for any session started inside a worktree, also without `--worktree`, and does nothing in repositories without `env`.

Claude Code only runs a repository's project hooks once you've trusted its folder. If the hooks live in the repository's `.claude/settings.json` and you've never run `claude` there, `claude -w` stops with "Workspace trust not yet accepted": run `claude` once in the repository, accept the trust dialog, and retry. Hooks in `~/.claude/settings.json` don't need this for repositories you already trust.

The new worktree starts from the HEAD of the worktree Claude Code runs in (the main checkout's current commit when you start there), like `shogun new`, and Claude Code's `worktree.baseRef` setting doesn't apply. Checked with Claude Code 2.1.301.

## Herdr

[Herdr](https://herdr.dev) is a terminal multiplexer for AI agents: it keeps their sessions running in the background and shows which one is working, blocked or done. Herdr runs the agents and ShogunAi creates and prepares the worktrees they work in.

**Recommended flow.** Create the worktree from ShogunAi (the desktop app or `shogun new`), so it lands in the project's worktrees folder, provisioned, with its ports and environment. Then use the "Open in Herdr" action of its row in the desktop app: Herdr opens it as a worktree of its repository, grouped under the repository's space like its own "Open worktree…", focused, with a single pane whose shell already has the worktree's environment (`PORT`, `ANDROID_SERIAL`, …, everything `shogun env` prints), and runs the project's agent command there, the same command "Copy command" copies. If Herdr already has that worktree open, the action only focuses it and doesn't type anything into its panes. Herdr's server must be running (`herdr`); the action calls `herdr` directly and doesn't need `shogun` on any `PATH`.

Don't use Herdr's "New worktree" (or `herdr worktree create`) for ShogunAi projects: Herdr always places those checkouts in its own folder (`~/.herdr/worktrees/<repo>/<branch>`, or the `[worktrees] directory` of its `config.toml`), outside the project's worktrees folder, and ShogunAi lists them with the Herdr origin.

**Rescue path: the ShogunAi plugin for Herdr.** For worktrees created from Herdr anyway, by mistake or by an agent through Herdr's API, install the plugin once:

```bash
herdr plugin install LuxionServer/ShogunAi/integrations/herdr
```

The plugin's manifest and notes live in [`integrations/herdr/`](https://github.com/LuxionServer/ShogunAi/tree/main/integrations/herdr) in the repository. On every worktree Herdr creates, it runs `shogun hook herdr`, which provisions it like `shogun provision` does: files from `.shogunai.json` and `.worktreeinclude`, your project's local files, reserved ports and `envFile`. When the repository has a ShogunAi project and the worktree is outside its worktrees folder, it also shows a Herdr notification saying so and recommending creating worktrees from ShogunAi.

The plugin also listens to `worktree.removed`: when you remove a worktree from Herdr ("Remove worktree" or `herdr worktree remove`) in a repository with a ShogunAi project, `shogun hook herdr` deletes its branch if Git considers it merged, and keeps it otherwise, the same as removing from ShogunAi does. Run `herdr plugin update` to get this on an existing install.

The other way round needs no plugin: removing a worktree from ShogunAi (the row's remove action, "Clean up", `shogun clean --yes` or Claude Code's removal) also closes its Herdr workspace, and the agent running there, once the worktree is removed. The dialogs say beforehand which worktrees are open in Herdr; if Herdr isn't installed or running, nothing changes, and if closing the workspace fails, the removal still counts and a warning shows Herdr's message.

- The plugin runs `shogun`, so it must be on the `PATH` of the shell you start Herdr from (Herdr's background server keeps that shell's environment; restart it with `herdr server stop` after installing `shogun`). `herdr plugin log` shows each run with the files it copied, or the error.
- Herdr opens the new workspace a moment before the plugin runs; an agent you start instantly could find the files still being copied.
- The plugin doesn't type anything into the new workspace's shell. Its variables reach agents the usual way: Claude Code's `SessionStart` hook above, the `envFile`, or `direnv` (see below).

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
