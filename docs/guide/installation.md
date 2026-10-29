# Installation

ShogunAi ships as a desktop app with the `shogun` command built in. You don't need a JDK or any development tools: every installer bundles its own runtime.

## macOS (Homebrew)

```bash
brew tap LuxionServer/shogunai
brew install --cask shogunai
```

Homebrew picks the installer for your Mac's architecture (Apple Silicon or Intel) automatically, and links the `shogun` command.

!!! note "Unverified developer warning"
    The app isn't notarized by Apple. If macOS warns that "the developer cannot be verified" the first time you open it, open it with right click → Open, or run:

    ```bash
    xattr -dr com.apple.quarantine "/Applications/ShogunAi.app"
    ```

## Windows (winget)

```powershell
winget install LuxionServer.ShogunAi
```

`winget upgrade LuxionServer.ShogunAi` updates it to each new release. This installs the app only; for the `shogun` command see below.

## Windows (Scoop)

```powershell
scoop bucket add shogunai https://github.com/LuxionServer/scoop-shogunai
scoop install shogunai
```

Scoop installs the app without administrator rights, adds a Start menu shortcut and provides the `shogun` command, which uses the runtime bundled in the app (no Java needed). `scoop update shogunai` updates both.

## Manual download

Download the installer for your operating system from the [latest release](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Operating system | Installer |
|---|---|
| macOS (Apple Silicon) | `-arm64.dmg` |
| macOS (Intel) | `-x64.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Install it like any other desktop app and open it.

## Updates

The desktop app tells you when a newer release is out and reminds you how to update it for the way you installed it; see [Updates](desktop-app.md#updates) for what it checks and how to turn it off. With Homebrew, Scoop and winget you can also update whenever you like: `brew upgrade --cask shogunai`, `scoop update shogunai` or `winget upgrade LuxionServer.ShogunAi`. A new release can reach those package managers a few minutes after it is published.

## The `shogun` command

The desktop app can install it for you, whichever way you installed ShogunAi: open Preferences, find Claude Code and activate "Install command-line tool". It uses the app's own bundled runtime, so it needs no Java and no download, and the same button appears next to the hooks' "Install" when the command is missing (the hooks can't be installed until it is available). On macOS and Linux it links the app's launcher as `shogun` in a user-writable folder already on your `PATH`, or in `~/.local/bin` (the app shows the line to add to your shell profile if that folder isn't on your `PATH`). There, "your `PATH`" is your login shell's: an app opened from the Dock or Finder gets a minimal `PATH`, so at start-up the app asks your shell (`$SHELL -l -i`) for its own, and if the shell doesn't answer within a few seconds it uses its own `PATH` plus `/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin` and `~/.cargo/bin`. The app reads it once, so restart it after changing your shell profile. On Windows it writes `shogun.cmd` in `%USERPROFILE%\.shogunai\bin` and adds that folder to your user `PATH`, with no administrator rights; restart your terminal and Claude Code afterwards. "Remove command-line tool" undoes only what the app created. A development build (`./gradlew run`) has no launcher to expose and shows the manual steps instead.

By hand:

| How you installed ShogunAi | How to get `shogun` |
|---|---|
| Homebrew | Already installed with the Cask; `shogun --version` works in a new terminal. |
| Scoop | Already installed with the app; `shogun --version` works in a new terminal, with no Java needed. |
| Linux (`.deb`) | The app's launcher is the command. Link it once: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`. |
| Windows (winget or `.msi`), or without installing the app | Download `shogun-<version>.zip` from the release, unzip it and add its `bin` folder to your `PATH`. It needs Java 21 or later. |

The Windows `.msi` launcher has no console, so with winget or the `.msi` use the zip for the command, or install with Scoop instead. See the [command-line reference](cli.md) for what it does.

## Optional tools

ShogunAi only needs Git. Some features use other tools when they're present, and are hidden or report a clear error when they aren't:

| Tool | Used for |
|---|---|
| Git LFS | Repositories that use LFS: without it, creating a worktree fails and asks you to install it. |
| `ps`, `lsof` (macOS, Linux) | Detecting the agents running in a worktree and listing Gradle daemons. |
| Android SDK (`emulator`, `adb`) | The [Android emulator provider](providers.md#android-emulator). |
| Xcode and `simctl` (macOS) | The [iOS simulator](providers.md#ios-simulator) and [Xcode](providers.md#xcode) providers. |
| [Herdr](https://herdr.dev) | "Open in Herdr" and the Herdr plugin; see [Using with agents](../agents.md#herdr). |
| [Orca](https://onorca.dev) 1.4.243 or newer | "Open in Orca", Orca's agent states and the `orca.yaml` hooks; see [Using with agents](../agents.md#orca). ShogunAi looks for `orca` (and, on Linux, only `orca-ide`, since `orca` there is usually GNOME's screen reader). Opening a worktree may set its repository to show non-Orca worktrees in Orca. Orca's agent states come from its own Claude Code hooks; `orca agent hooks off` removes them if you use Orca without its states, and ShogunAi then falls back to Claude Code's session registry. |
