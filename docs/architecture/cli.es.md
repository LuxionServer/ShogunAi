# Herramienta de línea de comandos

El comando `shogun` ejecuta las operaciones de worktrees de ShogunAi desde un terminal, un script o un hook de un agente, sin abrir la ventana de escritorio. Vive en el módulo **`cli`** (`cli/src/main/kotlin/app/luxion/shogunai/cli`) y llama a los mismos casos de uso de `core` que la app, así que un worktree creado o limpiado desde la línea de comandos queda exactamente igual que si lo hubiera hecho la app.

## Comandos

| Comando | Qué hace | Salida |
|---|---|---|
| `shogun new <id-tarea> [--fix]` | Crea un worktree en una rama nueva `feature/` (o `fix/`), provisionado como lo hace la app (`CreateWorktreeUseCase`). | La ruta absoluta del worktree, sola en la salida estándar. |
| `shogun new --branch <rama>` | Crea un worktree para una rama local existente (`CreateWorktreeFromBranchUseCase`). | Igual. |
| `shogun provision [<ruta>]` | Copia los archivos locales y el provisionado del repositorio en un worktree ya existente, p. ej. uno creado por otra herramienta (`ProvisionWorktreeUseCase`). Por defecto, el directorio actual. | Una ruta relativa copiada por línea. |
| `shogun status` | Lista todos los worktrees del repositorio con su estado. | Una línea por worktree: ruta, rama y después detalles (`main`, `origin=shogunai\|claude-code\|other`, `locked`, `changes=N`, `ahead=N behind=N` respecto al upstream, `merged`, `folder-missing`). |
| `shogun clean [--yes] [--delete-branches] [--stop-gradle-daemons]` | Selecciona los mismos worktrees que "Limpiar" en la app (`selectCleanupCandidates`) y solo los lista; con `--yes` los elimina (`CleanUpWorktreesUseCase`), y con `--delete-branches` borra además sus ramas mergeadas. Con `--stop-gradle-daemons` lista los daemons de Gradle antes de eliminar nada y después para los ociosos cuya última build fue en un worktree eliminado (`idleDaemonsOfRemovedWorktrees`, `StopGradleDaemonsUseCase`); un daemon que no se puede listar o parar se informa en la salida de error sin cambiar el código de salida. | `would remove …` / `would stop Gradle daemon …`, o `removed …` / `kept branch …` / `stopped …` / `kept …`, y los fallos en la salida de error. |
| `shogun gradle` | Lista los daemons de Gradle y de Kotlin de la máquina (`ListGradleDaemonsUseCase`). Funciona fuera de un repositorio; dentro de uno, atribuye cada daemon de Gradle a un worktree y, si el repositorio declara el provider de Gradle sin `org.gradle.caching=true`, termina con el aviso de la caché de build (`CheckGradleBuildCacheUseCase`). | Una línea por daemon: pid, tipo y versión, `memory=`, `state=busy\|idle\|unknown` (con `idle-for=`), `worktree=<ruta>\|removed\|other-project`; y después `total memory=… daemons=N`. |
| `shogun gradle stop [<pid>...]` | Para todos los daemons ociosos, o solo los indicados (`StopGradleDaemonsUseCase`). Los ocupados y los de estado desconocido se conservan, y también los de Kotlin mientras haya una build de Gradle en marcha. Un pid que no es un daemon, o un `kill` que falla, sale con `1`. | `stopped <pid> …` / `kept <pid> … (<motivo>)`. |
| `shogun hook worktree-create` | Hook `WorktreeCreate` de Claude Code: lee el JSON del hook por la entrada estándar (`name`, `cwd`), crea un worktree provisionado para `name` en `feature/<name>` en el repositorio de `cwd` (la carpeta de worktrees del proyecto, o `<repo>/.claude/worktrees` sin proyecto), o lo reutiliza si ya existe. | La ruta del worktree, única línea de la salida estándar, como exige el hook. |
| `shogun hook worktree-remove` | Hook `WorktreeRemove` de Claude Code: lee `worktree_path` de la entrada estándar, elimina ese worktree enlazado con `git worktree remove --force` (lo que haría Claude Code sin el hook) y borra su rama solo si está mergeada (`DeleteMergedBranchUseCase`). | Nada; si la rama se conserva, se avisa en la salida de error. |
| `shogun help`, `shogun --version` | Ayuda y versión (`BuildInfo.VERSION`, la misma que la de la app). | |

Códigos de salida: `0` éxito, `1` la operación falló (error en la salida de error, a partir del mensaje de `WorktreeError`), `2` uso incorrecto (incluida una entrada del hook que no es el JSON esperado). Los comandos de hook reciben la entrada estándar a través de `readInput` de `ShogunCli`, así que los tests pasan el JSON directamente. Cómo configurarlos, y las recetas para otros agentes, están en [Uso con agentes](../agents.md). La salida es texto plano en inglés; de momento solo la línea única de `new` está pensada para que la consuman scripts.

## Repositorio y proyecto

Cada comando parte de un directorio (el actual, o la ruta que se pasa a `provision`):

1. `ResolveRepositoryUseCase` encuentra el repositorio base con `git rev-parse --git-common-dir`, así que funciona desde el worktree principal, cualquier subcarpeta o un worktree enlazado.
2. El proyecto es el de `~/.shogunai/projects.json` cuya ruta de repositorio base es el mismo directorio, comparando rutas canónicas (así `/tmp/…` y `/private/tmp/…` coinciden en macOS). Aporta la carpeta de worktrees, los archivos locales y la rama principal.
3. Sin proyecto, el CLI usa una configuración sin archivos locales y con la rama principal detectada. Solo `new` necesita carpeta de worktrees; la pide con `--worktrees-root <dir>` (las rutas relativas se resuelven respecto al directorio actual).

## Estructura

- `CliCommand.kt` — `parseArguments(args)` convierte los argumentos en un `CliCommand` sellado o en un error de uso. Escrito a mano: cuatro comandos y unas pocas opciones no justifican una librería de parsing ni su coste de arranque.
- `ShogunCli.kt` — `ShogunCli(puertos…, workingDirectory, out, err).run(args): Int` ejecuta un comando. Recibe los puertos y los flujos de salida, así que `ShogunCliTest` prueba cada comando con los test fixtures de `core` y buffers de texto.
- `Main.kt` — `main` y `runShogun(args)` construyen `ShogunCli` con las implementaciones reales (`ProcessBuilderShellCommandExecutor`, `NioFileManager`, `JsonProjectRepository`, `RepositoryProvisioningFiles`, `ProcessGradleDaemonSource`).

El módulo aplica el plugin `application` de Gradle, así que `./gradlew :cli:run --args="status"` lo ejecuta durante el desarrollo y `:cli:distZip` construye el zip independiente.

## Cómo se distribuye

La app de escritorio empaquetada lleva un runtime de jlink sin binario `java`, así que el CLI no se puede arrancar con un script que lo reutilice. En su lugar, **el propio lanzador de la app es el comando**: el `main(args)` de `desktopApp` llama a `runShogun` y termina cuando recibe argumentos (ignorando el antiguo argumento `-psn_…` de macOS), y abre la ventana en caso contrario. El camino del CLI nunca carga AWT ni Compose, así que no aparece ninguna ventana ni icono en el Dock, y arranca en alrededor de una décima de segundo.

| Plataforma | Cómo se instala `shogun` |
|---|---|
| macOS (Homebrew) | El Cask enlaza `ShogunAi.app/Contents/MacOS/ShogunAi` como `shogun`. |
| Linux (`.deb`) | El lanzador es `/opt/shogunai/bin/ShogunAi`; enlázalo como `shogun` a mano (el README explica cómo). |
| Windows, o cualquier sistema con Java 21+ | `shogun-<versión>.zip` de la release: `bin/shogun` y `bin/shogun.bat`. El lanzador del `.msi` de Windows no tiene consola, así que no puede imprimir. |

El razonamiento y las mediciones de arranque están en el `design.md` del cambio `shogun-cli` (`openspec/changes/archive/`).
