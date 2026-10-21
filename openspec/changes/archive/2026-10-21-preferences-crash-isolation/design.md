# Design

## Context

Preferences (`Window`) and About (`DialogWindow`) are composed inside the same `application {}` as the main window, so they share its Recomposer. Compose Multiplatform 1.11.1 routes errors of a window to `LocalWindowExceptionHandlerFactory`; the default factory shows a dialog, sends `WINDOW_CLOSING` and rethrows, and the rethrow ends in the application's Recomposer and `exitProcess`. The existing `installUncaughtExceptionLogging` records the error but cannot prevent the exit.

The main window's `AppMessages` is created inside the main `Window` content in `main.kt`, so code outside it can't show messages today.

## Goals / Non-Goals

**Goals:**
- An error in Preferences or About closes only that window, is recorded and is reported in the main window.
- The handler's decision logic is testable without a window.

**Non-Goals:**
- Isolating the main window itself.
- The native crash seen on another machine.
- Running secondary windows in a separate composition.

## Decisions

**Own `WindowExceptionHandlerFactory`, provided to the secondary windows, never rethrowing.** Validated on this version with a temporary injected `error(...)` and a handler that only logged and closed the window: initial composition, a recomposition triggered 3 s after opening, and a `LaunchedEffect` all reached the handler; the main window stayed alive each time. So a separate composition or `runCatching` around domain calls is not needed. Alternative rejected: wrapping content in `runCatching`, because composables can't be wrapped in try/catch and effects would still escape.

**Testable core as a plain class.** `SecondaryWindowErrorHandler(log, onClosed, onReport)` with `handle(error)`: records `unhandledEntry(error)` in the `DiagnosticsLog`, then calls the window's close callback and the report callback. A thin adapter turns it into the Compose `WindowExceptionHandlerFactory`. Next to `DiagnosticsHooks.kt`; tested with `FakeDiagnosticsLog` like `DiagnosticsHooksTest`.

**Close from the handler on the UI thread after the handler returns.** The close callback sets `preferencesOpen`/`aboutOpen` to false, as seen working in the experiment; `CancellationException` is not treated as an error.

**Hoist `rememberAppMessages()` to the application scope** in `main.kt` and pass the same instance to `AppShell`, so the handler can call `messages.show(strings.secondaryWindowError)`. The message text is a new string in `Strings`, `EnglishStrings` and `SpanishStrings`, read at report time from the current `appearance.strings`.

**One handler for both windows**, provided with `CompositionLocalProvider` around each window call in `main.kt`.

## Risks / Trade-offs

- [`LocalWindowExceptionHandlerFactory` is `@ExperimentalComposeUiApi`] → opt-in at the single use site; the compile fails on API change, and the design validation can be repeated with the temporary injection.
- [Swallowing an error could leave the window half-torn] → the window is closed in every case and its state is recreated on reopening.
- [An error loop: reopening throws again] → each occurrence closes the window and shows the message; nothing retries automatically.
- [Hoisting `AppMessages` moves its coroutine scope to the application] → it only launches snackbar display jobs; they already outlive recompositions.

## Open Questions

- Whether the message should include an action that copies the diagnostic report; can be added later without changing the isolation.
