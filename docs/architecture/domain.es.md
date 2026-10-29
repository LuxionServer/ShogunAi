# Capa de dominio

Vive en `core/src/main/kotlin/app/luxion/shogunai/domain`. No depende de ninguna implementación concreta de infraestructura.

## Modelos

### `ProjectConfig`

Configuración de un repositorio sobre el que se crean worktrees. Es el punto que hace que el flujo sea reutilizable entre proyectos, en vez de tener rutas o nombres de archivo fijos en el código.

| Campo | Descripción |
|---|---|
| `baseRepositoryPath` | Ruta absoluta del repositorio principal, desde donde se ejecuta Git. |
| `worktreesRoot` | Carpeta padre donde se crean los worktrees. |
| `secretFiles` | "Archivos locales": rutas de archivos o carpetas, relativas al repositorio base o absolutas, o patrones (una entrada con `*`, `?` o `[`, ver `isLocalPattern`), que se provisionan en cada nuevo worktree solo en esta máquina (p. ej. `local.properties`). Lo que declara el propio repositorio vive en `.shogunai.json` (ver `RepositoryProvisioning`). |
| `linkedSecretFiles` | Las entradas de `secretFiles` que se enlazan en vez de copiarse; `isLinked(entry)` ignora un separador final. Vacía en los proyectos guardados antes de que existiera, así que sus entradas se siguen copiando. |
| `agentLaunchConfig` | El agente por defecto del proyecto (`AgentLaunchConfig`, ver abajo), que usan todos los lanzamientos salvo que el usuario elija otro. |
| `additionalAgents` | Los demás agentes del proyecto, en orden. `agents` es el agente por defecto seguido de estos, y `agentNamed(displayName)` busca uno por nombre y, si no lo encuentra, devuelve el de por defecto. Mantener el de por defecto en su propio campo hace que una versión anterior, que ignora las claves desconocidas, lo siga lanzando. |
| `simulator` | `SimulatorSettings` de esta máquina: el tipo de dispositivo y el runtime del simulador de iOS (`null` significa automático). Solo se usa cuando el repositorio declara el provider de simulador de iOS; nunca se guarda en el repositorio. |
| `emulator` | `EmulatorSettings` de esta máquina: la AVD que usar (`null` significa automática) y `noWindow`. Solo se usa cuando el repositorio declara el provider de emulador; nunca se guarda en el repositorio. |
| `mainBranch` | Rama opcional contra la que se comparan los worktrees para mostrar su estado (p. ej. `develop`). `null` significa que se detecta a partir del repositorio (ver `ResolveMainBranchUseCase`). |
| `editorOverride` | `EditorId` con el que abrir los worktrees de este proyecto en esta máquina en lugar del IDE por defecto elegido en Preferencias (`null` significa el de por defecto). |
| `fetchBeforeCreate` | Si el diálogo «Nuevo worktree» de la app de escritorio hace fetch de `origin` antes de crear, según lo último elegido para este proyecto. |

Helpers de rutas:

- `worktreePathFor(taskId)` — `<worktreesRoot>/<taskId>`, sustituyendo cualquier `/` del id de tarea por `-`, así que un id como `team/TASK-1` corresponde a una sola carpeta (`team-TASK-1`).
- `worktreePathForBranch(branch)` — carpeta para una rama existente: quita un prefijo conocido de `BranchType` (`feature/`, `fix/`) y sustituye los `/` restantes por `-`, de modo que `feature/TASK-123` acaba en la misma carpeta que un worktree de rama nueva para `TASK-123`.
- `baseSecretPath(fileName)` — el archivo de secretos dentro del repositorio base.

`Project` une una `ProjectConfig` con un `id` y un `name` visible; es lo que guarda el catálogo de proyectos.

### `AgentLaunchConfig`

```kotlin
@Serializable
data class AgentLaunchConfig(
    val name: String? = null,
    val agentCommand: String = "claude",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val useHeadroom: Boolean = false,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: executable().replaceFirstChar { it.uppercase() }

    fun executable(): String = agentCommand.trim().substringBefore(' ').substringAfterLast('/')

    fun resolvedCommand(): String = if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
```

Una `data class` plana con valores por defecto, para que se serialice con `kotlinx.serialization` sin un `SerializersModule` polimórfico y los proyectos guardados antes de que existiera un campo se sigan cargando. `agentCommand` puede llevar argumentos (`claude --model opus`); `executable()` es su primera palabra sin carpetas. Dentro de su proyecto, un agente se identifica por `displayName`: su `name` o, si no tiene, su ejecutable con la inicial en mayúscula (`codex` → «Codex»).

Headroom (`headroom wrap`) está desactivado salvo que el agente lo active. `useHeadroom` se escribe siempre, igual que `ProjectConfig.agentLaunchConfig`: las versiones anteriores, en las que Headroom venía activado por defecto, omitían el campo (o el agente por defecto entero) cuando estaba activado, así que `projects.json` lee un agente guardado sin `useHeadroom`, o un proyecto sin `agentLaunchConfig`, como envuelto con Headroom. El agente del multirepo en `~/.shogunai/multirepos.json` se escribe igual, siempre con su `useHeadroom`.

`WorktreeAgents` gestiona la elección por worktree: un mapa de ruta de worktree a nombre de agente, guardado en `settings.json` (nunca en el repositorio). `agentFor` devuelve el agente del worktree o el de por defecto; `choose` guarda un agente que no es el de por defecto y olvida la entrada cuando se elige el de por defecto; `prune` quita las entradas del proyecto cuyo worktree ya no existe o cuyo agente se renombró o eliminó.

### `BranchType`

Enum `FEATURE` / `FIX`, cada uno con un `prefix` (`"feature"` / `"fix"`) que se antepone al ID de tarea al nombrar la rama: `feature/TASK-123`.

### `Worktree` / `BranchOption`

- `Worktree` — un worktree activo tal como lo reporta `git worktree list`: `path`, `branch` (`null` si está en `detached`), `head`, `isMain`, `isBare`, `isLocked` (una línea `locked`, p. ej. un worktree que está usando una sesión de un agente) e `isPrunable` (una línea `prunable`: su carpeta ya no existe).
- `BranchOption` — una rama ofrecida al crear un worktree: `name`, `isCheckedOut` (ya en uso en el repositorio base o en otro worktree) e `isRemote` (una rama de `origin/`, nombrada con ese prefijo). `withoutLocalTwins()` quita las ramas remotas que tienen una rama local con el mismo nombre corto.

Un `Worktree` devuelto por la creación también lleva `skippedPostCreateCommands` y `skippedVariables`, los comandos post-creación del repositorio que no se ejecutaron y los nombres de las variables declaradas que no se aplicaron porque el repositorio no está aprobado (ver [Confianza en el repositorio](#confianza-en-el-repositorio)).

### `WorktreeStatus` / `MainBranch` / `Divergence`

- `MainBranch` — la rama contra la que se comparan los worktrees: su `name` (`develop`) y la `ref` usada en los comandos de Git (`origin/develop`, o `develop` cuando no hay rama remota de seguimiento).
- `Divergence` — commits `ahead` (por delante) y `behind` (por detrás) respecto a otra rama.
- `WorktreeStatus` — el estado de un worktree: `uncommittedChanges`, las divergencias `upstream` y `mainBranch` (`null` cuando no aplican), `lastCommitAt`, `createdAt` (cuándo se creó el worktree, leído de la fecha de modificación del archivo `commondir` en la carpeta administrativa de Git para él, que se encuentra a través del archivo `.git` del worktree; `null` para el worktree principal o si no se puede leer) y `neverCommitted`. `base` es la divergencia con la base del worktree (su rama base registrada, o la rama principal si no se resuelve ninguna) y `baseName` el nombre de esa rama; ambos son `null` cuando no hay base o el worktree está en ella. `hasOwnCommits` es verdadero cuando la comparación con la base tiene `ahead > 0` (o, sin base, cuando la rama no está «sin empezar»). `isNotStarted` es `neverCommitted`: la rama nunca tuvo commits propios (ver `ReadWorktreeStatusUseCase`). `isMergedIntoBase` e `isMergedIntoMainBranch` se derivan de cada comparación: la rama no está «sin empezar», no tiene nada que le falte a esa rama (`ahead == 0`) y esa rama ha avanzado más allá de ella (`behind > 0`), así que ni una rama recién creada ni una cuya base avanzó después de crearla aparecen como mergeadas. `isMerged` es cualquiera de las dos.

### `RepositoryProvisioning`

Lo que el repositorio declara que necesita cada nuevo worktree, leído de ficheros en la raíz del repositorio base y versionado con él, así que lo comparte todo el que lo clona y no depende de la máquina:

| Campo | Origen |
|---|---|
| `copyPatterns` | La lista `copy` de `.shogunai.json`. |
| `linkPatterns` | La lista `link` de `.shogunai.json`: archivos que se enlazan con un symlink a la copia del repositorio base en vez de copiarse, así que todos los worktrees los comparten (se copian cuando el sistema de archivos no puede crear enlaces). |
| `postCreateCommands` | `hooks.postCreate` de `.shogunai.json`: comandos como listas argv, que se ejecutan en cada worktree nuevo o provisionado cuando el usuario los ha aprobado en esta máquina (ver [Confianza en el repositorio](#confianza-en-el-repositorio)). Una sola lista de strings es un comando. |
| `worktreeIncludePatterns` | Las líneas de `.worktreeinclude` (el fichero de Claude Code), sin líneas en blanco ni comentarios `#`. |
| `providers` | El objeto `providers` de `.shogunai.json`: los providers que activa el repositorio, cada uno con su id como clave y un objeto de opciones. `gradle` (`GradleProvider`, con `daemonMaxHeap`, `idleTimeoutMinutes` y `kotlinDaemonMaxHeap` opcionales) hace visibles los [daemons de Gradle](#daemons-de-gradle) del repositorio y, con ajustes, acota los daemons de las builds de los worktrees; `emulator` (`EmulatorProvider`, con un `avd` opcional que prefiere el repositorio) da a cada worktree su propio [emulador de Android](#emulador-de-android); `ios-simulator` (`IosSimulatorProvider`, con preferencias opcionales `device` y `runtime`) da a cada worktree su propio [simulador de iOS](#simulador-de-ios); `xcode` (`XcodeProvider`, con `compilationCache` y `cocoaPods` opcionales) muestra y limpia los [DerivedData de Xcode](#deriveddata-de-xcode), puede activar la caché de compilación y puede clonar los pods de CocoaPods del repositorio base en los worktrees nuevos. Los ids desconocidos se ignoran. |
| `environment` | El objeto `env` y el `envFile` de `.shogunai.json`, como un `EnvironmentDeclaration` (ver [Entorno del worktree](#entorno-del-worktree)). |

`allPatterns` es la unión de `copyPatterns` y `worktreeIncludePatterns` sin duplicados; un archivo que también coincide con un patrón de `link` se enlaza, no se copia. Los patrones usan la sintaxis de patrones de ignore de Git, relativos a la raíz del repositorio: `local.properties` coincide a cualquier profundidad, `/local.properties` solo en la raíz, `.idea/runConfigurations/` una carpeta entera, `.env*` un glob.

```json
{ "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"], "providers": { "gradle": {} } }
```

Los campos desconocidos de `.shogunai.json` se ignoran, para que versiones posteriores puedan añadir campos sin romper las anteriores.

Lo que el provisionado hizo con cada archivo es un `ProvisionedFile` (`path` y `ProvisionMode`: `COPIED`, `LINKED`, `COPIED_INSTEAD_OF_LINKING`). `ProvisionDrift` (`path` y `ProvisionDriftState`: `MISSING`, `DIFFERS`, `NOT_LINKED`, `DELETED_IN_BASE`) es un archivo provisionado que ya no está como lo dejó el provisionado, tal como lo informa `CheckProvisionedFilesUseCase`.

### Entorno del worktree

Variables que recibe cada worktree, declaradas en `.shogunai.json` (`WorktreeEnvironment.kt`):

```json
{
  "env": { "PORT": "${port}", "API_URL": "http://localhost:${port:api}", "APP_SUFFIX": "-${worktree}" },
  "envFile": ".env.shogunai"
}
```

- `EnvironmentDeclaration` — las `variables` en el orden del fichero y el `envFile` opcional, una ruta relativa a la raíz del worktree que no puede salir de él. `portKeys` lista los puertos que necesita la declaración.
- `EnvironmentVariable` — un nombre de variable válido (letras, dígitos y `_`, sin empezar por dígito y no reservado), el texto declarado de su plantilla (`source`) y esa plantilla, convertida al leer el fichero en `TemplatePart`s: `Literal`, `WorktreeName` (`${worktree}`, el nombre de la carpeta del worktree) y `Port(key)`. `${port}` usa como clave el nombre de la propia variable, así que cada variable tiene su puerto; `${port:<clave>}` usa la clave `port:<clave>`, compartida por todas las variables que la usan (`:` no puede aparecer en un nombre de variable, así que las claves nunca chocan). `$$` es un `$` literal; cualquier otro `${…}` lanza `InvalidEnvironmentTemplate`, que la lectura convierte en `InvalidRepositoryConfig`.
- `WorktreeEnvironment` — un entorno resuelto: las `variables` ya renderizadas, los `ports` reservados para el worktree, los nombres de las variables declaradas pendientes de aprobación (`awaitingApproval`) y si la declaración está aprobada (`declarationApproved`).

No se pueden declarar nombres de variable que cambian cómo se comportan las shells, los runtimes, el cargador dinámico, Git, los gestores de paquetes, los compiladores, las herramientas de build, los clientes de red o los agentes de programación (`isReservedVariableName`, que compara en mayúsculas con nombres exactos, prefijos como `XDG_`, `SSH_`, `NODE_` salvo `NODE_ENV`, `ANTHROPIC_` o `CLAUDE_`, y los sufijos `_PROXY`, `_OPTS` y `_HOME`; la lista completa está en [Configuración › Nombres de variable reservados](../guide/configuration.md#nombres-de-variable-reservados)). Un nombre así, en `env` o en los `portVariables` del provider `docker-compose`, hace inválido `.shogunai.json` (`RepositoryConfigIssue.ReservedVariableName` en el formulario de ajustes), esté o no aprobado el repositorio. Una plantilla o un `envFile` con un carácter Unicode de control (Cc) o de formato (Cf), o un separador de línea o párrafo (`hasHiddenCharacters`), también es inválido (`InvalidVariableValue`, `EnvFileInvalidCharacters`), así que cada pregunta que los muestra enseña exactamente lo que se aplica. Las variables que añaden los providers, como `GRADLE_OPTS`, no se declaran y no se comprueban; como sus nombres están reservados, `env` ya no puede sustituirlas.

### Emulador de Android

El provider de emulador (`Emulator.kt`) solo actúa en repositorios que declaran `"providers": { "emulator": {} }`; para cualquier otro repositorio nada busca el SDK, ejecuta `adb` ni añade variables.

- `EmulatorSettings` — los ajustes de máquina guardados en `ProjectConfig.emulator`.
- `chooseAvd(requested, settings, preferred, available)` — la AVD que arrancar: la pedida para este arranque; si no, el ajuste de la máquina; si no, la AVD preferida del repositorio si esta máquina la tiene; si no, la primera disponible; `null` cuando no hay ninguna.
- Un worktree reserva la clave de puerto de consola `emulator` entre los puertos pares 5554–5584 (el rango que explora `adb`), con el puerto siguiente (el puerto adb del emulador) también libre, cuando adquiere su emulador del [pool de emuladores](#pools-de-recursos), la primera vez que lo arranca y no cuando se resuelve su entorno. A partir de entonces recibe `ANDROID_SERIAL=emulator-<puerto>` (`emulatorSerial`); antes, su entorno no tiene `ANDROID_SERIAL`.
- `SdkLocation` (raíz del SDK, ejecutables `emulator` y `adb`), `EmulatorState` (`STOPPED`, `BOOTING`, `RUNNING`) y `WorktreeEmulator` (serial y estado) describen lo que informa el [puerto del SDK de Android](#androidsdk).

### Simulador de iOS

El provider de simulador de iOS (`IosSimulator.kt`) solo actúa en macOS y en repositorios que declaran `"providers": { "ios-simulator": {} }`.

- `SimulatorSettings` — los ajustes de máquina guardados en `ProjectConfig.simulator`.
- `SimulatorRuntime` / `SimulatorDeviceType` — lo que Xcode tiene instalado, tal como lo informa `simctl` (identificador, nombre visible, versión, plataforma y los tipos de dispositivo que admite cada runtime).
- `chooseSimulator(requested, settings, preferred, runtimes)` — el runtime por nombre visible (`iOS 18.5`, sin distinguir mayúsculas) a partir del primero de esta operación, el ajuste de máquina y la preferencia del repositorio que esté instalado; si no, el runtime de iOS más reciente por versión. Después, el tipo de dispositivo por nombre entre los iPhone e iPad del runtime en el mismo orden; si no, su iPhone más reciente: el número de modelo más alto del nombre y, entre esos, el nombre más corto (el modelo base, o el Pro si no hay base), sea cual sea el orden en que Apple los liste. `null` sin runtime de iOS.
- Un worktree guarda el recurso `ios-simulator`, el UDID de un simulador creado para él con el nombre `ShogunAi · <carpeta del repositorio> · <carpeta del worktree>` (`worktreeSimulatorName`), cuando lo adquiere del [pool de simuladores](#pools-de-recursos), la primera vez que lo arranca; entonces recibe `IOS_SIMULATOR_UDID=<UDID>` en su entorno. El prefijo `ShogunAi · ` es lo que distingue los simuladores huérfanos de los del usuario.

### Pools de recursos

Los emuladores, los simuladores de iOS y los daemons de Gradle son recursos de capacidad limitada que comparten los worktrees (`ResourcePool.kt`). No hay un registro de pools: un pool se calcula a partir de lo que ya existe.

- `ResourceKind` (`EMULATOR`, `IOS_SIMULATOR`, `GRADLE_DAEMON`), `CapacityProfile` (`AUTOMATIC`, `LIGHT`, `BALANCED`, `PER_WORKTREE`, `CUSTOM`), `ResourceLimits` (`emulators`, `simulators` y `gradleDaemons`, donde `null` significa sin límite) y `ResourceSettings` (el perfil y sus límites personalizados, guardados como `resources` en los ajustes de la app y nunca en el repositorio).
- `PoolHolder` es un recurso en uso: su tipo, la ruta del worktree, el directorio de administración de Git, el estado, si está inactivo, cuándo se usó por última vez, cuándo caduca su concesión (`leaseExpiresAt`), el estado del agente de su worktree (`agentState`, `null` sin sesión viva) y su `releaseRank`. `PoolUsage` es un pool: su `limit`, su `effectiveLimit` (el límite de emuladores nunca supera los 16 pares de puertos de consola) y sus holders. `PoolRelease` es lo que liberó una adquisición o un recorte.
- Un worktree tiene un emulador mientras tiene reservado el puerto `emulator`, un simulador mientras tiene registrado un UDID `ios-simulator`, y un daemon de Gradle pertenece al worktree de su última build. Los daemons de Kotlin no cuentan en ningún pool.
- La tabla de perfiles: Automático da `clamp(min((GB − 8) / 8, núcleos / 4), 1, 4)` emuladores y simuladores y `clamp(GB / 8, 2, 8)` daemons de Gradle (Ligero cuando no se puede leer la memoria); Ligero es 1, 1 y 2; Equilibrado es 2, 2 y 4; Uno por worktree no tiene límite; Personalizado es un número positivo o sin límite por tipo.
- El `shogunai-env.json` de cada worktree guarda un objeto `lastUsed` (clave a milisegundos de época) junto a sus puertos y recursos. Un recurso sin registro es el usado menos recientemente.
- El mismo archivo guarda `leases` (clave a los milisegundos de época de la última renovación) y `releases` (clave a un `ReleaseRecord`: cuándo se liberó, el worktree para el que se liberó y el puerto o UDID anterior). Una concesión caduca `ResourceSettings.leaseDuration` (`leaseMinutes`, 20 por defecto, un ajuste de la máquina) después de su última renovación; una concesión ausente cuenta como caducada. El registro de liberación se borra cuando el worktree vuelve a adquirir ese tipo, y los registros recientes de otros worktrees ponen en cuarentena sus puertos.
- `releaseRank` decide quién se puede liberar y en qué orden: `null` para un worktree cuyo agente trabaja, con una concesión vigente o con un dispositivo arrancando; 1 para un dispositivo detenido sin sesión de agente; 2 para uno detenido cuyo agente ha terminado o está inactivo; 3 para uno en marcha; 4 cuando el agente está bloqueado. El menos recientemente usado desempata.

### Turno del escritorio

El escritorio es un único recurso de toda la máquina que los agentes se turnan para manejar con computer use (`DesktopTurn.kt`). No es un `ResourceKind`: no tiene perfil, límite ni orden de liberación.

- `DesktopTurn` es quien lo tiene: la carpeta administrativa de Git y la ruta de su worktree, `acquiredAt` y `expiresAt` (milisegundos epoch). Un turno cuyo `expiresAt` ya pasó cuenta como libre. `DesktopTurnState` es quien lo tiene (o `null`) y los `WaitTicket` vivos de la cola; `DesktopTurnAcquisition` añade si se renovó (`renewed`) y `waitedSeconds`; `DesktopTurnRelease` es `Released` o `NobodyHolds`.
- `DesktopTurnLedger` agrupa el `DesktopTurnStore`, el bloqueo de la máquina, la cola de espera y la comprobación de procesos que comparten los tres casos de uso. `AcquireDesktopTurnUseCase` da el turno durante `ResourceSettings.desktopTurnDuration` (`desktopTurnMinutes`, 10 por defecto) cuando está libre o caducado y nadie va por delante en la cola `desktop`, lo renueva a quien lo tiene y, si no, falla con `DesktopTurnTaken` o, con un `DesktopWait`, espera en la cola con el mismo bucle `WaitInLine` que las adquisiciones de dispositivos (`DesktopTurnWaitTimedOut` al llegar al plazo). Solo volver a pedirlo renueva un turno: que el agente esté trabajando no.
- `ReleaseDesktopTurnUseCase` libera el turno de quien lo tiene (`DesktopTurnHeldByOther` para otro worktree), `ifHeldBy` un worktree para los hooks `Stop` y `SessionEnd`, `force` para la app de escritorio, y `onWorktreeRemoved`, al que llama la eliminación del worktree antes de borrar la carpeta y que también quita los tickets de ese worktree. `ReadDesktopTurnUseCase` devuelve el `DesktopTurnState`.

### Herdr

`Herdr.kt` contiene lo que informa el [puerto de Herdr](#herdrclient): `HerdrWorktreeOpening` (id del workspace, id de su pane raíz y si Herdr ya tenía abierto el worktree), `HerdrAgent` (un pane de agente: el `name` del agente, su `AgentState` o `null` cuando Herdr informa `unknown`, el directorio en el que trabaja y el id de su pane), `HerdrPane` (un pane: su id, workspace, directorio, el `agent` que Herdr detecta en él y su `agentStatus` tal cual, `unknown` cuando no hay ninguno; `hasLiveAgent` es cualquier otro estado, `idle` y `done` incluidos), además de `HerdrOpening`, el resultado de abrir un worktree en Herdr: su `HerdrOutcome` (`OPENED`, `STARTED_AGENT`, `FOCUSED`), el id del pane del agente y el nombre del agente. `AGENT_START_SECONDS` (10) es lo que una apertura espera a que Herdr detecte un agente escrito.

### Orca

`Orca.kt` contiene lo que informa el [puerto de Orca](#orcaclient): `OrcaStatus` (si el runtime de Orca responde y su versión), `OrcaRepo` (un repositorio registrado: id, ruta y su visibilidad de worktrees ajenos), `OrcaWorktree` (la ruta de un espacio de trabajo, si lo creó Orca, sus `OrcaAgent`, cada uno un nombre de agente y su `AgentState`: `working`, `waiting` y `done` de Orca pasan a `WORKING`, `BLOCKED` y `DONE`, y, para un espacio de trabajo de carpeta, su `folderId`, además de su `comment`, su `workspaceStatus` y los números de la pull request y la issue enlazadas) y `OrcaTerminal` (el identificador, la ruta del worktree, vacía en un espacio de trabajo de carpeta, y el título de una terminal), `OrcaWorkspace`, el destino de las operaciones de terminal: `Worktree(path)`, seleccionado como `path:<ruta>`, o `Folder(id)`, un espacio de trabajo de carpeta que solo la app de Orca puede crear y que ninguna ruta selecciona, como `id:folder:<id>` (`OrcaWorktree.workspace` da el destino de una fila), `OrcaWorktreeUpdate`, los campos que fijar con `orca worktree set` (solo se envían los no nulos), además de `OrcaOpening`, el resultado de abrir un worktree en Orca: su `OrcaOutcome` (`STARTED_AGENT`, `FOCUSED`), el identificador de la terminal, el agente y el nombre de sesión confirmado. `ORCA_START_SECONDS` (30) es lo que una apertura espera a que Orca arranque y `ORCA_MINIMUM_VERSION` (1.4.243) el Orca más antiguo que ShogunAi acepta.

### Daemons de Gradle

Lo que el provider de Gradle observa en la máquina (`GradleDaemon.kt`):

- `DaemonProcess` — un daemon en ejecución: `pid`, `kind` (`DaemonKind.GRADLE` o `KOTLIN`), `gradleVersion` (solo daemons de Gradle), `residentMemoryBytes`, `lastBuildDirectory` (el directorio de la última build que recibió un daemon de Gradle) y `state`.
- `DaemonState` — `Busy`, `Idle(since)`, `Unknown` (un daemon de Gradle cuyo estado no se puede leer) o `Untracked` (daemons de Kotlin, que no lo registran).
- `GradleDaemon` — un `DaemonProcess` con su `owner` en un repositorio: `DaemonOwner.Worktree(path)`, `RemovedWorktree` (su último directorio de build ya no existe) u `OtherProject`; `null` para daemons de Kotlin, daemons sin directorio de build conocido o fuera de un repositorio.
- `DaemonStopReport` — el resultado de una parada: `stopped`, `kept` (cada uno con un `DaemonKeptReason`: `BUSY`, `STATE_UNKNOWN` o `BUILD_RUNNING`), `notDaemons` (pids pedidos que no son daemons) y `failures`.

### DerivedData de Xcode

Lo que el provider de Xcode encuentra en la ubicación de DerivedData de Xcode (`DerivedData.kt`), solo en macOS y en repositorios que declaran `"providers": { "xcode": {} }`:

- `DerivedDataFolder` — la carpeta de un proyecto: `path`, `workspacePath` (el `.xcodeproj`, `.xcworkspace` o paquete que registra su `info.plist`), `sizeBytes` (`null` hasta que se mide), `lastAccessed` y `packagesBytes`, la parte del tamaño que son paquetes Swift (`SourcePackages`).
- `DerivedDataOwner` — `Worktree(path)` (el worktree del repositorio que contiene el workspace, con los worktrees anidados resueltos), `OtherProject` (el workspace existe en otro sitio), `Orphaned` (el workspace ya no existe) o `UnavailableVolume` (el workspace está en un volumen no montado, así que no se distingue de un proyecto vivo).
- `DerivedDataListing` — cada carpeta con su dueño (`AttributedDerivedData`), el tamaño de la caché de compilación compartida cuando se pide y `packageCaches` (`PackageCacheSizes`: las cachés compartidas de SwiftPM y CocoaPods, del puerto `PackageCacheSource`); `orphans` y `ofWorktrees()` la filtran.
- `DerivedDataDeletion` — el resultado de borrar huérfanas: `deleted`, `failures` (`DerivedDataDeletionFailure`: ruta y detalle) y `bytesFreed`.

### `WorktreeOrigin` / `RunningAgent`

Lo que la vista general de worktrees muestra de cada worktree (`WorktreeOverview.kt`): `WorktreeOrigin` (`SHOGUNAI`, `CLAUDE_CODE`, `HERDR`, `ORCA`, `OTHER`), la herramienta que lo creó según su ubicación (`classifyOrigin`, ver [Vista completa de worktrees](#vista-completa-de-worktrees)), y `RunningAgent`, un CLI de agente que se ejecuta en él: su `name` y, cuando Herdr lo informa, su `state` (`AgentState`: `WORKING`, `BLOCKED`, `DONE`, `IDLE`), el `stateSource` que lo informó (`AgentStateSource`: `HERDR`, `ORCA`, `CLAUDE_CODE`) y, cuando lo informa Herdr, su `herdrPaneId`. `AgentSessionRecord` (`AgentSession.kt`) es lo que dejan los hooks de Claude Code por sesión: `sessionId`, `agent`, `state`, `updatedAt`, `transcriptPath` y `pid`. `waitsForUser` es verdadero para un agente bloqueado o terminado.

### Herramientas

`Tools.kt` describe en qué puede abrir un worktree la app de escritorio: `HostOs` (`MACOS`, `WINDOWS`, `LINUX`), `TerminalId` (`TERMINAL`, `ITERM2`, `WINDOWS_TERMINAL`, que usa PowerShell, `TMUX`, `ZELLIJ`), `EditorId` (`VS_CODE`, `INTELLIJ_IDEA`, `ANDROID_STUDIO`, `XCODE`, este último solo en macOS), `DetectedTools` (los terminales e IDEs encontrados en la máquina) y `TerminalLaunch` (la ruta del worktree, sus variables, el comando del agente y la línea de lanzamiento completa que ejecuta un terminal).

`ShellQuoting` entrecomilla valores para la shell en la que acaban: `posix` (comillas simples, `'\''` para una comilla interior), `posixCommand` (una lista argv como línea, entrecomillando solo las palabras que lo necesitan), `powershell` (comillas simples, duplicando cualquier tipo de comilla simple) y `windows` (comillas dobles).

### Actualizaciones

`Update.kt` modela la comprobación de actualizaciones: `AppVersion` (`X.Y.Z`, comparada numéricamente; `parse` acepta una `v` opcional y rechaza todo lo demás; `DEVELOPMENT` es `0.0.0`), `HostArchitecture` (`ARM64`, `X64`), `PublishedRelease` (versión, página de la release, `ReleaseAsset`s y `ReleaseNotes`), `InstallChannel` (`HOMEBREW`, `DMG`, `SCOOP`, `WINGET`, `MSI`, `DEB`, `UNKNOWN`) y `UpdateAction`, lo que debe hacer el usuario según el canal: ejecutar un comando (`brew upgrade --cask shogunai`, `scoop update shogunai`, `winget upgrade LuxionServer.ShogunAi`), descargar un asset elegido de la release por extensión y arquitectura (con `sudo apt install ./<asset>` para un `.deb`) o abrir la página de la release. `UpdateCheck` es el resultado: `NotDue`, `UpToDate` o `Available` con la release, el canal, la acción y `notes`, las `VersionNotes` (versión, página de la release, notas) de cada versión publicada más nueva que la que se ejecuta, de la más nueva a la más antigua. `UpdatePreferences` guarda `checkAutomatically` y `skippedVersion`.

`ReleaseNotes.kt` guarda las notas de una release por idioma (`english`, `spanish`, cualquiera puede ser `null`). `parse(body)` lee el cuerpo de una release de GitHub: con los marcadores `shogunai:notes:en` / `shogunai:notes:es` que escribe el workflow (ver [Proceso de release](../process/release.md#formato-del-cuerpo-de-la-release)) toma cada idioma entre sus marcadores; sin ellos, en las releases anteriores a ese formato, toma como inglés las notas que genera GitHub, quitando el sufijo `by @user in <enlace>` de cada cambio, el encabezado «What's Changed», la sección «New Contributors» y la línea «Full Changelog»; un resultado vacío es `NONE`. `forLanguage(language)` devuelve las notas de ese idioma, o las inglesas si no las hay.

`RecentWorktree` (`projectId`, `path`) es una entrada de los worktrees recientes que lista el icono de la barra de menús.

### `ThemeMode` / `AppLanguage`

Preferencias del usuario: `LIGHT` / `DARK` / `SYSTEM` y `ENGLISH` / `SPANISH`. Se guardan en `settings.json` (ver [Capa de infraestructura](infrastructure.md)).

### `WorktreeError`

`sealed class` con los errores esperables del flujo, para que la UI decida qué mostrar (y lo traduzca) a partir de datos estructurados en vez de parsear strings:

- `BaseRepositoryNotFound` — el repo base configurado no existe en disco.
- `WorktreeAlreadyExists` — ya hay un directorio en la ruta destino.
- `InvalidTaskId` — el id de tarea no es un segmento de ref de Git válido una vez normalizado.
- `SecretFileNotFound` — faltan archivos locales en el repo base.
- `RepositoryNotFound` — el directorio no está dentro de un repositorio Git con árbol de trabajo (lo usa el CLI).
- `BareRepositoryNotSupported` — el directorio es un repositorio bare o está dentro de uno.
- `RepositoryHasNoCommits` — el repositorio no tiene ningún commit desde el que crear una rama.
- `LocalFileUnreadable` — `projects.json` o `settings.json` no se pueden parsear; guarda la ruta y el motivo. El fichero se aparta antes de volver a escribirlo (ver [Capa de infraestructura](infrastructure.md)).
- `UpdateCheckFailed` — no se pudieron leer de GitHub las releases publicadas, o no había ninguna.
- `NotAWorktree` — la ruta a provisionar es el worktree principal o no es uno de los worktrees del repositorio.
- `InvalidRepositoryConfig` — `.shogunai.json` existe pero no es JSON válido, no es un objeto, su `copy` o su `link` no son una lista de strings, su `hooks.postCreate` no es un comando ni una lista de comandos, su `providers` (o un provider conocido dentro de él) no es un objeto, una opción de un provider tiene el tipo equivocado, o su `env` o `envFile` no son válidos (incluido un nombre de variable reservado); guarda la ruta del fichero y el detalle.
- `RepositoryConfigChanged` — `.shogunai.json` cambió en disco desde que lo leyó el formulario de ajustes, así que no se sobrescribe.
- `NoFreePort` — todos los puertos entre 20000 y 29999 están reservados u ocupados.
- `EnvironmentFileWriteFailed` — no se pudo escribir el `envFile` declarado; guarda la ruta y la causa.
- `StartPointNotFound` — el punto de partida que se pasa a `shogun new --from` no identifica un commit.
- `ProcessListingFailed` — `lsof` o `ps` fallaron al detectar los agentes en ejecución (p. ej. en Windows).
- `NoEmulatorPort` — todos los puertos de consola de emulador entre 5554 y 5584 están reservados u ocupados.
- `DesktopTurnTaken` — otro worktree tiene un turno del escritorio válido, u otros lo esperan antes; guarda quién lo tiene (`null` si está libre) y cuántos esperan.
- `DesktopTurnWaitTimedOut` — el escritorio no se liberó dentro del plazo de la espera; guarda cuánto esperó y quién lo tiene.
- `DesktopTurnHeldByOther` — un worktree intentó liberar un turno que tiene otro.
- `ResourcePoolFull` — un pool está en su límite y ninguno de sus recursos se puede liberar; guarda el tipo, el límite y cada holder con su worktree, estado y, mientras está protegido, la caducidad de su concesión o que su agente está trabajando.
- `AndroidSdkNotFound` — no se encontró el Android SDK; guarda los sitios revisados.
- `NoAvdAvailable` — la máquina no tiene ningún Android Virtual Device.
- `EmulatorBootTimeout` — el emulador no terminó de arrancar a tiempo; se deja arrancando.
- `EmulatorExitedEarly` — el proceso del emulador terminó mientras arrancaba; guarda las últimas líneas de su log.
- `AndroidToolFailed` — `emulator` o `adb` fallaron; guarda el comando, el código de salida y la salida.
- `EmulatorProviderNotDeclared` — se ejecutó un comando de emulador en un repositorio que no declara el provider de emulador.
- `NoIosRuntime` — Xcode no tiene instalado ningún runtime de simulador de iOS.
- `SimulatorBootTimeout` — el simulador no terminó de arrancar a tiempo; se deja arrancando.
- `IosSimulatorProviderNotDeclared` — se ejecutó un comando de simulador en un repositorio que no declara el provider de simulador de iOS.
- `IosSimulatorsNotSupported` — el sistema no es macOS con Xcode.
- `AppleToolFailed` — `xcrun simctl` u otra herramienta de Xcode falló; guarda el comando, el código de salida y la salida.
- `EnvironmentFileTracked` — el `envFile` declarado es un fichero que Git versiona en el worktree, así que no se sobrescribe.
- `EnvironmentFileOutsideWorktree` — el `envFile` declarado queda fuera del worktree tras seguir los symlinks.
- `InvalidRef` — un nombre de rama o punto de partida empieza por `-`, así que Git podría leerlo como una opción.
- `GitCommandFailed` — un comando de Git devolvió un código de salida distinto de cero; guarda el comando, el código de salida y la salida de error de Git.
- `SecretCopyFailed` — la copia o el enlace de archivos locales o del repositorio falló después de haber creado ya el worktree, incluido un archivo que acabaría fuera del worktree y un fallo al escribir el registro del provisionado.
- `LinkedFolderOccupied` — una carpeta local en modo enlazar no se puede enlazar porque el worktree ya tiene ahí una carpeta real con archivos que ShogunAi no copió; no se borra nada de ella.
- `PostCreateCommandFailed` — un comando post-creación no pudo arrancar o terminó con error; guarda el worktree, el comando, su código de salida y su salida de error. El worktree se conserva.
- `ClipboardWriteFailed` — no se pudo copiar el comando de lanzamiento al portapapeles.
- `GitLfsNotFound` — el repositorio usa Git LFS pero `git-lfs` no está instalado.
- `BranchNotFound` — la rama existente pedida no existe en local.
- `BranchAlreadyCheckedOut` — la rama ya está en uso en otro worktree o en el repositorio base.
- `HerdrNotInstalled` — no se encuentra el comando `herdr`.
- `HerdrCommandFailed` — falló un comando de `herdr`; guarda el comando y el mensaje de Herdr (por ejemplo, que no hay servidor de Herdr en marcha).
- `OrcaNotInstalled`, `OrcaNotStarted` (Orca no aceptaba comandos 30 segundos después de `orca open`), `OrcaCommandFailed` (guarda el comando y el `error.message` de Orca, o nombra la versión mínima para un Orca más antiguo) y `OrcaAgentNotStarted` (identificador de la terminal y comando) — falló abrir un worktree en Orca. `OrcaMultirepoFolderNotOpen` (la carpeta) — Orca no tiene espacio de trabajo para una carpeta de multirepo que no es un repositorio Git; su mensaje da las dos formas de añadirla en la app de Orca.
- `TerminalLaunchFailed`, `TerminalAutomationRefused` (macOS no dejó que ShogunAi controlara Terminal o iTerm2) y `TerminalUnsupported` (no disponible en este sistema, o tmux anterior a 3.0) — falló abrir un worktree en un terminal.
- `EditorLaunchFailed` y `FolderOpenFailed` — falló abrir un worktree en un IDE o en el gestor de archivos; guardan el mensaje de la herramienta.
- `WorktreeTargetNotFound`, `AmbiguousWorktreeTarget` y `CannotRemoveMainWorktree` — `shogun remove` recibió un id de tarea, ruta o rama que no corresponde a ningún worktree, que corresponde a varios, o que es el principal.
- `AgentRunningInWorktree` — hay un agente en ejecución en el worktree a eliminar; guarda los nombres de los agentes.

## Puertos

Los casos de uso dependen de interfaces, no de implementaciones. Esto es lo que permite testearlos con dobles.

### `ShellCommandExecutor`

```kotlin
interface ShellCommandExecutor {
    suspend fun execute(command: List<String>, workingDirectory: String? = null): CommandResult
    suspend fun executeWithin(command: List<String>, timeout: Duration, workingDirectory: String? = null): CommandResult
}
```

El comando se pasa como **argv** (lista de strings), nunca como una cadena para que la interprete un shell. Motivo: evitar diferencias entre el shell por defecto de macOS (zsh) y el de Linux (bash), y evitar problemas de escapado/inyección de comandos.

`CommandResult` guarda `command`, `exitCode`, `stdout` y `stderr` sin interpretar su significado — cada caso de uso decide qué cuenta como éxito con `isSuccess` (`exitCode == 0`). Un proceso que ni siquiera puede arrancar (p. ej. el ejecutable no está en el `PATH`) lanza `CommandExecutionException`.

`executeWithin` acota la ejecución de un comando: si no termina a tiempo se detiene y lanza `CommandExecutionException.TimedOut`. Los comandos de solo lectura pasan un límite de `CommandTimeouts` (`GIT_READ`, 30 s, para las lecturas de Git; `LISTING`, 10 s, para los listados de dispositivos, procesos y herramientas; `NETWORK`, 60 s, para `git fetch`); los comandos que pueden tardar con motivo (crear un worktree, los comandos post-creación, las builds) usan `execute`, sin límite. Su implementación por defecto ignora el límite, así que los dobles de test no tienen que implementarlo. Una corrutina cancelada siempre detiene el comando, y ningún comando espera nunca entrada.

### `FileManager`

```kotlin
interface FileManager {
    fun exists(path: String): Boolean
    fun isInside(root: String, path: String): Boolean
    fun copy(source: String, destination: String)
    fun createSymbolicLink(target: String, link: String)
    fun isSymbolicLinkTo(link: String, target: String): Boolean
    fun hasSameContent(first: String, second: String): Boolean
    fun directorySize(path: String, excluding: Set<String> = emptySet()): Long
    fun isDirectory(path: String): Boolean
    fun listTree(directory: String): DirectoryListing
    fun isSymbolicLink(path: String): Boolean
    fun deleteFile(path: String)
    fun modificationStamps(path: String): List<Pair<String, Long>>
    fun readProperties(path: String): Map<String, String>
    fun writeText(path: String, text: String)
    fun readText(path: String): String?
    fun lastModifiedMillis(path: String): Long?
}
```

Abstrae el sistema de archivos para que la copia de secretos y la medición de worktrees sean testeables sin tocar disco. `isInside` indica si `path`, una vez resuelto a través de symlinks su ancestro existente más cercano, está dentro de `root`; el provisionado lo comprueba antes de cada copia, enlace y escritura del fichero de entorno, para que un symlink hostil en una rama no haga escribir a ShogunAi fuera del worktree. `copy` crea las carpetas padre que falten en el destino. `createSymbolicLink` sustituye lo que haya en `link` por un symlink, o lanza `SymbolicLinksNotSupported` donde no se pueden crear enlaces (p. ej. Windows sin el privilegio); `isSymbolicLinkTo` y `hasSameContent` son lo que compara la comprobación del provisionado. `listTree` devuelve un `DirectoryListing` de una carpeta: sus archivos regulares (`files`) y los enlaces simbólicos que omite (`skippedLinks`), ambos como rutas relativas separadas por `/`, con los ocultos incluidos, sin seguir enlaces, y nada si la carpeta no existe. `isSymbolicLink` distingue un enlace sin seguirlo, y `deleteFile` borra un archivo o enlace, sin hacer nada si no existe. `directorySize` suma los archivos regulares bajo `path`, saltándose las carpetas de `excluding`. `modificationStamps` devuelve cada archivo y carpeta bajo `path` (o el propio `path` si es un archivo) con su fecha de modificación, y nada si no existe. `readProperties` lee un fichero de propiedades de Java (como `gradle.properties`), vacío si no existe o no se puede leer. `writeText` sustituye un fichero de forma atómica, creando sus carpetas; `lastModifiedMillis` devuelve la fecha de última modificación de un archivo, o `null` si falta o no se puede leer. `readText` devuelve su contenido, o `null` si no existe o no se puede leer.

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
    fun fingerprint(repositoryPath: String): String
    fun addPatterns(
        repositoryPath: String,
        copyPatterns: List<String>,
        linkPatterns: List<String> = emptyList(),
        expectedFingerprint: String? = null,
    )
    fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String
    fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String)
}
```

Lee el provisionado del repositorio (lanzando `InvalidRepositoryConfig` si `.shogunai.json` no se puede leer), añade patrones a `copy` y `link` en una sola escritura (así mueve la pantalla de configuración los archivos locales al repositorio y enlaza los secretos móviles que sugiere), y previsualiza y escribe los providers, el entorno y los patrones pendientes editados en el formulario de ajustes (`RepositoryConfigEdit`, cuyos `addCopyPatterns` y `addLinkPatterns` se añaden sin duplicados).

### `ProvisioningRecordStore`

```kotlin
interface ProvisioningRecordStore {
    fun read(worktreePath: String): Map<String, List<String>>
    fun write(worktreePath: String, localFolders: Map<String, List<String>>)
}
```

Recuerda, por worktree, los archivos que el provisionado copió de cada carpeta local (entrada de carpeta → archivos relativos a ella). `NioProvisioningRecordStore` lo guarda en `shogunai-provisioning.json` dentro de la carpeta de administración de Git del worktree (que encuentra a través de su fichero `.git`), así que nunca está en el árbol de trabajo y desaparece con el worktree. Un registro que falta o no se puede leer se lee vacío; un registro vacío borra el fichero. `ProvisioningRecordStore.NONE` no guarda nada, para quien no lo necesita. `fingerprint` es un hash del contenido del fichero; una escritura que recibe la huella leída antes falla con `RepositoryConfigChanged` si el fichero cambió entretanto.

### `EnvironmentStateStore` / `PortProbe`

```kotlin
interface EnvironmentStateStore {
    fun readPorts(adminDirectory: String): Map<String, Int>
    fun writePorts(adminDirectory: String, ports: Map<String, Int>)
    fun readResources(adminDirectory: String): Map<String, String>
    fun writeResources(adminDirectory: String, resources: Map<String, String>)
    fun readLastUsed(adminDirectory: String): Map<String, Long>
    fun writeLastUsed(adminDirectory: String, lastUsed: Map<String, Long>)
    fun readLeases(adminDirectory: String): Map<String, Long>
    fun writeLeases(adminDirectory: String, leases: Map<String, Long>)
    fun readReleases(adminDirectory: String): Map<String, ReleaseRecord>
    fun writeReleases(adminDirectory: String, releases: Map<String, ReleaseRecord>)
    fun reservationsOnMachine(commonDirectory: String): List<WorktreeReservations>
    fun release(adminDirectory: String, key: String)
    fun reservedPortsOnMachine(commonDirectory: String): Set<Int>
    fun recordedResourcesOnMachine(commonDirectory: String, key: String): Set<String>
    fun <T> withLock(block: () -> T): T
}

interface PortProbe {
    fun isFree(port: Int): Boolean
}
```

`EnvironmentStateStore` guarda los puertos reservados y los recursos de los providers (como el UDID de un simulador) de cada worktree en el directorio de administración que Git tiene para ese worktree (`.git/worktrees/<id>/` para uno enlazado, `.git/` para el principal), lista todas las reservas de la máquina (los worktrees de este repositorio más los de cualquier repositorio para el que ShogunAi haya guardado reservas) y ejecuta un bloque con el lock de reservas de la máquina. `PortProbe` indica si un puerto está libre en la máquina.

### `AndroidSdk`

```kotlin
interface AndroidSdk {
    fun locate(repositoryPath: String): SdkLocation
    suspend fun listAvds(sdk: SdkLocation): List<String>
    suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState>
    fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean): EmulatorProcess
    suspend fun stopEmulator(sdk: SdkLocation, serial: String)
}
```

Todo lo que el provider de emulador necesita del SDK de Android, usado solo en repositorios que declaran el provider: encontrar el SDK (`AndroidSdkNotFound` con los sitios revisados), listar las AVDs, el estado de todos los emuladores en marcha en una sola llamada, arrancar un emulador desacoplado en un puerto de consola y pararlo. El `EmulatorProcess` arrancado indica si el proceso sigue vivo y da las últimas líneas de su log, para que una espera de arranque pueda fallar en cuanto el emulador termina. Los errores de las herramientas son `AndroidToolFailed`.

### `AppleSimulators` / `KmpTestWiring`

```kotlin
interface AppleSimulators {
    fun isSupported(): Boolean
    suspend fun runtimes(): List<SimulatorRuntime>
    suspend fun devices(): List<SimulatorDevice>
    suspend fun create(name: String, deviceTypeIdentifier: String, runtimeIdentifier: String): String
    suspend fun boot(udid: String)
    suspend fun show(udid: String)
    suspend fun shutdown(udid: String)
    suspend fun delete(udid: String)
}

interface KmpTestWiring {
    fun isWiredInRepository(repositoryPath: String): Boolean
    fun usesKotlinMultiplatform(repositoryPath: String): Boolean
    fun isInitScriptInstalled(): Boolean
    fun initScriptPath(): String
    fun installInitScript()
    fun uninstallInitScript()
}
```

`AppleSimulators` es todo lo que el provider de simulador de iOS necesita de los simuladores de Xcode, usado solo en macOS y en repositorios que declaran el provider: los runtimes instalados (con los tipos de dispositivo que admite cada uno), los dispositivos existentes con su estado, y crear, arrancar, mostrar en el visor de simuladores de Xcode (Simulator o Device Hub), apagar y borrar uno. Los errores de las herramientas son `AppleToolFailed`. `KmpTestWiring` indica si los scripts de Gradle del repositorio aplican el plugin de Kotlin Multiplatform, si pasan `IOS_SIMULATOR_UDID` a los tests de simulador de KMP, e instala o quita el init script de Gradle para toda la máquina que lo hace en todos los proyectos.

### `HerdrConfigSource`

```kotlin
interface HerdrConfigSource {
    fun worktreesRoot(): String
}
```

Dónde crea [Herdr](https://herdr.dev) sus worktrees, para distinguirlos de otras ubicaciones. Nunca falla: si no hay una configuración legible devuelve la carpeta por defecto de Herdr.

### `HerdrClient`

```kotlin
interface HerdrClient {
    suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening
    suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String
    suspend fun closePane(paneId: String)
    suspend fun runInPane(paneId: String, command: String)
    suspend fun openWorktreeWorkspaces(repository: String): Map<String, String>
    suspend fun closeWorkspace(workspaceId: String)
    suspend fun listAgents(): List<HerdrAgent>
    suspend fun focusAgent(paneId: String)
}
```

Las pocas operaciones de [Herdr](https://herdr.dev) que ShogunAi usa para abrir ahí un worktree y para cerrarlo después: `openWorktreeWorkspaces` devuelve, de los worktrees de un repositorio que Herdr tiene abiertos, su ruta y el id de su workspace; `closeWorkspace` cierra un workspace y los procesos de sus panes. El resto: abrir un worktree existente como workspace enfocado agrupado bajo su repositorio (o enfocarlo si ya está abierto), dividir un pane en otro enfocado cuya shell arranca en un directorio con variables extra, cerrar un pane y enviar un comando a un pane. `listAgents` devuelve todos los panes de agente que sigue Herdr con su estado, `listPanes` devuelve los panes de todos los workspaces, o de uno, con su directorio y el estado de su agente, y `focusAgent` pone delante el pane de un agente. Los ids de pane no se guardan, porque Herdr los renumera cuando se mueven los panes; los de `listAgents` solo se usan en el momento. Los errores son `HerdrNotInstalled` y `HerdrCommandFailed`; un agente que Herdr no detecta tras escribirlo es `HerdrAgentNotStarted` (pane y comando), lanzado por los casos de uso de apertura.

### `OrcaClient`

```kotlin
interface OrcaClient {
    fun isInstalled(): Boolean
    suspend fun status(): OrcaStatus
    suspend fun start()
    suspend fun bringToFront()
    suspend fun repos(): List<OrcaRepo>
    suspend fun addRepo(path: String): OrcaRepo
    suspend fun showExternalWorktrees(repoId: String)
    suspend fun worktrees(repoId: String): List<OrcaWorktree>
    suspend fun agentsByWorktree(): List<OrcaWorktree>
    suspend fun updateWorktree(path: String, update: OrcaWorktreeUpdate)
    suspend fun terminals(workspace: OrcaWorkspace): List<OrcaTerminal>
    suspend fun terminalAgent(handle: String): String?
    suspend fun createTerminal(workspace: OrcaWorkspace, title: String, command: String, focus: Boolean): OrcaTerminal
    suspend fun switchTo(handle: String)
    suspend fun closeTerminals(workspace: OrcaWorkspace)
    suspend fun attachEmulator(workspace: OrcaWorkspace, serial: String)
}
```

Las operaciones de [Orca](https://onorca.dev) que usa ShogunAi, con el vocabulario de Orca (un repositorio con worktrees y terminales, no los workspaces y panes de Herdr): si Orca está instalado y su runtime responde, arrancar la app de escritorio o traer su ventana al frente, registrar un repositorio y mostrar sus worktrees ajenos a Orca, listar los worktrees visibles de un repositorio, los agentes de todos los espacios de trabajo en una sola llamada (incluidos los de carpeta), las terminales de un espacio de trabajo y el agente que Orca identifica en una, fijar los metadatos de Orca de un worktree (comentario, estado del tablero, pull request e issue enlazadas), crear una terminal enfocada que ejecuta un comando, cambiar a una terminal, cerrar todas las terminales de un espacio de trabajo y mostrar un emulador en marcha en la vista de emuladores de un espacio de trabajo. Las operaciones de terminal reciben un `OrcaWorkspace`, así que las mismas llamadas sirven para un worktree y para el espacio de trabajo de carpeta de un multirepo. La apertura pasa por el puerto [`AgentRunner`](#agentrunner). Los errores son los casos `Orca*` de `WorktreeError`.

### `AgentRunner`

```kotlin
interface AgentRunner {
    val id: RunnerId
    fun isInstalled(): Boolean
    suspend fun openWorktree(config: ProjectConfig, worktree: Worktree, agent: AgentLaunchConfig = config.agentLaunchConfig): Result<RunnerOpening>
    suspend fun openGroup(group: WorktreeGroup): Result<List<GroupRunnerOpening>>
    suspend fun openMultirepoAgent(multirepo: Multirepo): Result<RunnerOpening>
}
```

Un solo puerto para abrir agentes en un runner, sea cual sea. `HerdrAgentRunner` y `OrcaAgentRunner` lo implementan sobre los casos de uso de apertura de cada runner, que siguen hablando el vocabulario de su cliente, y traducen su resultado a `RunnerOpening` (`runner`, `outcome` `OPENED`, `STARTED_AGENT` o `FOCUSED`, `handle`, el pane de Herdr o la terminal de Orca, `agent` y `sessionName`). El `open` de la CLI y las acciones del multirepo y "Nuevo cambio" de la app de escritorio pasan por él. Cerrar espacios de trabajo y terminales al eliminar y leer los estados de los agentes siguen siendo de cada runner, porque se hacen para todos los runners instalados, se elija el que se elija.

### Elección del runner

El runner elegido en las preferencias es `ToolPreferenceRepository.loadRunner()`/`saveRunner()` (`RunnerId?`, en `settings.json`, junto al terminal y el IDE). `runnerInUse(chosen, installed)` lo convierte en el runner en uso: el elegido mientras esté instalado; si no, el primero instalado de Herdr y Orca; si no, el elegido o Herdr.

### `OrcaWorkspaceStore`

Recuerda la carpeta donde Orca creó un worktree por última vez (`workspacesRoot`, `remember`), para reconocer los worktrees de Orca mientras Orca no está abierto, y el estado del tablero que ShogunAi escribió por última vez para cada worktree (`boardStatuses`, `rememberBoardStatuses`), para respetar un estado movido a mano en Orca. `NONE` no recuerda nada.

### `GitHubClient`

```kotlin
interface GitHubClient {
    suspend fun pullRequestFor(directory: String, branch: String): Int?
    suspend fun issueExists(directory: String, number: Int): Boolean
}
```

Las consultas a GitHub que necesita la sincronización con Orca: la pull request más reciente cuya rama de origen es una rama dada, y si existe una issue, ambas en el repositorio de un directorio. Una consulta que falta o falla es `null` o `false`, nunca un error. `NONE` no encuentra nada.

### `GradleDaemonSource`

```kotlin
interface GradleDaemonSource {
    suspend fun list(): List<DaemonProcess>
}
```

Lista los daemons de Gradle y de Kotlin que se ejecutan en la máquina con su estado, o falla con `ProcessListingFailed` cuando no se pueden listar los procesos.

### `DerivedDataSource`

```kotlin
interface DerivedDataSource {
    fun isSupported(): Boolean
    suspend fun folders(): List<DerivedDataFolder>
    suspend fun size(folder: DerivedDataFolder): Long

    suspend fun packagesSize(folder: DerivedDataFolder): Long
    suspend fun compilationCacheBytes(): Long?
    fun projectExists(workspacePath: String): Boolean
    fun isOnUnavailableVolume(workspacePath: String): Boolean
    suspend fun delete(folder: DerivedDataFolder)
}
```

Todo lo que el provider de Xcode necesita de los DerivedData de Xcode: si el sistema es macOS, las carpetas de proyecto de la ubicación actual sin su tamaño (nunca las cachés compartidas de Xcode, que no tienen `info.plist`), el tamaño de una carpeta medido bajo demanda y cancelable, el tamaño de la caché de compilación compartida (`null` si no existe), si un workspace sigue existiendo o está en un volumen no montado, y borrar una carpeta.

### `TerminalLauncher` / `EditorLauncher` / `FolderLauncher` / `ToolDetector`

```kotlin
interface TerminalLauncher {
    val terminal: TerminalId
    suspend fun open(launch: TerminalLaunch): Result<Unit>
}

interface EditorLauncher {
    val editor: EditorId
    suspend fun open(path: String): Result<Unit>
}

interface FolderLauncher {
    suspend fun open(path: String): Result<Unit>
}

interface ToolDetector {
    suspend fun detect(): DetectedTools
}
```

Un `TerminalLauncher` por terminal admitido abre una ventana o pestaña nueva en el worktree y ejecuta ahí la línea de lanzamiento; un `EditorLauncher` por IDE abre la carpeta del worktree; `FolderLauncher` la muestra en el gestor de archivos del sistema; `ToolDetector` encuentra qué terminales e IDEs tiene la máquina. Los fallos son los tipados `TerminalLaunchFailed`, `TerminalAutomationRefused`, `TerminalUnsupported`, `EditorLaunchFailed` y `FolderOpenFailed`.

### `ReleaseSource`

```kotlin
interface ReleaseSource {
    suspend fun published(): Result<List<PublishedRelease>>
}
```

Las releases publicadas recientes de ShogunAi, sin borradores ni pre-releases, o `UpdateCheckFailed`.

### `RepositoryTrust` / `RepositoryApproval`

```kotlin
data class RepositoryApprovalSubject(
    val commands: List<List<String>>,
    val variables: List<Pair<String, String>>,
    val envFile: String?,
)

interface RepositoryTrust {
    fun isApproved(repository: String, subject: RepositoryApprovalSubject): Boolean
    fun approve(repository: String, subject: RepositoryApprovalSubject)
    fun revoke(repository: String)
}

fun interface RepositoryApproval {
    suspend fun decide(repository: String, subject: RepositoryApprovalSubject): ApprovalDecision
}
```

El sujeto es lo que `.shogunai.json` pide a ShogunAi ejecutar o aplicar: los comandos post-creación, cada variable declarada con el texto de su plantilla y `envFile` (`RepositoryProvisioning.approvalSubject`). `RepositoryTrust` recuerda, por repositorio y en esta máquina, el sujeto exacto que aprobó el usuario; cualquier diferencia no está aprobada, y un sujeto vacío siempre lo está. `RepositoryApproval` pregunta al usuario cuando no lo está y responde `APPROVE` o `SKIP`: la app de escritorio muestra el sujeto en el diálogo que inició la creación, el CLI pregunta en un terminal, y los hooks y `--json` usan `RepositoryApproval.SKIP`. `RepositoryTrust.NONE` solo aprueba sujetos vacíos, y es lo que usan por defecto los casos de uso construidos sin almacén de confianza, así que olvidarse de pasarlo falla de forma segura.

### `AgentSessionStore`

Guarda los `AgentSessionRecord` de las sesiones de un worktree: `write`, `remove` y `list`, por ruta de worktree. `FileAgentSessionStore` es la implementación (ver [infraestructura](infrastructure.md#fileagentsessionstore)); el `hook agent-state` de la CLI escribe y `DetectRunningAgentsUseCase` lee y borra los registros muertos.

### `ClaudeSessionRegistry`

Lista las sesiones de Claude Code que corren en la máquina como `ClaudeSession` (`pid`, `cwd`, `name`, `startedAt` opcional). Es el registro propio de Claude Code, de solo lectura y separado de `AgentSessionStore`, que es el estado de ShogunAi alimentado por hooks. `FileClaudeSessionRegistry(directory)` lee los ficheros `*.json` de `<directorio de configuración de Claude>/sessions` (`ClaudeCodeHooks.claudeSessionsDirectory`, a partir de `CLAUDE_CONFIG_DIR` o del home del usuario, construido en las raíces de composición) de forma tolerante: ignora los campos desconocidos, se salta los ficheros sin `pid` o `cwd` o que no se pueden leer o interpretar, un directorio que no existe no tiene sesiones, y nunca escribe. Comprobar si siguen vivas queda para quien las usa.

Los nombres de sesión viven junto a `AgentLaunchConfig`: `sessionName(parts)` pasa a minúsculas, convierte cada tramo de caracteres fuera de `[a-z0-9]` en un guion, recorta los guiones, une las partes con `-` y limita el resultado a 64 caracteres; `groupMemberSessionName`, `worktreeSessionName` (el nombre de la carpeta del proyecto, más el de la carpeta del worktree salvo que sea el principal) y `multirepoSessionName` construyen los nombres que usa `open --herdr`. `namedClaudeCommand(command, name)` añade ` -n <nombre>` solo a un comando único que ejecuta `claude` (por nombre o por ruta), opcionalmente tras asignaciones `NOMBRE=valor`, `env` o `headroom wrap`, y sin su propio `-n`/`--name`; si no, devuelve `null`. `uniqueSessionName` elige el primero libre de `name`, `name-2`, `name-3`…

### Repositorio workspace de un multirepo

`MultirepoDeclaration` tiene un `workspace` opcional (`WorkspaceSetting`: `enabled`, `alias`, `default`; en JSON `false` o `{ "alias", "default" }`). `Multirepo` lleva `folderIsRepository`, que fija `ResolveMultirepoUseCase.build` a partir de la entrada `.git` de la carpeta. `workspaceRepository` es la carpeta como repositorio cuando eso es cierto y la declaración no la desactiva (o lista la carpeta en `repos` con la ruta `.`), `workspaceDetected` indica que ninguna declaración lo dice, y `repositories`, `aliases` y `defaultRepos` son las listas efectivas con él, así que crear, estado, limpiar y el catálogo de escritorio lo tratan como un repositorio más; `declaredRepositories` es solo el mapa `repos`. `validate` rechaza un alias inválido o que choque con una clave de `repos`. `ConfigureMultirepoUseCase.setWorkspace`/`clearWorkspace` escriben solo ese campo, y `setDefaults` traduce el alias del workspace a su marca `default`. Un repositorio de workspace sin proyecto recibe `<worktreesRoot>/<alias>` como carpeta de worktrees en `multirepoMembers`. `RegisterProjectUseCase` recibe el multirepo cuyo workspace es la carpeta (`withWorkspaceAt`) y registra ese proyecto con la carpeta propuesta.

`AgentSearch.multirepoFolder(carpeta, comando)` es una búsqueda exacta: `DetectRunningAgentsUseCase` solo le atribuye un directorio de trabajo igual a la carpeta, nunca una subcarpeta ni un repositorio miembro.

### Aprobación del multirepo y agente local

Una declaración puede venir de un repositorio clonado, así que `ResolveMultirepoUseCase` solo aplica un multirepo registrado en `MultirepoSource.knownFolders()` y cuyo `approvalSubject` está aprobado allí (`Multirepo.approved`). El sujeto es un texto con la carpeta normalizada, cada alias declarado con su ruta resuelta (ordenados por alias), la carpeta de worktrees resuelta y el ajuste de workspace declarado: lo que decide dónde crea ShogunAi ramas y worktrees. `defaultRepos` y el `agent` de la declaración no forman parte de él. La búsqueda hacia arriba de `invoke` solo encaja con carpetas registradas, `declaring` y `approved()` se saltan los multirepos sin aprobar, y `known()` devuelve todos los registrados con su estado para la app de escritorio. `nearestDeclaration` encuentra la declaración más cercana, registrada o no, para `shogun multirepo trust`/`show` y para el aviso del CLI sobre una declaración ignorada. `validate` rechaza alias, rutas de repositorio y `worktreesRoot` con caracteres de control o de formato Unicode, para que la revisión no se pueda falsear con secuencias de escape.

El agente del multirepo es un ajuste local (`MultirepoSource.agent`/`setAgent`, `Multirepo.localAgent`); `Multirepo.agent` usa `claude` sin Headroom por defecto y nunca lee `MultirepoDeclaration.agent`, que solo se conserva para que los archivos antiguos se sigan leyendo y `ignoresDeclaredAgent` pueda avisar. `ConfigureMultirepoUseCase` se niega a escribir un multirepo sin aprobar (`WorktreeError.MultirepoNeedsReview`), escribe la declaración sin `agent` y aprueba el sujeto que ha escrito, así que las ediciones del propio ShogunAi lo mantienen aprobado y una hecha en otro sitio no. `trust` registra y aprueba exactamente la declaración revisada (se niega si el archivo ha cambiado entretanto) y `revoke` la olvida.

### Repositorios

- `ProjectRepository` — `list()`, `save(project)`, `delete(id)` sobre el catálogo de proyectos.
- `ThemePreferenceRepository` / `LanguagePreferenceRepository` — `load()` / `save(...)` de cada preferencia del usuario.
- `ShellPreferenceRepository` — el estado del shell de escritorio: último destino, tamaño de la ventana, barra lateral y grupos plegados, el último proyecto usado para crear, los elementos recientes de la paleta, la agrupación y el orden de cada vista, el umbral de inactividad, los proyectos y worktrees recientes y las opciones de la bandeja.
- `ToolPreferenceRepository` — el terminal elegido y el IDE por defecto.
- `UpdatePreferenceRepository` — `load()`, `setCheckAutomatically(enabled)` y `skip(version)`.

## Casos de uso

Los casos de uso de worktrees se construyen por proyecto (reciben su `ProjectConfig`) y devuelven `Result<T>` (con `runCatching`), con errores tipados como `WorktreeError`. Todos los comandos de Git se ejecutan desde el repositorio base.

### `CreateWorktreeUseCase`

Crea un worktree en una rama **nueva** `<tipo>/<taskId>`. Primero normaliza el id de tarea (`normalizeTaskId`: recorta y sustituye los espacios por `-`) y lo valida como segmento de ref de Git; un punto de partida que empieza por `-` es `InvalidRef`. Después, en este orden:

1. **Validaciones previas** (repo base existe, destino libre, archivos locales presentes) y **resolución de los archivos del repositorio** — se hacen *antes* de tocar Git para no dejar un worktree a medias.
2. `git worktree add <path> -b <branch> [<startPoint>]`. Quien llama resuelve el punto de partida opcional a un id de commit con `ResolveStartPointUseCase` (`git rev-parse --verify --quiet <ref>^{commit}` en su propia carpeta, con fallo `StartPointNotFound`), de modo que `HEAD` es el del worktree donde se ejecuta el comando aunque Git corra en el repositorio base. La app de escritorio pasa la referencia elegida en su selector «Partir de», resuelta del mismo modo; sin ninguna, la rama parte del HEAD del repositorio base. Cuando quien llama pasa también un `baseBranch`, se registra justo después como `branch.<rama>.gh-merge-base` (ver [Ramas base](#ramas-base)); si esa escritura falla, el worktree se revierte como ante cualquier fallo posterior.
3. Copia de los archivos locales y de los de `copy` del repositorio al nuevo directorio, en las mismas rutas relativas, y enlace con symlink de los de `link` a las copias del repositorio base (copiándolos donde no se admiten enlaces). Cada destino tiene que quedar dentro del worktree una vez resueltos los symlinks (`FileManager.isInside`).
4. Resolución de la aprobación del repositorio (ver [Confianza en el repositorio](#confianza-en-el-repositorio)): ya aprobado, o preguntado con `RepositoryApproval`, guardando un `APPROVE` antes de seguir.
5. Preparación de su entorno con el `WorktreeEnvironmentPreparer` que recibe: `PrepareWorktreeEnvironmentUseCase` lo resuelve reservando puertos (`ResolveWorktreeEnvironmentUseCase`) y, si se declara `envFile` y la declaración está aprobada, `WriteEnvironmentFileUseCase` lo escribe en formato dotenv, negándose a sobrescribir un fichero que Git versiona (`git ls-files --error-unmatch`, `EnvironmentFileTracked`) e informando de los fallos de escritura como `EnvironmentFileWriteFailed`. El preparador es `WorktreeEnvironmentPreparer.NONE` por defecto, en lo que se apoyan los tests que no tratan del entorno; la app y el CLI siempre pasan el real.
6. Ejecución de los comandos post-creación del repositorio en el worktree, solo si está aprobado. Cuando el repositorio no está aprobado y el usuario no lo aprueba en ese momento, sus comandos y los nombres de sus variables declaradas se devuelven en `skippedPostCreateCommands` y `skippedVariables` del worktree. Un comando que falla es `PostCreateCommandFailed`, y el worktree se conserva, porque todo lo anterior salió bien.

Para resolver los archivos del repositorio se lee `RepositoryProvisioning` y, si declara algún patrón, se ejecuta `git ls-files --others --ignored -z -x <patrón>…`: todos los archivos que Git no versiona y coinciden con un patrón (los versionados ya llegan con el checkout). Sin patrones no se ejecuta ningún comando de Git. Un `.shogunai.json` ilegible (`InvalidRepositoryConfig`) o un `git ls-files` que falla (`GitCommandFailed`) detienen la creación antes de crear nada. Los archivos locales se comprueban de forma estricta (`SecretFileNotFound`) y los patrones de forma laxa: un patrón sin coincidencias no copia nada. Una entrada local que es una carpeta en modo copiar se expande con `FileManager.listTree` en los archivos que contiene cada vez que se resuelven los archivos, así que la creación, el provisionado y la comprobación de deriva ven archivos sueltos y un archivo añadido después a la carpeta se incorpora; los enlaces simbólicos que omite viajan como `skippedLinks`. Si la carpeta ya existe en el worktree se fusiona (los archivos con la misma ruta se sobrescriben, el resto se conserva); una carpeta inexistente es `SecretFileNotFound`, como un archivo inexistente. Una entrada local en modo enlazar se enlaza, declare lo que declare el repositorio, y una en modo copiar se copia; una carpeta enlazada se convierte en un único enlace simbólico a la carpeta base, que sustituye a una carpeta real solo si todos sus archivos constan en el registro como copiados de esa carpeta (si no, `LinkedFolderOccupied`), y a una carpeta copiada cuyo destino es un enlace a la carpeta base se le quita antes ese enlace. Un patrón local se expande con la misma llamada a `git ls-files` que `copy`, así que solo selecciona archivos sin versionar, y no se comprueba que exista.

Después de copiar, el registro de `ProvisioningRecordStore` de cada carpeta copiada se sustituye por los archivos recién copiados. Antes de copiar, un archivo registrado que ya no existe en su carpeta base se borra del worktree si allí es un archivo regular, y se devuelve como eliminado; los archivos que el registro no lista nunca se tocan. La comprobación de deriva informa de esos archivos como `DELETED_IN_BASE`.

Si la copia o la preparación del entorno fallan **después** de crear el worktree, se revierte con `git worktree remove --force` y se borra la rama que creó la creación con `git branch -D -- <rama>` (ambos best-effort) antes de propagar el error, así que un reintento no encuentra nada en su camino. Si `git worktree add` falla porque falta `git-lfs`, se eliminan del mismo modo el worktree a medio crear y su rama nueva y se devuelve `GitLfsNotFound`.

### `CreateWorktreeFromBranchUseCase`

El mismo flujo para una rama que **ya existe**, en local o solo en `origin`. El nombre se busca de forma exacta con `git show-ref --verify --quiet`: una rama local (`refs/heads/<nombre>`) ejecuta `git worktree add <path> <branch>` (sin `-b`); si no, `origin/<nombre>`, o un nombre dado con el prefijo `origin/`, crea una rama local que la sigue con `git worktree add --track -b <nombre> <path> origin/<nombre>`, y una reversión borra esa rama nueva. En ambos casos la carpeta es la que da `worktreePathForBranch` al nombre local, y archivos, entorno y comandos post-creación siguen como en `CreateWorktreeUseCase`. Una rama ya en uso en otro sitio falla con `BranchAlreadyCheckedOut`, una desconocida con `BranchNotFound` y un nombre que empieza por `-` con `InvalidRef`.

### `ListBranchesUseCase`

Lista las ramas locales y las de `origin/` (`git for-each-ref --format=%(refname) refs/heads refs/remotes/origin/`, sin `origin/HEAD`) y marca las locales que ya están en uso, usando `git worktree list --porcelain`. La UI muestra las marcadas deshabilitadas y, en «Rama existente», oculta las ramas remotas que tienen gemela local (`withoutLocalTwins`).

### `FetchRemoteUseCase`

Ejecuta `git fetch --prune origin` en el repositorio base con el timeout de red, sin las preguntas de credenciales de Git (`GIT_TERMINAL_PROMPT=0`, para que unas credenciales que faltan fallen en vez de esperar), e informa de un fallo como `GitCommandFailed`. `hasOrigin()` indica si el repositorio tiene un remoto `origin`, que es cuando la app de escritorio ofrece el fetch. `shogun new --fetch` y la opción «Hacer fetch de origin antes» lo usan antes de resolver el punto de partida o la rama.

### `ListWorktreesUseCase`

Ejecuta `git worktree list --porcelain` (formato estable pensado para parsear, a diferencia de la salida legible por defecto) y parsea el resultado con `parseWorktreeList`. Marca `isMain = true` en el worktree cuya ruta es el mismo directorio que `config.baseRepositoryPath` (`sameDirectory`: symlinks resueltos, barras finales y separadores ignorados, sin distinguir mayúsculas en macOS y Windows). Cuando recibe un `FileManager` (la app de escritorio se lo pasa), un repositorio base que ya no existe falla con `BaseRepositoryNotFound` sin ejecutar Git.

### `ResolveMainBranchUseCase`

Resuelve la `MainBranch` una vez por carga de la lista, desde el repositorio base y sin acceso a red: `config.mainBranch` si está definida; si no, la rama por defecto del remoto `origin` (`git symbolic-ref refs/remotes/origin/HEAD`), y como alternativa una `main` local y después `master`. Una rama con nombre se compara a través de `origin/<nombre>` cuando esa ref existe (`git rev-parse --verify`), porque los merges suelen llegar antes al remoto, y a través de la rama local en caso contrario (`resolveBranchRef`, compartido con las bases registradas). Devuelve `null` cuando no se puede resolver nada.

### `ReadWorktreeStatusUseCase`

Lee un `WorktreeStatus`, ejecutando cada comando en la carpeta del propio worktree:

- `git --no-optional-locks status --porcelain` — cambios sin commitear. Los locks opcionales se desactivan para que `git status` nunca reescriba el índice; si no, leer los estados cambiaría los metadatos que vigila el refresco automático. Es el único comando obligatorio: si falla (p. ej. la carpeta ya no existe), falla todo el estado.
- `git rev-list --left-right --count @{upstream}...HEAD` — divergencia con el upstream; se omite con HEAD separado y es `null` si no hay upstream.
- `git rev-list --left-right --count <ref principal>...HEAD` — divergencia con la rama principal; se omite cuando no hay rama principal o el worktree está en ella.
- `git rev-list --left-right --count <ref base>...HEAD` — divergencia con la base, la `base` que pasa quien llama (la rama principal por defecto); se reutiliza la divergencia con la rama principal cuando la base es la rama principal, y se omite cuando el worktree está en su base.
- `git log -1 --format=%ct` — fecha del último commit, `null` si no hay commits.
- `git reflog show --format=%H refs/heads/<rama>` — solo cuando la rama se leería como mergeada en su base o en la rama principal y no tiene upstream: un reflog con una sola entrada (su creación) que sigue siendo la punta de la rama significa que la rama nunca tuvo commits propios, así que se lee como «sin empezar» (`neverCommitted`) en vez de mergeada.

Todos los comandos se ejecutan con el timeout de lectura de Git.

Los merges por squash o rebase no se detectan como mergeados, y las ramas remotas de seguimiento están tan actualizadas como el último fetch del usuario.

### `CleanUpWorktreesUseCase`

`selectCleanup(worktrees, statuses, runningAgents)` elige los worktrees a limpiar: no el principal, no bloqueados, y o bien con la carpeta perdida (`isPrunable`) o bien mergeados (en su base o en la rama principal) y sin cambios sin commitear. Los worktrees sin estado cargado nunca se eligen, y un worktree que cumple pero tiene un agente en ejecución pasa a `keptForRunningAgent` con sus agentes, así que ni la app de escritorio ni `shogun clean` lo eliminan. `selectCleanupCandidates` devuelve solo los candidatos.

`CleanUpWorktreesUseCase` recibe esos candidatos, la `MainBranch` y si hay que borrar las ramas, y devuelve un `CleanupReport` (`removed`, `keptBranches`, `failures`, `herdrFailures`, `orcaFailures`). Cada worktree existente se elimina con `git worktree remove` (nunca con `--force`), y los que han perdido la carpeta con un único `git worktree prune`. Si un paso falla se anota y se sigue con los demás. Después, las ramas de cada worktree eliminado, incluidos los podados, solo se borran si `git merge-base --is-ancestor` sigue teniendo éxito contra su base registrada (leída con `git config --get` antes de borrar, porque `git branch -D` elimina el registro) o contra la rama principal, y entonces con `git branch -D`: `-d` comprueba contra el `HEAD` del repositorio base, que en un repositorio git-flow puede no contener una rama mergeada en `develop`.

### Vista completa de worktrees

- `classifyOrigin(worktree, config, herdrWorktreesRoot, orca)` devuelve el `WorktreeOrigin` de un worktree que no es el principal: `CLAUDE_CODE` si su ruta está dentro de una carpeta `.claude/worktrees/` (se comprueba primero, porque esa carpeta puede estar dentro de la raíz de worktrees), `HERDR` si está dentro de la carpeta de worktrees de Herdr (cuando se indica, leída con [`HerdrConfigSource`](#herdrconfigsource)), `ORCA` cuando los `OrcaOrigins` dados dicen que lo creó Orca, `SHOGUNAI` si está dentro de `config.worktreesRoot` y `OTHER` en otro caso. Las rutas se comparan con separadores `/` y sin barra final, así que también funcionan las de Windows.
- `activityDate(worktree, status)` devuelve un `ActivityDate(at, kind)`: la fecha del último commit (`COMMIT`) cuando el worktree es el principal o su rama tiene commits propios, y en otro caso la fecha de creación del worktree (`CREATED`), recurriendo al último commit cuando no se puede leer. La fila, el orden «Última actividad» e `isInactive` la usan.
- `isInactive(worktree, status, agents, now, inactiveAfter)` es verdadero para un worktree que no es el principal, con commits propios, sin cambios sin commitear, sin agentes en ejecución (uno inactivo cuenta) y con una fecha de actividad más antigua que `inactiveAfter`, una `Duration` que la app toma de su ajuste `inactiveAfterDays` (3 días por defecto, `DEFAULT_INACTIVITY_THRESHOLD`).
- `DetectRunningAgentsUseCase` devuelve los `RunningAgent` por ruta de worktree, a partir de dos fuentes leídas a la vez. El escaneo de procesos ejecuta `lsof -d cwd -F pcn` una vez para obtener el directorio de trabajo de cada proceso, asigna cada proceso al worktree con la ruta más larga que lo contiene (`owningWorktree`, así un proceso en `.claude/worktrees/<nombre>` no se atribuye al worktree principal) y ejecuta `ps -o pid=,comm=` para esos procesos. El nombre del ejecutable se compara con los CLIs de agentes conocidos (`claude`, `codex`, `gemini`, `cursor-agent`, `opencode`, `aider`, `copilot`) y con los ejecutables de todos los agentes del proyecto. Puede buscar en varios proyectos a la vez (`AgentSearch`, los comandos de agente y los worktrees de un proyecto): `lsof` y `ps` se siguen ejecutando una vez, y los agentes de un proyecto solo se reconocen en los worktrees de ese proyecto. No se usa el nombre que da `lsof` porque Claude Code renombra su proceso con su versión. Cuando recibe un `HerdrClient`, también lee `listAgents()` (límite de 5 segundos) y atribuye cada agente de Herdr a un worktree por su directorio: completa el agente del mismo nombre detectado por procesos con su estado y su pane de Herdr, o se añade cuando el escaneo de procesos no lo ve, y varios panes de un mismo agente en un worktree se quedan con el estado más urgente (bloqueado, terminado, trabajando, ocioso). Un fallo de Herdr (no instalado, sin servidor, timeout) no da datos de Herdr, nunca hace fallar la detección y hace que los 30 segundos siguientes se salte Herdr. Si `lsof` o `ps` fallan (p. ej. en Windows) y Herdr no informa nada, falla con `WorktreeError.ProcessListingFailed`.
- Si recibe un `AgentSessionStore`, también lee los registros de todos los worktrees y da al agente `claude` el estado de su sesión viva más urgente (bloqueado, terminado, trabajando) con la fuente `CLAUDE_CODE`. Un registro está vivo mientras su `pid` sea un proceso `claude` encontrado por la misma llamada a `ps` (así un pid reutilizado no lo revive); uno sin pid está vivo mientras corra un proceso `claude` en ese worktree y tenga menos de 12 horas. Los registros muertos se borran. Un registro bloqueado o terminado cuyo transcript se modificó más de un segundo después del registro se lee como trabajando. El estado de Herdr, aplicado después, gana; un agente de Herdr sin estado conserva el del hook.
- Si recibe un `OrcaWorktreeReader`, también lee los agentes de Orca una vez por detección: el lector omite Orca cuando no está instalado, consulta `status()` y nunca llama a `start()`, ejecuta `agentsByWorktree()` (un único `orca worktree ps`) en 2 segundos como mucho y, tras un fallo, un tiempo agotado o un Orca cerrado, omite Orca durante 30 segundos. Las filas se asocian a los worktrees por ruta canónica (`/tmp` y `/private/tmp` son la misma), los agentes de Orca enriquecen al agente del mismo nombre detectado por procesos con la fuente `ORCA`, o se añaden cuando el escaneo de procesos no los ve, y gana el más urgente de varios agentes del mismo nombre. El estado de Orca gana al del hook; un estado de Herdr al menos igual de urgente se conserva. Tras cada lectura el lector da también `origins()`: las rutas canónicas de los worktrees que creó Orca o, mientras no se puede preguntar a Orca, la carpeta donde Orca creó worktrees por última vez (recordada con `OrcaWorkspaceStore`) o `~/orca/workspaces`. En cuanto `status()` dice que Orca está en marcha, el lector ejecuta también `ports()` (un único `orca ports --all`) en paralelo, en sus propios 2 segundos y con su propia espera de 30 segundos tras un fallo, de modo que un fallo de los puertos nunca oculta los agentes; `ports(worktrees)` responde con esa lectura los `ObservedPort` de cada worktree (puerto, proceso, dirección), asociados por ruta canónica, uno por puerto y ordenados, o `null` cuando no se pudo preguntar a Orca. El `AgentActivity` de cada agente de Orca (la `AgentTool` a la que espera, su prompt y su último mensaje) se copia al `RunningAgent` con su estado, también cuando se conserva un estado de Herdr.
- Si recibe un `ClaudeSessionRegistry`, añade al agente `claude` de cada worktree los `ClaudeSessionRef` (`name`, `pid`) de las sesiones con nombre cuyo `pid` está entre esos mismos pids de `claude` y cuyo `cwd` se atribuye a ese worktree como los procesos (el worktree más profundo, no dentro de un repositorio anidado, exactamente la carpeta para el agente del multirepo), ordenados por nombre. Un registro que falla deja los agentes sin sesiones.
- `worktreeActivity(worktree, status, agents, now, inactiveAfter)` da a cada worktree un único estado para las vistas de la app de escritorio y la barra de menús, el primero que se cumple: `Missing`; `NeedsYou` cuando un agente está bloqueado o terminado (`waitsForUser`); `AgentWorking` cuando un agente trabaja o no tiene estado conocido (los agentes inactivos se saltan, así que nunca deciden el estado); `Unknown` (estado sin leer); `NeedsYou` por cambios sin commitear o commits sin subir; `Idle` (worktree principal); `New` (sin commits propios y sin fusionar); `Merged`; `Inactive`; y `Stopped` en otro caso. Los cuatro primeros son los *activos* (`isActive`).
- `MeasureWorktreeSizeUseCase` devuelve el tamaño de un worktree con `FileManager.directorySize`, en el dispatcher de IO, excluyendo cualquier otro worktree listado que esté anidado dentro. Devuelve `null` si la carpeta no existe.
- `ReadGitMetadataFingerprintUseCase` devuelve una huella de los metadatos de Git del repositorio principal: los pares ordenados (ruta relativa a `.git`, fecha de modificación) de `HEAD`, `index`, `packed-refs`, `FETCH_HEAD` y todo lo que hay bajo `refs` y `worktrees`, leídos en el dispatcher de IO. Los commits, checkouts, bloqueos, fetches y worktrees añadidos o eliminados por cualquier herramienta la cambian; `objects` no se incluye. Está vacía cuando `<baseRepositoryPath>/.git` no es un directorio.

### `ClaudeCodeHooks`

Instala, actualiza, comprueba y quita los hooks de ShogunAi en un `settings.json` de Claude Code, trabajando sobre el árbol JSON ya analizado (nunca sobre texto) a través de `FileManager`. Los hooks deseados son una sola tabla (`DESIRED`: evento → comando); un hook es de ShogunAi cuando es un hook `command` cuyo comando empieza por `shogun hook `. `previewInstall` / `previewUninstall` calculan un `HooksPreview` (hooks añadidos, reemplazados y quitados); `install` quita los hooks de ShogunAi que difieren y añade los que faltan como su propio grupo, `uninstall` los quita junto con los grupos y eventos que quedan vacíos, y ninguno escribe cuando la vista previa está vacía. `status` da `HooksInstallState.INSTALLED`, `OUTDATED` o `MISSING` con los eventos que difieren. Un archivo que no es un objeto JSON, o cuyo `hooks` no lo es, es `WorktreeError.InvalidClaudeSettings` y nunca se escribe. `userSettingsPath` respeta `CLAUDE_CONFIG_DIR`; `projectSettingsPath` es `<repositorio>/.claude/settings.json`.

### `RemoveWorktreeUseCase`

Ejecuta `git worktree remove [--force] <path>` y, opcionalmente, `git branch -d -- <branch>` para borrar la rama local (borrado seguro, no `-D`). Devuelve un `WorktreeRemoval` cuyo `herdrFailure` es el error de cerrar el workspace de Herdr del worktree, si falló (ver [`CloseHerdrWorkspacesUseCase`](#closeherdrworkspacesusecase)).

El borrado en sí (`WorktreeFolderRemoval`, compartido con la limpieza) trata tres fallos de `git worktree remove`. Una carpeta bloqueada cierra los procesos de Herdr que la usan y reintenta una vez, y falla con `WorktreeFolderInUse` si sigue bloqueada. "is not a working tree" y "Filename too long" significan que Git ya ha desregistrado el worktree y ha dejado su carpeta (Git borra el directorio administrativo aunque falle el borrado de la carpeta), así que se termina el resto: `git worktree prune` y luego `FileManager.deleteRecursively` sobre la carpeta, salvo que sea el repositorio base o tenga su propio directorio `.git`. Una carpeta que no se puede borrar se convierte en `WorktreeFolderInUse` con `unregistered = true`.

!!! note
    Git no permite eliminar un worktree desde dentro de sí mismo, otro motivo para ejecutar siempre los comandos desde el repositorio base.

### `ReleaseWorktreeResourcesUseCase` / `resolveWorktreeTarget`

Eliminar un worktree también libera lo que retienen sus providers, en dos pasos alrededor de la eliminación. `plan(config, worktree, allWorktrees)` lee, mientras el worktree aún existe, su emulador en marcha, su simulador arrancado y los daemons de Gradle ociosos cuya última build fue en él, cada uno solo si el repositorio declara ese provider; `release(config, plan)` los para después de que la eliminación haya salido bien. Los fallos se recogen como mensajes en `ReleasedResources.failures` en vez de hacer fallar: `shogun remove` los informa y la app de escritorio los ignora. La limpieza mantiene sus propias paradas, opcionales y por lotes.

`resolveWorktreeTarget(target, worktrees, config, canonicalPath)` es como `shogun remove` encuentra su worktree: una ruta existente de un worktree listado; si no, una rama que coincida exactamente con la de un único worktree; y si no, un id de tarea traducido con `worktreePathFor`. Sin coincidencias es `WorktreeTargetNotFound`, con varias es `AmbiguousWorktreeTarget` con sus rutas, y el worktree principal es `CannotRemoveMainWorktree`. `findWorktreeTarget` hace la misma búsqueda sin rechazar el worktree principal, para `shogun open`.

### Casos de uso de los pools de recursos

- `ResolveResourceLimitsUseCase` convierte los `ResourceSettings` guardados en `ResourceLimits` con la tabla de perfiles, usando el puerto `MachineCapacity` (`totalMemoryBytes()` y `cores()`, implementado sobre el `OperatingSystemMXBean` de la JVM). Un ajuste ausente o ilegible significa Automático.
- `ReadResourcePoolUseCase` construye un `PoolUsage` de emuladores y simuladores a partir de todas las reservas de la máquina (asignando cada directorio de administración a su worktree mediante su archivo `gitdir`) y del estado de los dispositivos, y el de los daemons de Gradle a partir de `ListGradleDaemonsUseCase`.
- `AcquireWorktreeResourceUseCase` es lo primero que llaman los casos de uso de arranque del emulador y del simulador. Bajo el lock de reservas: un recurso que el worktree ya tiene solo actualiza su último uso y su concesión; con hueco en el pool reserva un puerto o crea el simulador; en el límite libera el holder con el `releaseRank` más bajo (deteniendo antes un emulador en marcha o apagando un simulador arrancado, eliminando el simulador y guardando un `ReleaseRecord` en la víctima) y reserva; sin ningún holder liberable falla con `ResourcePoolFull`. Los puertos se eligen en este orden: el puerto anterior del worktree si está libre, uno no liberado dentro de la duración de la concesión, cualquiera libre. Devuelve los `PoolRelease` que hizo y reescribe el archivo de entorno declarado del worktree y el de cada uno liberado. `StartWorktreeEmulatorUseCase` y `StartWorktreeSimulatorUseCase` devuelven un `DeviceStart` con el dispositivo y ese resultado.
- Esperar capacidad: antes de liberar nada, la adquisición lee la `DeviceWaitQueue` del pool (descartando los tickets cuyo proceso `ProcessLiveness` da por muerto) y falla con `ResourcePoolFull`, con el número de quienes esperan, cuando el ticket de otro worktree va por delante del suyo (o de cualquiera, sin ticket); un worktree que ya tiene su dispositivo nunca se bloquea. El bucle es `WaitInLine`, compartido con el turno del escritorio e indexado por un `WaitQueueName` (`emulator`, `ios-simulator`, `desktop`). Con un `DeviceWait`, primero lo intenta sin ticket y, ante `ResourcePoolFull`, añade un `WaitTicket` y reintenta, durmiendo hasta lo primero entre 10 segundos, un segundo tras la próxima caducidad de una concesión o el plazo, e informando de un `DeviceWaitProgress` en cada intento. Siempre quita su ticket (también si se cancela), falla con `DeviceWaitTimedOut` cuando pasa el plazo y devuelve `waitedSeconds` en `ResourceAcquisition` (`null` sin espera). El arranque del dispositivo ocurre después de la espera, fuera de su plazo.
- `ReadAgentActivityUseCase` da el estado del agente de un worktree a partir de las sesiones vivas de `AgentSessionStore` (trabajando antes que bloqueado, que terminado, que inactivo; `null` sin ninguna), `RenewWorktreeLeasesUseCase` renueva las concesiones de los dispositivos que tiene un worktree sin tocar su último uso ni adquirir nada, y `ReadReleaseRecordUseCase` lee el registro de liberación de un worktree.
- `TrimGradleDaemonsUseCase` detiene los daemons de Gradle inactivos, primero los que llevan más tiempo inactivos, mediante `StopGradleDaemonsUseCase`, hasta que el número esté dentro del límite; los daemons ocupados y los de estado desconocido nunca se detienen por esto.

### `CloseOrcaTerminalsUseCase`

Los mismos dos pasos para Orca. `withTerminals(paths)` devuelve las rutas con terminales de Orca, consultadas solo cuando Orca está instalado y su runtime responde (nunca arranca Orca); cualquier fallo cuenta como sin terminales. `close(withTerminals, removedPaths)` ejecuta `closeTerminals` para cada ruta eliminada que tenía terminales y devuelve un `CleanupFailure` por cada cierre que falla, que los llamadores muestran como aviso (`WorktreeRemoval.orcaFailure`, `CleanupReport.orcaFailures`). Cuando `git worktree remove` falla porque la carpeta está en uso, la eliminación cierra también las terminales de Orca de ese worktree (`closeOne`) antes de su único reintento. Está conectado en las mismas rutas de eliminación que el de Herdr.

### `CloseHerdrWorkspacesUseCase`

Cierra el workspace de Herdr de los worktrees eliminados, en dos pasos alrededor de la eliminación. `openWorkspaces(paths)` hace una única llamada `worktree list` para el repositorio base, antes de eliminar nada (después Git ya no lista el worktree), y devuelve el id de workspace de cada ruta dada que Herdr tiene abierta, comparando rutas con `normalizePath`. Cualquier fallo (Herdr no instalado o en marcha, respuesta inesperada) da un mapa vacío: si ShogunAi no ve a Herdr no hay nada que cerrar ni que informar. `close(workspaces, removedPaths)` cierra solo los workspaces de las rutas que se eliminaron de verdad (incluidos los worktrees perdidos que se podan) y devuelve un `CleanupFailure` por cada cierre que falló, en vez de lanzar. `RemoveWorktreeUseCase` y `CleanUpWorktreesUseCase` lo reciben como parámetro opcional y `null` deja su comportamiento como estaba; un fallo de Git no cierra nada.

### `CopyWorktreeLaunchCommandUseCase`

Copia al portapapeles `cd <ruta del worktree> && <comando del agente>`, para que el usuario lo pegue en la terminal que prefiera. La ruta va entrecomillada con `ShellQuoting.posix` (`ShellQuoting.windows` en Windows), así que la shell en la que se pega no interpreta ningún carácter de ella, y el comando es el `resolvedCommand()` del agente indicado, el de por defecto del proyecto si no se indica ninguno.

### `OpenWorktreeInTerminalUseCase`

Abre un worktree en el terminal elegido con su entorno y un agente (el de por defecto si no se indica ninguno). Elige el `TerminalLauncher` de ese `TerminalId` (`TerminalUnsupported` si no hay ninguno), resuelve el entorno del worktree como `shogun env` y construye una línea de lanzamiento: para terminales POSIX `cd <ruta> && export NOMBRE=<valor> … && <comando del agente>`, cada valor entrecomillado con `ShellQuoting.posix`; para Windows Terminal el equivalente en PowerShell, `Set-Location -LiteralPath <ruta>; $env:NOMBRE = <valor>; …; <comando del agente>`. El lanzador recibe la línea y las partes con las que se construye, porque tmux fija él mismo las variables y la carpeta y solo escribe el comando del agente.

### `OpenWorktreeInHerdrUseCase`

Abre un worktree en Herdr con su entorno y un agente (el de por defecto del proyecto salvo que se indique otro). Pide a Herdr que abra el worktree del repositorio base del proyecto, con el nombre de su carpeta; si Herdr ya lo tenía abierto y uno de sus panes tiene un agente vivo, enfoca el pane de ese agente y devuelve `FOCUSED` sin escribir nada. Si no, resuelve el entorno del worktree (el mismo que `shogun env`, con las variables de los proveedores como `ANDROID_SERIAL`, reservando puertos si hace falta). En un workspace ya abierto sin agente siempre divide el pane raíz en uno nuevo cuya shell arranca en el worktree con esas variables, conserva los demás panes y devuelve `STARTED_AGENT`. En un workspace nuevo, si el entorno no está vacío, divide el pane raíz igual y cierra el raíz, para que el workspace se quede con un único pane, y devuelve `OPENED`. En ambos casos el `resolvedCommand()` del agente se ejecuta con `HerdrAgentLauncher`, que lo escribe y consulta `listPanes` de ese workspace cada 500 ms hasta que el pane tiene un agente vivo, y falla con `HerdrAgentNotStarted` a los 10 segundos (el pane queda abierto). El launcher también enfoca, así que `OpenMultirepoAgentInHerdrUseCase` sigue las mismas reglas para el workspace simple del agente del multirepo (un pane nuevo dividido del que encuentra en la carpeta del multirepo, sin variables extra), y `OpenGroupInHerdrUseCase` ejecuta este caso de uso para cada worktree de un grupo. Los casos de uso pasan al launcher un nombre de sesión (`worktreeSessionName`, `groupMemberSessionName` o `multirepoSessionName`) y la carpeta. Cuando el launcher tiene un `ClaudeSessionRegistry` y `namedClaudeCommand` acepta el comando, hace el nombre único frente a las sesiones vivas (un pid cuenta como vivo cuando `isClaudeProcessAlive`, por defecto un `ProcessHandle` en marcha cuyo comando termina en `claude` o `claude.exe`), escribe el comando con nombre y, una vez detectado el agente, consulta el registro durante hasta 10 segundos buscando una sesión viva con ese nombre en esa carpeta; el nombre confirmado va en `HerdrOpening.sessionName`, que se queda en `null` si se agota el tiempo sin que falle la apertura. Cuando enfoca un agente `claude` vivo, informa de la sesión viva de la carpeta con el nombre esperado o, si no, de la que arrancó más tarde. Cuando Herdr ya informa de un agente en el worktree, la app de escritorio enfoca el pane de ese agente con `HerdrClient.focusAgent` en vez de llamar a este caso de uso. `shogun open <destino> --herdr` también lo llama, sin esa comprobación de agentes.

El nombrado de sesiones (`freeName`, `named`, `awaitSession`, `focusedSessionName`) vive en `ClaudeSessionNaming`, compartido por `HerdrAgentLauncher` y Orca.

### `OpenWorktreeInOrcaUseCase`

Abre un worktree en Orca con su entorno y un agente. Cuando `status()` dice que Orca no está abierto llama a `start()` y falla con `OrcaNotStarted` si Orca sigue sin estarlo. Busca el repositorio base entre `repos()` por ruta canónica, o lo añade, y llama a `showExternalWorktrees` para ese repositorio solo cuando `worktrees(repoId)` no lista el worktree (comprobar el listado en vez del ajuste cubre `inherit`, cuyo valor efectivo el CLI no expone). Después busca un agente vivo: la primera de `terminals(path)` cuyo `terminalAgent` tiene valor recibe `switchTo` y el resultado es `FOCUSED`, con el nombre de la sesión viva de Claude. Si no, resuelve el entorno, nombra el comando del agente como Herdr, lo envuelve en `EnvironmentCommand.posix` (`env 'NOMBRE=valor' … <comando>`) o, en Windows, `EnvironmentCommand.powershell` (las asignaciones que usa `OpenWorktreeInTerminalUseCase`), crea una terminal enfocada titulada con el nombre de la sesión o del agente y consulta `terminalAgent` cada 500 ms durante hasta 10 segundos, fallando con `OrcaAgentNotStarted` (la terminal queda abierta). Tras enfocar o arrancar el agente llama a `bringToFront()`, ignorando un fallo, porque ni `terminal switch` ni `terminal create --focus` traen la ventana de Orca al frente. El nombre de sesión confirmado, esperado como con Herdr, va a `OrcaOpening.sessionName`. Lo llaman "Abrir en Orca" de la app de escritorio y `shogun open <destino> --orca`. Antes de buscar un agente vivo, cuando su consulta `runningEmulator` (`ReadEmulatorStatesUseCase.runningSerial`, conectada por la CLI y la app; `OpenGroupInOrcaUseCase` la pasa con la configuración de cada miembro) da un serial, llama a `attachEmulator` para el worktree, ignorando un fallo, e informa del serial en `OrcaOpening.emulator`, de modo que la terminal del agente se enfoca después de la pestaña del emulador. Arrancar Orca, registrar un repositorio, enfocar un agente vivo y arrancar uno viven en `OrcaAgentLauncher`, compartido con los casos de uso de multirepo de abajo.

`ShowEmulatorInOrcaUseCase(worktreePath, serial)` muestra un emulador que ShogunAi acaba de arrancar y devuelve un `OrcaEmulatorView`: `NotOpen` cuando Orca no está instalado, `status()` no está en marcha (Orca nunca se arranca) o `agentsByWorktree()` no tiene fila para la ruta canónica del worktree; `Shown` tras `attachEmulator`; `Failed(message)` ante cualquier error o pasados 10 segundos. `shogun emulator start` y los arranques de emulador de la app de escritorio (el detalle del worktree y la vista de la máquina) lo llaman tras un arranque correcto. Nada llama a Orca cuando un emulador se para: el stream de Orca termina con el emulador.

`OpenGroupInOrcaUseCase` falla una sola vez con `OrcaNotInstalled` cuando Orca no está instalado y, si no, ejecuta `OpenWorktreeInOrcaUseCase` para cada worktree de un grupo, uno tras otro, con el agente de su proyecto y `groupMemberSessionName`, y devuelve un `GroupOrcaOpening` (alias, ruta y resultado) por worktree. `OpenMultirepoAgentInOrcaUseCase` arranca Orca si hace falta, toma las filas de `agentsByWorktree()` cuya ruta canónica es la carpeta del multirepo (un espacio de trabajo de carpeta, una carpeta añadida con "Abrir como carpeta" o un worktree principal de Git), prefiriendo una con agentes y después el orden de Orca, y enfoca su agente vivo o arranca allí el agente del multirepo con `multirepoSessionName` y sin variables extra. Sin ninguna fila así, una carpeta que es un repositorio Git se registra y se usa su worktree principal; cualquier otra carpeta falla con `OrcaMultirepoFolderNotOpen` sin tocar Orca.

### `OrcaWorktreeSync`

Mantiene los metadatos de Orca de los worktrees que lista Orca al día con ShogunAi. `sync(items, rows, prune)` recibe un `OrcaSyncItem` por worktree (el worktree, su `WorktreeStatus`, sus agentes y su `Activity`) y las filas del último `agentsByWorktree()`, emparejadas por ruta canónica. Se omiten el worktree principal, los espacios de trabajo de carpeta, los worktrees que Orca no lista y las actividades sin columna (`Missing`, `Idle`, `Unknown`). Para el resto envía un `updateWorktree` con solo lo que cambió:

- el estado del tablero de `orcaBoardStatus` (`New` → `todo`, `AgentWorking` y `NeedsYou` → `in-progress`, `Stopped` e `Inactive` → `in-review`, `Merged` → `completed`), solo cuando difiere del recordado en `OrcaWorkspaceStore` (o, la primera vez, del de Orca), así que una tarjeta movida a mano se queda hasta que cambia el estado;
- el comentario de `orcaComment`, un resumen de una línea que empieza por `ShogunAi: `, solo cuando el comentario de Orca está vacío o ya empieza así;
- la pull request (`GitHubClient.pullRequestFor`) de una rama con upstream y la issue que nombra la rama (`issueNumberOf`: `123-…`, `issue-123`, `gh-123`) cuando existe, solo cuando Orca no tiene ninguna, preguntando a GitHub como mucho una vez cada 10 minutos por worktree.

Los fallos se devuelven por worktree en lugar de lanzarse. `prune` olvida los estados recordados de rutas que Orca ya no lista. La app de escritorio lo llama al final de cada comprobación periódica con las últimas filas del lector (`OrcaWorktreeReader.lastRows()`, `null` mientras Orca no está abierto), y el CLI después de `shogun open --orca` y `shogun hook orca setup` para ese worktree.

### Ramas base

La rama de la que parte una rama nueva se registra en la configuración de Git del repositorio como `branch.<rama>.gh-merge-base`, la clave que `gh pr create` lee como base por defecto. Git la guarda en la configuración común a todos los worktrees, la mueve con `git branch -m` y la elimina con `git branch -D`, así que ShogunAi nunca tiene que limpiarla.

- `ResolveBaseBranchUseCase` convierte una referencia en ese nombre de rama con `git rev-parse --verify --quiet --symbolic-full-name <ref>`, ejecutado donde se resuelve el punto de partida: `refs/heads/<b>` y `refs/remotes/<remoto>/<b>` dan `<b>`, y un tag, un commit, un `HEAD` separado, el `HEAD` de un remoto o una referencia desconocida dan `null`, así que no se registra nada. El CLI lo llama con la misma referencia que `ResolveStartPointUseCase`, la app de escritorio con la referencia de «Partir de» o `HEAD`, y `CreateGroupUseCase` pasa directamente `MainBranch.name`. `CreateWorktreeFromBranchUseCase` no registra nada.
- `ReadBaseBranchesUseCase` lee todos los registros con un solo `git config --get-regexp` y devuelve un mapa de rama a base (código de salida 1 sin salida es un mapa vacío).
- `ResolveRecordedBasesUseCase` devuelve, por rama, la `MainBranch` de su base registrada: una lectura de `ReadBaseBranchesUseCase` más un `resolveBranchRef` por cada base distinta. Las bases que no se resuelven se omiten y una lectura fallida da un mapa vacío, así que quien llama cae en la rama principal (`recordedBases[rama] ?: mainBranch`). `shogun status`, `shogun clean`, la lista de la app de escritorio y cada repositorio de un grupo multirepo (`RepositoryBases`) lo usan para pasar a `ReadWorktreeStatusUseCase` la base de cada worktree.
- `SetBaseBranchUseCase` valida una rama con `ResolveBaseBranchUseCase` en el repositorio base (si no, `NotABranch`) y escribe la misma clave.

### `ResolveStartPointUseCase`

Resuelve la referencia de la que parte una rama nueva a un id de commit con `git rev-parse --verify --quiet <ref>^{commit}`, ejecutado en el directorio de quien llama, así que `HEAD` es el worktree donde se ejecuta el comando y no el repositorio base. Una referencia desconocida falla con `StartPointNotFound` antes de crear nada. Lo usan `shogun new`, el hook `WorktreeCreate` y el selector «Partir de» de la app de escritorio, que resuelve su referencia en el repositorio base después del fetch opcional.

### `ResolveRepositoryUseCase`

Encuentra el repositorio base (worktree principal) que contiene un directorio, para los frontales que parten de un directorio en vez de un proyecto, como el CLI. Ejecuta ahí `git rev-parse --path-format=absolute --git-common-dir --is-bare-repository`: la carpeta de Git común la comparten todos los worktrees, así que, cuando es una carpeta `.git`, su padre es el worktree principal tanto si el directorio es el worktree principal, una subcarpeta o un worktree enlazado. Si no, el worktree principal es el `core.worktree` de la carpeta común cuando está definido (un submódulo), o la primera entrada de `git worktree list --porcelain` (un repositorio con la carpeta de Git aparte). Un repositorio bare es `BareRepositoryNotSupported`; cualquier cosa que no sea un repositorio es `RepositoryNotFound`.

### `DeleteMergedBranchUseCase`

Borra una rama local solo cuando está contenida en su base registrada o en la rama principal del proyecto, resuelta con `ResolveMainBranchUseCase` (`git merge-base --is-ancestor <rama> <ref>` contra cada una y después `git branch -D -- <rama>`, la misma regla que usa la limpieza), y devuelve si se borró en vez de fallar. Una rama mergeada solo en su upstream se conserva, y un nombre que empieza por `-` es `InvalidRef`. Lo usan los hooks `WorktreeRemove` de Claude Code y de Herdr del CLI, donde conservar la rama es lo esperado y no un error.

### `ProvisionWorktreeUseCase`

Aplica los mismos archivos que la creación (archivos locales y provisionado del repositorio, ver `CreateWorktreeUseCase`) a un worktree enlazado **ya existente**, p. ej. uno creado por otra herramienta. La ruta tiene que ser uno de los worktrees del repositorio (según `ListWorktreesUseCase`) y no el principal (`NotAWorktree`). Los archivos se copian (o enlazan) encima de los que haya, la aprobación se resuelve y el entorno se prepara igual (reservando puertos para un worktree creado por otra herramienta), se ejecutan los comandos post-creación aprobados y devuelve un `ProvisionResult`: cada `ProvisionedFile`, los comandos saltados y las variables saltadas. A diferencia de la creación, una copia o un entorno fallidos no eliminan nada: el worktree no es de ShogunAi como para borrarlo. Creación y provisionado comparten las mismas funciones de copia y de comandos.

### `CheckProvisionedFilesUseCase`

Compara un worktree enlazado con lo que le daría ahora el provisionado, sin cambiar nada: cada archivo que debería estar copiado y falta o difiere del del repositorio base (`hasSameContent`), y cada archivo que debería estar enlazado y falta o no es un enlace a la copia del repositorio base, como un `ProvisionDrift`. Lo usan `shogun provision --check` y el detalle del worktree de la app de escritorio.

### Confianza en el repositorio

`.shogunai.json` está versionado, así que quien controla una rama controla lo que declara. Sus comandos post-creación solo se ejecutan, y sus variables declaradas y su archivo de entorno solo se aplican, cuando el usuario aprobó ese sujeto exacto en esta máquina (`RepositoryTrust`, con la ruta real del repositorio como clave). La creación y el provisionado consultan primero el almacén y solo preguntan a `RepositoryApproval` cuando el sujeto no está aprobado, una sola vez para comandos y variables, antes de preparar el entorno; `APPROVE` guarda la aprobación y `SKIP` devuelve los comandos y los nombres de las variables como saltados. Un sujeto que cambia (por ejemplo tras un pull) vuelve a preguntar.

Las variables declaradas se controlan en un solo sitio, `ResolveWorktreeEnvironmentUseCase`, que recibe el almacén de confianza: mientras el sujeto no está aprobado resuelve como si no hubiera nada declarado (sin puertos declarados, variables ni precedencia sobre los providers) e informa de los nombres declarados en `awaitingApproval`, con `declarationApproved = false`, que `WriteEnvironmentFileUseCase` respeta sin escribir nada. Todos los consumidores (el hook `SessionStart` del agente, `shogun env`, los lanzamientos de terminal y de Herdr, el detalle de escritorio, los casos de uso de dispositivos) pasan por él, así que ninguno puede entregarlas sin aprobar. Guardar el entorno desde los ajustes de la app de escritorio aprueba el sujeto guardado cuando el repositorio estaba aprobado antes de guardar o no declara comandos post-creación, y los ajustes permiten aprobarlo o revocarlo explícitamente.

### `RepairWorktreesUseCase`

Vuelve a enlazar el repositorio base y sus worktrees después de moverlos en disco. Git guarda rutas absolutas en ambos sentidos (`<worktree>/.git` apunta al repositorio base y `.git/worktrees/<nombre>/gitdir` apunta de vuelta), así que un movimiento las rompe. Ejecuta `git worktree repair` desde el (nuevo) repositorio base; los worktrees que ya no existen en su ruta antigua pero sí en la misma posición relativa bajo el nuevo `worktreesRoot` se pasan como argumentos, para que Git arregle ambos sentidos. La UI lo ejecuta tras guardar un proyecto cuya ruta del repositorio base o carpeta raíz de worktrees cambió.

### `ResolveWorktreeEnvironmentUseCase`

Resuelve el entorno de un worktree a partir de la declaración del repositorio base, si está aprobada (ver [Confianza en el repositorio](#confianza-en-el-repositorio)), y de los providers que declara. Cada puerto necesario es un `PortRequest` (una clave, los puertos candidatos, los que también tienen que estar libres y el error cuando no queda ninguno): las claves `${port}` declaradas piden 20000–29999 (`declaredPortRequests`); los providers añaden las suyas con `EnvironmentContribution`s, que da la fábrica inyectada `ProviderContributions` (`DEFAULT` conoce el emulador; la app y el CLI pasan una que también construye la del simulador de iOS, que necesita el puerto `AppleSimulators` y los ajustes de máquina del proyecto), junto con las variables que derivan de sus puertos. Sin nada declarado y sin providers que aporten, devuelve un `WorktreeEnvironment` vacío sin ejecutar ningún comando. Si no, busca los directorios de Git de administración y común del worktree (`git rev-parse --path-format=absolute --git-dir --git-common-dir` dentro del worktree) y, con el lock de la máquina:

1. conserva los puertos guardados del worktree cuyas claves se siguen pidiendo y libera el resto;
2. reserva cada clave que falte en el primer candidato que, junto con sus puertos adicionales, ningún worktree de la máquina tenga reservado y `PortProbe` dé por libre (si no, el error de la petición, p. ej. `NoFreePort`);
3. escribe las reservas solo si han cambiado.

Las aportaciones también pueden pedir recursos (`ResourceRequest`: una clave y cómo comprobar, crear y descartar uno). Al reservar, un recurso guardado que sigue existiendo (`exists`) se conserva; si no, se crea uno fuera del lock (crear un simulador ejecuta `xcrun` durante alrededor de un segundo) y se registra dentro de él, y si otro proceso registró uno mientras tanto, el nuevo se descarta y se usa el registrado. Sin reservar, los recursos solo se leen. Las variables de los providers van primero y las variables declaradas en `env` sustituyen a una del provider con el mismo nombre. Un puerto reservado no se vuelve a comprobar, porque se espera que el propio servidor del worktree esté escuchando en él. Con `reserve = false` (para mostrar los puertos sin efectos secundarios) solo lee los puertos guardados, sin lock ni escritura, y no devuelve variables hasta que todos los puertos estén reservados. `formatEnvironment` presenta las variables como `sh` (`export NOMBRE='valor'`, entrecomillado con `ShellQuoting.posix`, seguro para `eval`), `dotenv` (`NOMBRE=valor`, entre comillas dobles salvo que el valor sea simple, escapando `\`, `"`, saltos de línea, `$` y comillas invertidas, para que cargar el fichero nunca ejecute un comando) o `json`.

Encontrar los directorios de Git de un worktree es lo único que lanza un proceso; cuando recibe una `WorktreeEnvironmentCache` (la app de escritorio se la pasa), los recuerda por ruta de worktree hasta que la caché se invalida, cosa que la app hace cada vez que un proyecto recarga su lista de worktrees. El CLI no usa caché.

### `PrepareWorktreeEnvironmentUseCase` / `WriteEnvironmentFileUseCase`

`PrepareWorktreeEnvironmentUseCase` es el `WorktreeEnvironmentPreparer` que ejecutan la creación y el aprovisionamiento una vez copiados los archivos: resuelve el entorno del worktree reservando y, cuando la declaración tiene `envFile`, llama a `WriteEnvironmentFileUseCase`. Este escribe las variables en formato dotenv en la ruta declarada, negándose a escribir en una ruta que queda fuera del worktree (`EnvironmentFileOutsideWorktree`) o sobre un fichero que Git versiona (`git ls-files --error-unmatch`, `EnvironmentFileTracked`) y devolviendo los errores de escritura como `EnvironmentFileWriteFailed`. `WorktreeEnvironmentPreparer.NONE` no hace nada, para los tests que no prueban el entorno.

### Casos de uso del simulador de iOS

La aportación del provider viene de `MachineProviderContributions`, que la app y el CLI inyectan en `ResolveWorktreeEnvironmentUseCase`: la del emulador más, en macOS y en repositorios que declaran `ios-simulator`, una petición de recurso cuyo `create` elige el tipo de dispositivo y el runtime con `chooseSimulator` (esta operación, los `SimulatorSettings` del proyecto, las preferencias del repositorio; `NoIosRuntime` si no hay ninguno) y ejecuta `simctl create` con `worktreeSimulatorName`, cuyo `exists` comprueba la lista de dispositivos y cuyo `discard` lo borra. `requesting(settings)` devuelve la misma fábrica con el tipo de dispositivo y el runtime de esta operación.

- `StartWorktreeSimulatorUseCase(config, worktreePath, requested)` resuelve el entorno con un resolvedor que crea (así el simulador existe), lo arranca salvo que ya esté arrancado y consulta `devices()` cada segundo hasta que está arrancado; a los 120 s falla con `SimulatorBootTimeout` y lo deja arrancando.
- `StopWorktreeSimulatorUseCase` lee el UDID registrado sin crear nada y apaga el simulador si está en marcha; si no hay ninguno registrado no hay nada que parar.
- `ReadSimulatorStatesUseCase` asocia cada worktree con simulador registrado y existente a su UDID, nombre y estado, con una sola llamada a `devices()`.
- `DeleteSimulatorsUseCase` borra los simuladores indicados que sigan existiendo (apagándolos antes), y `orphans(config)` lista los simuladores con el prefijo `ShogunAi · ` cuyo UDID no tiene registrado ningún worktree de la máquina (`recordedOnMachine`). Los simuladores sin el prefijo nunca se tocan.

Todos fallan con `IosSimulatorProviderNotDeclared` o `IosSimulatorsNotSupported` (o no devuelven nada, en el caso de los estados y los huérfanos) cuando el repositorio no declara el provider o el sistema no es macOS, antes de ejecutar `xcrun`.

### Casos de uso del emulador

Los tres fallan con `EmulatorProviderNotDeclared` (o no devuelven nada, en el caso de los estados) cuando el repositorio no declara el provider de emulador, antes de tocar el SDK.

- `StartWorktreeEmulatorUseCase(config, worktreePath, avd?, noWindow?)` resuelve el entorno del worktree (reservando su puerto de emulador si hace falta), vuelve enseguida si su serial ya está en marcha y, si no, arranca la AVD de `chooseAvd` (`NoAvdAvailable` si no hay ninguna) con el ajuste de ventana de la llamada o del proyecto, salvo que ya esté arrancando, y consulta `deviceStates` cada 2 s hasta que está en marcha. Si entretanto termina el proceso del emulador que arrancó, falla en el acto con `EmulatorExitedEarly` y el final del log del emulador; a los 180 s falla con `EmulatorBootTimeout` y deja el emulador arrancando. La pausa se inyecta, así que los tests no esperan.
- `StopWorktreeEmulatorUseCase(config, worktreePath)` lee el puerto de emulador del worktree sin reservar; si no hay ninguno reservado no hay nada que parar. `stopSerials` para los seriales indicados que estén en marcha, y la limpieza lo usa con seriales leídos antes de eliminar los worktrees.
- `ReadEmulatorStatesUseCase(config, worktrees)` asocia cada worktree con puerto de emulador reservado a su serial y su estado, a partir de las reservas guardadas y una sola llamada a `deviceStates`; los worktrees sin reserva no aparecen.

### Casos de uso de los daemons de Gradle

Se usan cuando el repositorio declara el provider de Gradle (y desde `shogun gradle` en cualquier sitio):

- `ListGradleDaemonsUseCase` lista los daemons de `GradleDaemonSource` y, dados los worktrees del repositorio, atribuye cada daemon de Gradle por su último directorio de build: `RemovedWorktree` si esa carpeta ya no existe; si no, el worktree con la ruta más larga que la contiene (la misma regla que con los agentes en ejecución, así que ganan los worktrees anidados); y si no, `OtherProject`.
- `StopGradleDaemonsUseCase` para los pids indicados, o todos los daemons. Antes vuelve a listarlos, así que decide con su estado actual: un daemon de Gradle solo se para si está `Idle`, y uno de Kotlin solo si ningún daemon de Gradle está `Busy` o `Unknown`. Para con `kill <pid>` (SIGTERM, para que la JVM ejecute los shutdown hooks de Gradle). Un fallo no detiene el resto.
- `idleDaemonsOfRemovedWorktrees` elige, de un listado tomado antes de una limpieza, los daemons de Gradle ociosos cuya última build fue en uno de los worktrees eliminados. La limpieza se los pasa a `StopGradleDaemonsUseCase`, cuya comprobación en el momento sigue conservando un daemon que se haya puesto ocupado.
- `CheckGradleBuildCacheUseCase` indica si `org.gradle.caching=true` está en el `gradle.properties` del repositorio o en `$GRADLE_USER_HOME/gradle.properties`. Con la caché de build local de Gradle activada, un worktree nuevo reutiliza las salidas de tareas compiladas en otros worktrees, porque las claves de la caché no dependen de la ruta del checkout.
- `gradleSettingsContribution` es la contribución del provider al entorno cuando `GradleProvider.hasSettings`: una sola variable, `GRADLE_OPTS`, que `gradleOpts` construye con `-Dorg.gradle.jvmargs="…"`, `-Dorg.gradle.daemon.idletimeout=<ms>` y `-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx<tamaño>`, cada una solo si su ajuste está declarado. Las propiedades de sistema de `GRADLE_OPTS` se imponen a `gradle.properties`, también al del usuario. `gradleDaemonArguments` conserva los `org.gradle.jvmargs` efectivos (`effectiveGradleJvmArgs`: el `gradle.properties` del usuario o, si no, el del repositorio base) sustituyendo cada `-Xmx` por el heap declarado, o deja solo el heap cuando no hay argumentos o contienen comillas dobles. El valor no depende del worktree, así que los daemons de todos los worktrees son compatibles y se reutilizan entre ellos; un `GRADLE_OPTS` declarado en `env` gana, como cualquier variable de un provider.

### Casos de uso de Xcode

Se usan en macOS cuando el repositorio declara el provider de Xcode:

- `ListDerivedDataUseCase(worktrees, includeCompilationCache, measure)` atribuye cada carpeta de `DerivedDataSource`: un workspace que no existe es `UnavailableVolume` si su volumen no está montado y, si no, `Orphaned`; uno que existe pertenece al worktree de ruta más larga que lo contiene (`owningWorktree`, la misma regla que los agentes en ejecución y los daemons de Gradle) y, si no, a `OtherProject`. `measure` indica a qué carpetas se les mide el tamaño: `NONE`, `ORPHANS` o `ALL`. Las huérfanas son de toda la máquina: cuando su proyecto ya no existe, nada puede usar la carpeta, la creara la herramienta que la creara.
- `DeleteOrphanedDerivedDataUseCase` vuelve a listar las carpetas, se queda con las que siguen huérfanas, comprueba cada workspace otra vez justo antes de borrar (un proyecto restaurado entretanto conserva su carpeta), mide cada huérfana que aún no tiene tamaño para que `bytesFreed` sea correcto y devuelve un `DerivedDataDeletion`; un fallo no detiene el resto. La carpeta de un worktree eliminado es huérfana por definición, así que la limpieza solo ejecuta este caso de uso después de eliminar.
- `PrepareCocoaPodsUseCase` (un `CocoaPodsPreparer`) se ejecuta desde `PrepareWorktreeEnvironmentUseCase` después del fichero de entorno, así que lo reciben todos los flujos de creación y provisión, y devuelve un `PodsPreparation` (`cloned`, `installNeeded` con un `PodsInstallReason`, `error`) que `PreparedWorktree` lleva a `Worktree.pods` y `ProvisionResult.pods`. Con `XcodeProvider.cocoaPods` en macOS, lista los `Podfile.lock` versionados con `git ls-files` y, para cada carpeta sin `Pods`, clona el `Pods` de la base con `cp -c -R` cuando su `Manifest.lock` tiene el mismo contenido; nunca lanza excepciones, así que los pods no pueden deshacer un worktree.
- `xcodeCompilationCacheContribution` es la contribución al entorno del provider cuando `XcodeProvider.compilationCache` está activo, en macOS (`MachineProviderContributions`): un recurso `xcode-compilation-cache` cuyo `create` escribe `~/.shogunai/xcode/compilation-cache.xcconfig` (`COMPILATION_CACHE_ENABLE_CACHING` y prefix mapping de Swift y Clang, cinco líneas `KEY = YES`) y cuyo `exists` comprueba que el fichero tiene ese contenido, más la variable `XCODE_XCCONFIG_FILE=<ruta>`. Al ser un recurso, solo se escribe al resolver con reserva y se vuelve a crear si se borra; el fichero es el mismo para todos los repositorios de la máquina. Un `XCODE_XCCONFIG_FILE` declarado en `env` gana.

### Casos de uso de la comprobación de actualizaciones

- `CheckForUpdateUseCase(mode)` lee las preferencias y, en modo `AUTOMATIC`, devuelve `NotDue` cuando la comprobación automática está desactivada o la versión en ejecución es de desarrollo (`0.0.0`). Si no, pide a `ReleaseSource` las releases publicadas y toma la versión más alta como la última (una lista vacía es `UpdateCheckFailed`): una versión que no es más nueva que la que se ejecuta o, en modo automático, que no es más nueva que la versión saltada, es `UpToDate`; una más nueva es `Available` con el canal de instalación, su `UpdateAction` y las notas de cada versión más nueva que la que se ejecuta, de la más nueva a la más antigua. Una lectura fallida se devuelve como fallo; la app de escritorio la ignora en modo automático y la muestra en modo manual.
- `DetectInstallChannelUseCase` indica cómo se instaló la app en ejecución, y solo se llama cuando hay una versión más nueva: en macOS `HOMEBREW` cuando existe una carpeta `shogunai` en `/opt/homebrew/Caskroom` o `/usr/local/Caskroom` y, si no, `DMG`; en Windows `SCOOP` cuando la app se ejecuta desde `\scoop\apps\shogunai\`, si no `WINGET` cuando `winget list --id LuxionServer.ShogunAi --exact` la lista (límite de 10 segundos), y si no `MSI`; en Linux `DEB` cuando la app vive bajo `/opt/shogunai` y, si no, `UNKNOWN`.
