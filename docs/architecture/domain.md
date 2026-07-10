# Capa de dominio

Vive en `desktopApp/src/main/kotlin/app/luxion/shogunai/domain`. No depende de ninguna implementación concreta de infraestructura.

## Modelos

### `ProjectConfig`

Configuración de un repositorio sobre el que se crean worktrees. Es el punto que hace que el flujo sea reutilizable entre proyectos, en vez de tener rutas o nombres de archivo fijos en el código.

| Campo | Descripción |
|---|---|
| `baseRepositoryPath` | Ruta absoluta del repositorio principal, desde donde se ejecuta Git. |
| `worktreesRoot` | Carpeta padre donde se crean los worktrees. |
| `secretFiles` | Archivos de credenciales locales a copiar al nuevo worktree (p. ej. `local.properties`). |

Expone dos helpers de rutas: `worktreePathFor(taskId)` (`<worktreesRoot>/<taskId>`) y `baseSecretPath(fileName)`.

### `BranchType`

Enum `FEATURE` / `FIX`, cada uno con un `prefix` (`"feature"` / `"fix"`) que se antepone al ID de la tarea al nombrar la rama: `feature/TASK-123`.

### `Worktree`

Representa un worktree activo tal y como lo reporta `git worktree list`: `path`, `branch` (`null` si está `detached`), `head`, `isMain`, `isBare`.

### `WorktreeError`

`sealed class` de errores esperados del flujo, para que la futura GUI pueda decidir qué mostrar sin parsear strings:

- `BaseRepositoryNotFound` — el repo base configurado no existe en disco.
- `WorktreeAlreadyExists` — ya hay un directorio en la ruta destino.
- `SecretFileNotFound` — faltan archivos de secretos en el repo base.
- `GitCommandFailed` — un comando de Git devolvió exit code != 0.
- `SecretCopyFailed` — falló la copia de secretos tras crear el worktree ya creado.

## Puertos

Los casos de uso dependen de interfaces, no de implementaciones. Esto es lo que permite testearlos con dobles.

### `ShellCommandExecutor`

```kotlin
interface ShellCommandExecutor {
    suspend fun execute(command: List<String>, workingDirectory: String? = null): CommandResult
}
```

El comando se pasa como **argv** (lista de strings), nunca como una cadena para que un shell la interprete. Motivo: evitar diferencias entre el shell por defecto de macOS (zsh) y Arch Linux (bash), y evitar problemas de escapado/inyección de comandos.

`CommandResult` guarda `command`, `exitCode`, `stdout`, `stderr` sin interpretar su significado — cada caso de uso decide qué considera éxito vía `isSuccess` (`exitCode == 0`).

### `FileManager`

```kotlin
interface FileManager {
    fun exists(path: String): Boolean
    fun copy(source: String, destination: String)
}
```

Abstrae el sistema de ficheros para que la copia de secretos sea unitariamente testeable sin tocar disco.

## Casos de uso

Todos devuelven `Result<T>` (usando `runCatching`), con errores tipados como `WorktreeError`.

### `CreateWorktreeUseCase`

Orquesta, en este orden:

1. **Validaciones previas** (repo base existe, destino libre, secretos presentes) — se hacen *antes* de tocar Git para no dejar un worktree a medias que luego no se pueda completar.
2. `git worktree add <path> -b <branch>`.
3. Copia de los archivos de secretos al nuevo directorio.

Si la copia de secretos falla **después** de crear el worktree, se revierte con `git worktree remove --force` (best-effort) antes de propagar `SecretCopyFailed`.

### `RemoveWorktreeUseCase`

Ejecuta `git worktree remove [--force] <path>` y, opcionalmente, `git branch -d <branch>` para borrar la rama local (borrado seguro, no `-D`).

!!! note
    Ambos comandos se ejecutan siempre desde el repositorio base — Git no permite eliminar un worktree desde dentro de sí mismo.

### `ListWorktreesUseCase`

Ejecuta `git worktree list --porcelain` (formato estable pensado para parsearse, a diferencia de la salida legible por defecto) y parsea el resultado con `parseWorktreeList`. Marca `isMain = true` en el worktree cuya ruta coincide con `config.baseRepositoryPath`.
