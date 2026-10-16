# Herramienta de línea de comandos

El comando `shogun` ejecuta las operaciones de worktrees de ShogunAi desde un terminal, un script o un hook de un agente, sin abrir la ventana de escritorio. Vive en el módulo **`cli`** (`cli/src/main/kotlin/app/luxion/shogunai/cli`) y llama a los mismos casos de uso de `core` que la app, así que un worktree creado o limpiado desde la línea de comandos queda exactamente igual que si lo hubiera hecho la app.

## Comandos

| Comando | Qué hace | Salida |
|---|---|---|
| `shogun new <id-tarea> [--fix]` | Crea un worktree en una rama nueva `feature/` (o `fix/`), provisionado como lo hace la app (`CreateWorktreeUseCase`). | La ruta absoluta del worktree, sola en la salida estándar. |
| `shogun new --branch <rama>` | Crea un worktree para una rama local existente (`CreateWorktreeFromBranchUseCase`). | Igual. |
| `shogun provision [<ruta>]` | Copia los archivos locales y el provisionado del repositorio en un worktree ya existente, p. ej. uno creado por otra herramienta (`ProvisionWorktreeUseCase`). Por defecto, el directorio actual. | Una ruta relativa copiada por línea. |
| `shogun status` | Lista todos los worktrees del repositorio con su estado. | Una línea por worktree: ruta, rama y después detalles (`main`, `origin=shogunai\|claude-code\|other`, `locked`, `changes=N`, `ahead=N behind=N` respecto al upstream, `merged`, `folder-missing`). |
| `shogun clean [--yes] [--delete-branches]` | Selecciona los mismos worktrees que "Limpiar" en la app (`selectCleanupCandidates`) y solo los lista; con `--yes` los elimina (`CleanUpWorktreesUseCase`), y con `--delete-branches` borra además sus ramas mergeadas. | `would remove …`, o `removed …` / `kept branch …`, y los fallos en la salida de error. |
| `shogun help`, `shogun --version` | Ayuda y versión (`BuildInfo.VERSION`, la misma que la de la app). | |

Códigos de salida: `0` éxito, `1` la operación falló (error en la salida de error, a partir del mensaje de `WorktreeError`), `2` uso incorrecto. La salida es texto plano en inglés; de momento solo la línea única de `new` está pensada para que la consuman scripts.

## Repositorio y proyecto

Cada comando parte de un directorio (el actual, o la ruta que se pasa a `provision`):

1. `ResolveRepositoryUseCase` encuentra el repositorio base con `git rev-parse --git-common-dir`, así que funciona desde el worktree principal, cualquier subcarpeta o un worktree enlazado.
2. El proyecto es el de `~/.shogunai/projects.json` cuya ruta de repositorio base es el mismo directorio, comparando rutas canónicas (así `/tmp/…` y `/private/tmp/…` coinciden en macOS). Aporta la carpeta de worktrees, los archivos locales y la rama principal.
3. Sin proyecto, el CLI usa una configuración sin archivos locales y con la rama principal detectada. Solo `new` necesita carpeta de worktrees; la pide con `--worktrees-root <dir>` (las rutas relativas se resuelven respecto al directorio actual).

## Estructura

- `CliCommand.kt` — `parseArguments(args)` convierte los argumentos en un `CliCommand` sellado o en un error de uso. Escrito a mano: cuatro comandos y unas pocas opciones no justifican una librería de parsing ni su coste de arranque.
- `ShogunCli.kt` — `ShogunCli(puertos…, workingDirectory, out, err).run(args): Int` ejecuta un comando. Recibe los puertos y los flujos de salida, así que `ShogunCliTest` prueba cada comando con los test fixtures de `core` y buffers de texto.
- `Main.kt` — `main` y `runShogun(args)` construyen `ShogunCli` con las implementaciones reales (`ProcessBuilderShellCommandExecutor`, `NioFileManager`, `JsonProjectRepository`, `RepositoryProvisioningFiles`).

El módulo aplica el plugin `application` de Gradle, así que `./gradlew :cli:run --args="status"` lo ejecuta durante el desarrollo y `:cli:distZip` construye el zip independiente.

## Cómo se distribuye

La app de escritorio empaquetada lleva un runtime de jlink sin binario `java`, así que el CLI no se puede arrancar con un script que lo reutilice. En su lugar, **el propio lanzador de la app es el comando**: el `main(args)` de `desktopApp` llama a `runShogun` y termina cuando recibe argumentos (ignorando el antiguo argumento `-psn_…` de macOS), y abre la ventana en caso contrario. El camino del CLI nunca carga AWT ni Compose, así que no aparece ninguna ventana ni icono en el Dock, y arranca en alrededor de una décima de segundo.

| Plataforma | Cómo se instala `shogun` |
|---|---|
| macOS (Homebrew) | El Cask enlaza `ShogunAi.app/Contents/MacOS/ShogunAi` como `shogun`. |
| Linux (`.deb`) | El lanzador es `/opt/shogunai/bin/ShogunAi`; enlázalo como `shogun` a mano (el README explica cómo). |
| Windows, o cualquier sistema con Java 21+ | `shogun-<versión>.zip` de la release: `bin/shogun` y `bin/shogun.bat`. El lanzador del `.msi` de Windows no tiene consola, así que no puede imprimir. |

El razonamiento y las mediciones de arranque están en el `design.md` del cambio `shogun-cli` (`openspec/changes/archive/`).
