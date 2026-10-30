# Design

## Context

`coverage-kover` set `core`'s branch rule at its measured figure and left the rest to this change. On `develop` at 24b64c40, Kover 0.9.11 on macOS counts 7,344 branches in `core`'s production code, 1,519 of them missed (79.3%), spread over 167 source files. About 285 sit on class headers: the code kotlinx.serialization generates for `@Serializable` classes (the deserializing constructor checks which fields were present, `write$Self` decides whether to write a field equal to its default) and the bitmasks of constructors with default arguments. The other 1,234 are in written code: `?.` and `?:` chains, `if`s on a command's exit code or output, `takeIf`, `when` arms, short-circuited `&&` and `||`.

Kover's IntelliJ agent already leaves out data class `equals`, `hashCode` and `toString`, the `$serializer` classes and exhaustive `when`'s hidden `else`, but it can only exclude whole classes or annotated functions, not a synthetic method inside a class: excluding the generated serialization code would mean excluding every `@Serializable` class, including the logic some of them hold (`ResourceSettings.quotaMode`, for example).

## Goals / Non-Goals

**Goals:**

- 100% of `core`'s branches, with the rule at 100.
- Every new test asserts the behavior its branch decides (a value, an error, a command sent through a port), not just that the code ran.

**Non-Goals:**

- Branches of `cli` and the desktop app: other changes (`coverage-cli-branches`, `coverage-desktop`) own them.
- Refactoring beyond removing what is unreachable.
- Changing how coverage is measured or verified in CI.

## Decisions

### No new exclusions

The generated serialization branches stay counted and are covered by tests, because the only exclusion Kover offers would hide written code too, and because those branches decide real behavior: a settings file written by an older version lacks newer fields and must read as their defaults; a file missing a required field must be rejected as unreadable. Rejected: excluding `@Serializable` classes, or moving their logic out so they could be excluded, which bends the production code around the tool.

### Serialization branches are covered through the file formats

Each `@Serializable` class is exercised through the adapter that reads and writes it (`JsonSettingsFile`, `JsonMultirepoSource`, `NioEnvironmentStateStore`…) where one exists, with three kinds of input: a document with every optional field absent (defaults), one with every field set to a non-default value (round trip), and, for classes with required fields, one missing a required field (rejected with the adapter's typed error). Classes used only in memory or only through a model's own JSON helpers are tested with `Json` directly in their model test. Writing with defaults is covered by encoding a default instance and one with every field changed, with whatever `Json` configuration the adapter uses. Where the adapter's `Json` never encodes defaults, the branch that would write a default anyway is only reachable with another configuration, and the test uses the adapter's own `Json` instance to stay honest about what production does; if no production configuration can reach a branch, the class's serialization is the one listed as unreachable below.

### Unreachable branches are removed, not tested around

When a branch can't be taken by any input (a `?.` on a value the type flow already proves non-null, an `?:` after a call that never returns `null`, a `takeIf` whose condition is always true at its only call site, a default argument every caller passes), the production code is simplified so the branch disappears, keeping behavior identical. Each removal is recorded below with its reason. Rejected: reflection or test-only seams to force impossible states; a seam is only added, as `coverage-kover` did, for a real failure a test can't otherwise provoke (an I/O error, a lock that can't be taken), with today's behavior as its default.

### Tests live next to the existing ones

New cases go into the existing test class of each production class when there is one, using the fakes in `core`'s test fixtures; a fake gains a configurable answer when a branch needs one it can't give today. Adapters over the file system use temporary folders; adapters over external tools (`orca`, `herdr`, `xcrun simctl`, `adb`, `docker`, `gradle`) use `FakeShellCommandExecutor` answers, never the real tool. Code that depends on the operating system is covered through its `osName` parameter.

### The floor rises as it goes

The work goes package by package (models, then use cases, then infrastructure). After each group, `core`'s rule in `core/build.gradle.kts` is raised to the whole percentage reached, so a partial state is never left with slack, and it ends at 100.

### Production changes

Filled in while implementing: each production change, the branch it removes and why no input reaches it.

## Effect on the changes in flight

`coverage-cli-branches`, `coverage-desktop`, `herdr-e2e-windows`, `desktop-host-environment`, `doctor-provider-checks` and `packaged-smoke-test` run in parallel. Those that add code to `core` (`desktop-host-environment`, `doctor-provider-checks`) must cover all its branches once this change is in `develop`. If one of them merges first, this change brings in `develop` before its pull request and covers any branch it left; the coverage floors in the build scripts and the roadmap's coverage item are the expected merge conflicts.

## Risks / Trade-offs

- [Tests written only to touch a branch] → Each test names the behavior and asserts its outcome; reviewed like any other test.
- [100% leaves no slack: a single untested branch fails CI] → That's the point of the rule; `koverXmlReport` points to the line, and the guideline in `AGENTS.md` already asks for `koverVerify` before a change is done.
- [Kotlin or Kover upgrades can add generated branches] → An upgrade that adds branches comes with the tests for them, like any other change.
- [Simplifying production code to remove an unreachable branch can change behavior] → Only branches proven unreachable by type or by every call site are removed, and the existing tests of the class keep passing.

## Migration Plan

None: tests and a build rule. Rolling back is lowering the rule, which the guideline forbids except by reverting the change.
