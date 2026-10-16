# Proposal

## Why

Android builds install on every connected device unless `ANDROID_SERIAL` names one: the Android Gradle plugin's `installDebug`, `uninstall*` and `connectedAndroidTest` all pass `System.getenv("ANDROID_SERIAL")` to their device provider and filter devices by it (checked in AGP 9.0.1's bytecode). With two worktrees and two emulators, each agent installs on both. The per-worktree environment (#71) can now deliver a serial to each agent, so an emulator provider can give every worktree its own emulator. ShogunAi is used for any stack, so none of this may appear or run unless a repository asks for it.

## What Changes

- `.shogunai.json` accepts `"providers": { "emulator": {} }`, optionally with a preferred AVD (`{ "avd": "Pixel_8_API_35" }`) used when it exists on the machine. Without the provider, nothing Android-related is shown, detected or run.
- Machine settings per project (stored with the project, never in the repository): the AVD to use and whether to run the emulator without a window. The project configuration screen shows them only for repositories that declare the provider. The CLI accepts `--avd` and `--no-window` for a single start.
- AVD choice: CLI flag, then the project's machine setting, then the repository's preference if that AVD exists, then the first AVD the SDK lists.
- The Android SDK is found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, the repository's `local.properties` `sdk.dir`, then the platform's default location; a missing SDK or AVD is a clear error, only when the emulator is actually needed.
- Each worktree reserves an emulator console port (even ports 5554–5584, the range `adb` detects, with the next port also free), stored like other reservations, and its environment gains `ANDROID_SERIAL=emulator-<port>`, delivered by `shogun env`, the `SessionStart` hook and `envFile`.
- `shogun emulator start|stop|status [<path>]` starts the worktree's emulator on its port in read-only mode (so one AVD serves several worktrees), waits until it has booted, stops it, or reports it. Starting is always on demand.
- Port reservations become unique across every repository on the machine, not just within one: a machine-level index lists the worktrees with reservations and drops entries whose worktree is gone. It fixes the cross-repository limit of #71 and matters most for the 16 emulator ports.
- Desktop app: for repositories with the provider, each worktree row shows its emulator (stopped, booting, running) with Start and Stop; the cleanup dialog offers, checked by default, to stop the emulators of the worktrees it removes; `shogun clean --stop-emulators` does the same.
- Docs and roadmap: Android/KMP page section, agents recipes (Claude learns from the `SessionStart` context how to start its emulator), architecture pages, both languages.

## Capabilities

### New Capabilities
- `emulator-provider`: declaring the provider, machine settings and AVD choice, SDK discovery, emulator port reservation and `ANDROID_SERIAL`, starting, stopping and reporting a worktree's emulator, and stopping emulators on cleanup.

### Modified Capabilities
- `worktree-environment`: port reservations are unique across the machine's repositories; providers can add variables to a worktree's environment.
- `repository-provisioning`: the `emulator` provider declaration and its validation.
- `project-configuration`: emulator machine settings, shown only for repositories that declare the provider.
- `command-line-tool`: `shogun emulator start|stop|status` and `shogun clean --stop-emulators`.
- `worktree-overview`: the emulator state and actions in the worktree row.
- `worktree-cleanup`: the option to stop the emulators of removed worktrees.

## Impact

- `core`: `EmulatorProvider` declaration, `EmulatorSettings` in `ProjectConfig`, an `AndroidSdk` port (SDK discovery, AVD listing, starting and stopping emulators, device states through `adb`), a machine-level reservation index, port requests from providers in `ResolveWorktreeEnvironmentUseCase`, emulator use cases.
- `cli`: `emulator` command, `clean --stop-emulators`, help.
- `desktopApp`: row state and actions, project configuration section, cleanup option, strings in both languages.
- Repositories without the provider see no change; older versions ignore the new fields.
