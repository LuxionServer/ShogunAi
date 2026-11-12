# Spec Delta

## Purpose

Keeps ShogunAi's layering, composition roots and source style rules from eroding by checking the source code on every test run, so a change that breaks one of them fails the build instead of relying on review.

## ADDED Requirements

### Requirement: The rules run with the unit tests
The architecture rules SHALL run as part of `core`'s unit tests, read the Kotlin sources of `core`, `cli` and `desktopApp`, and fail with the path, line and rule of every violation. `core`'s tests SHALL be rerun when any of those sources changes, not only `core`'s own.

#### Scenario: Violation in another module
- **WHEN** a change adds an import of a `core` infrastructure class to a file under `desktopApp/src/main/kotlin/app/luxion/shogunai/ui`
- **THEN** `./gradlew test` fails and the failure names that file, its line and the broken rule

#### Scenario: Gradle cache
- **WHEN** only a file of `cli` changes since the last successful test run
- **THEN** `./gradlew test` reruns `core`'s tests instead of reporting them up to date

### Requirement: The domain never depends on infrastructure
No file of `core`'s domain SHALL import a declaration from the infrastructure package.

#### Scenario: Use case importing an adapter
- **WHEN** a use case imports a class from `app.luxion.shogunai.infrastructure`
- **THEN** the tests fail naming that import

### Requirement: Domain packages have no cycles
Inside the domain, models SHALL import no other project package, executor ports SHALL import none, the other ports SHALL import only models and executor ports, and use cases and infrastructure SHALL never import each other.

#### Scenario: Model importing a use case
- **WHEN** a file of the domain's model package imports a declaration from the use case package
- **THEN** the tests fail naming that import

#### Scenario: Port importing a use case
- **WHEN** a port in the domain's io package imports a declaration from the use case package
- **THEN** the tests fail naming that import

#### Scenario: Infrastructure importing a use case
- **WHEN** a file of `core`'s infrastructure imports a declaration from the use case package
- **THEN** the tests fail naming that import

#### Scenario: Current sources
- **WHEN** the tests run on the sources of this change
- **THEN** no import between domain packages breaks the rule and no exception is listed for it

### Requirement: The domain doesn't touch the operating system
Domain files SHALL NOT use the JVM's file, path, process or system property APIs directly, except for the files on an exception list that names, per file, the APIs it uses.

#### Scenario: New system access
- **WHEN** a domain file that is not on the exception list uses `java.io.File`, `java.nio.file`, `kotlin.io.path`, `System.getProperty`, `ProcessBuilder` or `Runtime`
- **THEN** the tests fail naming the file, line and API

#### Scenario: Listed file uses a new API
- **WHEN** a file on the exception list starts using an API the list doesn't name for it
- **THEN** the tests fail naming the file and the API

### Requirement: Exception lists only shrink
Every exception list of the architecture rules SHALL describe the current sources exactly: an entry that no longer matches a violation SHALL fail the tests until it is removed, so fixing a violation also removes its exception.

#### Scenario: Violation fixed
- **WHEN** a change moves the last system access out of a domain file on the exception list without removing its entry
- **THEN** the tests fail asking to remove that entry

#### Scenario: Missing file
- **WHEN** a file named on an exception list no longer exists
- **THEN** the tests fail asking to remove that entry

### Requirement: Infrastructure is wired only in composition roots
In `desktopApp`, only `main.kt` and `AppContainer.kt` SHALL import `core` infrastructure, and the only `cli` declaration it SHALL import is `runShogun`. In `cli`, only `Main.kt` and `ShogunCli.kt` SHALL import `core` infrastructure.

#### Scenario: Screen creating an adapter
- **WHEN** a file of the desktop app's UI imports a `core` infrastructure class
- **THEN** the tests fail naming that import

#### Scenario: Desktop app using a CLI internal
- **WHEN** a desktop app file imports a `cli` declaration other than `runShogun`
- **THEN** the tests fail naming that import

#### Scenario: CLI text helper creating an adapter
- **WHEN** a `cli` file other than `Main.kt` and `ShogunCli.kt` imports a `core` infrastructure class
- **THEN** the tests fail naming that import

### Requirement: Sources have no comments
No Kotlin source of any source set and no Gradle script of the three modules or the root project SHALL contain a line or block comment. Text inside string and character literals, such as a URL, SHALL NOT count as a comment.

#### Scenario: Line comment
- **WHEN** a test file contains `val retries = 3 // enough for CI`
- **THEN** the tests fail naming that file and line

#### Scenario: KDoc
- **WHEN** a production file contains a `/** ... */` block before a function
- **THEN** the tests fail naming that file and line

#### Scenario: URL in a string
- **WHEN** a file contains `"https://herdr.dev"` or a raw string with `//` inside
- **THEN** no comment is reported for it

### Requirement: Commands are run as argv
No production source SHALL run a command through `Runtime.exec`, and only the production files on an exception list SHALL name a shell interpreter (`sh`, `bash`, `zsh`, `fish`, `cmd`, `powershell`, `pwsh`, with or without `.exe`) in a string literal.

#### Scenario: Shell in a use case
- **WHEN** a use case runs `listOf("bash", "-c", script)`
- **THEN** the tests fail naming that file and line

#### Scenario: Runtime.exec
- **WHEN** any production file calls `Runtime.getRuntime().exec(...)`
- **THEN** the tests fail naming that file and line

### Requirement: Production files stay small
No production Kotlin file of `core`, `cli` or `desktopApp` SHALL be longer than 400 lines, except for the files on an exception list, each capped at the length it has when listed. A listed file SHALL NOT grow past its cap, and a listed file at or under 400 lines SHALL be removed from the list.

#### Scenario: New large file
- **WHEN** a change adds a production file of 450 lines that is not on the list
- **THEN** the tests fail naming the file and its length

#### Scenario: Listed file grows
- **WHEN** a change adds lines to a listed file past its cap
- **THEN** the tests fail naming the file, its length and its cap

#### Scenario: Listed file shrinks
- **WHEN** a change splits a listed file until it has 400 lines or fewer
- **THEN** the tests fail asking to remove its entry
