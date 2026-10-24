# compiler-warnings Specification

## Purpose
Keeps the build free of Kotlin compiler warnings by making any warning fail the compilation, in every module and source set, so new warnings are fixed in the change that introduces them.

## Requirements

### Requirement: Compiler warnings fail the build
Every Kotlin compilation of every Gradle module, including production, test and test fixture sources, SHALL fail when the compiler reports a warning. A module added later SHALL inherit this behavior without configuring it itself.

#### Scenario: Warning in production code
- **WHEN** a change adds an unnecessary non-null assertion (`!!`) to a file in `core/src/main`
- **THEN** `./gradlew build` fails and names that file and line

#### Scenario: Warning in a test
- **WHEN** a change calls a deprecated API from a test in `desktopApp/src/test`
- **THEN** `./gradlew test` fails, so the CI check of its pull request fails

#### Scenario: Clean build
- **WHEN** `./gradlew build` runs on a commit without compiler warnings
- **THEN** it succeeds and the compiler output contains no `w:` lines

### Requirement: Tests may use experimental coroutines test APIs
Test and test fixture sources SHALL compile using APIs marked `kotlinx.coroutines.ExperimentalCoroutinesApi` without a per-file opt-in, while production sources SHALL still need an explicit opt-in to use them.

#### Scenario: Test advances virtual time
- **WHEN** a test calls `advanceUntilIdle()` or creates an `UnconfinedTestDispatcher` without an `@OptIn` annotation
- **THEN** it compiles without warnings

#### Scenario: Production code uses an experimental API
- **WHEN** a file in `core/src/main` uses an API marked `ExperimentalCoroutinesApi` without `@OptIn`
- **THEN** the compilation fails
