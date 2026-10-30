# Clean account rehearsal

A script for going through ShogunAi the way a new user does, in a macOS user account that has none of the developer's instructions or settings, before each release or demo. It catches what the developer's own setup hides: a demo on another Mac once found that the app opened from the Dock reported `shogun` as missing, refused to start the guided tour, read Claude Code's hooks as unusable and failed to copy a worktree's local files, none of which happened on the developer's machine.

The CI smoke test of the packaged app checks the same path automatically on a clean runner; the rehearsal adds what only a person can judge: what the app shows, whether its messages make sense and whether the tour's agent starts. It takes about half an hour.

## When

- **Before a release**: while the release pull request (`release/X.X.X` → `main`) is open, with the `.dmg` built from that branch. A failure blocks merging it until it's fixed or the maintainer decides, in its issue, that it doesn't block.
- **Before a demo**: with the latest published release, on the Mac the demo will run on when possible.

## 1. Get the `.dmg`

- Before a release, build it from the release branch with the manual `macOS installer` workflow, which signs and notarizes it like the published one without touching any release, tag or package manager (see [macOS signing and notarization](release.md#macos-signing-and-notarization)):

    ```bash
    gh workflow run macos-installer.yml -R LuxionServer/ShogunAi --ref release/X.X.X -f version=X.X.X
    gh run download <run-id> -R LuxionServer/ShogunAi -n installer-macos-latest
    ```

    `installer-macos-latest` is the Apple Silicon `.dmg` (`-arm64`) and `installer-macos-26-intel` the Intel one (`-x64`); take the one for the Mac you rehearse on.

- Before a demo, download the `.dmg` for the Mac's architecture from the [latest release](https://github.com/LuxionServer/ShogunAi/releases/latest).

Copy it to `/Users/Shared` so the new account can reach it.

Install it from the `.dmg`, never with Homebrew: the Cask links `shogun` itself and would hide the app's own installation of the command.

## 2. Create the account

1. In System Settings › Users & Groups, click "Add User…" and create a **Standard** account (not an administrator), for example "ShogunAi Rehearsal". From Terminal, the same is `sudo sysadminctl -addUser rehearsal -fullName "ShogunAi Rehearsal" -password -`.
2. Log in to it (fast user switching from the menu bar or Control Center is enough) and skip every optional step of macOS's setup assistant.
3. Create a new account each time and delete it afterwards (see [Clean up](#7-clean-up)): an account kept from a previous rehearsal already has `~/.shogunai`, `~/.claude`, `~/.local/bin` and shell profile changes, which is exactly the state the rehearsal must not have.

What's installed machine-wide stays visible to the new account: Homebrew in `/opt/homebrew`, the Xcode Command Line Tools' Git and the apps in `/Applications`. Before going on, open Terminal in the new account and check:

- `git --version` works (if macOS offers to install the Command Line Tools, install them: Git is the only thing ShogunAi needs).
- `which shogun` prints nothing. If it prints `/opt/homebrew/bin/shogun`, the Cask is installed on this Mac and the command step can't be rehearsed: uninstall the Cask from the developer's account (`brew uninstall --cask shogunai`) or rehearse on another Mac.
- `ls ~/AGENTS.md ~/.claude ~/.shogunai` finds none of them.

Then install Claude Code with its [official installer](https://docs.anthropic.com/en/docs/claude-code/setup) and sign in, without configuring anything else, so the hooks the app installs are the only ones. The tour's last step opens it.

## 3. Install and open from the Dock

1. Open the `.dmg` from `/Users/Shared` and drag ShogunAi into `~/Applications` (create the folder if it's missing), not `/Applications`, so the developer's copy isn't replaced.
2. Open it from Finder. **Check**: macOS opens it without any warning about an unidentified developer or damaged app (it's signed and notarized).
3. Keep it in the Dock (right-click its icon › Options › Keep in Dock), quit it, and from now on open it **only from the Dock**: that's how it gets macOS's minimal `PATH`, the environment that hid the original problems.
4. **Check**: the window shows the empty state with "Add project" and "Take the tour", and ShogunAi › About ShogunAi shows the version being rehearsed.

## 4. The guided tour

Click "Take the tour" (or Help › ShogunAi Tour). See [Desktop app](../guide/desktop-app.md) for what each step does.

1. **Welcome**: it says what it will create and where (`~/.shogunai/tour`). Click "Start".
2. **Command-line tool**: since `shogun` isn't installed, the step shows it as missing and offers "Install command-line tool". **Check**: it doesn't say the command is installed but not on the `PATH`, and it isn't skipped. Install it. **Check**: the step reports it as available, or shows the line to add to the shell profile when it linked it in `~/.local/bin` and that folder isn't on the login shell's `PATH`; in that case add the line, restart the app from the Dock and confirm the step no longer shows it.
3. **Sample repository**: **Check**: it's created without errors, and the project "ShogunAi tour" appears with worktrees under every state ("New", "Unfinished", "Stopped", "Merged", "Inactive", a locked one and "Folder missing").
4. **Open the tour's agent**: choose the terminal. **Check**: Terminal opens in the sample's main worktree and Claude Code starts with "Start the ShogunAi tour"; accept "Yes, I trust this folder". Go through at least the first two steps of the tour with the agent and check that it speaks the app's language and waits for you between steps.

## 5. The command and the hooks

1. Open a **new** Terminal window and run `shogun --version`. **Check**: it prints the version being rehearsed.
2. In Preferences (Cmd+Comma), find Claude Code. **Check**: "Install command-line tool" is no longer offered and the hooks' "Install" is enabled. Click "Install".
3. In Terminal, run `shogun hooks status`. **Check**: it says the hooks are `installed` for the user settings.
4. Start Claude Code in any worktree of the tour (`claude` from its folder) and send it a message. **Check**: the worktree's row in the app shows the agent with a state (working, then done), which comes from the hooks.

## 6. A project with local files and `shogun new`

1. In Terminal, create a small repository with a file Git doesn't track:

    ```bash
    mkdir -p ~/projects/main-repo && cd ~/projects/main-repo
    git init -b main
    echo "local.properties" > .gitignore
    echo "sdk.dir=/placeholder" > local.properties
    git add .gitignore && git commit -m "Initial commit"
    ```

2. In the app, click "Add project", choose `~/projects/main-repo` and, under "Local files", add `local.properties`. Save. **Check**: the project appears in the sidebar with its main worktree.
3. In Terminal, from `~/projects/main-repo`, run `shogun new TASK-123`. **Check**: it prints the new worktree's path without errors, and `cat <path>/local.properties` prints `sdk.dir=/placeholder`.
4. **Check**: the app shows the new worktree under the project, as "New", without restarting it.
5. Back in the app, create a second worktree with "New worktree" on the project. **Check**: it's created and also gets `local.properties`.
6. In a new Terminal window, run `shogun doctor`. **Check**: no line starts with `fail`, and the project `main-repo` shows its local file as available. Warnings are fine as long as their fix makes sense (for example the agent tools this account doesn't use). A `fail` is a failure of the rehearsal: report it with the whole output.

## Reporting a failure

Stop at the first failure: later steps often fail because of it.

1. Note the step number (for example "4.2"), what the script expected and what you saw.
2. Take a screenshot of the app or Terminal.
3. In the app, choose Help › Copy Diagnostic Report and paste it into the report. It has the app version, macOS's version and architecture and the latest entries of the diagnostics log, with paths, the user name, project names and branches replaced by placeholders (see [Diagnostics](../guide/desktop-app.md#diagnostics)).
4. When a command failed, add the command and its full output.
5. Add the output of `shogun doctor` run in a new Terminal window: it says what's wrong and how to fix it. If the command isn't installed yet, the diagnostic report of the previous item already has the app's own check.
6. Open a GitHub issue with all of it, titled with the step, for example "Rehearsal 0.11.0, step 4.2: the tour says shogun isn't on the PATH". Before posting, check that the screenshots and outputs show nothing private; the account's user name is in paths outside the diagnostic report.

Before a release, link the issue from the release pull request. When the failure is fixed, rehearse again from the start in a new account.

## 7. Clean up

1. Log out of the rehearsal account.
2. From the developer's account, delete it in System Settings › Users & Groups, choosing to delete its home folder (or `sudo sysadminctl -deleteUser rehearsal`).
3. Delete the `.dmg` from `/Users/Shared`, and reinstall the Cask in the developer's account if it was uninstalled in step 2.
