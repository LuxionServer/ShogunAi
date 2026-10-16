# Capa de dominio

Vive en `desktopApp/src/main/kotlin/app/luxion/shogunai/domain`. No depende de ninguna implementación concreta de infraestructura.

## Modelos

### `ProjectConfig`

Configuración de un repositorio sobre el que se crean worktrees. Es el punto que hace que el flujo sea reutilizable entre proyectos, en vez de tener rutas o nombres de archivo fijos en el código.

| Campo | Descripción |
|---|---|
| `baseRepositoryPath` | Ruta absoluta del repositorio principal, desde donde se ejecuta Git. |
| `worktreesRoot` | Carpeta padre donde se crean los worktrees. |
| `secretFiles` | Archivos de credenciales locales a copiar a cada nuevo worktree (p. ej. `local.properties`), relativos al repositorio base. |
| `agentLaunchConfig` | Comando del agente que se incluye en el comando de lanzamiento copiado para un worktree (`AgentLaunchConfig`, ver abajo). |
| `mainBranch` | Rama opcional contra la que se comparan los worktrees para mostrar su estado (p. ej. `develop`). `null` significa que se detecta a partir del repositorio (ver `ResolveMainBranchUseCase`). |

Helpers de rutas:

- `worktreePathFor(taskId)` — `<worktreesRoot>/<taskId>`.
- `worktreePathForBranch(branch)` — carpeta para una rama existente: quita un prefijo conocido de `BranchType` (`feature/`, `fix/`) y sustituye los `/` restantes por `-`, de modo que `feature/TASK-123` acaba en la misma carpeta que un worktree de rama nueva para `TASK-123`.
- `baseSecretPath(fileName)` — el archivo de secretos dentro del repositorio base.

`Project` une una `ProjectConfig` con un `id` y un `name` visible; es lo que guarda el catálogo de proyectos.

### `AgentLaunchConfig`

```kotlin
@Serializable
data class AgentLaunchConfig(
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    fun resolvedCommand(): String = if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
```

Una `data class` plana con valores por defecto, para que se serialice con `kotlinx.serialization` sin un `SerializersModule` polimórfico y los proyectos guardados antes de que existiera un campo se sigan cargando.

### `BranchType`

Enum `FEATURE` / `FIX`, cada uno con un `prefix` (`"feature"` / `"fix"`) que se antepone al ID de tarea al nombrar la rama: `feature/TASK-123`.

### `Worktree` / `BranchOption`

- `Worktree` — un worktree activo tal como lo reporta `git worktree list`: `path`, `branch` (`null` si está en `detached`), `head`, `isMain`, `isBare`, `isLocked` (una línea `locked`, p. ej. un worktree que está usando una sesión de un agente) e `isPrunable` (una línea `prunable`: su carpeta ya no existe).
- `BranchOption` — una rama local ofrecida al crear un worktree desde una rama existente: `name` e `isCheckedOut` (ya en uso en el repositorio base o en otro worktree).

### `WorktreeStatus` / `MainBranch` / `Divergence`

- `MainBranch` — la rama contra la que se comparan los worktrees: su `name` (`develop`) y la `ref` usada en los comandos de Git (`origin/develop`, o `develop` cuando no hay rama remota de seguimiento).
- `Divergence` — commits `ahead` (por delante) y `behind` (por detrás) respecto a otra rama.
- `WorktreeStatus` — el estado de un worktree: `uncommittedChanges`, las divergencias `upstream` y `mainBranch` (`null` cuando no aplican) y `lastCommitAt`. `isMerged` es derivado: la rama no tiene nada que le falte a la rama principal (`ahead == 0`) y la rama principal ha avanzado más allá de ella (`behind > 0`), así que una rama recién creada no aparece como mergeada.

### `ThemeMode` / `AppLanguage`

Preferencias del usuario: `LIGHT` / `DARK` / `SYSTEM` y `ENGLISH` / `SPANISH`. Se guardan en `settings.json` (ver [Capa de infraestructura](infrastructure.md)).

### `WorktreeError`

`sealed class` con los errores esperables del flujo, para que la UI decida qué mostrar (y lo traduzca) a partir de datos estructurados en vez de parsear strings:

- `BaseRepositoryNotFound` — el repo base configurado no existe en disco.
- `WorktreeAlreadyExists` — ya hay un directorio en la ruta destino.
- `InvalidTaskId` — el id de tarea no es un segmento de ref de Git válido una vez normalizado.
- `SecretFileNotFound` — faltan archivos de secretos en el repo base.
- `GitCommandFailed` — un comando de Git devolvió un código de salida distinto de cero; guarda el comando, el código de salida y la salida de error de Git.
- `SecretCopyFailed` — la copia de secretos falló después de haber creado ya el worktree.
- `ClipboardWriteFailed` — no se pudo copiar el comando de lanzamiento al portapapeles.
- `GitLfsNotFound` — el repositorio usa Git LFS pero `git-lfs` no está instalado.
- `BranchNotFound` — la rama existente pedida no existe en local.
- `BranchAlreadyCheckedOut` — la rama ya está en uso en otro worktree o en el repositorio base.

## Puertos

Los casos de uso dependen de interfaces, no de implementaciones. Esto es lo que permite testearlos con dobles.

### `ShellCommandExecutor`

```kotlin
interface ShellCommandExecutor {
    suspend fun execute(command: List<String>, workingDirectory: String? = null): CommandResult
}
```

El comando se pasa como **argv** (lista de strings), nunca como una cadena para que la interprete un shell. Motivo: evitar diferencias entre el shell por defecto de macOS (zsh) y el de Linux (bash), y evitar problemas de escapado/inyección de comandos.

`CommandResult` guarda `command`, `exitCode`, `stdout` y `stderr` sin interpretar su significado — cada caso de uso decide qué cuenta como éxito con `isSuccess` (`exitCode == 0`). Un proceso que ni siquiera puede arrancar (p. ej. el ejecutable no está en el `PATH`) lanza `CommandExecutionException`.

### `FileManager`

```kotlin
interface FileManager {
    fun exists(path: String): Boolean
    fun copy(source: String, destination: String)
    fun directorySize(path: String, excluding: Set<String> = emptySet()): Long
    fun isDirectory(path: String): Boolean
    fun modificationStamps(path: String): List<Pair<String, Long>>
}
```

Abstrae el sistema de archivos para que la copia de secretos y la medición de worktrees sean testeables sin tocar disco. `directorySize` suma los archivos regulares bajo `path`, saltándose las carpetas de `excluding`. `modificationStamps` devuelve cada archivo y carpeta bajo `path` (o el propio `path` si es un archivo) con su fecha de modificación, y nada si no existe.

### `ClipboardWriter`

```kotlin
interface ClipboardWriter {
    fun write(text: String): Result<Unit>
}
```

Escribe el comando de lanzamiento de un worktree en el portapapeles del sistema.

### Repositorios

- `ProjectRepository` — `list()`, `save(project)`, `delete(id)` sobre el catálogo de proyectos.
- `ThemePreferenceRepository` / `LanguagePreferenceRepository` — `load()` / `save(...)` de cada preferencia del usuario.

## Casos de uso

Los casos de uso de worktrees se construyen por proyecto (reciben su `ProjectConfig`) y devuelven `Result<T>` (con `runCatching`), con errores tipados como `WorktreeError`. Todos los comandos de Git se ejecutan desde el repositorio base.

### `CreateWorktreeUseCase`

Crea un worktree en una rama **nueva** `<tipo>/<taskId>`. Primero normaliza el id de tarea (`normalizeTaskId`: recorta y sustituye los espacios por `-`) y lo valida como segmento de ref de Git. Después, en este orden:

1. **Validaciones previas** (repo base existe, destino libre, secretos presentes) — se hacen *antes* de tocar Git para no dejar un worktree a medias.
2. `git worktree add <path> -b <branch>`.
3. Copia de los archivos de secretos al nuevo directorio.

Si la copia de secretos falla **después** de crear el worktree, se revierte con `git worktree remove --force` (best-effort) antes de propagar `SecretCopyFailed`. Si `git worktree add` falla porque falta `git-lfs`, se eliminan el worktree a medio crear y su rama nueva y se devuelve `GitLfsNotFound`.

### `CreateWorktreeFromBranchUseCase`

El mismo flujo para una rama que **ya existe** en local: comprueba la rama con `git branch --list`, ejecuta `git worktree add <path> <branch>` (sin `-b`) en la carpeta que da `worktreePathForBranch` y copia los secretos. Una rama ya en uso en otro sitio falla con `BranchAlreadyCheckedOut`.

### `ListLocalBranchesUseCase`

Lista las ramas locales (`git for-each-ref --format=%(refname:short) refs/heads`) y marca las que ya están en uso, usando `git worktree list --porcelain`. La UI muestra las marcadas deshabilitadas.

### `ListWorktreesUseCase`

Ejecuta `git worktree list --porcelain` (formato estable pensado para parsear, a diferencia de la salida legible por defecto) y parsea el resultado con `parseWorktreeList`. Marca `isMain = true` en el worktree cuya ruta coincide con `config.baseRepositoryPath`.

### `ResolveMainBranchUseCase`

Resuelve la `MainBranch` una vez por carga de la lista, desde el repositorio base y sin acceso a red: `config.mainBranch` si está definida; si no, la rama por defecto del remoto `origin` (`git symbolic-ref refs/remotes/origin/HEAD`), y como alternativa una `main` local y después `master`. Una rama con nombre se compara a través de `origin/<nombre>` cuando esa ref existe (`git rev-parse --verify`), porque los merges suelen llegar antes al remoto, y a través de la rama local en caso contrario. Devuelve `null` cuando no se puede resolver nada.

### `ReadWorktreeStatusUseCase`

Lee un `WorktreeStatus`, ejecutando cada comando en la carpeta del propio worktree:

- `git --no-optional-locks status --porcelain` — cambios sin commitear. Los locks opcionales se desactivan para que `git status` nunca reescriba el índice; si no, leer los estados cambiaría los metadatos que vigila el refresco automático. Es el único comando obligatorio: si falla (p. ej. la carpeta ya no existe), falla todo el estado.
- `git rev-list --left-right --count @{upstream}...HEAD` — divergencia con el upstream; se omite con HEAD separado y es `null` si no hay upstream.
- `git rev-list --left-right --count <ref principal>...HEAD` — divergencia con la rama principal; se omite cuando no hay rama principal o el worktree está en ella.
- `git log -1 --format=%ct` — fecha del último commit, `null` si no hay commits.

Los merges por squash o rebase no se detectan como mergeados, y las ramas remotas de seguimiento están tan actualizadas como el último fetch del usuario.

### `CleanUpWorktreesUseCase`

`selectCleanupCandidates(worktrees, statuses)` elige los worktrees a limpiar: no el principal, no bloqueados, y o bien con la carpeta perdida (`isPrunable`) o bien mergeados en la rama principal y sin cambios sin commitear. Los worktrees sin estado cargado nunca se eligen.

`CleanUpWorktreesUseCase` recibe esos candidatos, la `MainBranch` y si hay que borrar las ramas, y devuelve un `CleanupReport` (`removed`, `keptBranches`, `failures`). Cada worktree existente se elimina con `git worktree remove` (nunca con `--force`), y los que han perdido la carpeta con un único `git worktree prune`. Si un paso falla se anota y se sigue con los demás. Después, las ramas de cada worktree eliminado, incluidos los podados, solo se borran si `git merge-base --is-ancestor <rama> <ref principal>` sigue teniendo éxito, y entonces con `git branch -D`: `-d` comprueba contra el `HEAD` del repositorio base, que en un repositorio git-flow puede no contener una rama mergeada en `develop`.

### Vista completa de worktrees

- `classifyOrigin(worktree, config)` devuelve el `WorktreeOrigin` de un worktree que no es el principal: `CLAUDE_CODE` si su ruta está dentro de una carpeta `.claude/worktrees/` (se comprueba primero, porque esa carpeta puede estar dentro de la raíz de worktrees), `SHOGUNAI` si está dentro de `config.worktreesRoot` y `OTHER` en otro caso. Las rutas se comparan con separadores `/` y sin barra final, así que también funcionan las de Windows.
- `isInactive(worktree, status, agents, now)` es verdadero para un worktree que no es el principal, sin cambios sin commitear, sin agentes en ejecución y con su último commit más antiguo que `INACTIVITY_THRESHOLD` (14 días).
- `DetectRunningAgentsUseCase` devuelve los `RunningAgent` por ruta de worktree. Ejecuta `lsof -d cwd -F pcn` una vez para obtener el directorio de trabajo de cada proceso, asigna cada proceso al worktree con la ruta más larga que lo contiene (así un proceso en `.claude/worktrees/<nombre>` no se atribuye al worktree principal) y ejecuta `ps -o pid=,comm=` para esos procesos. El nombre del ejecutable se compara con los CLIs de agentes conocidos (`claude`, `codex`, `gemini`, `cursor-agent`, `opencode`, `aider`, `copilot`) y con el comando de agente del propio proyecto. No se usa el nombre que da `lsof` porque Claude Code renombra su proceso con su versión. Si `lsof` o `ps` fallan (p. ej. en Windows), falla con `WorktreeError.ProcessListingFailed`.
- `MeasureWorktreeSizeUseCase` devuelve el tamaño de un worktree con `FileManager.directorySize`, en el dispatcher de IO, excluyendo cualquier otro worktree listado que esté anidado dentro. Devuelve `null` si la carpeta no existe.
- `ReadGitMetadataFingerprintUseCase` devuelve una huella de los metadatos de Git del repositorio principal: los pares ordenados (ruta relativa a `.git`, fecha de modificación) de `HEAD`, `index`, `packed-refs`, `FETCH_HEAD` y todo lo que hay bajo `refs` y `worktrees`, leídos en el dispatcher de IO. Los commits, checkouts, bloqueos, fetches y worktrees añadidos o eliminados por cualquier herramienta la cambian; `objects` no se incluye. Está vacía cuando `<baseRepositoryPath>/.git` no es un directorio.

### `RemoveWorktreeUseCase`

Ejecuta `git worktree remove [--force] <path>` y, opcionalmente, `git branch -d <branch>` para borrar la rama local (borrado seguro, no `-D`).

!!! note
    Git no permite eliminar un worktree desde dentro de sí mismo, otro motivo para ejecutar siempre los comandos desde el repositorio base.

### `CopyWorktreeLaunchCommandUseCase`

Copia al portapapeles `cd "<ruta del worktree>" && <comando del agente>`, con el comando del agente de `AgentLaunchConfig.resolvedCommand()`, para que el usuario lo pegue en la terminal que prefiera.

### `RepairWorktreesUseCase`

Vuelve a enlazar el repositorio base y sus worktrees después de moverlos en disco. Git guarda rutas absolutas en ambos sentidos (`<worktree>/.git` apunta al repositorio base y `.git/worktrees/<nombre>/gitdir` apunta de vuelta), así que un movimiento las rompe. Ejecuta `git worktree repair` desde el (nuevo) repositorio base; los worktrees que ya no existen en su ruta antigua pero sí en la misma posición relativa bajo el nuevo `worktreesRoot` se pasan como argumentos, para que Git arregle ambos sentidos. La UI lo ejecuta tras guardar un proyecto cuya ruta del repositorio base o carpeta raíz de worktrees cambió.
