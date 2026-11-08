# Proposal

## Why

The roadmap's "Reliability on any machine" asks for the desktop app's coverage outside its composables to reach 100%, one package per change. `coverage-desktop-texts` covered the texts; the next package in its plan is the project settings (`ui/projectconfig`), where 277 lines and 259 branches no test runs today decide what a project saves, what is written to the repository's `.shogunai.json` and which machine-only settings are shown. A regression there loses a user's configuration or writes the wrong file without any test noticing.

## What Changes

- Unit tests for the project settings view model and its models cover the paths no test reaches today: every provider switch and suggestion, environment rows, repository approval and revocation, the emulator and simulator sections in every state, the base path inspection, the unsaved-changes guard, the local file picker and the save outcomes.
- Compose UI tests (`runComposeUiTest`, like the existing `*UiTest` classes) cover the lambdas the compiler moves out of the settings composables (`onClick`, `onValueChange`, dialog and section content), which Kover counts: the settings form and its save bar, section index, preview and discard dialogs, the repository configuration section, the agents section, and the emulator and simulator sections.
- Branches no input reaches are rewritten in an equivalent form instead of being excluded, and listed with their reason in design.md.
- The desktop floors in `desktopApp/build.gradle.kts` go up to the whole percentages measured after this change.
- The roadmap item names what is left after this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None: the tests check the behavior the `project-configuration` and `local-file-picker` specs already require. If they find a defect, its fix and the requirement it breaks are added to this change's specs before it is fixed.

## Impact

- Tests: new and extended tests under `desktopApp/src/test/kotlin/app/luxion/shogunai/ui/projectconfig/`.
- Production code: only equivalent rewrites of unreachable branches in `ui/projectconfig`, and fixes for defects the tests find, if any.
- Build: `desktopApp/build.gradle.kts` (floors).
- Docs: `docs/roadmap.md` and `docs/roadmap.es.md`.
- Parallel work: `machine-views-polish` and `active-multirepo-rows` change other desktop packages and may also raise or touch the floors in `desktopApp/build.gradle.kts`; that conflict is resolved and the floors re-measured when bringing in `develop` before the pull request.
