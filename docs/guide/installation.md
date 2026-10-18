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

## Manual download

Download the installer for your operating system from the [latest release](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Operating system | Installer |
|---|---|
| macOS (Apple Silicon) | `-arm64.dmg` |
| macOS (Intel) | `-x64.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Install it like any other desktop app and open it.

## The `shogun` command

| How you installed ShogunAi | How to get `shogun` |
|---|---|
| Homebrew | Already installed with the Cask; `shogun --version` works in a new terminal. |
| Linux (`.deb`) | The app's launcher is the command. Link it once: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`. |
| Windows, or without installing the app | Download `shogun-<version>.zip` from the release, unzip it and add its `bin` folder to your `PATH`. It needs Java 21 or later. |

The Windows `.msi` launcher has no console, so on Windows use the zip for the command. See the [command-line reference](cli.md) for what it does.

## Optional tools

ShogunAi only needs Git. Some features use other tools when they're present, and are hidden or report a clear error when they aren't:

| Tool | Used for |
|---|---|
| Git LFS | Repositories that use LFS: without it, creating a worktree fails and asks you to install it. |
| `ps`, `lsof` (macOS, Linux) | Detecting the agents running in a worktree and listing Gradle daemons. |
| Android SDK (`emulator`, `adb`) | The [Android emulator provider](providers.md#android-emulator). |
| Xcode and `simctl` (macOS) | The [iOS simulator](providers.md#ios-simulator) and [Xcode](providers.md#xcode) providers. |
| [Herdr](https://herdr.dev) | "Open in Herdr" and the Herdr plugin; see [Using with agents](../agents.md#herdr). |
