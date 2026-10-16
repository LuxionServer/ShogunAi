# Proposal

## Why

Gradle reuses an idle daemon, but a busy one can't be shared: when agents build in three worktrees at the same time, three daemons start, each with the heap the repository's `gradle.properties` asks for (Android projects commonly set 4–8 GB), plus their Kotlin compile daemons, and they stay alive for three hours after the last build. The Gradle provider (#70) shows that cost and stops idle daemons by hand; the per-worktree environment (#71) now gives a way to change how every worktree builds without touching tracked files. A repository should be able to say, once, how much memory each worktree's daemons may use and how soon they go away.

## What Changes

- The Gradle provider declaration accepts optional settings: `{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1g" } } }`. Each one is optional and validated; `{}` keeps today's behavior exactly.
- When at least one setting is declared, every worktree's environment gains `GRADLE_OPTS`, delivered like any other variable (`shogun env`, the `SessionStart` hook, `envFile`). It carries Gradle system properties that override `gradle.properties` for builds started from that shell (verified with Gradle 9: `-Dorg.gradle.jvmargs` in `GRADLE_OPTS` wins over the project's `gradle.properties`, and `-Dorg.gradle.daemon.idletimeout` sets the daemon's idle timeout):
  - `daemonMaxHeap` sets the daemon's `-Xmx` while keeping the other JVM arguments the repository (or the user's Gradle home) already sets, such as `-Dfile.encoding` or metaspace limits.
  - `idleTimeoutMinutes` sets `org.gradle.daemon.idletimeout`.
  - `kotlinDaemonMaxHeap` sets the Kotlin compile daemon's `-Xmx` through the `kotlin.daemon.jvmargs` project property.
- Every worktree, the main one included, gets the same values, so the daemons stay compatible and are reused across worktrees instead of multiplying.
- A `GRADLE_OPTS` declared in `env` keeps taking precedence over the provider's, as for any provider variable.
- The Gradle daemons section of the desktop app states the declared settings, so the user can tell the limits apply.
- Docs: the Gradle part of the Android/KMP documentation and the agents page explain the settings, what they don't cover (builds started from an IDE that doesn't inherit the shell, a user's own `GRADLE_OPTS` being replaced) in both languages; the roadmap item is removed when the change is archived.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `repository-provisioning`: the Gradle provider declaration gains its optional settings and their validation.
- `gradle-provider`: the provider contributes `GRADLE_OPTS` to every worktree's environment from the declared settings, and the daemons section shows them.

## Impact

- `core`: `GradleProvider` becomes a data class with optional settings; parsing and validation in `RepositoryProvisioningFiles`; a Gradle environment contribution in `MachineProviderContributions` that reads the effective `org.gradle.jvmargs` (user's Gradle home, then the repository) through `FileManager.readProperties`.
- `desktopApp`: the declared settings in the Gradle daemons section, strings in English and Spanish.
- `cli`: no new commands; `shogun env` and the hooks deliver the new variable through the existing environment resolution.
- Repositories that declare `"gradle": {}` or no Gradle provider see no change; older versions ignore the new fields.
