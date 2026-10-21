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
| `xcode`, `xcode clean` | `ListDerivedDataUseCase`, `DeleteOrphanedDerivedDataUseCase` | Sin el provider o fuera de macOS sale con `0`. |
| `env` | `ResolveWorktreeEnvironmentUseCase` (reservando), `formatEnvironment` | Reserva solo los puertos declarados; las reservas bajo demanda (el puerto del emulador, el simulador) se leen, nunca se adquieren. |
| `emulator` | `StartWorktreeEmulatorUseCase`, `StopWorktreeEmulatorUseCase`, `ReadEmulatorStatesUseCase` | `start` adquiere el emulador mediante `AcquireWorktreeResourceUseCase` con los límites del perfil de capacidad de la máquina, imprime lo que liberó por la salida de errores y la línea `export ANDROID_SERIAL=…` cuando acaba de adquirirlo. `status` nunca reserva un puerto e imprime `not acquired` antes del primer arranque. Sin el provider: `EmulatorProviderNotDeclared`. |
| `simulator` | `StartWorktreeSimulatorUseCase`, `StopWorktreeSimulatorUseCase`, `ReadSimulatorStatesUseCase`, `KmpTestWiring` | `start` adquiere (crea) el simulador del mismo modo e imprime `export IOS_SIMULATOR_UDID=…` cuando acaba de hacerlo. `status` nunca crea un simulador e imprime `not acquired` antes del primer arranque. Sin el provider: `IosSimulatorProviderNotDeclared`; fuera de macOS: `IosSimulatorsNotSupported`. |
| `simulator gradle-init` | `KmpTestWiring` | `install` pregunta por la entrada estándar (`readInput`) salvo con `--yes`. |
| `hook worktree-create` | `CreateWorktreeUseCase`, o el worktree existente | Lee `name` y `cwd` del JSON del hook; sin proyecto el worktree va a `<repo>/.claude/worktrees`. La ruta debe ser la única línea de la salida estándar. |
| `hook worktree-remove` | `RemoveWorktreeUseCase` (forzado), `DeleteMergedBranchUseCase`, `CloseHerdrWorkspacesUseCase` | Forzar la eliminación es lo que haría Claude Code sin el hook. |
| `hook session-start` | `ResolveWorktreeEnvironmentUseCase` | `CLAUDE_ENV_FILE` se lee a través de una consulta del entorno inyectada. Un `.shogunai.json` ilegible sale con `1` antes de escribir. No reserva nada: para un worktree sin emulador o simulador, la línea de contexto explica cómo adquirirlo. |
| `hook agent-state` | `AgentSessionStore` (`FileAgentSessionStore`) | Convierte `UserPromptSubmit`, `Notification` (`idle_prompt` es terminada, `auth_success` se ignora, cualquier otra es bloqueada), `Stop` y `SessionEnd` en un registro del worktree que contiene el `cwd` del hook. El `pid` de la sesión es el primer ancestro `claude` del proceso (`ProcessHandle`), inyectado como `agentProcessId`. Tras una entrada válida sale siempre con `0`; cualquier fallo se traga para que el hook nunca bloquee a Claude Code. |
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
- `Main.kt` — `main` y `runShogun(args)` construyen `ShogunCli` con las implementaciones reales (`ProcessBuilderShellCommandExecutor`, `NioFileManager`, `JsonProjectRepository`, `RepositoryProvisioningFiles`, `ProcessGradleDaemonSource`, `NioEnvironmentStateStore`, `SocketPortProbe`, `JsonResourceLimitsRepository`, `JvmMachineCapacity`, `LocalAndroidSdk`, `SimctlAppleSimulators`, `FileKmpTestWiring`, `HerdrConfigFile`, `MacDerivedDataSource`; `ShogunCli` usa `CliHerdrClient` como `HerdrClient` por defecto), y resuelve los entornos con `MachineProviderContributions`, que lee los ajustes de simulador de un repositorio en su proyecto.

El módulo aplica el plugin `application` de Gradle, así que `./gradlew :cli:run --args="status"` lo ejecuta durante el desarrollo y `:cli:distZip` construye el zip independiente.

## Cómo se distribuye

La app de escritorio empaquetada lleva un runtime de jlink sin binario `java`, así que el CLI no se puede arrancar con un script que lo reutilice. En su lugar, **el propio lanzador de la app es el comando**: el `main(args)` de `desktopApp` llama a `runShogun` y termina cuando recibe argumentos (ignorando el antiguo argumento `-psn_…` de macOS), y abre la ventana en caso contrario. El camino del CLI nunca carga AWT ni Compose, así que no aparece ninguna ventana ni icono en el Dock, y arranca en alrededor de una décima de segundo.

| Plataforma | Cómo se instala `shogun` |
|---|---|
| macOS (Homebrew) | El Cask enlaza `ShogunAi.app/Contents/MacOS/ShogunAi` como `shogun`. |
| Linux (`.deb`) | El lanzador es `/opt/shogunai/bin/ShogunAi`; enlázalo como `shogun` a mano ([Instalación](../guide/installation.md#el-comando-shogun) explica cómo). |
| Windows, o cualquier sistema con Java 21+ | `shogun-<versión>.zip` de la release: `bin/shogun` y `bin/shogun.bat`. El lanzador del `.msi` de Windows no tiene consola, así que no puede imprimir. |

El razonamiento y las mediciones de arranque están en el `design.md` del cambio `shogun-cli` (`openspec/changes/archive/`).
