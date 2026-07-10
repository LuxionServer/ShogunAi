# Visión general

ShogunAi es un proyecto **Kotlin Multiplatform** con un único target real: **JVM de escritorio**.

## Módulos

```mermaid
graph TD
    desktopApp["desktopApp (Compose Desktop, jvm)"] --> shared["shared (commonMain, jvm())"]
```

- **`shared`**: código pensado para compartirse entre distintos targets de Compose Multiplatform. Hoy solo contiene UI/utilidades genéricas (`App`, `Greeting`, `Platform`) porque el `shared` fue generado por la plantilla base del proyecto.
- **`desktopApp`**: la aplicación de escritorio (Compose Desktop) y, además, **todo el dominio y la infraestructura del flujo de worktrees**.

!!! note "¿Por qué el dominio vive en `desktopApp` y no en `shared`?"
    `shared` usa `jvm()` como único target — no hay iOS, Android ni WASM en este proyecto. Meter lógica de dominio en un módulo "compartido" que en la práctica solo corre en un target añadiría una capa de indirección sin beneficio real. Ver [decisiones de diseño](../decisions/index.md).

## Capas dentro de `desktopApp`

```
desktopApp/src/main/kotlin/app/luxion/shogunai/
├── domain/           # modelos, puertos y casos de uso — sin dependencias de infraestructura
│   ├── model/        # ProjectConfig, BranchType, Worktree, WorktreeError
│   ├── executor/      # puerto ShellCommandExecutor + CommandResult
│   ├── io/            # puerto FileManager
│   └── usecase/       # CreateWorktreeUseCase, RemoveWorktreeUseCase, ListWorktreesUseCase
├── infrastructure/   # implementaciones concretas de los puertos de dominio
└── main.kt           # punto de entrada de la app Compose Desktop
```

El dominio se define en términos de **puertos** (interfaces) y no conoce `ProcessBuilder` ni `java.nio.file` directamente; eso permite sustituir cada puerto por un doble de prueba (`FakeShellCommandExecutor`, `FakeFileManager`) en los tests de casos de uso, sin tocar disco ni lanzar procesos reales.

Ver el detalle de cada capa:

- [Capa de dominio](domain.md)
- [Capa de infraestructura](infrastructure.md)
