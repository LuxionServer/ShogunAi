# Visión general

ShogunAi es una aplicación de **escritorio Kotlin/JVM** construida con **Compose Multiplatform** (Compose Desktop), más la herramienta de línea de comandos `shogun`.

## Módulos

Hay tres módulos de Gradle:

| Módulo | Qué contiene | Depende de |
|---|---|---|
| **`core`** | El dominio (modelos, puertos, casos de uso) y la infraestructura que implementa sus puertos: procesos, ficheros, el catálogo JSON y las preferencias. Kotlin/JVM puro, sin Compose ni AWT. | Corrutinas, `kotlinx.serialization` |
| **`desktopApp`** | La aplicación de escritorio: UI Compose (pantallas y ViewModels), `AppContainer`, el punto de entrada y `AwtClipboardWriter`, la única implementación de un puerto que necesita el toolkit de escritorio. | `core`, `cli`, Compose Desktop |
| **`cli`** | El comando `shogun`: el parsing de argumentos y sus comandos (worktrees, entorno, providers y hooks de agentes; ver la [referencia de la línea de comandos](../guide/cli.md)) sobre `core`. El lanzador de la app de escritorio lo ejecuta cuando recibe argumentos. | `core` |

`core` existe para que un segundo frontal —la herramienta de línea de comandos `shogun`— pueda ejecutar los mismos casos de uso sin arrastrar la UI de escritorio. Los paquetes no cambiaron al extraerlo (`app.luxion.shogunai.domain…`, `app.luxion.shogunai.infrastructure…`); lo que mantiene la UI fuera de `core` es el límite del módulo. Los dobles de prueba viven en los test fixtures de `core`, compartidos por los tests de todos los módulos.

El `build.gradle.kts` raíz fija la política del compilador para todos los módulos Kotlin, incluidos los que se añadan más adelante: cualquier aviso del compilador hace fallar la build, tanto en el código de producción como en los tests y los test fixtures, así que un aviso se arregla en el cambio que lo introduce. Los tests y los test fixtures hacen opt-in de `kotlinx.coroutines.ExperimentalCoroutinesApi`, porque usan a propósito las APIs de tiempo virtual; el código de producción sigue necesitando un opt-in explícito.

!!! note "Por qué ahora un módulo `core`"
    El módulo `shared` vacío de la plantilla se eliminó en la primera iteración porque solo la app de escritorio lo iba a usar (ver [decisiones de diseño](../decisions/index.md)). Eso cambió con el CLI `shogun`, un segundo target real, y `core` se extrajo en el cambio `extract-core-module` (`openspec/changes/archive/`).

## Capas

```
core/src/main/kotlin/app/luxion/shogunai/
├── domain/           # modelos, puertos y casos de uso — sin dependencias de infraestructura
│   ├── model/        # ProjectConfig, Worktree, WorktreeStatus, RepositoryProvisioning, WorktreeEnvironment, modelos de los providers, WorktreeError…
│   ├── executor/     # puertos ShellCommandExecutor y ClipboardWriter + CommandResult
│   ├── io/           # puertos: FileManager, ProjectRepository, preferencias, RepositoryProvisioningSource,
│   │                 #   EnvironmentStateStore, PortProbe, AndroidSdk, AppleSimulators, KmpTestWiring,
│   │                 #   HerdrClient, HerdrConfigSource, OrcaClient, OrcaWorkspaceStore, GitHubClient,
│   │                 #   GradleDaemonSource, DerivedDataSource
│   └── usecase/      # worktrees (crear, listar, provisionar, eliminar, reparar, limpiar, estado, vista general),
│                     #   entorno y puertos, providers (Gradle, emulador, simulador, Xcode), Herdr, Orca
└── infrastructure/   # implementaciones concretas de los puertos de dominio
core/build/generated/buildInfo/  # BuildInfo.VERSION, compartido por la app y el CLI

desktopApp/src/main/kotlin/app/luxion/shogunai/
├── infrastructure/   # AwtClipboardWriter
├── ui/               # pantallas Compose, ViewModels y navegación
├── AppContainer.kt   # cablea dominio + infraestructura para la UI, sin DI framework
└── main.kt           # punto de entrada: ejecuta el CLI con argumentos, abre la ventana sin ellos

cli/src/main/kotlin/app/luxion/shogunai/cli/
├── CliCommand.kt     # parsing de argumentos a un CliCommand sellado
├── ShogunCli.kt      # ejecuta cada comando sobre los casos de uso de core
└── Main.kt           # punto de entrada independiente (runShogun)
```

El dominio se define en términos de **puertos** (interfaces), así que cada puerto se puede sustituir por un doble de prueba (`FakeShellCommandExecutor`, `FakeFileManager`) en los tests de casos de uso, sin tocar disco ni lanzar procesos reales.

## Reglas que comprueba el build

Los tests de `core/src/test/kotlin/app/luxion/shogunai/architecture/` leen los fuentes de los tres módulos en cada ejecución de los tests y fallan, indicando fichero y línea, cuando se rompe una de estas reglas:

| Paquete de `core` | Puede importar del proyecto |
|---|---|
| `domain.model` | nada |
| `domain.executor` | nada |
| `domain.io` | `domain.model`, `domain.executor` |
| `domain.usecase` | `domain.model`, `domain.executor`, `domain.io` |
| `infrastructure` | `domain.model`, `domain.executor`, `domain.io` |

- **El dominio no toca el sistema**: nada de `java.io.File`, `java.nio.file`, `kotlin.io.path`, `System.getProperty`, `ProcessBuilder` ni `Runtime` en `domain/`.
- **Raíces de composición**: en la app de escritorio, solo `main.kt` y `AppContainer.kt` usan infraestructura, y `runShogun` es lo único que usa de la CLI; en la CLI, solo `Main.kt` y `ShogunCli.kt` usan infraestructura.
- **Sin comentarios** en ningún fuente Kotlin ni script de Gradle.
- **Comandos como argv**: nada de `Runtime.exec`, y ningún intérprete de shell nombrado como comando.
- **Longitud de fichero**: ningún fichero de producción de más de 400 líneas.

Parte del código existente aún no cumple las reglas del sistema, las shells y la longitud. Cada caso está en una lista de excepciones en `ArchitectureExceptions.kt`, que debe coincidir exactamente con el código: un caso nuevo falla, y también una entrada que ya no corresponde a nada, así que las listas solo pueden encoger.

Ver el detalle de cada capa:

- [Capa de dominio](domain.md)
- [Capa de infraestructura](infrastructure.md)
- [Capa de UI](ui.md)
- [Línea de comandos](cli.md)
