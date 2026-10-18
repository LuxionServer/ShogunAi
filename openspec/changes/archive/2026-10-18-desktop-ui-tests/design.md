## Context

`desktopApp` tests today are plain JUnit tests over view models and pure helpers (`SelectorTest` only covers `groupOptions`). Compose Multiplatform 1.11.1 ships `compose.uiTest` for desktop, which runs composables headlessly through `runComposeUiTest` without opening a window.

## Goals / Non-Goals

**Goals:**
- Headless UI tests that run in `./gradlew test` on every platform CI uses.
- Cover the three guards left manual by `fix-validation-findings`.

**Non-Goals:**
- Screenshot or visual regression testing.
- End-to-end tests of the packaged app.
- Covering every screen; only the reusable selectors and the creation form guard.

## Decisions

- Use `@OptIn(ExperimentalTestApi::class) runComposeUiTest` with `compose.uiTest` (`testImplementation(compose.uiTest)`), JUnit 4 as in the existing tests. Alternative: `createComposeRule`, which needs `compose.desktop.uiTestJUnit4`; the standalone API avoids the rule and keeps tests self-contained.
- Find nodes by text and semantics (`onNodeWithText`, `assertIsNotEnabled`, `performSemanticsAction(OnClick)`), so the disabled-option test exercises the accessibility path that a mouse click doesn't.
- The "Create worktree" guard is tested through the smallest composable that can host it; if the button is only reachable inside `WorktreeScreen`, extract its enabled computation into an `internal` function and test it plus the screen wiring, instead of building the full view model graph.

## Risks / Trade-offs

- Headless Compose tests need a JDK with AWT support; CI runners already run the desktop tests, so this should hold. If it fails there, the fallback is `-Djava.awt.headless=false` via a virtual display.
- Text-based lookups tie tests to English strings; tests pass literal labels to the composables, so they don't depend on the localization layer.
