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

- `Worktree` — un worktree activo tal como lo reporta `git worktree list`: `path`, `branch` (`null` si está en `detached`), `head`, `isMain`, `isBare`.
- `BranchOption` — una rama local ofrecida al crear un worktree desde una rama existente: `name` e `isCheckedOut` (ya en uso en el repositorio base o en otro worktree).

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
}
```

Abstrae el sistema de archivos para que la copia de secretos sea testeable sin tocar disco.

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

### `RemoveWorktreeUseCase`

Ejecuta `git worktree remove [--force] <path>` y, opcionalmente, `git branch -d <branch>` para borrar la rama local (borrado seguro, no `-D`).

!!! note
    Git no permite eliminar un worktree desde dentro de sí mismo, otro motivo para ejecutar siempre los comandos desde el repositorio base.

### `CopyWorktreeLaunchCommandUseCase`

Copia al portapapeles `cd "<ruta del worktree>" && <comando del agente>`, con el comando del agente de `AgentLaunchConfig.resolvedCommand()`, para que el usuario lo pegue en la terminal que prefiera.

### `RepairWorktreesUseCase`

Vuelve a enlazar el repositorio base y sus worktrees después de moverlos en disco. Git guarda rutas absolutas en ambos sentidos (`<worktree>/.git` apunta al repositorio base y `.git/worktrees/<nombre>/gitdir` apunta de vuelta), así que un movimiento las rompe. Ejecuta `git worktree repair` desde el (nuevo) repositorio base; los worktrees que ya no existen en su ruta antigua pero sí en la misma posición relativa bajo el nuevo `worktreesRoot` se pasan como argumentos, para que Git arregle ambos sentidos. La UI lo ejecuta tras guardar un proyecto cuya ruta del repositorio base o carpeta raíz de worktrees cambió.
