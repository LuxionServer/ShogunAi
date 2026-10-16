# Capa de dominio

Vive en `core/src/main/kotlin/app/luxion/shogunai/domain`. No depende de ninguna implementación concreta de infraestructura.

## Modelos

### `ProjectConfig`

Configuración de un repositorio sobre el que se crean worktrees. Es el punto que hace que el flujo sea reutilizable entre proyectos, en vez de tener rutas o nombres de archivo fijos en el código.

| Campo | Descripción |
|---|---|
| `baseRepositoryPath` | Ruta absoluta del repositorio principal, desde donde se ejecuta Git. |
| `worktreesRoot` | Carpeta padre donde se crean los worktrees. |
| `secretFiles` | "Archivos locales": rutas exactas, relativas al repositorio base o absolutas, que se copian a cada nuevo worktree solo en esta máquina (p. ej. `local.properties`). Lo que declara el propio repositorio vive en `.shogunai.json` (ver `RepositoryProvisioning`). |
| `agentLaunchConfig` | Comando del agente que se incluye en el comando de lanzamiento copiado para un worktree (`AgentLaunchConfig`, ver abajo). |
| `emulator` | `EmulatorSettings` de esta máquina: la AVD que usar (`null` significa automática) y `noWindow`. Solo se usa cuando el repositorio declara el provider de emulador; nunca se guarda en el repositorio. |
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

### `RepositoryProvisioning`

Lo que el repositorio declara que necesita cada nuevo worktree, leído de ficheros en la raíz del repositorio base y versionado con él, así que lo comparte todo el que lo clona y no depende de la máquina:

| Campo | Origen |
|---|---|
| `copyPatterns` | La lista `copy` de `.shogunai.json`. |
| `worktreeIncludePatterns` | Las líneas de `.worktreeinclude` (el fichero de Claude Code), sin líneas en blanco ni comentarios `#`. |
| `providers` | El objeto `providers` de `.shogunai.json`: los providers que activa el repositorio, cada uno con su id como clave y un objeto de opciones. `gradle` (`GradleProvider`, todavía sin opciones) hace visibles los [daemons de Gradle](#daemons-de-gradle) del repositorio; `emulator` (`EmulatorProvider`, con un `avd` opcional que prefiere el repositorio) da a cada worktree su propio [emulador de Android](#emulador-de-android). Los ids desconocidos se ignoran. |
| `environment` | El objeto `env` y el `envFile` de `.shogunai.json`, como un `EnvironmentDeclaration` (ver [Entorno del worktree](#entorno-del-worktree)). |

`allPatterns` es su unión sin duplicados. Los patrones usan la sintaxis de patrones de ignore de Git, relativos a la raíz del repositorio: `local.properties` coincide a cualquier profundidad, `/local.properties` solo en la raíz, `.idea/runConfigurations/` una carpeta entera, `.env*` un glob.

```json
{ "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"], "providers": { "gradle": {} } }
```

Los campos desconocidos de `.shogunai.json` se ignoran, para que versiones posteriores puedan añadir campos sin romper las anteriores.

### Entorno del worktree

Variables que recibe cada worktree, declaradas en `.shogunai.json` (`WorktreeEnvironment.kt`):

```json
{
  "env": { "PORT": "${port}", "API_URL": "http://localhost:${port:api}", "APP_SUFFIX": "-${worktree}" },
  "envFile": ".env.shogunai"
}
```

- `EnvironmentDeclaration` — las `variables` en el orden del fichero y el `envFile` opcional, una ruta relativa a la raíz del worktree que no puede salir de él. `portKeys` lista los puertos que necesita la declaración.
- `EnvironmentVariable` — un nombre de variable válido (letras, dígitos y `_`, sin empezar por dígito) y su plantilla, convertida al leer el fichero en `TemplatePart`s: `Literal`, `WorktreeName` (`${worktree}`, el nombre de la carpeta del worktree) y `Port(key)`. `${port}` usa como clave el nombre de la propia variable, así que cada variable tiene su puerto; `${port:<clave>}` usa la clave `port:<clave>`, compartida por todas las variables que la usan (`:` no puede aparecer en un nombre de variable, así que las claves nunca chocan). `$$` es un `$` literal; cualquier otro `${…}` lanza `InvalidEnvironmentTemplate`, que la lectura convierte en `InvalidRepositoryConfig`.
- `WorktreeEnvironment` — un entorno resuelto: las `variables` ya renderizadas y los `ports` reservados para el worktree.

### Emulador de Android

El provider de emulador (`Emulator.kt`) solo actúa en repositorios que declaran `"providers": { "emulator": {} }`; para cualquier otro repositorio nada busca el SDK, ejecuta `adb` ni añade variables.

- `EmulatorSettings` — los ajustes de máquina guardados en `ProjectConfig.emulator`.
- `chooseAvd(requested, settings, preferred, available)` — la AVD que arrancar: la pedida para este arranque; si no, el ajuste de la máquina; si no, la AVD preferida del repositorio si esta máquina la tiene; si no, la primera disponible; `null` cuando no hay ninguna.
- Cada worktree reserva la clave de puerto de consola `emulator` entre los puertos pares 5554–5584 (el rango que explora `adb`), con el puerto siguiente (el puerto adb del emulador) también libre, y recibe `ANDROID_SERIAL=emulator-<puerto>` (`emulatorSerial`). El plugin de Android para Gradle lee `ANDROID_SERIAL` en `installDebug`, `uninstall*` y `connectedAndroidTest` para usar solo ese dispositivo.
- `SdkLocation` (raíz del SDK, ejecutables `emulator` y `adb`), `EmulatorState` (`STOPPED`, `BOOTING`, `RUNNING`) y `WorktreeEmulator` (serial y estado) describen lo que informa el [puerto del SDK de Android](#androidsdk).

### Daemons de Gradle

Lo que el provider de Gradle observa en la máquina (`GradleDaemon.kt`):

- `DaemonProcess` — un daemon en ejecución: `pid`, `kind` (`DaemonKind.GRADLE` o `KOTLIN`), `gradleVersion` (solo daemons de Gradle), `residentMemoryBytes`, `lastBuildDirectory` (el directorio de la última build que recibió un daemon de Gradle) y `state`.
- `DaemonState` — `Busy`, `Idle(since)`, `Unknown` (un daemon de Gradle cuyo estado no se puede leer) o `Untracked` (daemons de Kotlin, que no lo registran).
- `GradleDaemon` — un `DaemonProcess` con su `owner` en un repositorio: `DaemonOwner.Worktree(path)`, `RemovedWorktree` (su último directorio de build ya no existe) u `OtherProject`; `null` para daemons de Kotlin, daemons sin directorio de build conocido o fuera de un repositorio.
- `DaemonStopReport` — el resultado de una parada: `stopped`, `kept` (cada uno con un `DaemonKeptReason`: `BUSY`, `STATE_UNKNOWN` o `BUILD_RUNNING`), `notDaemons` (pids pedidos que no son daemons) y `failures`.

### `ThemeMode` / `AppLanguage`

Preferencias del usuario: `LIGHT` / `DARK` / `SYSTEM` y `ENGLISH` / `SPANISH`. Se guardan en `settings.json` (ver [Capa de infraestructura](infrastructure.md)).

### `WorktreeError`

`sealed class` con los errores esperables del flujo, para que la UI decida qué mostrar (y lo traduzca) a partir de datos estructurados en vez de parsear strings:

- `BaseRepositoryNotFound` — el repo base configurado no existe en disco.
- `WorktreeAlreadyExists` — ya hay un directorio en la ruta destino.
- `InvalidTaskId` — el id de tarea no es un segmento de ref de Git válido una vez normalizado.
- `SecretFileNotFound` — faltan archivos locales en el repo base.
- `RepositoryNotFound` — el directorio no está dentro de un repositorio Git con árbol de trabajo (lo usa el CLI).
- `NotAWorktree` — la ruta a provisionar es el worktree principal o no es uno de los worktrees del repositorio.
- `InvalidRepositoryConfig` — `.shogunai.json` existe pero no es JSON válido, no es un objeto, su `copy` no es una lista de strings, su `providers` (o un provider conocido dentro de él) no es un objeto, o su `env` o `envFile` no son válidos; guarda la ruta del fichero y el detalle.
- `NoFreePort` — todos los puertos entre 20000 y 29999 están reservados u ocupados.
- `EnvironmentFileTracked` — el `envFile` declarado es un fichero que Git versiona en el worktree, así que no se sobrescribe.
- `GitCommandFailed` — un comando de Git devolvió un código de salida distinto de cero; guarda el comando, el código de salida y la salida de error de Git.
- `SecretCopyFailed` — la copia de archivos locales o del repositorio falló después de haber creado ya el worktree.
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
    fun readProperties(path: String): Map<String, String>
}
```

Abstrae el sistema de archivos para que la copia de secretos y la medición de worktrees sean testeables sin tocar disco. `copy` crea las carpetas padre que falten en el destino. `directorySize` suma los archivos regulares bajo `path`, saltándose las carpetas de `excluding`. `modificationStamps` devuelve cada archivo y carpeta bajo `path` (o el propio `path` si es un archivo) con su fecha de modificación, y nada si no existe. `readProperties` lee un fichero de propiedades de Java (como `gradle.properties`), vacío si no existe o no se puede leer.

### `ClipboardWriter`

```kotlin
interface ClipboardWriter {
    fun write(text: String): Result<Unit>
}
```

Escribe el comando de lanzamiento de un worktree en el portapapeles del sistema.

### `RepositoryProvisioningSource`

```kotlin
interface RepositoryProvisioningSource {
    fun read(repositoryPath: String): RepositoryProvisioning
    fun addCopyPatterns(repositoryPath: String, patterns: List<String>)
}
```

Lee el provisionado del repositorio (lanzando `InvalidRepositoryConfig` si `.shogunai.json` no se puede leer) y añade patrones a `copy`, que es como la pantalla de configuración mueve los archivos locales al repositorio.

### `EnvironmentStateStore` / `PortProbe`

```kotlin
interface EnvironmentStateStore {
    fun readPorts(adminDirectory: String): Map<String, Int>
    fun writePorts(adminDirectory: String, ports: Map<String, Int>)
    fun reservedPortsOnMachine(commonDirectory: String): Set<Int>
    fun <T> withLock(block: () -> T): T
}

interface PortProbe {
    fun isFree(port: Int): Boolean
}
```

`EnvironmentStateStore` guarda los puertos reservados de cada worktree en el directorio de administración que Git tiene para ese worktree (`.git/worktrees/<id>/` para uno enlazado, `.git/` para el principal), lista todas las reservas de la máquina (los worktrees de este repositorio más los de cualquier repositorio para el que ShogunAi haya guardado reservas) y ejecuta un bloque con el lock de reservas de la máquina. `PortProbe` indica si un puerto está libre en la máquina.

### `AndroidSdk`

```kotlin
interface AndroidSdk {
    fun locate(repositoryPath: String): SdkLocation
    suspend fun listAvds(sdk: SdkLocation): List<String>
    suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState>
    fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean)
    suspend fun stopEmulator(sdk: SdkLocation, serial: String)
}
```

Todo lo que el provider de emulador necesita del SDK de Android, usado solo en repositorios que declaran el provider: encontrar el SDK (`AndroidSdkNotFound` con los sitios revisados), listar las AVDs, el estado de todos los emuladores en marcha en una sola llamada, arrancar un emulador desacoplado en un puerto de consola y pararlo. Los errores de las herramientas son `AndroidToolFailed`.

### `GradleDaemonSource`

```kotlin
interface GradleDaemonSource {
    suspend fun list(): List<DaemonProcess>
}
```

Lista los daemons de Gradle y de Kotlin que se ejecutan en la máquina con su estado, o falla con `ProcessListingFailed` cuando no se pueden listar los procesos.

### Repositorios

- `ProjectRepository` — `list()`, `save(project)`, `delete(id)` sobre el catálogo de proyectos.
- `ThemePreferenceRepository` / `LanguagePreferenceRepository` — `load()` / `save(...)` de cada preferencia del usuario.

## Casos de uso

Los casos de uso de worktrees se construyen por proyecto (reciben su `ProjectConfig`) y devuelven `Result<T>` (con `runCatching`), con errores tipados como `WorktreeError`. Todos los comandos de Git se ejecutan desde el repositorio base.

### `CreateWorktreeUseCase`

Crea un worktree en una rama **nueva** `<tipo>/<taskId>`. Primero normaliza el id de tarea (`normalizeTaskId`: recorta y sustituye los espacios por `-`) y lo valida como segmento de ref de Git. Después, en este orden:

1. **Validaciones previas** (repo base existe, destino libre, archivos locales presentes) y **resolución de los archivos del repositorio** — se hacen *antes* de tocar Git para no dejar un worktree a medias.
2. `git worktree add <path> -b <branch>`.
3. Copia de los archivos locales y después de los del repositorio al nuevo directorio, en las mismas rutas relativas.
4. Preparación de su entorno con el `WorktreeEnvironmentPreparer` que recibe: `PrepareWorktreeEnvironmentUseCase` lo resuelve reservando puertos (`ResolveWorktreeEnvironmentUseCase`) y, si se declara `envFile`, `WriteEnvironmentFileUseCase` lo escribe en formato dotenv, negándose a sobrescribir un fichero que Git versiona (`git ls-files --error-unmatch`, `EnvironmentFileTracked`) e informando de los fallos de escritura como `EnvironmentFileWriteFailed`. El preparador es `WorktreeEnvironmentPreparer.NONE` por defecto, en lo que se apoyan los tests que no tratan del entorno; la app y el CLI siempre pasan el real.

Para resolver los archivos del repositorio se lee `RepositoryProvisioning` y, si declara algún patrón, se ejecuta `git ls-files --others --ignored -z -x <patrón>…`: todos los archivos que Git no versiona y coinciden con un patrón (los versionados ya llegan con el checkout). Sin patrones no se ejecuta ningún comando de Git. Un `.shogunai.json` ilegible (`InvalidRepositoryConfig`) o un `git ls-files` que falla (`GitCommandFailed`) detienen la creación antes de crear nada. Los archivos locales se comprueban de forma estricta (`SecretFileNotFound`) y los patrones de forma laxa: un patrón sin coincidencias no copia nada.

Si la copia o la preparación del entorno fallan **después** de crear el worktree, se revierte con `git worktree remove --force` (best-effort) antes de propagar el error. Si `git worktree add` falla porque falta `git-lfs`, se eliminan el worktree a medio crear y su rama nueva y se devuelve `GitLfsNotFound`.

### `CreateWorktreeFromBranchUseCase`

El mismo flujo para una rama que **ya existe** en local: comprueba la rama con `git branch --list`, ejecuta `git worktree add <path> <branch>` (sin `-b`) en la carpeta que da `worktreePathForBranch` y copia los archivos locales y los del repositorio de la misma forma. Una rama ya en uso en otro sitio falla con `BranchAlreadyCheckedOut`.

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

### `ResolveRepositoryUseCase`

Encuentra el repositorio base (worktree principal) que contiene un directorio, para los frontales que parten de un directorio en vez de un proyecto, como el CLI. Ejecuta ahí `git rev-parse --path-format=absolute --git-common-dir`: la carpeta `.git` común la comparten todos los worktrees, así que su padre es el worktree principal tanto si el directorio es el worktree principal, una subcarpeta o un worktree enlazado. Cualquier otra cosa (no es un repositorio, es un repositorio bare) es `RepositoryNotFound`.

### `DeleteMergedBranchUseCase`

Borra una rama local con `git branch -d`, que Git rechaza cuando la rama no está mergeada, y devuelve si se borró en vez de fallar. Lo usa el hook `WorktreeRemove` del CLI, donde conservar la rama es lo esperado y no un error.

### `ProvisionWorktreeUseCase`

Aplica los mismos archivos que la creación (archivos locales y provisionado del repositorio, ver `CreateWorktreeUseCase`) a un worktree enlazado **ya existente**, p. ej. uno creado por otra herramienta. La ruta tiene que ser uno de los worktrees del repositorio (según `ListWorktreesUseCase`) y no el principal (`NotAWorktree`). Los archivos se copian encima de los que haya, y devuelve las rutas relativas copiadas. El entorno se prepara igual (reservando puertos para un worktree creado por otra herramienta). A diferencia de la creación, una copia o un entorno fallidos no eliminan nada: el worktree no es de ShogunAi como para borrarlo. Creación y provisionado comparten la misma función de copia.

### `RepairWorktreesUseCase`

Vuelve a enlazar el repositorio base y sus worktrees después de moverlos en disco. Git guarda rutas absolutas en ambos sentidos (`<worktree>/.git` apunta al repositorio base y `.git/worktrees/<nombre>/gitdir` apunta de vuelta), así que un movimiento las rompe. Ejecuta `git worktree repair` desde el (nuevo) repositorio base; los worktrees que ya no existen en su ruta antigua pero sí en la misma posición relativa bajo el nuevo `worktreesRoot` se pasan como argumentos, para que Git arregle ambos sentidos. La UI lo ejecuta tras guardar un proyecto cuya ruta del repositorio base o carpeta raíz de worktrees cambió.

### `ResolveWorktreeEnvironmentUseCase`

Resuelve el entorno de un worktree a partir de la declaración del repositorio base y de los providers que declara. Cada puerto necesario es un `PortRequest` (una clave, los puertos candidatos, los que también tienen que estar libres y el error cuando no queda ninguno): las claves `${port}` declaradas piden 20000–29999 (`declaredPortRequests`); los providers añaden las suyas con `EnvironmentContribution`s (`providerContributions`), junto con las variables que derivan de sus puertos. Sin nada declarado y sin providers que aporten, devuelve un `WorktreeEnvironment` vacío sin ejecutar ningún comando. Si no, busca los directorios de Git de administración y común del worktree (`git rev-parse --path-format=absolute --git-dir --git-common-dir` dentro del worktree) y, con el lock de la máquina:

1. conserva los puertos guardados del worktree cuyas claves se siguen pidiendo y libera el resto;
2. reserva cada clave que falte en el primer candidato que, junto con sus puertos adicionales, ningún worktree de la máquina tenga reservado y `PortProbe` dé por libre (si no, el error de la petición, p. ej. `NoFreePort`);
3. escribe las reservas solo si han cambiado.

Las variables de los providers van primero y las variables declaradas en `env` sustituyen a una del provider con el mismo nombre. Un puerto reservado no se vuelve a comprobar, porque se espera que el propio servidor del worktree esté escuchando en él. Con `reserve = false` (para mostrar los puertos sin efectos secundarios) solo lee los puertos guardados, sin lock ni escritura, y no devuelve variables hasta que todos los puertos estén reservados. `formatEnvironment` presenta las variables como `sh` (`export NOMBRE='valor'`, comillas simples con escape `'\''`, seguro para `eval`), `dotenv` (`NOMBRE=valor`, entre comillas dobles y escapado salvo que el valor sea simple) o `json`.

### Casos de uso del emulador

Los tres fallan con `EmulatorProviderNotDeclared` (o no devuelven nada, en el caso de los estados) cuando el repositorio no declara el provider de emulador, antes de tocar el SDK.

- `StartWorktreeEmulatorUseCase(config, worktreePath, avd?, noWindow?)` resuelve el entorno del worktree (reservando su puerto de emulador si hace falta), vuelve enseguida si su serial ya está en marcha y, si no, arranca la AVD de `chooseAvd` (`NoAvdAvailable` si no hay ninguna) con el ajuste de ventana de la llamada o del proyecto, salvo que ya esté arrancando, y consulta `deviceStates` cada 2 s hasta que está en marcha. A los 180 s falla con `EmulatorBootTimeout` y deja el emulador arrancando. La pausa se inyecta, así que los tests no esperan.
- `StopWorktreeEmulatorUseCase(config, worktreePath)` lee el puerto de emulador del worktree sin reservar; si no hay ninguno reservado no hay nada que parar. `stopSerials` para los seriales indicados que estén en marcha, y la limpieza lo usa con seriales leídos antes de eliminar los worktrees.
- `ReadEmulatorStatesUseCase(config, worktrees)` asocia cada worktree con puerto de emulador reservado a su serial y su estado, a partir de las reservas guardadas y una sola llamada a `deviceStates`; los worktrees sin reserva no aparecen.

### Casos de uso de los daemons de Gradle

Se usan cuando el repositorio declara el provider de Gradle (y desde `shogun gradle` en cualquier sitio):

- `ListGradleDaemonsUseCase` lista los daemons de `GradleDaemonSource` y, dados los worktrees del repositorio, atribuye cada daemon de Gradle por su último directorio de build: `RemovedWorktree` si esa carpeta ya no existe; si no, el worktree con la ruta más larga que la contiene (la misma regla que con los agentes en ejecución, así que ganan los worktrees anidados); y si no, `OtherProject`.
- `StopGradleDaemonsUseCase` para los pids indicados, o todos los daemons. Antes vuelve a listarlos, así que decide con su estado actual: un daemon de Gradle solo se para si está `Idle`, y uno de Kotlin solo si ningún daemon de Gradle está `Busy` o `Unknown`. Para con `kill <pid>` (SIGTERM, para que la JVM ejecute los shutdown hooks de Gradle). Un fallo no detiene el resto.
- `idleDaemonsOfRemovedWorktrees` elige, de un listado tomado antes de una limpieza, los daemons de Gradle ociosos cuya última build fue en uno de los worktrees eliminados. La limpieza se los pasa a `StopGradleDaemonsUseCase`, cuya comprobación en el momento sigue conservando un daemon que se haya puesto ocupado.
- `CheckGradleBuildCacheUseCase` indica si `org.gradle.caching=true` está en el `gradle.properties` del repositorio o en `$GRADLE_USER_HOME/gradle.properties`. Con la caché de build local de Gradle activada, un worktree nuevo reutiliza las salidas de tareas compiladas en otros worktrees, porque las claves de la caché no dependen de la ruta del checkout.
