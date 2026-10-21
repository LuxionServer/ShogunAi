## 1. Handler

- [ ] 1.1 Add `SecondaryWindowErrorHandler` next to `DiagnosticsHooks.kt`: records `unhandledEntry(error)`, ignores `CancellationException`, then calls the close and report callbacks, never rethrowing
- [ ] 1.2 Add the adapter to a Compose `WindowExceptionHandlerFactory` (opt-in for `ExperimentalComposeUiApi`)
- [ ] 1.3 Add the message to `Strings`, `EnglishStrings` and `SpanishStrings`

## 2. Wiring

- [ ] 2.1 Hoist `rememberAppMessages()` in `main.kt` to the application scope and pass it to `AppShell`
- [ ] 2.2 Provide the factory around `PreferencesWindow` and `AboutWindow` in `main.kt`, closing the window and showing the message

## 3. Tests and validation

- [ ] 3.1 Unit tests in the style of `DiagnosticsHooksTest`: records the error, closes the window, reports, doesn't rethrow, ignores cancellation
- [ ] 3.2 Run `./gradlew test`
- [ ] 3.3 Re-validate in the running app with a temporary injected failure (initial composition, recomposition, `LaunchedEffect`, About) and remove the injection before committing
- [ ] 3.4 Check the diagnostics log entry and the message in both languages

## 4. Docs

- [ ] 4.1 Update `docs/` if it describes diagnostics or the secondary windows (English and Spanish)
