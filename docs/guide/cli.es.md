# Referencia de la línea de comandos

`shogun` ejecuta las operaciones de worktrees de ShogunAi desde un terminal, un script o un hook de agente. Trabaja sobre el repositorio Git que contiene el directorio actual (el checkout principal, una subcarpeta o cualquiera de sus worktrees), y usa el proyecto de ese repositorio en la app de escritorio si lo hay: su carpeta de worktrees, sus archivos locales y su rama principal. Consulta [Instalación](installation.md#el-comando-shogun) para tenerlo en el `PATH`.

```bash
shogun new TASK-123                # worktree nuevo en feature/TASK-123, provisionado; imprime su ruta
shogun new --branch hotfix/login   # worktree para una rama existente
shogun provision ../otro-worktree  # copia los archivos declarados en un worktree creado por otra herramienta
shogun status                      # todos los worktrees con sus cambios, divergencia y si están mergeados
shogun status --json               # lo mismo, como un documento JSON para scripts
shogun remove TASK-123             # elimina un worktree y para su emulador y simulador
shogun repair                      # re-enlaza los worktrees tras mover el repositorio
shogun clean --yes                 # elimina los worktrees mergeados o sin carpeta (sin --yes solo los lista)
eval "$(shogun env)"               # carga las variables de este worktree desde "env" en .shogunai.json
```

La salida es texto plano en inglés; los scripts deben usar `--json`, que aceptan todos los comandos que informan de un resultado (ver [Salida JSON](#salida-json)).

## Códigos de salida

| Código | Significado |
|---|---|
| `0` | Éxito. |
| `1` | La operación falló; el motivo va a la salida de error. Los fallos que un comando no trata de forma específica se imprimen en una sola línea que empieza por `shogun:`, nunca como un stack trace. |
| `2` | Uso incorrecto: comando u opción desconocidos, argumento que falta, o entrada de un hook que no es el JSON esperado. |
| `3` | `provision --check` encontró diferencias entre el worktree y el repositorio base. |

## Worktrees

### `new`

```text
shogun new <task-id> [--fix] [--from <ref>] [--fetch] [--worktrees-root <dir>]
shogun new --branch <branch> [--fetch] [--worktrees-root <dir>]
```

Crea un worktree, copia en él los archivos declarados, prepara su [entorno](../agents.md#entorno-por-worktree) e imprime su ruta absoluta, sola en la salida estándar, así que `cd "$(shogun new TASK-123)"` funciona.

- Con un id de tarea, el worktree recibe una rama nueva `feature/<task-id>`, o `fix/<task-id>` con `--fix`. La rama parte del HEAD del worktree donde ejecutas el comando, o de `--from <ref>` (una rama, tag o commit; una referencia desconocida falla antes de crear nada).
- Con `--branch`, el worktree hace checkout de una rama existente. `--fix` y `--from` no se aceptan con ella. Una rama que existe solo en `origin`, indicada como `<nombre>` u `origin/<nombre>`, recibe una rama local con ese nombre que la sigue; una rama local con ese nombre tiene prioridad.
- `--fetch` ejecuta antes `git fetch --prune origin`, para que `--from origin/develop` o una rama remota estén al día. Nunca espera credenciales: si el fetch falla, se imprime el error de Git, no se crea nada y el código de salida es 1.
- `--worktrees-root <dir>` indica dónde va el worktree. Es obligatorio cuando el repositorio no tiene proyecto en la app de escritorio; las rutas relativas se resuelven desde el directorio actual.
- Un id de tarea con `/` (`team/TASK-1`) conserva la `/` en la rama (`feature/team/TASK-1`) y se convierte en una sola carpeta, `team-TASK-1`.
- Si la creación del worktree falla después de que Git haya creado el worktree o su rama nueva, se eliminan ambos, así que volver a intentarlo con el mismo id de tarea funciona. Una rama que ya existía nunca se borra.
- Funciona en submódulos y en repositorios cuya carpeta de Git está separada del árbol de trabajo. En un repositorio bare falla diciendo que los repositorios bare todavía no están soportados, y en un repositorio sin commits falla pidiendo un primer commit.
- Cuando Git falla por una causa que reconoce (la rama ya existe, hay un archivo de bloqueo, permiso denegado…), una línea `hint:` tras el mensaje de Git lo explica y sugiere qué hacer, por ejemplo `shogun new --branch feature/TASK-123`.

### `provision`

```text
shogun provision [<path>]
shogun provision --check [<path>]
```

Copia los archivos locales del proyecto y los archivos `copy` declarados del repositorio en un worktree existente (por defecto, el directorio actual), enlaza sus archivos `link`, reserva sus puertos, escribe su `envFile` y ejecuta los comandos de `hooks.postCreate`. Úsalo con worktrees creados por otra herramienta. Imprime una ruta por línea: los archivos enlazados terminan en `(linked)`, y los declarados en `link` que se copiaron porque no hay enlaces simbólicos terminan en `(copied: symbolic links aren't available)`.

Con `--check` no se cambia nada: compara los archivos que daría el provisionado al worktree con los del repositorio base e imprime una línea por diferencia, `<ruta>: missing`, `<ruta>: differs` (una copia cuyo contenido cambió en el repositorio base) o `<ruta>: not linked` (un archivo de `link` que no es un enlace simbólico al del repositorio base). Sale con 0 si todo coincide, 3 si hay alguna diferencia y 1 si hay un error.

Los comandos posteriores a la creación solo se ejecutan cuando están aprobados en este equipo (ver [Aprobar los comandos posteriores a la creación](configuration.md#aprobar-los-comandos-posteriores-a-la-creacion)). En un terminal, `shogun new` y `shogun provision` imprimen los comandos y preguntan `Run these commands? [y/N]` por la salida de error; solo un sí explícito los ejecuta y aprueba. Sin terminal omiten los comandos, imprimen `shogun: skipped N post-creation commands not approved on this machine; run 'shogun trust' in <repo> to review them` por la salida de error y terminan bien.

#### Cuando falla un comando posterior a la creación

El worktree se conserva. `shogun new` imprime su ruta por la salida estándar, el fallo (comando, código de salida y salida de error) por la salida de error, y sale con 1. `shogun provision` imprime el fallo por la salida de error y sale con 1.

### `trust`

```text
shogun trust [<path>] [--yes] [--revoke]
```

Imprime los comandos posteriores a la creación que declara el repositorio, uno por línea, y si están aprobados en este equipo. En un terminal pregunta si aprobarlos; `--yes` aprueba sin preguntar, para que lo puedan hacer scripts y CI, y `--revoke` quita la aprobación del repositorio. Si el repositorio no declara comandos, lo dice. Código de salida 0 salvo que algo falle.

### `status`

Lista todos los worktrees del repositorio, uno por línea: ruta, rama (o `(detached)`) y después detalles:

| Detalle | Significado |
|---|---|
| `main` | El checkout principal. |
| `origin=shogunai\|claude-code\|herdr\|other` | Dónde se creó el worktree. |
| `locked` | Lo retiene una sesión de agente. |
| `agents=claude(blocked),codex` | Agentes en ejecución en él, con el estado que informa Herdr (`working`, `blocked`, `done`, `idle`) cuando está en marcha. |
| `changes=N` | Cambios sin commitear. |
| `ahead=N behind=N` | Divergencia respecto a su upstream. |
| `merged` | Mergeado en la rama principal. |
| `not-started` | La rama nunca tuvo commits propios (se muestra en lugar de `merged`). |
| `folder-missing` | Su carpeta se borró a mano. |
| `status-unavailable (motivo)` | No se pudo leer su estado, con el motivo de Git. |

#### `status --json`

```text
shogun status [--json]
```

Imprime un único objeto JSON compacto en una línea, en vez de las líneas de texto. Los errores siguen yendo a la salida de error como texto, sin nada en la salida estándar. Por ejemplo (con saltos de línea aquí):

```json
{
  "repository": "/home/dev/projects/main-repo",
  "mainBranch": "main",
  "worktrees": [
    {
      "path": "/home/dev/projects/main-repo",
      "branch": "main",
      "head": "4f1c2e9a0b7d3c58e1f6a2b94d07c3e5a8f1b620",
      "main": true,
      "locked": false,
      "folderMissing": false,
      "origin": null,
      "agents": [{ "name": "claude", "herdrState": "blocked" }],
      "status": {
        "uncommittedChanges": 0,
        "upstream": { "ahead": 0, "behind": 0 },
        "mainBranch": null,
        "merged": false,
        "lastCommitAt": "2026-10-18T09:12:44Z"
      }
    }
  ]
}
```

| Campo | Significado |
|---|---|
| `repository`, `mainBranch` | La ruta del repositorio y su rama principal resuelta (`null` si no se puede resolver). |
| `branch`, `head` | La rama activa (`null` si está detached) y el commit. |
| `main`, `locked`, `folderMissing` | El checkout principal, retenido por una sesión de agente, carpeta borrada a mano. |
| `origin` | `shogunai`, `claude-code`, `herdr` u `other`; `null` para el checkout principal. |
| `agents` | Los agentes en ejecución en él: `name` y `herdrState` (`working`, `blocked`, `done`, `idle`; `null` cuando Herdr no está en marcha o no informa de uno). |
| `status` | `null` cuando falta la carpeta o no se pudo leer su estado. |
| `statusProblem` | Solo aparece cuando no se pudo leer el estado: el motivo de Git. |
| `status.uncommittedChanges`, `status.merged` | Cambios sin commitear; mergeado en la rama principal. |
| `status.upstream`, `status.mainBranch` | `{ "ahead": n, "behind": n }` respecto al upstream y a la rama principal; `null` si no hay. |
| `status.lastCommitAt` | Hora del último commit en ISO-8601 UTC, o `null`. |

Todos los campos están siempre presentes, con `null` cuando no aplican. Los nombres de los campos son un contrato: las versiones futuras solo añaden campos y nunca renombran, eliminan ni cambian el tipo de uno.

### `remove`

```text
shogun remove <id de tarea | ruta | rama> [--force|-f] [--delete-branch]
```

Elimina un worktree enlazado, igual que "Eliminar" en la app de escritorio. El objetivo es, por este orden, la ruta de un worktree, el nombre exacto de su rama o un id de tarea (el worktree en `<carpeta de worktrees>/<id de tarea>`); si no hay coincidencia, o hay más de una, no elimina nada y falla (listando los candidatos). El worktree principal nunca se elimina.

Sin `--force` rechaza un worktree con cambios sin commitear o sin seguimiento, o donde se ejecuta un agente (`shogun: claude is running in <ruta>; use --force to remove it anyway`), y termina con `1`. Con `--force` lo elimina igualmente. La rama se conserva salvo que pases `--delete-branch`, que la borra solo si está mergeada en la rama principal del proyecto (`deleted branch …`, o `kept branch … (not merged into the main branch)`).

Tras eliminarlo, el comando para lo que los providers del repositorio mantienen para ese worktree: su emulador de Android (`stopped emulator …`), su simulador de iOS (`stopped simulator …`) y los daemons de Gradle ociosos cuya última build fue en él (`stopped Gradle daemon …`). Un recurso que no se puede parar se indica en la salida de error y el código de salida es `1`; para entonces el worktree ya está eliminado. También se cierra su espacio de trabajo de Herdr.

### `repair`

```text
shogun repair
```

Vuelve a enlazar los worktrees tras mover el repositorio o la carpeta de worktrees, como hace la app de escritorio al cambiar las carpetas de un proyecto: los worktrees que Git aún lista en su ubicación antigua se buscan en la carpeta de worktrees del proyecto, por el nombre de su carpeta, y se re-enlazan (`repaired <ruta>`). Si no hay nada que re-enlazar imprime `Nothing to repair.` Necesita el proyecto del repositorio en la app de escritorio, porque ahí se guarda la carpeta de worktrees; sin él, termina con `2`.

### `clean`

```text
shogun clean [--yes|-y] [--delete-branches] [--stop-gradle-daemons] [--stop-emulators]
             [--delete-simulators] [--delete-derived-data]
```

Selecciona los mismos worktrees que "Limpiar" en la app de escritorio: ni el principal, ni los bloqueados, ni uno donde se ejecute un agente, y o bien mergeados sin cambios sin commitear o bien sin carpeta. Sin `--yes` solo lista lo que haría (`would remove …`) y termina con el comando que hay que ejecutar, repitiendo las opciones que diste y nombrando solo lo que se eliminaría; con `--yes` lo hace (`removed …`). Si no hay nada que hacer imprime `Nothing to clean up.`

Un worktree donde se ejecuta un agente se lista como `kept <ruta> (claude is running)`; si en la plataforma no se pueden comprobar los agentes, una nota en la salida de error lo dice. Un worktree cuyo estado no se pudo leer se lista como `skipped <ruta> (status unavailable: <motivo>)`.

| Opción | Además… |
|---|---|
| `--delete-branches` | Borra las ramas de los worktrees eliminados si están mergeadas en la rama principal del proyecto (`kept branch …` si no). |
| `--stop-gradle-daemons` | Para los daemons de Gradle ociosos cuya última build fue en un worktree eliminado. |
| `--stop-emulators` | Para los emuladores de Android de los worktrees eliminados. |
| `--delete-simulators` | Borra los simuladores de iOS de los worktrees eliminados y los simuladores de ShogunAi huérfanos. |
| `--delete-derived-data` | Borra los DerivedData de Xcode huérfanos, incluidas las carpetas de los worktrees eliminados. |

También se cierran los espacios de trabajo de Herdr de los worktrees eliminados (`would close Herdr workspace of …` en el listado). Un daemon, emulador o espacio de Herdr que no se puede parar o cerrar se indica en la salida de error sin cambiar el código de salida.

## Entorno

### `env`

```text
shogun env [<path>] [--format sh|dotenv|json]
```

Imprime el entorno del worktree que contiene la ruta (o el directorio actual), a partir de `env` en `.shogunai.json` y de los providers, reservando sus puertos la primera vez. `sh` (por defecto) imprime líneas `export NOMBRE='valor'` para `eval "$(shogun env)"`; `dotenv` y `json` sirven para otras herramientas. Un entorno vacío no imprime nada.

## Providers

Estos comandos actúan sobre los [providers](providers.md) que un repositorio declara en `.shogunai.json`. Cuando un comando necesita un provider que el repositorio no declara, lo dice y sale con `1`.

### `gradle`

```text
shogun gradle
shogun gradle stop [<pid>...]
```

`gradle` lista los daemons de Gradle y Kotlin de la máquina: pid, tipo y versión, `memory=`, `state=busy|idle|unknown` (con `idle-for=`) y `worktree=<path>|removed|other-project`, y después el total (`No Gradle or Kotlin daemons running.` si no hay ninguno). Funciona también fuera de un repositorio. Dentro de un repositorio Gradle sin la caché de build de Gradle, termina con un aviso.

`gradle stop` para todos los daemons ociosos, o solo los ids de proceso indicados; los ocupados y los de estado desconocido se conservan (`kept <pid> … (<motivo>)`). Un pid que no es un daemon sale con `1`.

### `emulator`

```text
shogun emulator start|stop|status [<path>] [--avd <name>] [--no-window]
```

Arranca (y espera a), para o muestra el emulador de Android del worktree. `--avd` y `--no-window` solo se aplican a `start` y sustituyen los ajustes del proyecto en ese arranque. `status` imprime `emulator-5556 running|booting|stopped`.

### `simulator`

```text
shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]
shogun simulator gradle-init install|uninstall [--yes|-y]
```

Crea si hace falta, arranca (y espera a) y abre, apaga o muestra el simulador de iOS del worktree (solo macOS). `--device` y `--runtime` solo se aplican cuando `start` crea el simulador. En un repositorio Kotlin Multiplatform, `status` dice además si los tests de KMP usan el simulador.

`gradle-init install` instala un init script de Gradle para toda la máquina que hace que los tests de simulador KMP de todos los proyectos usen el simulador del worktree; muestra el script y pregunta `[y/N]` salvo con `--yes`. `uninstall` lo elimina.

### `xcode`

```text
shogun xcode
shogun xcode clean
```

`xcode` lista las carpetas de DerivedData de los worktrees del repositorio con su tamaño, las carpetas huérfanas de la máquina (`orphans: N (tamaño)`) y, cuando se declara la caché de compilación, su tamaño. `xcode clean` borra las carpetas huérfanas (`deleted <path>`, `freed <tamaño>`, o `Nothing to delete.`). Sin el provider de Xcode, o fuera de macOS, dice que el provider no está activo y sale con `0`.

## Salida JSON

Además de `status --json`, todos los comandos que informan de un resultado aceptan `--json`: `new`, `provision`, `trust`, `remove`, `repair`, `clean`, `gradle`, `gradle stop`, `xcode`, `xcode clean`, `emulator` y `simulator` (no `simulator gradle-init`, `env`, `hook`, `help` ni `--version`; en ellos es una opción desconocida y sale con `2`). La opción puede ir en cualquier posición tras el comando.

Las reglas son las mismas para todos:

- Un único objeto JSON compacto en una línea por la salida estándar, en vez del texto, con el mismo código de salida que la salida de texto.
- Un error anterior al resultado se imprime como texto por la salida de error, sin nada en la salida estándar.
- Nunca pregunta. Los comandos posteriores a la creación que no están aprobados en esta máquina se omiten y se listan (`shogun trust` los aprueba), y `trust` solo informa salvo que se indique `--yes`.
- Todos los campos documentados están siempre presentes, con `null` cuando no aplican, listas vacías en vez de ausentes y enumeraciones en minúsculas. Las rutas son absolutas. Las versiones posteriores solo añaden campos, nunca renombran, quitan ni cambian el tipo de uno.

| Comando | Objeto |
|---|---|
| `new` | `path`, `branch`, `created` (siempre `true`), `skippedPostCreateCommands` (lista de listas argv) y `postCreateFailure`: `null`, o `{ command, exitCode, stderr }` si falló un comando posterior a la creación. En ese caso el objeto se imprime igualmente, porque el worktree existe, y el código de salida es `1`. |
| `provision` | `path`, `copied`, `linked` y `copiedInsteadOfLinking` (rutas de archivos), `skippedPostCreateCommands` y `postCreateFailure`, como en `new`. Si falló un comando, las tres listas de archivos van vacías. |
| `provision --check` | `path` y `drift`: lista de `{ path, state }` con `state` `missing`, `differs` o `not-linked`. El código de salida es `3` si `drift` no está vacío. |
| `trust` | `repository`, `commands` (listas argv), `approved` y `changed` (esta ejecución lo aprobó o revocó). Un repositorio sin comandos posteriores a la creación es `approved: true` y sin comandos. `--revoke` informa `approved: false` si había comandos. |
| `remove` | `path`, `branch` (la del worktree, o `null` si está desacoplado), `branchDeleted` (el nombre, o `null` si se conservó o no se pasó `--delete-branch`), `providers` (`emulatorsStopped` con los serial, `simulatorsStopped` con los udid y `gradleDaemonsStopped` con los pid) y `failures` (mensajes de lo que no se pudo detener, código de salida `1`). |
| `repair` | `repaired`: las rutas de los worktrees re-enlazados. |
| `clean` | `dryRun`; `candidates`: `{ path, branch, reason }` con `reason` `merged` o `folder-missing`; `keptForAgents`: `{ path, agents }`; `skipped`: `{ path, reason }` (estado no disponible); `removed`; `deletedBranches`; `failures`: `{ path, message }`; y `providers`: `gradleDaemonsStopped`, `emulatorsStopped`, `simulatorsDeleted` y `derivedDataDeleted`. Sin `--yes` (`dryRun: true`) `removed`, `deletedBranches` y las listas de `providers` van vacías. |
| `gradle` | `daemons`: `{ pid, kind, version, memoryBytes, state, idleSince, worktree }` con `kind` `gradle` o `kotlin`, `state` `busy`, `idle`, `unknown` o `untracked`, `idleSince` en ISO-8601 UTC (solo si está inactivo) y `worktree` una ruta, `removed`, `other-project` o `null` fuera de un repositorio; y `totalMemoryBytes`. |
| `gradle stop` | `stopped` (pids), `kept`: `{ pid, kind, reason }` con `reason` `busy`, `state-unknown` o `build-running`, `notDaemons` (pids) y `failures`: `{ pid, message }`. El código de salida es `1` si alguna de las dos últimas no está vacía. |
| `xcode` | `active` (`false` si el provider no está activo, con el resto vacío), `folders`: `{ path, sizeBytes, worktree }`, `orphans`: `{ path, sizeBytes }`, `unavailableVolumeFolders` (un número) y `compilationCacheBytes` (`null` salvo que esté declarada). |
| `xcode clean` | `active`, `deleted` (rutas), `freedBytes` y `failures`: `{ path, message }`. |
| `emulator start\|stop\|status` | `worktree`, `serial` y `state` (`running`, `booting` o `stopped`). `serial` y `state` son `null` si el worktree aún no tiene puerto de emulador reservado. |
| `simulator start\|stop\|status` | `worktree`, `udid`, `name` y `state` (`shutdown`, `booting` o `booted`), `null` si el worktree aún no tiene simulador. El aviso sobre los tests KMP no forma parte. |

```bash
shogun clean --json | jq -r '.candidates[].path'
shogun emulator status --json | jq -r '.serial'
shogun provision --check --json | jq -e '.drift | length == 0'
```

## Hooks de agentes

Estos comandos están pensados para que los llamen agentes y herramientas, no para escribirlos a mano. Cómo configurarlos está en [Uso con agentes](../agents.md).

| Comando | Lo llama |
|---|---|
| `shogun hook worktree-create` | El hook `WorktreeCreate` de Claude Code. Lee `name` y `cwd` de la entrada estándar, crea (o reutiliza) un worktree provisionado en `feature/<name>` que parte del HEAD del worktree de `cwd`, e imprime su ruta. Si falla un comando posterior a la creación, imprime igualmente la ruta como última línea y sale con 0, con el fallo en la salida de error, para que Claude Code use el worktree. Los comandos posteriores a la creación sin aprobar se omiten con una nota por la salida de error; nunca pregunta. |
| `shogun hook worktree-remove` | El hook `WorktreeRemove` de Claude Code. Elimina el worktree de `worktree_path`, borra su rama solo si está mergeada en la rama principal del proyecto (una rama mergeada solo en su propio upstream se conserva, con una nota en la salida de error) y cierra su espacio de Herdr. |
| `shogun hook session-start` | El hook `SessionStart` de Claude Code. Añade el entorno del worktree a `CLAUDE_ENV_FILE` y le dice a Claude qué valores usa. |
| `shogun hook herdr` | El plugin de ShogunAi para Herdr, en `worktree.created` (provisiona el worktree, omitiendo con una nota los comandos posteriores a la creación sin aprobar) y `worktree.removed` (borra su rama si está mergeada en la rama principal del proyecto). Lee `HERDR_PLUGIN_EVENT`, `HERDR_PLUGIN_EVENT_JSON` y `HERDR_BIN_PATH`. |

## Ayuda y versión

| Comando | Imprime |
|---|---|
| `shogun help`, `-h`, `--help` | El resumen de uso. |
| `shogun <comando> --help`, `-h` | El uso y las opciones de ese comando, con código de salida 0. |
| `shogun version`, `-v`, `--version` | `shogun <versión>`, la misma versión que la app. |
