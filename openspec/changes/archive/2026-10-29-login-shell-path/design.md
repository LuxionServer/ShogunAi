# Design

## Context

See proposal.md for the motivation. Where the app's process `PATH` is read today:

- `AppContainer` builds `CommandLineTool` with `pathFolders` split from `System.getenv("PATH")`. `CommandLineTool.isOnPath` compares the folder of the `shogun` it found against that list, both for `status()` and for `installLink`, which picks the first writable folder of `userBinFolders` (`/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin`, `~/.cargo/bin`) that is on the `PATH`, else `~/.local/bin`.
- `ExecutableLocator` defaults its `pathFolders` to the same process `PATH` and adds `ToolCatalog.userBinFolders`, so it finds `/opt/homebrew/bin/shogun` even from the Dock; `CommandLineTool` then rejects it as not on the `PATH`, which gives `INSTALLED_NOT_ON_PATH`.
- `ProcessBuilderShellCommandExecutor` already prepends `/opt/homebrew/bin`, `/opt/homebrew/sbin` and `/usr/local/bin` to every child process's `PATH` (`HomebrewPath.merge`).

`ps eww` on the installed app opened from Finder on this Mac shows `PATH=/usr/bin:/bin:/usr/sbin:/sbin`, `SHELL=/bin/zsh` and `HOME` set. Running `/bin/zsh -l -i -c 'printf …"$PATH"'` from that same environment prints the user's full `PATH` in about 30 ms.

## Goals / Non-Goals

**Goals:**

- The app's `shogun` status and install decisions use the login shell's `PATH` on macOS and Linux.
- Reading it never blocks start-up and never hangs the app.
- The domain stays testable with fakes: the shell is behind a port.

**Non-Goals:**

- One host environment for all the code (shell, home, `PATH` read in one place and used everywhere, including the child processes the app starts): that is the roadmap's next bullet.
- Changing the CLI, which runs from a terminal and already has the user's `PATH`.
- Windows, whose GUI apps already receive the user's `PATH`.

## Decisions

### A `LoginShellPath` port, read once and cached

`domain/io/LoginShellPath` is a `fun interface` with `folders(): List<String>`. Its implementation, `infrastructure/ShellLoginShellPath`, resolves the folders once (a synchronized `lazy`) and returns the cached list afterwards. `AppContainer` starts the resolution on a daemon thread as it's built, so it is usually done before the first status check; a caller that arrives earlier waits at most the time limit.

`CommandLineTool` and `ExecutableLocator` take `pathFolders: () -> List<String>` instead of a list. A provider rather than a value lets `AppContainer` build them before the shell has answered, without blocking start-up. `ExecutableLocator`'s default stays the process `PATH`, so the CLI and every other caller behave as before.

Alternative considered: a `suspend` port. `ExecutableLocator.find` isn't `suspend` and is used by the editor resolver and the tool detector, so a `suspend` port would ripple through code this change doesn't need to touch. The blocking wait is bounded by the time limit and happens once.

### How the shell is run

`$SHELL -l -i -c 'printf "\n__SHOGUNAI_PATH__%s__SHOGUNAI_PATH__\n" "$PATH"'` through `ShellCommandExecutor.executeWithin` with a 5-second limit, as argv. Login (`-l`) reads `.zprofile`/`.bash_profile`/`.profile`, where Homebrew's `shellenv` usually goes; interactive (`-i`) also reads `.zshrc`/`.bashrc`, where many people set their `PATH`. The executor already redirects standard input from the null device and kills the process and its descendants on timeout, so a shell waiting for input or a slow rc file can't hang the app.

The value is taken from between the markers, because interactive rc files often print banners or warnings. The markers' line is taken whatever the exit code, since an rc file with an error makes the shell exit non-zero after printing it. `printf` and `"$PATH"` behave the same in sh, bash, zsh and fish (fish joins `PATH` with `:` when quoted).

Alternative considered: `env` or `printenv PATH`. They add a dependency on a binary's location, and `printf` is a builtin in every shell above.

### Result and fallback

- Read: the login shell's folders, followed by the process `PATH`'s folders, without duplicates. The process folders stay so nothing the app could see before is lost.
- Not read (no `SHELL`, the shell can't start, it times out, no markers, or an empty value): the process `PATH`'s folders followed by `ToolCatalog.userBinFolders`. Adding the usual install folders keeps a Homebrew `shogun` available in the worst case, at the price of treating `~/.local/bin` as on the `PATH` when the shell can't confirm it.
- Windows: the process `PATH`, without starting anything.

### Where it is used

`AppContainer` passes the same `ShellLoginShellPath` to `CommandLineTool` (status and install) and to the two `ExecutableLocator`s it builds (the one that finds `shogun` and the editor resolver's, which also helps finding `code` or `idea` installed in folders only the shell's `PATH` has). Child processes keep `HomebrewPath.merge`; see the investigation below for why that isn't where the second failure broke.

## Investigation of the second failure

On the demo Mac, a new worktree ended up without its local files, with no error. Findings:

- Creating a worktree from the app provisions it in-process (`CreateWorktreeUseCase` → `addWorktreeAndCopySecrets`, copying with `FileManager`); only `git` runs as a child process, with `HomebrewPath.merge`. A declared file missing from the base folder throws `SecretFileNotFound` before anything is created. Nothing on this path depends on `shogun` being on the `PATH`, and its failures are reported.
- Claude Code's hooks call `shogun hook worktree-create`. When `shogun` can't be run there, Claude Code's `WorktreeCreate` hook fails and the worktree isn't created at all, so that isn't a silent "no local files" either.
- But with the first failure, `CommandLineToolStatus.usableByHooks` is false, so `ClaudeHooksState.canChangeHooks` is false and the app refuses to install Claude Code's hooks. Without them, Claude Code creates its worktrees (`claude --worktree`, the agent entering a worktree) with plain `git worktree add`: no local files, no ports, no environment, and no error. Combined with the tour refusing to start, which is what pushes a new user to install the hooks, this matches what was seen.
- Orca resolves the login shell's environment itself (its terminal host on this Mac runs with the user's full `PATH`), so its `orca.yaml` scripts find `shogun`. Herdr started by the app gets `HomebrewPath.merge`, which covers a Homebrew `shogun`; a `shogun` only in `~/.local/bin` would be missed there, which the next roadmap bullet (one host environment, including child processes) addresses.

Conclusion: the most likely cause is the same root cause, through the hooks the app wouldn't install. Fixing the status makes the hooks installable, so no extra change is needed here. It can't be confirmed without the demo Mac; if it reappears with the hooks installed, it belongs to the roadmap's "checking before acting" bullet.

## Risks / Trade-offs

- [An rc file with side effects runs once at start-up] → It's what every terminal the user opens does; it runs once, with no input and a time limit.
- [A slow shell delays the first status check up to 5 seconds] → It starts in the background at start-up; after the limit the fallback is used.
- [The fallback treats `~/.local/bin` as on the `PATH` when the shell can't be read] → Only when the shell can't confirm anything; the alternative, the minimal `PATH`, is the bug being fixed.
- [The `PATH` is read once, so a change to the profile isn't seen until the app restarts] → Same as a terminal that was already open; the installation that the app makes itself lands in a folder already on that `PATH`.
