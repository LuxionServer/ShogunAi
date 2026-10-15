# Visión general

ShogunAi es una aplicación de **escritorio Kotlin/JVM** construida con **Compose Multiplatform** (Compose Desktop).

## Módulos

Hay un único módulo de Gradle, **`desktopApp`**: la aplicación de escritorio (Compose Desktop), la UI (pantallas y ViewModels) y **todo el dominio y la infraestructura del flujo de worktrees**.

!!! note "¿Por qué no hay un módulo `shared` aparte?"
    La plantilla del proyecto generó un módulo Kotlin Multiplatform `shared` vacío, y se eliminó porque nada lo usaba. No hay targets iOS, Android ni WASM en este proyecto: meter lógica de dominio en un módulo "compartido" que en la práctica solo corre en un target añadiría una capa de indirección sin beneficio real. Si algún día aparece un segundo target real, se puede extraer un módulo compartido en ese momento. Ver [decisiones de diseño](../decisions/index.md).

## Capas dentro de `desktopApp`

```
desktopApp/src/main/kotlin/app/luxion/shogunai/
├── domain/           # modelos, puertos y casos de uso — sin dependencias de infraestructura
│   ├── model/        # ProjectConfig, Project, AgentLaunchConfig, BranchType, BranchOption, Worktree, WorktreeError, ThemeMode, AppLanguage
│   ├── executor/     # puertos ShellCommandExecutor y ClipboardWriter + CommandResult
│   ├── io/           # puertos FileManager, ProjectRepository, ThemePreferenceRepository, LanguagePreferenceRepository
│   └── usecase/      # Create/CreateFromBranch/List/ListLocalBranches/Remove/Repair de worktrees, CopyWorktreeLaunchCommand
├── infrastructure/   # implementaciones concretas de los puertos de dominio
├── ui/               # pantallas Compose, ViewModels y navegación
├── AppContainer.kt   # cablea dominio + infraestructura para la UI, sin DI framework
└── main.kt           # punto de entrada de la app Compose Desktop
```

El dominio se define en términos de **puertos** (interfaces) y no conoce `ProcessBuilder` ni `java.nio.file` directamente; eso permite sustituir cada puerto por un doble de prueba (`FakeShellCommandExecutor`, `FakeFileManager`) en los tests de casos de uso, sin tocar disco ni lanzar procesos reales.

Ver el detalle de cada capa:

- [Capa de dominio](domain.md)
- [Capa de infraestructura](infrastructure.md)
- [Capa de UI](ui.md)
