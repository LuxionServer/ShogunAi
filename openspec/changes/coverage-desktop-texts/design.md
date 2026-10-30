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

- 100% of lines and branches of `ui/i18n`.
- Raise the desktop floors to the whole percentages measured after this change.
- Write down how the rest of the desktop app gets to 100%.

**Non-Goals:**

- Covering other packages; they are the next changes (below).
- Reviewing the wording of the translations.

## Decisions

### One reflective test for every text

A test lists the members of `Strings` with `kotlin-reflect` and evaluates each one in both languages. Functions are called with every combination of sample arguments for their parameter types: a marked string for `String`, `0`, `1` and `2` for counts (`Int`, `Long`), both booleans, every value of an enum, an empty, a one-element and a two-element list for `List<String>`, and `null` as well for nullable parameters. It asserts what the spec requires: no text is blank, and every string argument appears in the result.

That reaches every plain text and every branch over counts, booleans and enums with one test that also covers texts added later, instead of hundreds of hand-written assertions repeating the tables. A member whose parameter type the test can't sample makes the test fail and name the member, so a new text can't silently stay untested; the members that take a domain object (`HookEntry`, `CommandLineToolStatus`, `InstallationReport`, `ResourceLimits`, `Throwable`…) are listed in the test as covered by explicit tests, and the reflective test skips only those.

The single allowed empty text is `environmentSummary(0, 0)`, the summary of a worktree without ports nor variables, which the inspector turns into "no summary". It is listed in the test as the one exception.

Alternatives considered: a hand-written assertion per text (thousands of lines that only repeat the tables), and a snapshot of every text (it would fail on every wording change without saying anything about defects). A prototype of the reflective test covered 300 of the 509 lines and found no blank translation nor dropped argument.

### `kotlin-reflect` in tests only

Java reflection sees the members but not Kotlin's nullability nor the type argument of `List<String>`, which the test needs to pick its samples. `kotlin-reflect` is added to the version catalog with the Kotlin version and used only as a `testImplementation` of the desktop app, so the packaged app doesn't change.

### Explicit tests for errors and texts with domain objects

The error descriptions are a `when` over `WorktreeError` and over `GitFailureCause`; each case needs an instance of its error. A test builds every error the existing tests don't reach and checks, in both languages, that its summary and next step are not blank and name the values the error carries (paths, branches, tools), plus the optional parts of the texts that have them (a lock file path or none, a reason or none). The same is done for the texts with domain-object parameters, the installation texts and the branches of `ErrorDescription` still uncovered.

### Production changes

None are planned. If a test finds a blank translation or a dropped argument it is fixed in the table, and code that no input can reach is removed rather than excluded; either is recorded here.

### Floors

After the tests, the desktop floors in `desktopApp/build.gradle.kts` go up to the measured whole percentages (expected around 88% of lines and 73% of branches).

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

The root package goes last because `desktop-host-environment` is changing how the app resolves its host environment there, and its wiring may need seams, or a reasoned exclusion like the CLI's `MainKt`, which that change's shape will decide. The last change sets both floors to 100.

## Risks / Trade-offs

- [The reflective test is less readable than explicit assertions] → It is one short test with its sampling rules in one function, and its failures name the member and language.
- [The string-argument check could reject a text that drops an argument on purpose] → None does today; if one is needed, it is listed in the test with its reason, like the empty summary.
- [Coverage changes in parallel] → The other coverage changes touch `core` and `cli`; `desktop-host-environment` may move installation texts. Floors and texts are merged when bringing in `develop`, and the floors re-measured.
