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

You can also use the IDE's run widget.

## Run the tests

```bash
./gradlew test
```

## Finding your way around the code

The project is Kotlin Multiplatform with a single real target (desktop JVM). All the domain, infrastructure, and UI live in `desktopApp`:

```
desktopApp/src/main/kotlin/app/luxion/shogunai/
├── domain/           # models, ports (interfaces), and use cases
├── infrastructure/   # concrete implementations of the ports (Git, filesystem)
├── ui/               # Compose screens, ViewModels, and navigation
├── AppContainer.kt   # wires domain + infrastructure, no DI framework
└── main.kt           # entry point
```

`shared` is declared but has no content of its own (see [Architecture › Overview](architecture/overview.md) for why).

For the detail of each layer: [Domain layer](architecture/domain.md), [Infrastructure layer](architecture/infrastructure.md), [UI layer](architecture/ui.md).

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
