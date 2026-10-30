# Rehearsal on a clean macOS account

The [packaged smoke test](release.md#packaged-smoke-test) checks every installer on fresh CI runners, but it opens no window and nobody reads the screens. Before each release, and before each demo on another machine, rehearse once as a new user would: on a macOS user account that has never run ShogunAi, without the developer's instructions, settings or tools, going through the guided tour to its end.

It takes about 20 minutes. Anything that doesn't behave as described below blocks the release or the demo until it's fixed or explained.

## 1. Create the clean account

1. In **System Settings → Users & Groups**, add a **Standard** user, for example "ShogunAi rehearsal". Don't sign it in to an Apple Account, and don't copy any dotfiles, `~/.claude`, `~/.shogunai`, `AGENTS.md` or shell profile into it.
2. Switch to it with fast user switching (the user menu in the menu bar), so the developer's session stays open but nothing of it is used.

A standard account matters: it can't write to `/opt/homebrew` or `/usr/local`, as on a Mac where Homebrew was installed by someone else or not at all.

## 2. Install

- **Release candidate**: download the `installer-macos-latest` (Apple Silicon) or `installer-macos-26-intel` artifact from the branch's `Release candidate` run in the Actions tab, unzip it and open the `.dmg`.
- **Published release** (before a demo): download the `.dmg` from the release page, or install with Homebrew from an account that has it, as [Installation](../guide/installation.md) says.

Drag ShogunAi to Applications (macOS asks for an administrator's password from a standard account). The app isn't notarized, so the first open shows the unverified developer warning: get past it as [Installation](../guide/installation.md) says, and check that warning is the only obstacle.

## 3. First launch

Open ShogunAi from the Dock, Launchpad or Finder, never from a terminal: only then does it get the minimal `PATH` the Dock gives it.

- The window shows the empty state with **Take the tour** and **Add project**.
- No error notice appears, and Preferences opens.

## 4. The guided tour

Choose **Take the tour** and check each step:

1. **Welcome**: it says what will be created and where (`~/.shogunai/tour`). Choose **Start**.
2. **Command-line tool**: on this account `shogun` is missing, so the step offers **Install command-line tool** and **Skip**. Install it: the app reports where it put it. In a standard account without a writable folder on the `PATH`, that's `~/.local/bin`, and the step shows the exact line to add to the shell profile. Add it, as a user would, and continue. If `shogun` was installed by the Homebrew Cask and is on the login shell's `PATH`, this step must not appear at all.
3. **Creating the sample**: it finishes without errors, the sidebar lists "ShogunAi tour" and its view, grouped by state, shows worktrees under "Needs you", "New", "Stopped", "Merged", "Inactive" and "Folder missing", and one row labelled "locked" (the labels in the app's language).
4. **Open the agent**: the step offers the ways available on this machine. With no agent installed, choosing one must explain what's missing instead of failing silently. To walk the tour's script, install Claude Code in this account, sign in, and open the agent in the terminal: it asks to trust the sample's folder, then starts the tour in the app's language. Follow it through status, cleanup, provisioning, the command line, hooks, environment and providers.

## 5. Beyond the tour

- **Claude Code hooks**: in Preferences, the Claude Code section offers **Install the hooks** once the command is usable (after restarting the app if it was installed into a folder that wasn't on the `PATH`). Install them and check they read as installed.
- **A project with local files**: create a small Git repository with an ignored `.env.local`, add it with **Add project**, list `.env.local` as a local file, create a worktree, and check the file is in it. Then, in a new terminal, run `shogun new second-task` in the repository and check the same file is copied.
- **Copy Diagnostic Report** (Help menu) copies a report without the account's paths or names beyond what it needs.

## 6. End and clean up

1. In the tour project's banner, choose **End tour** and confirm: the project disappears and `~/.shogunai/tour` no longer exists.
2. Log out, and from the developer's account delete the user in **System Settings → Users & Groups**, choosing to delete its home folder. Nothing it created lives outside it, except the app in `/Applications`, which the next release replaces.

## When something fails

Copy the diagnostic report from the Help menu, note the step and what was shown, and fix it in its own change before releasing; then rehearse again from a new account. If the smoke test passed but the rehearsal failed, add what was missed to the smoke test or to the end-to-end tests in the same fix, so it's caught automatically next time.
