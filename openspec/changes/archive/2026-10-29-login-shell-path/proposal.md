# Proposal

## Why

The desktop app, opened from the Dock or Finder on macOS, gets the minimal `PATH` (`/usr/bin:/bin:/usr/sbin:/sbin`), and it judges whether `shogun` is "on the `PATH`" against that. A demo on another Mac showed the result: a `shogun` installed by Homebrew in `/opt/homebrew/bin` was reported as installed but not on the `PATH`, the guided tour refused to start, Claude Code's hooks couldn't be installed from the app and "Install command-line tool" always fell back to `~/.local/bin`. It is the first bullet of roadmap item 1, "Reliability on any machine".

## What Changes

- On macOS and Linux, the desktop app reads the login shell's `PATH` once at start-up (the user's `$SHELL`, run as a login and interactive shell, with a time limit) and uses it, instead of its own process `PATH`, to tell whether `shogun` is available and to choose the folder "Install command-line tool" links it into.
- When the login shell can't be read (it fails, hangs past the limit or prints no `PATH`), the app falls back to its process `PATH` plus the folders where `shogun` is usually installed (`/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin`, `~/.cargo/bin`), so a Homebrew install still reads as available.
- The executable lookup the app uses to find `shogun` and the editors searches the same folders.
- Windows keeps its current behaviour: its GUI apps already get the user's `PATH`.
- Regression tests reproduce both failures seen in the demo before fixing them.
- The second failure seen in the demo (a worktree created without its local files, with no error) is investigated and its cause recorded in `design.md`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `command-line-tool-setup`: on macOS and Linux "on the `PATH`" means the login shell's `PATH`, both to detect the tool and to choose where to install it, with a fallback when the login shell can't be read.

## Impact

- Core: a new port for the login shell's `PATH` and its implementation on `ShellCommandExecutor`; `CommandLineTool` and `ExecutableLocator` take their `PATH` folders from a provider instead of a fixed list.
- Desktop app: `AppContainer` reads the login shell's `PATH` in the background at start-up and passes it to `CommandLineTool` and the `ExecutableLocator`s it creates. No new strings.
- CLI: unchanged; it runs from a terminal and keeps using its process `PATH`.
- Docs: the installation guide and the infrastructure architecture page, in English and Spanish.
