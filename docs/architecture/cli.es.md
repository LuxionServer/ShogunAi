# Herramienta de línea de comandos

El comando `shogun` ejecuta las operaciones de worktrees de ShogunAi desde un terminal, un script o un hook de un agente, sin abrir la ventana de escritorio. Vive en el módulo **`cli`** (`cli/src/main/kotlin/app/luxion/shogunai/cli`) y llama a los mismos casos de uso de `core` que la app, así que un worktree creado o limpiado desde la línea de comandos queda exactamente igual que si lo hubiera hecho la app.

## Comandos

Qué hace cada comando, sus opciones y su salida están en la [referencia de la línea de comandos](../guide/cli.es.md). Esta tabla relaciona cada comando con el código de `core` que ejecuta y anota los detalles que solo importan al modificarlo.

| Comando | Ejecuta | Notas de implementación |
|---|---|---|
| `new` | `ResolveStartPointUseCase`, y después `CreateWorktreeUseCase` o `CreateWorktreeFromBranchUseCase`, con `PrepareWorktreeEnvironmentUseCase` y `WriteEnvironmentFileUseCase` como preparador del entorno | Sin `--from`, el punto de partida es `HEAD` resuelto en el directorio de trabajo, así que un worktree creado desde un worktree enlazado parte de ese worktree, como hace `git worktree add -b`. Una referencia desconocida falla antes de crear nada. |
| `provision` | `ProvisionWorktreeUseCase` | |
| `status` | `ListWorktreesUseCase`, `ResolveMainBranchUseCase`, `ReadWorktreeStatusUseCase`, `classifyOrigin` | El JSON de `--json` se construye en `StatusJson.kt`; sus nombres de campo son un contrato, ver la [guía del CLI](../guide/cli.es.md#status-json). |
| `clean` | `selectCleanupCandidates`, `CleanUpWorktreesUseCase`, `CloseHerdrWorkspacesUseCase` y, según la opción, `StopGradleDaemonsUseCase`, `StopWorktreeEmulatorUseCase`, `DeleteSimulatorsUseCase`, `DeleteOrphanedDerivedDataUseCase` | Lo que la eliminación borra se lee antes: los daemons de Gradle (para `idleDaemonsOfRemovedWorktrees`), los serials de los emuladores en marcha (`ReadEmulatorStatesUseCase`, después `StopWorktreeEmulatorUseCase.stopSerials`), los UDID de los simuladores y los DerivedData huérfanos. Los simuladores huérfanos se vuelven a buscar después de eliminar, así que el simulador de un worktree cuya carpeta ya no existía se detecta como huérfano; con solo huérfanos el comando se ejecuta igualmente. Un daemon o emulador que no se puede parar, o un cierre de Herdr fallido, va a la salida de error sin cambiar el código de salida; una carpeta de DerivedData que no se puede borrar sale con `1`. |
| `gradle`, `gradle stop` | `ListGradleDaemonsUseCase`, `CheckGradleBuildCacheUseCase`, `StopGradleDaemonsUseCase` | Funciona fuera de un repositorio. Los daemons de Kotlin se conservan mientras corre una build de Gradle. |
| `pool status`, `pool trim` | `ReadResourcePoolUseCase`, `TrimGradleDaemonsUseCase` | `status` lee los tres pools frente a los límites del perfil de capacidad; `trim` detiene los daemons de Gradle inactivos que superan el límite. Ambos aceptan `--json` (`poolJson`, `poolTrimJson`). |
| `project add`, `project list`, `project remove` | `RegisterProjectUseCase`, `UnregisterProjectUseCase`, `ProjectRepository` | `add` construye el proyecto con `newProject`, los mismos valores por defecto que usa `ProjectConfigViewModel.chooseFolder` (`projectDefaultsFor`), y solo llama a `save` para un repositorio sin proyecto, comparado por ruta canónica, así que un proyecto existente nunca se reescribe. `remove` busca por ruta canónica y, si la carpeta ya no existe, por la ruta guardada. El JSON de `--json` se construye en `CommandJson.kt` (`projectJson`, `projectListJson`). |
| `app` | `ResolveRepositoryUseCase`, `RegisterProjectUseCase`, `AppInstanceLock`, `AppRequestChannel`, `AppLauncher` | Registra el repositorio, deja un `AppRequest` y lanza la app solo cuando nadie tiene el bloqueo de la app. Ver [Cómo se llega a la app de escritorio](#como-se-llega-a-la-app-de-escritorio). |
| `xcode`, `xcode clean` | `ListDerivedDataUseCase`, `DeleteOrphanedDerivedDataUseCase` | Sin el provider o fuera de macOS sale con `0`. |
| `env` | `ResolveWorktreeEnvironmentUseCase` (reservando), `formatEnvironment` | Reserva solo los puertos declarados; las reservas bajo demanda (el puerto del emulador, el simulador) se leen, nunca se adquieren. |
| `emulator` | `StartWorktreeEmulatorUseCase`, `StopWorktreeEmulatorUseCase`, `ReadEmulatorStatesUseCase` | `start` adquiere el emulador mediante `AcquireWorktreeResourceUseCase` con los límites del perfil de capacidad de la máquina, imprime lo que liberó por la salida de errores y la línea `export ANDROID_SERIAL=…` en cada arranque (que además renueva la concesión). Con `--wait` pasa un `DeviceWait` cuyo callback de progreso imprime una línea por la salida de errores solo cuando cambia, e imprime `waited <tiempo>` tras una espera real (`waited` con `--json`). `status` nunca reserva un puerto e imprime `not acquired` antes del primer arranque, seguido de `(released for <worktree> at <hora>)` cuando `ReadReleaseRecordUseCase` encuentra un registro de liberación (`releasedFor` con `--json`). Sin el provider: `EmulatorProviderNotDeclared`. |
| `headless` | `StartHeadlessAppUseCase`, `StopHeadlessAppUseCase`, `ReadHeadlessAppUseCase`, `CaptureHeadlessAppUseCase` | El worktree es la raíz del checkout de Git de la ruta, esté registrado como proyecto o no. `.shogunai.json` se lee del propio worktree. Los puertos `HeadlessAppLauncher` (`GradleHeadlessAppLauncher`: `gradlew <módulo>:hotRunAsync` con un init script) y `AppWindowService` (`MacAppWindowService`: un helper de JavaScript for Automation y `screencapture`) se inyectan, así que los tests usan fakes. Un ocultado denegado es un aviso por la salida de errores, no un fallo. `remove`, `clean` y `hook worktree-remove` pasan `StopHeadlessAppUseCase` a la eliminación, que para la app antes de borrar la carpeta. |
| `simulator` | `StartWorktreeSimulatorUseCase`, `StopWorktreeSimulatorUseCase`, `ReadSimulatorStatesUseCase`, `KmpTestWiring` | `start` adquiere (crea) el simulador del mismo modo e imprime `export IOS_SIMULATOR_UDID=…` cuando acaba de hacerlo. `status` nunca crea un simulador e imprime `not acquired` antes del primer arranque. Sin el provider: `IosSimulatorProviderNotDeclared`; fuera de macOS: `IosSimulatorsNotSupported`. |
| `simulator gradle-init` | `KmpTestWiring` | `install` pregunta por la entrada estándar (`readInput`) salvo con `--yes`. |
| `hook worktree-create` | `CreateWorktreeUseCase`, o el worktree existente | Lee `name` y `cwd` del JSON del hook; sin proyecto el worktree va a `<repo>/.claude/worktrees`. La ruta debe ser la única línea de la salida estándar. |
| `hook worktree-remove` | `RemoveWorktreeUseCase` (forzado), `DeleteMergedBranchUseCase`, `CloseHerdrWorkspacesUseCase` | Forzar la eliminación es lo que haría Claude Code sin el hook. |
| `hook session-start` | `ResolveWorktreeEnvironmentUseCase` | Solo un repositorio registrado como proyecto (`ProjectRepository`) resuelve y entrega su entorno; en cualquier otro imprime las reglas del flujo construidas sin leer `.shogunai.json`. El contexto nombra las variables sin sus valores, y aparte las pendientes de aprobación. `CLAUDE_ENV_FILE` se lee a través de una consulta del entorno inyectada. Un `.shogunai.json` ilegible sale con `1` antes de escribir. No reserva nada: para un worktree sin emulador o simulador, la línea de contexto explica cómo adquirirlo. |
| `hook agent-state` | `AgentSessionStore` (`FileAgentSessionStore`) | Convierte `UserPromptSubmit`, `Notification` (`idle_prompt` es terminada, `auth_success` se ignora, cualquier otra es bloqueada), `Stop` y `SessionEnd` en un registro del worktree que contiene el `cwd` del hook. El `pid` de la sesión es el primer ancestro `claude` del proceso (`ProcessHandle`), inyectado como `agentProcessId`. Con el estado trabajando llama además a `RenewWorktreeLeasesUseCase` para el worktree. Tras una entrada válida sale siempre con `0`; cualquier fallo se traga para que el hook nunca bloquee a Claude Code. |
| `hooks` | `ClaudeCodeHooks` | Primero previsualiza (`previewInstall` / `previewUninstall`), pregunta con `readAnswer` salvo con `--yes` y entonces escribe. `CLAUDE_CONFIG_DIR` sale de la consulta del entorno inyectada. |
| `hook herdr` | `ProvisionWorktreeUseCase` en `worktree.created`, `DeleteMergedBranchUseCase` en `worktree.removed` | El evento llega en `HERDR_PLUGIN_EVENT` y `HERDR_PLUGIN_EVENT_JSON`. En `worktree.removed` el repositorio sale de `data.workspace.worktree.repo_root` o, si no, del proyecto cuya carpeta de worktrees contiene la ruta, y nunca se llama a Herdr. En `worktree.created` fuera de la carpeta de worktrees del proyecto ejecuta `$HERDR_BIN_PATH notification show` (`herdr` si no está definida). Variables ausentes o inválidas salen con `2`. |
| `help`, `version` | | `version` imprime `BuildInfo.VERSION`, la misma que la de la app. |

Los errores se imprimen a partir del mensaje de `WorktreeError`. Los comandos de hook reciben la entrada estándar a través de `readInput` de `ShogunCli`, así que los tests pasan el JSON directamente.

## Repositorio y proyecto

Cada comando parte de un directorio (el actual, o la ruta que se pasa a `provision`):

1. `ResolveRepositoryUseCase` encuentra el repositorio base con `git rev-parse --git-common-dir`, así que funciona desde el worktree principal, cualquier subcarpeta o un worktree enlazado.
2. El proyecto es el de `~/.shogunai/projects.json` cuya ruta de repositorio base es el mismo directorio, comparando rutas canónicas (así `/tmp/…` y `/private/tmp/…` coinciden en macOS). Aporta la carpeta de worktrees, los archivos locales y la rama principal.
3. Sin proyecto, el CLI usa una configuración sin archivos locales y con la rama principal detectada. Solo `new` necesita carpeta de worktrees; la pide con `--worktrees-root <dir>` (las rutas relativas se resuelven respecto al directorio actual).

## Estructura

- `CliCommand.kt` — `parseArguments(args)` convierte los argumentos en un `CliCommand` sellado o en un error de uso. Escrito a mano: una docena de comandos con una veintena de opciones, cada uno con sus pequeñas reglas (`--from` no con `--branch`, `--avd` solo con `start`, alias como `-y`), no justifican una librería de parsing ni su coste de arranque.
- `ShogunCli.kt` — `ShogunCli(puertos…, workingDirectory, out, err).run(args): Int` ejecuta un comando. Recibe los puertos y los flujos de salida, así que `ShogunCliTest` prueba cada comando con los test fixtures de `core` y buffers de texto.
- `Main.kt` — `main` y `runShogun(args)` construyen `ShogunCli` con las implementaciones reales (`ProcessBuilderShellCommandExecutor`, `NioFileManager`, `JsonProjectRepository`, `RepositoryProvisioningFiles`, `ProcessGradleDaemonSource`, `NioEnvironmentStateStore`, `SocketPortProbe`, `JsonResourceLimitsRepository`, `JvmMachineCapacity`, `LocalAndroidSdk`, `SimctlAppleSimulators`, `FileKmpTestWiring`, `HerdrConfigFile`, `MacDerivedDataSource`, `NioProvisioningRecordStore`; `ShogunCli` usa `CliHerdrClient` como `HerdrClient` por defecto), y resuelve los entornos con `MachineProviderContributions`, que lee los ajustes de simulador de un repositorio en su proyecto.

El módulo aplica el plugin `application` de Gradle, así que `./gradlew :cli:run --args="status"` lo ejecuta durante el desarrollo y `:cli:distZip` construye el zip independiente.

## Cómo se llega a la app de escritorio

`shogun app` corre en un proceso distinto del de una app en marcha, así que la orden viaja por dos elementos de `~/.shogunai/`, ambos nuevos y ignorados por las versiones que no los conocen:

- **`app.lock`**: la app en marcha mantiene un bloqueo exclusivo sobre él durante toda su vida (`FileAppInstanceLock`). El CLI lo sondea con `tryLock`: si consigue el bloqueo, nadie está en marcha. El sistema operativo libera el bloqueo cuando el proceso muere, así que un cierre brusco no deja nada obsoleto.
- **`app-requests/`**: un fichero JSON por orden (`AppRequest`: `version`, `createdAt`, `action`, `projectId`, `settings`), escrito mediante un fichero temporal y un movimiento atómico (`FileAppRequestChannel`). Las acciones son `open-app` y `open-project`.

La app consulta la carpeta cada 500 ms aproximadamente desde `Main.kt` cuando tiene el bloqueo, la orden más antigua primero, y entrega cada una a `AppRequestHandler`, que refresca la lista de proyectos, trae la ventana al frente y navega mediante `ShellViewModel` (`selectProject` o `editProject`), de modo que se aplica la protección de cambios sin guardar y un destino que ya está abierto se deja como está. Las órdenes mal formadas, de una versión o acción desconocida, que nombran un proyecto inexistente o con más de un minuto se borran sin efecto.

Cuando nadie tiene el bloqueo, el CLI deja primero la orden y después lanza la app (`DesktopAppLauncher`): en macOS `open <bundle>`, en Windows y Linux el ejecutable del lanzador, resuelto desde la ruta del propio CLI. Un comando cuyo ejecutable no es el lanzador de la app (`java`, el zip independiente) falla con `AppNotAvailable` antes de registrar o escribir nada. El razonamiento y las alternativas (socket, esquema de URL `shogunai://`) están en el `design.md` del change `cli-app-projects`.

## Cómo se distribuye

La app de escritorio empaquetada lleva un runtime de jlink sin binario `java`, así que el CLI no se puede arrancar con un script que lo reutilice. En su lugar, **el propio lanzador de la app es el comando**: el `main(args)` de `desktopApp` llama a `runShogun` y termina cuando recibe argumentos (ignorando el antiguo argumento `-psn_…` de macOS), y abre la ventana en caso contrario. El camino del CLI nunca carga AWT ni Compose, así que no aparece ninguna ventana ni icono en el Dock, y arranca en alrededor de una décima de segundo.

| Plataforma | Cómo se instala `shogun` |
|---|---|
| macOS (Homebrew) | El Cask enlaza `ShogunAi.app/Contents/MacOS/ShogunAi` como `shogun`. |
| Linux (`.deb`) | El lanzador es `/opt/shogunai/bin/ShogunAi`; enlázalo como `shogun` a mano ([Instalación](../guide/installation.md#el-comando-shogun) explica cómo). |
| Windows, o cualquier sistema con Java 21+ | `shogun-<versión>.zip` de la release: `bin/shogun` y `bin/shogun.bat`. El lanzador del `.msi` de Windows no tiene consola, así que no puede imprimir. |

El razonamiento y las mediciones de arranque están en el `design.md` del cambio `shogun-cli` (`openspec/changes/archive/`).
