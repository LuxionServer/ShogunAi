# Proposal

## Why

ShogunAi's desktop app is on Compose Multiplatform 1.11.1. The planned `background-app-control` change needs Compose Hot Reload's experimental MCP server (`./gradlew :desktopApp:hotMcpServer`), which only ships with Compose Multiplatform 1.12, so the toolkit has to move to 1.12.x and land in `develop` before that change is implemented.

## What Changes

- Bump `composeMultiplatform` in `gradle/libs.versions.toml` from `1.11.1` to `1.12.2`, the latest 1.12 patch release.
- Apply whatever the bump forces (compatibility fixes, replacements for newly deprecated APIs), since every compiler warning is an error; behaviour stays unchanged.
- Keep Kotlin (`2.4.0`, which also versions the Compose compiler plugin), `androidx-lifecycle` (`2.11.0`) and `material-icons-extended` (`1.7.3`) as they are unless the bump requires otherwise.
- No new capability and no requirement change: `skip_specs: true`.

Not in scope: anything of `background-app-control` (no `desktopApp` provider, no `shogun headless`, no `.mcp.json`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a dependency upgrade with no spec-level behaviour change.

## Impact

- `gradle/libs.versions.toml` (version catalog); possibly `desktopApp` sources or tests if the bump surfaces deprecations or API changes.
- Dependencies resolved by the Compose Gradle plugin: Compose runtime/UI/foundation/material3, Skiko, Compose UI test, Compose Hot Reload.
- Release packaging (`compose.desktop` native distributions with ProGuard) is built with the new plugin and must keep working.
