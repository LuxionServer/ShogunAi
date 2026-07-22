## 1. Implementation

- [x] 1.1 Replace the `System Events`/Cmd+T AppleScript in `TerminalCommandBuilder.macosTerminalCommand` with a plain `tell application "Terminal" to do script "<command>"` + `activate`, removing the Accessibility-permission dependency
- [x] 1.2 Update the doc comment above `macosTerminalCommand` to explain the new approach and why the old one was unreliable

## 2. Tests

- [x] 2.1 Update `TerminalCommandBuilderTest`'s macOS Terminal test to assert the new argv shape (`do script`, no `System Events`) instead of the old Cmd+T/tab-forcing assertions
- [x] 2.2 Run `./gradlew :desktopApp:test` and confirm all tests pass

## 3. Verification

- [x] 3.1 Manually reproduce the original failure via `osascript` to confirm root cause (Accessibility permission denial aborts the whole script)
- [x] 3.2 Manually run the new `do script`-based AppleScript against a real `Terminal.app` to confirm it opens a new window with exit code 0 and no permission prompt
