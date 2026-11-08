# Design

## Context

The desktop app's texts live in `ui/i18n`: the `Strings` interface (about 600 properties and 190 functions), its two tables `EnglishStrings` and `SpanishStrings`, the error descriptions (`EnglishErrors`, `SpanishErrors`, `ErrorDescription`) and the installation texts. `StringsTest` checks a selection of them by hand. Kover counts every property initializer and every function body, so each text no test reads is an uncovered line, and each `if (count == 1)` or `when` over an enum is a pair of branches.

Measured on macOS on `develop` at 24b64c40, with the end-to-end tests:

| Package | Lines uncovered | Branches uncovered |
|---|---|---|
| `ui/i18n` | 509 of 2,652 | 475 of 1,358 |
| whole desktop app outside composables | 1,853 of 11,428 (83.8%) | 2,367 of 7,176 (67.0%) |

## Goals / Non-Goals

**Goals:**

- 100% of lines and branches of `ui/i18n`, except two branches that need a change in `core` (see below).
- Raise the desktop floors to the whole percentages measured after this change.
- Write down how the rest of the desktop app gets to 100%.

**Non-Goals:**

- Covering other packages; they are the next changes (below).
- Reviewing the wording of the translations.

## Decisions

### Reflective tests for every text and every error

Two tests list what they check with `kotlin-reflect` instead of naming each case:

- `EveryTextTest` evaluates every member of `Strings` in both languages. Each function is called with every combination of sample arguments and the test asserts what the spec requires: no text is blank, and every string argument (alone or in a list) appears in the result. A nullable return (`installationFix` of a check that passed) may be `null`.
- `EveryErrorTest` builds every subclass of `WorktreeError` the same way and asserts, in both languages, that its summary and next step are not blank and that the values it carries show up in them or in its technical details, and that an error whose next step points to its technical details has some. `worktreePath` is the one value not required: the error is shown on that worktree. Git causes, commands that can't start and unexpected exceptions get explicit tests next to it.

`TextSamples`, shared by both, gives the samples per type: a marked string per parameter (so one argument can't be found because of another), `0`, `1` and `2` for counts, both booleans, every value of an enum, an empty, a one-element and a full list, and `null` too for nullable types. Data classes and sealed types (`InstallationFinding`, `CommandLineToolStatus`, `GitFailureCause`, `UnavailableLocalFile`…) are built from their constructors with the samples of their parameters, so texts that take domain objects need no hand-written cases. A few types get a fixed table: pool holders (one per state, one with a working agent and one with a lease), a desktop turn, a duration and two exceptions. A type the samples can't build fails the test naming it, so a new text or error can't silently stay untested; combinations are capped at 10,000 per call to keep the run in seconds (an installation check reaches about 6,300 once provider tools and multirepos are checked).

The single allowed empty text is `environmentSummary(0, 0)`, the summary of a worktree without ports nor variables, which the inspector turns into "no summary".

Alternatives considered: a hand-written assertion per text (thousands of lines that only repeat the tables), and a snapshot of every text (it would fail on every wording change without saying anything about defects). The overview of the installation check, which counts failures and warnings across a list, keeps its explicit test, extended with the missing case.

### `kotlin-reflect` in tests only

Java reflection sees the members but not Kotlin's nullability, the type argument of a `List`, sealed subclasses nor primary constructors, which the samples need. `kotlin-reflect` is added to the version catalog with the Kotlin version and used only as a `testImplementation` of the desktop app, so the packaged app doesn't change.

### Defects the tests found

- The background app's errors (`HeadlessAppLaunchFailed`, `HeadlessAppExitedEarly`, `HeadlessAppStopFailed`) and `MacWindowToolFailed` told the user to check their technical details, but `technicalDetailsOf` gave them none. They now carry the launch log's path and last lines, the tool's detail or the reason.
- `LocalFileUnreadable` dropped the reason the file couldn't be read; it is now in the technical details.
- `HeadlessAppStopFailed` without a reason and `InvalidRepositoryConfig` without a detail pointed to details that don't exist; their next step now says what to try instead.

No translation was blank and no text dropped an argument.

### Branches no input reaches

Some branches Kotlin generates can't be reached by any input. Rather than excluding them, the code is written in an equivalent form whose branches all run:

- `x?.let { … } ?: y`, where the lambda never returns `null`, became `if (x == null) y else …`.
- A `when` over a nullable enum keeps a default branch for the `null` case that never runs; the `null` case is checked first with an `if` (or an early `?: return`) and the `when` covers the enum.
- `if (it == 1)` on an `Int?` already checked for `null` checks for `null` again on the boxed value; the limit texts go through a private `limitText` whose count is a non-null `Int`.
- The `is GitCommandFailed` branch inside the worktree error texts could never run, since the outer `when` caught it first; the Git cause now travels with the error so that branch is the one used.
- `?.let { it }` and `orEmpty()` on a message that is never `null` were removed or replaced (`message.toString()`).

Two branches stay uncovered: the `null` case of `cause` in the technical details of `CommandExecutionException` and of the errors that wrap a cause (`SecretCopyFailed`, `ClipboardWriteFailed`, `EnvironmentFileWriteFailed`). Their constructors take a non-null cause, but `Throwable.cause` is nullable. Declaring `override val cause: Throwable` in `core` removes them, but it also turns four safe calls in `core` into warnings (`CheckInstallationUseCase`, `TourUseCases`, `WorktreeCreation`, `ToolCommands`), which are uncovered branches of `core` that `coverage-core-branches` is working on in parallel. It is left to that change; if it doesn't make that change, the last desktop change does.

### Floors

Measured on macOS after this change, with the end-to-end tests:

| Package | Lines | Branches |
|---|---|---|
| `ui/i18n` | 100% (2,657 of 2,657) | 99.8% (1,302 of 1,304; the two above) |
| whole desktop app outside composables | 88.26% (1,342 uncovered) | 73.89% (1,864 uncovered) |

The floors in `desktopApp/build.gradle.kts` go up from 83 to 88 (lines) and from 66 to 73 (branches), and the branches to 74 once merged with the doctor's provider and multirepo checks, whose texts the samples cover.

### Plan for the rest of the desktop app

Consecutive changes, one per package, each raising the floors it reaches. Most of what is left are lambdas inside composables (`onClick`, `onChange`, content blocks), which Kover counts because the compiler moves them out of the annotated function; they are covered with Compose UI tests (`runComposeUiTest`) like the existing `*UiTest` classes.

| Order | Change | Packages | Lines / branches uncovered today |
|---|---|---|---|
| 1 | `coverage-desktop-texts` (this one) | `ui/i18n` | 509 / 475 |
| 2 | `coverage-desktop-project-config` | `ui/projectconfig` | 278 / 262 |
| 3 | `coverage-desktop-worktrees` | `ui/worktree`, `ui/worktree/inspector` | 284 / 673 |
| 4 | `coverage-desktop-shell` | `ui/shell`, `ui/tray` | 307 / 248 |
| 5 | `coverage-desktop-machine` | `ui/machine`, `ui/multirepo` | 159 / 382 |
| 6 | `coverage-desktop-components` | `ui/components`, `ui/actions`, `ui/palette`, `ui/theme`, `ui`, `diagnostics`, `infrastructure` | 77 / 159 |
| 7 | `coverage-desktop-wiring` | root package (`AppContainer`, `main.kt`) | 237 / 138 |

The patterns in "Branches no input reaches" apply to every package, and the reflective samples can be reused by the later changes for their own models. The root package goes last because `desktop-host-environment` is changing how the app resolves its host environment there, and its wiring may need seams, or a reasoned exclusion like the CLI's `MainKt`, which that change's shape will decide. The last change sets both floors to 100.

## Risks / Trade-offs

- [The reflective test is less readable than explicit assertions] → It is one short test with its sampling rules in one function, and its failures name the member and language.
- [The string-argument check could reject a text that drops an argument on purpose] → Only `worktreePath` in errors does today; another one is listed in the test with its reason, like the empty summary.
- [Rewriting code for coverage] → Each rewrite is equivalent and the tests that cover both sides of it run in both languages.
- [A blank string isn't sampled] → A blank reason or detail reaches the same branch as a non-blank one in all errors but a few that test `isNullOrBlank`; Kover doesn't count the branches inside inlined standard library functions, and sampling a blank string for every parameter would flag errors whose tool printed nothing, which is outside this change.
- [Coverage changes in parallel] → The other coverage changes touch `core` and `cli`; `desktop-host-environment` may move installation texts. Floors and texts are merged when bringing in `develop`, and the floors re-measured.
