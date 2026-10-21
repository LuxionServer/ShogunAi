# Proposal

## Why

An exception inside the Preferences window (or About) closes the whole app with no dialog: both windows are composed in the application's own composition, and Compose's default window exception handler rethrows into the application's recomposer, which exits the process. A bug in a secondary window should never take down the main window and the user's work.

## What Changes

- Preferences and About get their own window exception handler that records the error in the diagnostics log, closes only that window and does not rethrow.
- The main window tells the user, with a message, that the window closed because of an error and that it was recorded in the diagnostics log.
- Validated empirically on Compose Multiplatform 1.11.1 with a temporary injected failure: not rethrowing is enough for the three paths (initial composition, a later recomposition and a `LaunchedEffect`); the main window stays alive in all of them.
- Out of scope: the native crash seen on another machine, which left an empty diagnostic report.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-shell`: an error in the Preferences or About window closes only that window and is reported, instead of closing the app.

## Impact

- `desktopApp`: `main.kt` (message host hoisted so the handler can reach it), `PreferencesWindow.kt`, `AboutWindow.kt`, a new window exception handler next to `diagnostics/DiagnosticsHooks.kt`, `Strings`, `EnglishStrings`, `SpanishStrings`.
- Tests under `desktopApp/src/test`, in the style of `DiagnosticsHooksTest`.
- No changes to the core module, CLI or the diagnostics log format.
