# Proposal

## Why

Gradle already shares its dependency caches and wrapper distributions across every checkout of a machine, so parallel worktrees don't multiply that disk usage. What they do multiply is memory: when several agents build in different worktrees at the same time, each build starts its own Gradle daemon (and usually a Kotlin compile daemon), and each one stays alive for hours after the build ends, holding its heap. Branches with different Gradle versions or JVM arguments make it worse, because their daemons can never be reused. Nothing shows these daemons or ties them to the worktree that started them. This change adds the first Android/KMP/JVM provider: a Gradle provider, declared in the repository, that makes those daemons visible and lets the user stop the idle ones, including when cleaning up worktrees.

## What Changes

- `.shogunai.json` gains a `providers` object, keyed by provider id. `"providers": { "gradle": {} }` declares the repository as a Gradle build. Unknown providers are ignored; a known provider with a value that isn't an object makes the file invalid, as a wrong `copy` does today.
- ShogunAi lists the Gradle daemons and Kotlin compile daemons running on the machine with their memory use. For each Gradle daemon it also shows its Gradle version, whether it's busy or idle (and since when), and the worktree of its last build (a worktree of this repository, a worktree that no longer exists, or another project).
- The user can stop idle daemons, one by one or all at once. A busy daemon is never stopped, and a Kotlin daemon is only stopped while no Gradle daemon is busy.
- When the repository declares the Gradle provider, the worktree cleanup offers to also stop the idle Gradle daemons whose last build was in one of the removed worktrees.
- When the repository declares the Gradle provider and doesn't enable Gradle's build cache (`org.gradle.caching=true`), ShogunAi says so. With the build cache on, a new worktree's first build can reuse outputs built in another worktree, so it doesn't start cold.
- Desktop app: a "Gradle daemons" section on the worktree screen of projects that declare the provider.
- CLI: `shogun gradle` lists the daemons (and the build cache notice inside a Gradle repository), `shogun gradle stop [<pid>...]` stops idle ones, and `shogun clean --stop-gradle-daemons` stops the daemons of the removed worktrees.
- The roadmap gains a "Per-worktree environment for agents" item: emulators, ports or per-worktree Gradle settings all need variables that reach the agent's shell, which ShogunAi doesn't start. That is planned as its own change before the emulator provider.

## Capabilities

### New Capabilities
- `gradle-provider`: the Gradle provider: detecting Gradle and Kotlin daemons, attributing each Gradle daemon to the worktree of its last build, stopping idle daemons safely, and the build cache notice.

### Modified Capabilities
- `repository-provisioning`: `.shogunai.json` gains the `providers` field and its validation rules.
- `worktree-cleanup`: the cleanup dialog offers to stop the idle Gradle daemons of the removed worktrees when the repository declares the Gradle provider.
- `command-line-tool`: new `shogun gradle` and `shogun gradle stop` commands, and the `--stop-gradle-daemons` option of `shogun clean`.

## Impact

- `core`: provider declaration in `RepositoryProvisioning` and `RepositoryProvisioningFiles`; a new port to inspect daemons with its process- and log-based implementation; use cases to list and stop daemons and to check the build cache; `CleanUpWorktreesUseCase` reports the removed worktrees so the daemons can be matched to them.
- `desktopApp`: "Gradle daemons" section on the worktree screen, cleanup dialog option, strings in English and Spanish.
- `cli`: `gradle` command parsing and output, `clean` option.
- Docs: user-facing `.shogunai.json` and CLI documentation, architecture pages (both languages), roadmap.
- Platforms: daemon inspection relies on `ps` and Gradle's daemon logs, like the running-agent detection relies on `lsof`; on systems without them the section says it's unavailable and nothing else is affected.
