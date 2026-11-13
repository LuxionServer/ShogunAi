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
├── engine/           # ShogunEngine: el único cableado de los casos de uso, compartido por la app y la CLI
└── infrastructure/   # implementaciones concretas de los puertos de dominio, y hostEnginePorts
core/build/generated/buildInfo/  # BuildInfo.VERSION, compartido por la app y el CLI

desktopApp/src/main/kotlin/app/luxion/shogunai/
├── infrastructure/   # AwtClipboardWriter
├── ui/               # pantallas Compose, ViewModels y navegación
├── AppContainer.kt   # construye el motor para la UI y guarda los estados de la UI, sin DI framework
└── main.kt           # punto de entrada: ejecuta el CLI con argumentos, abre la ventana sin ellos

cli/src/main/kotlin/app/luxion/shogunai/cli/
├── CliCommand.kt     # parsing de argumentos a un CliCommand sellado
├── ShogunCli.kt      # ejecuta cada comando sobre los casos de uso del motor
└── Main.kt           # punto de entrada independiente (runShogun)
```

### Un único grafo de composición

`ShogunEngine` (`core`, paquete `engine`) es el único sitio donde se cablean los casos de uso entre sí. Recibe `EnginePorts`, una data class con cada puerto que necesitan los casos de uso compartidos y sin valores por defecto, así que un puerto olvidado es un error de compilación y no una función desactivada en silencio, y `EngineOptions` (reloj, pausas y la `WorktreeEnvironmentCache` opcional). Expone ya cableados los casos de uso de la máquina (resolución del entorno, pools de recursos, dispositivos, simuladores, emuladores, la app headless, el turno de escritorio, dispositivos físicos, Gradle, Maven, Compose, DerivedData, multirepos, detección de agentes) y, mediante `project(config)`, un `ProjectEngine` con los que reciben un `ProjectConfig` (listar, crear, provisionar, eliminar, limpiar, ramas); los que piden la aprobación de un repositorio la reciben en cada llamada.

`hostEnginePorts` (`infrastructure`) elige la implementación real de cada puerto para un entorno de host, una sola vez para las dos superficies. Las dos elecciones que difieren entre ellas son sus parámetros: la app de escritorio comparte las lecturas del estado de los emuladores entre sus paneles (`SharedDeviceStatesAndroidSdk`) y la línea de comandos no, y cada una indica a los dispositivos físicos desde qué carpeta localizar el SDK de Android (el directorio de trabajo de la CLI, el home de la app). La app además pasa una `WorktreeEnvironmentCache`, que la CLI, un comando por proceso, no necesita. Los tests construyen el motor con `fakeEnginePorts(...)` de los test fixtures de `core`.

El dominio se define en términos de **puertos** (interfaces) y no conoce `ProcessBuilder` ni `java.nio.file` directamente; eso permite sustituir cada puerto por un doble de prueba (`FakeShellCommandExecutor`, `FakeFileManager`) en los tests de casos de uso, sin tocar disco ni lanzar procesos reales.

Ver el detalle de cada capa:

- [Capa de dominio](domain.md)
- [Capa de infraestructura](infrastructure.md)
- [Capa de UI](ui.md)
- [Línea de comandos](cli.md)
