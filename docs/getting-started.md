# Getting started

Guide to cloning the repo, running the project for the first time, and knowing where to go next.

## Requirements

- **JDK 21** — Gradle uses toolchains (`gradle/gradle-daemon-jvm.properties`) and downloads it automatically if you don't have it installed.
- **Git**.
- (Optional) IntelliJ IDEA or Android Studio with the Kotlin Multiplatform plugin, to use the IDE's run configurations.

## Clone and build

```bash
git clone <repo-url>
cd ShogunAi
./gradlew build
```

## Run the app

```bash
./gradlew :desktopApp:run            # standard run
./gradlew :desktopApp:hotRun --auto  # hot reload
```

You can also use the IDE's run widget. To try the `shogun` command without packaging: `./gradlew :cli:run --args="status"` (it works on the repository Gradle runs from).

## Run the tests

```bash
./gradlew test
```

## Finding your way around the code

The project is a Kotlin/JVM desktop app built with Compose Multiplatform, plus the `shogun` command-line tool, in three Gradle modules. `core` holds the domain and the infrastructure, without any UI; `desktopApp` holds the Compose UI and wires everything together; `cli` is the command:

```
core/src/main/kotlin/app/luxion/shogunai/
├── domain/           # models, ports (interfaces), and use cases
└── infrastructure/   # concrete implementations of the ports (Git, filesystem)

desktopApp/src/main/kotlin/app/luxion/shogunai/
├── ui/               # Compose screens, ViewModels, and navigation
├── AppContainer.kt   # wires domain + infrastructure, no DI framework
└── main.kt           # entry point

cli/src/main/kotlin/app/luxion/shogunai/cli/  # the shogun command
```

For the detail of each layer: [Domain layer](architecture/domain.md), [Infrastructure layer](architecture/infrastructure.md), [UI layer](architecture/ui.md), [Command-line tool](architecture/cli.md).

## How changes are planned

Non-trivial changes are planned with [OpenSpec](process/openspec.md) before writing code: proposal → apply → archive. Check `openspec/changes/` for changes in progress and `openspec/changes/archive/` for the history.

## If you're going to work with an AI agent

Read `AGENTS.md` at the repo root — it captures the code and documentation style conventions agents (Claude, Copilot...) must follow in this project.

## View this documentation locally

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## Next stop

See the "Where to look" table in [Home](index.md) to know which page to go to based on what you need to understand.
