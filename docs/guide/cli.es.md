# Referencia de la línea de comandos

`shogun` ejecuta las operaciones de worktrees de ShogunAi desde un terminal, un script o un hook de agente. Trabaja sobre el repositorio Git que contiene el directorio actual (el checkout principal, una subcarpeta o cualquiera de sus worktrees), y usa el proyecto de ese repositorio en la app de escritorio si lo hay: su carpeta de worktrees, sus archivos locales y su rama principal. Consulta [Instalación](installation.md#el-comando-shogun) para tenerlo en el `PATH`.

```bash
shogun new TASK-123                # worktree nuevo en feature/TASK-123, provisionado; imprime su ruta
shogun new --branch hotfix/login   # worktree para una rama existente
shogun provision ../otro-worktree  # copia los archivos declarados en un worktree creado por otra herramienta
shogun status                      # todos los worktrees con sus cambios, divergencia y si están mergeados
shogun status --json               # lo mismo, como un documento JSON para scripts
shogun clean --yes                 # elimina los worktrees mergeados o sin carpeta (sin --yes solo los lista)
eval "$(shogun env)"               # carga las variables de este worktree desde "env" en .shogunai.json
```

La salida es texto plano en inglés; los scripts deben leer la línea única de `new` y `status --json`.

## Códigos de salida

| Código | Significado |
|---|---|
| `0` | Éxito. |
| `1` | La operación falló; el motivo va a la salida de error. |
| `2` | Uso incorrecto: comando u opción desconocidos, argumento que falta, o entrada de un hook que no es el JSON esperado. |
| `3` | `provision --check` encontró diferencias entre el worktree y el repositorio base. |

## Worktrees

### `new`

```text
shogun new <task-id> [--fix] [--from <ref>] [--worktrees-root <dir>]
shogun new --branch <branch> [--worktrees-root <dir>]
```

Crea un worktree, copia en él los archivos declarados, prepara su [entorno](../agents.md#entorno-por-worktree) e imprime su ruta absoluta, sola en la salida estándar, así que `cd "$(shogun new TASK-123)"` funciona.

- Con un id de tarea, el worktree recibe una rama nueva `feature/<task-id>`, o `fix/<task-id>` con `--fix`. La rama parte del HEAD del worktree donde ejecutas el comando, o de `--from <ref>` (una rama, tag o commit; una referencia desconocida falla antes de crear nada).
- Con `--branch`, el worktree hace checkout de una rama local existente. `--fix` y `--from` no se aceptan con ella.
- `--worktrees-root <dir>` indica dónde va el worktree. Es obligatorio cuando el repositorio no tiene proyecto en la app de escritorio; las rutas relativas se resuelven desde el directorio actual.

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
| `changes=N` | Cambios sin commitear. |
| `ahead=N behind=N` | Divergencia respecto a su upstream. |
| `merged` | Mergeado en la rama principal. |
| `folder-missing` | Su carpeta se borró a mano. |
| `status-unavailable` | No se pudo leer su estado. |

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
| `status` | `null` cuando falta la carpeta o no se pudo leer su estado. |
| `status.uncommittedChanges`, `status.merged` | Cambios sin commitear; mergeado en la rama principal. |
| `status.upstream`, `status.mainBranch` | `{ "ahead": n, "behind": n }` respecto al upstream y a la rama principal; `null` si no hay. |
| `status.lastCommitAt` | Hora del último commit en ISO-8601 UTC, o `null`. |

Todos los campos están siempre presentes, con `null` cuando no aplican. Los nombres de los campos son un contrato: las versiones futuras solo añaden campos y nunca renombran, eliminan ni cambian el tipo de uno.

### `clean`

```text
shogun clean [--yes|-y] [--delete-branches] [--stop-gradle-daemons] [--stop-emulators]
             [--delete-simulators] [--delete-derived-data]
```

Selecciona los mismos worktrees que "Limpiar" en la app de escritorio: ni el principal ni los bloqueados, y o bien mergeados sin cambios sin commitear o bien sin carpeta. Sin `--yes` solo lista lo que haría (`would remove …`); con `--yes` lo hace (`removed …`). Si no hay nada que hacer imprime `Nothing to clean up.`

| Opción | Además… |
|---|---|
| `--delete-branches` | Borra las ramas de los worktrees eliminados si están mergeadas (`kept branch …` si no). |
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

## Hooks de agentes

Estos comandos están pensados para que los llamen agentes y herramientas, no para escribirlos a mano. Cómo configurarlos está en [Uso con agentes](../agents.md).

| Comando | Lo llama |
|---|---|
| `shogun hook worktree-create` | El hook `WorktreeCreate` de Claude Code. Lee `name` y `cwd` de la entrada estándar, crea (o reutiliza) un worktree provisionado en `feature/<name>` que parte del HEAD del worktree de `cwd`, e imprime su ruta. Si falla un comando posterior a la creación, imprime igualmente la ruta como última línea y sale con 0, con el fallo en la salida de error, para que Claude Code use el worktree. Los comandos posteriores a la creación sin aprobar se omiten con una nota por la salida de error; nunca pregunta. |
| `shogun hook worktree-remove` | El hook `WorktreeRemove` de Claude Code. Elimina el worktree de `worktree_path`, borra su rama solo si está mergeada y cierra su espacio de Herdr. |
| `shogun hook session-start` | El hook `SessionStart` de Claude Code. Añade el entorno del worktree a `CLAUDE_ENV_FILE` y le dice a Claude qué valores usa. |
| `shogun hook herdr` | El plugin de ShogunAi para Herdr, en `worktree.created` (provisiona el worktree, omitiendo con una nota los comandos posteriores a la creación sin aprobar) y `worktree.removed` (borra su rama si está mergeada). Lee `HERDR_PLUGIN_EVENT`, `HERDR_PLUGIN_EVENT_JSON` y `HERDR_BIN_PATH`. |

## Ayuda y versión

| Comando | Imprime |
|---|---|
| `shogun help`, `-h`, `--help` | El resumen de uso. |
| `shogun version`, `-v`, `--version` | `shogun <versión>`, la misma versión que la app. |
