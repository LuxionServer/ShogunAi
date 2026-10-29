# Referencia de la línea de comandos

`shogun` ejecuta las operaciones de worktrees de ShogunAi desde un terminal, un script o un hook de agente. Trabaja sobre el repositorio Git que contiene el directorio actual (el checkout principal, una subcarpeta o cualquiera de sus worktrees), y usa el proyecto de ese repositorio en la app de escritorio si lo hay: su carpeta de worktrees, sus archivos locales y su rama principal. Consulta [Instalación](installation.md#el-comando-shogun) para tenerlo en el `PATH`.

```bash
shogun new TASK-123                # worktree nuevo en feature/TASK-123, provisionado; imprime su ruta
shogun new --branch hotfix/login   # worktree para una rama existente
shogun provision ../otro-worktree  # copia los archivos declarados en un worktree creado por otra herramienta
shogun status                      # todos los worktrees con sus cambios, divergencia y si están mergeados
shogun status --json               # lo mismo, como un documento JSON para scripts
shogun base                        # la rama de la que se creó la rama de este worktree, la base de su pull request
shogun remove TASK-123             # elimina un worktree y para su emulador y simulador
shogun repair                      # re-enlaza los worktrees tras mover el repositorio
shogun open TASK-123 --herdr       # abre un worktree en Herdr con su entorno y el agente del proyecto
shogun open TASK-123 --orca        # lo mismo en Orca
shogun clean --yes                 # elimina los worktrees mergeados o sin carpeta (sin --yes solo los lista)
shogun stacks                      # los stacks detectados en el repositorio y los providers que sugieren
shogun multirepo show              # en un multirepo: su nombre, sus repositorios, los de por defecto y su agente
shogun project add                 # registra este repositorio como proyecto de la app de escritorio
shogun app --settings              # abre la app de escritorio en los ajustes del proyecto de este repositorio
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
| `130` | El comando se interrumpió (Ctrl+C o una señal de terminación) y se detuvo limpiamente, por ejemplo saliendo de la cola de un `start --wait`. |

## Worktrees

### `new`

```text
shogun new <task-id> [--fix] [--from <ref>] [--fetch] [--worktrees-root <dir>]
shogun new --branch <branch> [--fetch] [--worktrees-root <dir>]
```

Crea un worktree, copia en él los archivos declarados, prepara su [entorno](../agents.md#entorno-por-worktree) e imprime su ruta absoluta, sola en la salida estándar, así que `cd "$(shogun new TASK-123)"` funciona.

- Con un id de tarea, el worktree recibe una rama nueva `feature/<task-id>`, o `fix/<task-id>` con `--fix`. La rama parte del HEAD del worktree donde ejecutas el comando, o de `--from <ref>` (una rama, tag o commit; una referencia desconocida falla antes de crear nada).
- Con `--branch`, el worktree hace checkout de una rama existente. `--fix` y `--from` no se aceptan con ella. Una rama que existe solo en `origin`, indicada como `<nombre>` u `origin/<nombre>`, recibe una rama local con ese nombre que la sigue; una rama local con ese nombre tiene prioridad.
- Una rama nueva nunca sigue a su punto de partida, así que al hacer push nunca apunta a la rama de la que partió.
- La rama de la que parte queda registrada como la [base](#base) de la rama nueva: la rama del worktree actual, o la que nombra `--from` (`origin/develop` se registra como `develop`). Un tag, un commit o un HEAD separado no registran nada, y `--branch` conserva lo que ya tuviera una rama existente.
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

Copia los archivos locales del proyecto y los archivos `copy` declarados del repositorio en un worktree existente (por defecto, el directorio actual), enlaza sus archivos `link`, reserva sus puertos, escribe su `envFile` y ejecuta los comandos de `hooks.postCreate`. Úsalo con worktrees creados por otra herramienta. Imprime una ruta por línea: los archivos enlazados terminan en `(linked)`, y los declarados en `link` que se copiaron porque no hay enlaces simbólicos terminan en `(copied: symbolic links aren't available)`. Después lista los archivos que borró porque se eliminaron de su carpeta local en el repositorio base, terminados en `(removed: deleted from the base folder)`, y los enlaces simbólicos que omitió dentro de carpetas locales, terminados en `(skipped: symbolic link)`. `shogun new` y el hook `worktree-create` avisan de los enlaces omitidos en la salida de error.

Con `--check` no se cambia nada: compara los archivos que daría el provisionado al worktree con los del repositorio base e imprime una línea por diferencia, `<ruta>: missing`, `<ruta>: differs` (una copia cuyo contenido cambió en el repositorio base) o `<ruta>: not linked` (un archivo de `link` que no es un enlace simbólico al del repositorio base) o `<ruta>: deleted in base` (un archivo copiado de una carpeta local que la carpeta base ya no tiene; el siguiente `shogun provision` lo borra). Sale con 0 si todo coincide, 3 si hay alguna diferencia y 1 si hay un error.

Los comandos posteriores a la creación se ejecutan, y las variables que el repositorio declara en `env` y su `envFile` se aplican, solo cuando el repositorio está aprobado en este equipo (ver [Aprobar un repositorio](configuration.md#aprobar-un-repositorio)). En un terminal, `shogun new` y `shogun provision` imprimen los comandos, las variables declaradas (`NOMBRE = plantilla`) y el archivo de entorno antes de preparar el entorno del worktree, y preguntan `Approve them on this machine? [y/N]` por la salida de error; solo un sí explícito los aprueba y aplica. Sin terminal los omiten, imprimen `shogun: skipped N post-creation commands not approved on this machine; run 'shogun trust' in <repo> to review them` y `shogun: skipped environment variables not approved on this machine (PORT, …); run 'shogun trust' in <repo> to review them` por la salida de error y terminan bien.

#### Cuando falla un comando posterior a la creación

El worktree se conserva. `shogun new` imprime su ruta por la salida estándar, el fallo (comando, código de salida y salida de error) por la salida de error, y sale con 1. `shogun provision` imprime el fallo por la salida de error y sale con 1.

### `trust`

```text
shogun trust [<path>] [--yes] [--revoke]
```

Imprime lo que el repositorio pide a ShogunAi ejecutar o aplicar, y si está aprobado en este equipo: sus comandos posteriores a la creación, uno por línea, después `Environment variables declared by <repo>:` con una línea `NOMBRE = plantilla` por variable, y `Environment file: <ruta>`. En un terminal pregunta si aprobarlos; `--yes` aprueba sin preguntar, para que lo puedan hacer scripts y CI, y `--revoke` quita la aprobación del repositorio. Si el repositorio no declara comandos, variables ni archivo de entorno, lo dice. Un agente que lee una nota sobre variables pendientes de aprobación debería pedirte que lo ejecutes en vez de ejecutarlo él. Código de salida 0 salvo que algo falle.

### `status`

Lista todos los worktrees del repositorio, uno por línea: ruta, rama (o `(detached)`) y después detalles:

| Detalle | Significado |
|---|---|
| `main` | El checkout principal. |
| `origin=shogunai\|claude-code\|herdr\|orca\|other` | Dónde se creó el worktree. |
| `base=develop` | La [base](#base) registrada de su rama. |
| `locked` | Lo retiene una sesión de agente. |
| `desktop(until 14:10)`, `desktop(waiting 1/2)` | Tiene el [turno del escritorio](#desktop) hasta esa hora, o lo espera en ese puesto de la cola. |
| `agents=claude(blocked, app-task-123),codex` | Agentes en ejecución en él, con su estado (`working`, `blocked`, `done`, `idle`) cuando lo informa Herdr u Orca o, para Claude, sus [hooks](../agents.md#estados-de-los-agentes-fuera-de-herdr). Un agente Claude también lista, tras su estado, los nombres de sus sesiones vivas cuya carpeta está en ese worktree, tal como los tiene el registro de sesiones de Claude Code (`claude(app-task-123)` cuando no hay estado). |
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
      "base": null,
      "agents": [{ "name": "claude", "state": "blocked", "stateSource": "claude-code", "herdrState": null, "sessions": [{ "name": "main-repo-task-123", "pid": 4242 }] }],
      "status": {
        "uncommittedChanges": 0,
        "upstream": { "ahead": 0, "behind": 0 },
        "mainBranch": null,
        "merged": false,
        "lastCommitAt": "2026-10-18T09:12:44Z",
        "createdAt": "2026-10-17T16:40:02Z"
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
| `origin` | `shogunai`, `claude-code`, `herdr`, `orca` u `other`; `null` para el checkout principal. |
| `base` | La [base](#base) registrada de su rama; `null` si no hay ninguna registrada o está en HEAD separado. |
| `agents` | Los agentes en ejecución en él: `name`, `state` (`working`, `blocked`, `done`, `idle`; `null` cuando nada informa de uno), `stateSource` (`herdr`, `orca`, `claude-code` o `null`), `herdrState`, el estado cuando la fuente es Herdr y `null` si no, y `sessions`, las sesiones vivas de Claude Code del agente en ese worktree (`name` y `pid`; vacía para otros agentes o cuando no se encuentra ninguna). |
| `status` | `null` cuando falta la carpeta o no se pudo leer su estado. |
| `statusProblem` | Solo aparece cuando no se pudo leer el estado: el motivo de Git. |
| `status.uncommittedChanges`, `status.merged` | Cambios sin commitear; mergeado en la rama principal. |
| `status.upstream`, `status.mainBranch` | `{ "ahead": n, "behind": n }` respecto al upstream y a la rama principal; `null` si no hay. |
| `status.lastCommitAt` | Hora del último commit en ISO-8601 UTC, o `null`. |
| `desktop` | `null`, o el [turno del escritorio](#desktop) del worktree: `{ "role": "holder", "since", "expiresAt" }` cuando lo tiene, `{ "role": "waiting", "position", "since" }` cuando lo espera, con horas ISO-8601 UTC. |
| `status.createdAt` | Hora en que se creó el worktree, en ISO-8601 UTC, leída de la carpeta administrativa de Git para él; `null` para el checkout principal o si no se puede leer. |

Todos los campos están siempre presentes, con `null` cuando no aplican. Los nombres de los campos son un contrato: las versiones futuras solo añaden campos y nunca renombran, eliminan ni cambian el tipo de uno.

### `base`

```text
shogun base [<task id | path | branch>] [--set <branch>] [--json]
```

Imprime la rama de la que se creó la rama de un worktree, sola en la salida estándar, para que `gh pr create --base "$(shogun base)"` abra su pull request contra la rama correcta. Sin destino, es el worktree que contiene el directorio actual.

- ShogunAi la registra al crear una rama: `shogun new`, el hook `WorktreeCreate` de Claude Code, «Nuevo worktree» de la app de escritorio y los worktrees de un [grupo de un multirepo](#multirepos), que parten de la rama principal de cada repositorio.
- Se guarda en la configuración de Git del repositorio como `branch.<rama>.gh-merge-base`, la clave que `gh pr create` ya lee como base por defecto. No se escribe nada en el árbol de trabajo; todos los worktrees del repositorio la ven, renombrar la rama la conserva y borrar la rama la elimina.
- Si no hay ninguna registrada (un worktree creado por Herdr o con `git worktree add`, o una rama anterior a que ShogunAi registrara bases), lo dice en la salida de error, sugiere `--set` y sale con `1`. Un worktree en HEAD separado no tiene rama y también sale con `1`.
- `--set <rama>` la registra, sustituyendo el valor anterior. El valor debe ser una rama local o de un remoto, cuyo prefijo de remoto se quita (`origin/develop` se registra como `develop`); un tag o un nombre desconocido no registran nada y salen con `1`.
- `--json` imprime `{ "path", "branch", "base" }`, con `base` a `null` (y código de salida `1`) si no hay ninguna registrada.

### `hooks`

```text
shogun hooks install   [--project] [--yes] [--json]
shogun hooks uninstall [--project] [--yes] [--json]
shogun hooks status    [--json]
```

Gestiona los [hooks de Claude Code](../agents.md#claude-code) de ShogunAi en el archivo de ajustes de Claude Code: `$CLAUDE_CONFIG_DIR/settings.json`, o `~/.claude/settings.json`; con `--project`, el `.claude/settings.json` del repositorio.

- `install` añade los hooks `WorktreeCreate`, `WorktreeRemove`, `SessionStart`, `UserPromptSubmit`, `Notification`, `Stop` y `SessionEnd`, conservando todos los demás ajustes y hooks. Un hook es de ShogunAi cuando su comando empieza por `shogun hook `; instalar otra vez añade los que faltan, reemplaza los que difieren y nunca duplica. Imprime lo que va a añadir (`+`), reemplazar (`~`) o quitar (`-`) y pregunta `[y/N]` salvo con `--yes`; si respondes que no, no escribe nada y sale con `0`.
- `uninstall` quita todos los hooks de ShogunAi, conserva todo lo demás y elimina un evento que se queda vacío.
- `status` dice si los hooks están `installed`, `outdated` (nombrando los eventos que faltan o difieren) o `missing`, para los ajustes del usuario y, dentro de un repositorio, para los del repositorio.
- Un archivo de ajustes que no sea JSON válido nunca se escribe: el comando imprime su ruta y el problema por la salida de error y sale con `1`.
- Con `--json` no se pregunta nada: `install` y `uninstall` solo escriben con `--yes`, e imprimen `path`, `applied` y los hooks `added`, `replaced` y `removed`. `status --json` imprime `user` y `project` (`null` fuera de un repositorio), cada uno con `path`, `state` y `events`.

### `guide`

```text
shogun guide [<tema>] [--plain]
shogun guide --migrate [--file <ruta>]
shogun guide install   (--agent codex|gemini | --project | --file <ruta>) [--yes] [--json]
shogun guide uninstall (--agent codex|gemini | --project | --file <ruta>) [--yes] [--json]
shogun guide status    [--file <ruta>] [--json]
```

Las reglas de flujo de ShogunAi para agentes, en los temas `worktrees`, `parallel`, `resources` y `cleanup`. Ver [Uso con agentes](../agents.md#reglas-de-flujo-de-shogunai).

- `guide [<tema>]` imprime la primera línea (las reglas son valores por defecto; el `AGENTS.md` del proyecto y las instrucciones de la persona tienen prioridad) y después las reglas del tema, o de todos los temas, que aplican en el directorio actual: checkout principal o worktree, si Herdr está disponible y qué providers declara el repositorio. Las reglas que no aplican van a continuación bajo "Not applicable here:", cada una con la condición que necesita. Un tema desconocido es un error de uso (código `2`) que lista los temas; fuera de un repositorio Git sale con `1` y apunta a `--plain`.
- `--plain` imprime todas las reglas de los temas pedidos con su condición, sin mirar el directorio actual, así que funciona en cualquier sitio.
- `--migrate` imprime cada regla con su id y una instrucción para un agente: comparar el fichero indicado con las reglas, listar lo que repite, proponer quitarlo y mostrar el diff sin aplicar nada hasta que la persona lo confirme. No lee ni escribe el fichero.
- `install` escribe un bloque que dice que se ejecute `shogun guide`, entre `<!-- shogun:guide v1 -->` y `<!-- /shogun:guide -->`, en un fichero elegido con `--agent` (`codex`: `$CODEX_HOME/AGENTS.md`, o `~/.codex/AGENTS.md`; `gemini`: `~/.gemini/GEMINI.md`), `--project` (el `AGENTS.md` del repositorio) o `--file`; hace falta uno de ellos, y Cursor no tiene fichero de usuario, así que `--agent cursor` es un error de uso. Crea el fichero si falta, conserva todos los demás bytes, nunca duplica el bloque, reemplaza uno de una versión anterior, imprime lo que va a escribir y pregunta `[y/N]` salvo con `--yes`; si respondes que no, no escribe nada y sale con `0`.
- `uninstall` quita el bloque y el salto de línea que añadió `install` de los mismos destinos, y deja todo lo demás.
- `status` dice, para los ficheros de Codex y Gemini, el `AGENTS.md` del repositorio (dentro de un repositorio) y `--file`, si el bloque está `installed`, `outdated` o `missing`.
- `install` y `status` avisan por la salida de error cuando un fichero menciona `shogun new`, `shogun open` o `shogun clean` fuera del bloque, y sugieren `guide --migrate`; `status` avisa además cuando el hook `SessionStart` de Claude Code está instalado y el `AGENTS.md` del repositorio o el `--file` también tiene el bloque, porque Claude Code recibiría las reglas dos veces. Los avisos nunca cambian un fichero.
- Con `--json`, `install` y `uninstall` no preguntan nada y solo escriben con `--yes`, e imprimen `path`, `applied`, `state` y `warnings`; `status --json` imprime `targets` (`{ target, path, state, duplicates }`) y `warnings`.

### `remove`

```text
shogun remove <id de tarea | ruta | rama> [--force|-f] [--delete-branch | --force-delete-branch]
```

Elimina un worktree enlazado, igual que "Eliminar" en la app de escritorio. El objetivo es, por este orden, la ruta de un worktree, el nombre exacto de su rama o un id de tarea (el worktree en `<carpeta de worktrees>/<id de tarea>`); si no hay coincidencia, o hay más de una, no elimina nada y falla (listando los candidatos). El worktree principal nunca se elimina.

Sin `--force` rechaza un worktree con cambios sin commitear o sin seguimiento, o donde se ejecuta un agente (`shogun: claude is running in <ruta>; use --force to remove it anyway`), y termina con `1`. Con `--force` lo elimina igualmente. La rama se conserva salvo que pases `--delete-branch`, que la borra solo si está mergeada en la rama principal del proyecto (`deleted branch …`, o `kept branch … (not merged into the main branch)`; una rama conservada no hace fallar el comando). `--force-delete-branch` la borra aunque no esté mergeada.

Tras eliminarlo, el comando para lo que los providers del repositorio mantienen para ese worktree: su emulador de Android (`stopped emulator …`), su simulador de iOS (`stopped simulator …`) y los daemons de Gradle ociosos cuya última build fue en él (`stopped Gradle daemon …`). Un recurso que no se puede parar se indica en la salida de error y el código de salida es `1`; para entonces el worktree ya está eliminado. También se cierra su espacio de trabajo de Herdr, y sus terminales de Orca mientras Orca está abierto (Orca nunca se arranca para esto; una terminal que no se puede cerrar se indica en la salida de error sin cambiar el código de salida). Su app de escritorio en segundo plano, si está en marcha, se para antes de borrar la carpeta. Si Git no puede borrar la carpeta porque un programa la está usando (típicamente una shell de Herdr u Orca en Windows), se cierran el espacio de trabajo, los paneles de Herdr dentro del worktree y sus terminales de Orca y se reintenta la eliminación una vez; un worktree que Git ya había desregistrado pero cuya carpeta queda se termina de eliminar borrando esa carpeta, y una carpeta que sigue en uso falla con un mensaje que lo explica.

### `repair`

```text
shogun repair
```

Vuelve a enlazar los worktrees tras mover el repositorio o la carpeta de worktrees, como hace la app de escritorio al cambiar las carpetas de un proyecto: los worktrees que Git aún lista en su ubicación antigua se buscan en la carpeta de worktrees del proyecto, por el nombre de su carpeta, y se re-enlazan (`repaired <ruta>`). Si no hay nada que re-enlazar imprime `Nothing to repair.` Necesita el proyecto del repositorio en la app de escritorio, porque ahí se guarda la carpeta de worktrees; sin él, termina con `2`.

### `open`

```text
shogun open <id de tarea | ruta | rama> --herdr [--agent <nombre>]
```

Abre un worktree en [Herdr](https://herdr.dev) como lo hace "Abrir en Herdr" en la app de escritorio: un espacio de trabajo enfocado bajo el espacio del repositorio, una shell con el entorno del worktree (lo que imprime `shogun env`) y el comando de lanzamiento del agente del proyecto ejecutándose en ella (`opened <ruta> in Herdr with Claude (pane w1:p2)`). Si Herdr ya tiene el worktree abierto y uno de sus paneles ejecuta un agente (Herdr informa de un estado para él, `idle` y `done` incluidos), enfoca el panel de ese agente sin escribir nada (`focused <ruta> in Herdr (already open, pane w1:p3)`). Si el espacio de trabajo está abierto pero ningún panel tiene un agente vivo, por ejemplo tras salir de él, arranca el agente en un panel nuevo de ese espacio de trabajo con el entorno del worktree y conserva los demás paneles (`started Claude in <ruta>'s open Herdr workspace (pane w1:p4)`). Cada línea nombra el panel del agente, para que un orquestador sepa dónde escribir.

Cuando el comando del agente ejecuta Claude Code (`claude`, una ruta que termina en `/claude`, o cualquiera de ellos tras `headroom wrap` o asignaciones de variables) como un único comando sin su propio `-n`/`--name`, `open` añade `-n <nombre>` para poder distinguir la sesión al enviarle mensajes: `<proyecto>-<id>` para un worktree, donde `<proyecto>` es el nombre de la carpeta del repositorio e `<id>` el de la carpeta del worktree (`main-repo-task-123`), y `<proyecto>` para el worktree principal. Los nombres son letras minúsculas, dígitos y guiones simples (los demás caracteres pasan a ser un guion, como mucho 64 caracteres), y si una sesión viva de Claude ya tiene el nombre se añade `-2`, `-3`… Otro agente, o un comando que encadena varios (`source env.sh && claude`), se escribe sin cambios. Cuando el agente arranca, `open` espera hasta 10 segundos más a que la sesión aparezca con ese nombre en el registro de sesiones de Claude Code (`~/.claude/sessions`, o `$CLAUDE_CONFIG_DIR/sessions`) y lo añade a la línea (`opened <ruta> in Herdr with Claude (pane w1:p2) as session main-repo-task-123`). Cuando solo enfoca un agente Claude vivo, informa de la sesión viva en esa carpeta, prefiriendo el nombre que le habría dado y si no la que arrancó más tarde. Sin un nombre confirmado la línea no lleva sesión y la apertura sigue teniendo éxito. El registro es un formato interno de Claude Code que se lee solo para consultar y de forma tolerante: si cambia o no se puede leer, simplemente no se informan nombres.

Tras escribir el comando del agente, `open` espera hasta 10 segundos a que Herdr detecte el agente en ese panel. Si no lo detecta, el comando falla con código de salida `1`, nombrando el panel y el comando, y el panel queda abierto: ahí se puede revisar un agente que Herdr no reconoce o que tarda en arrancar.

El destino se busca como en `remove`, salvo que se admite el worktree principal. `--herdr` es el destino y es obligatorio; `--agent <nombre>` ejecuta otro de los agentes del proyecto, buscado por nombre sin distinguir mayúsculas, y un nombre desconocido termina con `2` listando los agentes del proyecto. Si Herdr no está instalado, su comando falla o el agente no arranca, el error va a la salida de error y el código de salida es `1`.

```text
shogun open <id de tarea | ruta | rama> --orca [--agent <nombre>]
```

Abre un worktree en [Orca](https://onorca.dev), como hace "Abrir en Orca" de la app de escritorio (ver [Orca](../agents.md#orca)). Orca se arranca si no está abierto (hasta 30 segundos), el repositorio se registra en Orca cuando hace falta y los worktrees ajenos a Orca del repositorio se hacen visibles cuando Orca ocultaría este. Cuando una de las terminales de Orca del worktree tiene un agente vivo, se enfoca esa terminal y no se escribe nada (`focused <path>'s running agent in Orca (terminal term_…)`). Si no, una terminal nueva y enfocada ejecuta el comando del agente con el entorno del worktree (`env 'NOMBRE=valor' … <agente>`, o asignaciones de PowerShell en Windows), con el mismo nombre que con `--herdr`, y `open` espera hasta 10 segundos a que Orca informe del agente en ella (`opened <path> in Orca with Claude (terminal term_…) as session main-repo-task-123`); si no, el comando falla con código `1`, nombrando la terminal y el comando, y la terminal queda abierta. Hace falta exactamente uno de `--herdr` y `--orca`. Si Orca no está instalado (el mensaje nombra onorca.dev), no arranca o su comando falla, el error va a la salida de error y el código de salida es `1`. En la carpeta de un multirepo `--orca` abre los worktrees del grupo o el agente del multirepo; ver [Multirepos](#multirepos).

### `clean`

```text
shogun clean [--yes|-y] [--delete-branches] [--stop-gradle-daemons] [--stop-maven-daemons]
             [--stop-emulators] [--delete-simulators] [--delete-derived-data] [--down-compose-projects [--compose-volumes]]
```

Selecciona los mismos worktrees que "Limpiar" en la app de escritorio: ni el principal, ni los bloqueados, ni uno donde se ejecute un agente, y o bien mergeados sin cambios sin commitear o bien sin carpeta. Sin `--yes` solo lista lo que haría (`would remove …`) y termina con el comando que hay que ejecutar, repitiendo las opciones que diste y nombrando solo lo que se eliminaría; con `--yes` lo hace (`removed …`). Si no hay nada que hacer imprime `Nothing to clean up.`

Un worktree donde se ejecuta un agente se lista como `kept <ruta> (claude is running)`; si en la plataforma no se pueden comprobar los agentes, una nota en la salida de error lo dice. Un worktree cuyo estado no se pudo leer se lista como `skipped <ruta> (status unavailable: <motivo>)`.

| Opción | Además… |
|---|---|
| `--delete-branches` | Borra las ramas de los worktrees eliminados si están mergeadas en la rama principal del proyecto (`kept branch …` si no). |
| `--stop-gradle-daemons` | Para los daemons de Gradle ociosos cuya última build fue en un worktree eliminado. |
| `--stop-maven-daemons` | Para los daemons de Maven ociosos cuya última build fue en un worktree eliminado (`would stop Maven daemon …` sin `--yes`). |
| `--stop-emulators` | Para los emuladores de Android de los worktrees eliminados. |
| `--delete-simulators` | Borra los simuladores de iOS de los worktrees eliminados y los simuladores de ShogunAi huérfanos. |
| `--delete-derived-data` | Borra los DerivedData de Xcode huérfanos, incluidas las carpetas de los worktrees eliminados. |
| `--down-compose-projects` | Baja los proyectos de Docker Compose de los worktrees eliminados y elimina las redes e imágenes construidas que dejan, conservando sus volúmenes (`took down Compose project …`, `removed image …`, `kept volume …`; `would take down Compose project …` sin `--yes`). Solo en repositorios que declaran el provider `docker-compose`; un proyecto que no se puede bajar, o que Docker no esté disponible, se informa en la salida de error sin cambiar el código de salida. |
| `--compose-volumes` | Con `--down-compose-projects`, elimina también los volúmenes de los proyectos de los worktrees eliminados. Sin `--down-compose-projects` es un error de uso. |

También se cierran los espacios de trabajo de Herdr y las terminales de Orca de los worktrees eliminados (`would close Herdr workspace of …` y `would close Orca terminals of …` en el listado; a Orca solo se le pregunta mientras está abierto). Un daemon, emulador, espacio de Herdr o terminal de Orca que no se puede parar o cerrar se indica en la salida de error sin cambiar el código de salida.

## Multirepos

En la carpeta de un multirepo (ver [Multirepos y grupos](multirepo.md)), `new`, `status`, `open` y `clean` actúan sobre sus grupos, y `shogun multirepo` lo configura.

```text
shogun multirepo init [--worktrees-root <dir>] [--name <name>] [--use-worktrees-root | --keep-worktrees-folder] [--yes]
shogun multirepo show
shogun multirepo trust [<carpeta>] [--yes | --revoke]
shogun multirepo name <name> | --clear
shogun multirepo add-repo <alias> <ruta> [--use-worktrees-root | --keep-worktrees-folder]
shogun multirepo remove-repo <alias>
shogun multirepo defaults <alias>[,<alias>...] | --clear
shogun multirepo agent <comando> [--name <nombre>] [--headroom]
shogun multirepo workspace set [--alias <alias>] [--default] | clear | detect
shogun multirepo env [set <alias> <name> <template> | unset <alias> <name>]
shogun multirepo worktrees-root <dir>
shogun multirepo delete
shogun new <task-id> [--repos <alias>[,<alias>...]] [--fix] [--fetch]
shogun open <task id | rama> --herdr
shogun open --herdr
```

- `multirepo init` en una carpeta que ya tiene declaración (por ejemplo, una que se ha movido) muestra la misma revisión que `multirepo trust` y pregunta antes de aprobarla (`--yes` aprueba sin preguntar; sin terminal, o con `--json`, y sin `--yes` sale con `1`; si respondes que no, no cambia nada). Una vez aprobada registra el multirepo y un proyecto para cada repositorio declarado que no lo tenga (preguntando por `<worktreesRoot>/<alias>` como hace `add-repo`); ahí `--worktrees-root` y `--name` salen con `2`. En ambos casos olvida antes los multirepos conocidos cuya carpeta ya no existe. Su `--json` para una declaración existente tiene `name`, `path`, `declaration`, `registered` (false si ya estaba aprobado y era conocido) y los `projects` registrados.
- `multirepo name` fija el nombre con el que se muestra el multirepo; un nombre en blanco sale con `2`. `show` imprime primero el nombre, y su `--json` tiene `name`.
- Los subcomandos de `multirepo` funcionan en la carpeta del multirepo o en cualquier parte de sus repositorios y worktrees, y aceptan `--json`; fuera de un multirepo salen con `1`. Un alias incorrecto sale con `2`.
- Solo se aplican los multirepos aprobados en este equipo ([Aprobar un multirepo](multirepo.md#aprobar-un-multirepo)). Bajo una declaración sin aprobar, `new`, `status`, `open` y `clean` se comportan como fuera de un multirepo e imprimen en la salida de error un aviso con el archivo y `shogun multirepo trust`; los hooks no imprimen nada. `multirepo show` lo sigue mostrando, con `not approved on this machine` (`"approved": false` en `--json`); los subcomandos que escriben salen con `1`.
- `multirepo trust` muestra la declaración más cercana (o la de `<carpeta>`): su nombre declarado, cada repositorio con su ruta resuelta, la carpeta de worktrees y el repositorio de workspace, marcando las rutas fuera de la carpeta padre. En un terminal pregunta (`[y/N]`) y aprueba si respondes que sí; `--yes` aprueba sin preguntar; sin terminal y sin `--yes` sale con `1`. `--revoke` olvida el multirepo y conserva el archivo. Su objeto `--json` tiene `path`, `declaration`, `name` (si está declarado), `approved`, `changed`, `repos` (con `outsideParent`), `worktreesRoot`, `worktreesRootOutsideParent`, `workspace` e `ignoredDeclaredAgent`; sin `--yes` solo informa.
- `multirepo agent` guarda el agente del multirepo en este equipo, nunca en la declaración; un `agent` que haya dejado en la declaración una versión anterior se ignora, y `show` lo indica (`ignoredDeclaredAgent` en `--json`). El agente ejecuta su comando tal cual; `--headroom` lo envuelve con `headroom wrap`. `--no-headroom`, de versiones anteriores en las que Headroom venía activado por defecto, se sigue aceptando y no cambia nada; junto con `--headroom` sale con `2`.
- `new <id>` en la carpeta del multirepo cubre los repositorios por defecto, o `--repos` desde cualquier parte del multirepo; imprime la ruta de cada worktree que crea en la salida estándar y una línea por repositorio en la salida de error, y sale con `1` si algún repositorio falló. `--branch`, `--from` y `--worktrees-root` no se aplican a un grupo y salen con `2`. Su objeto `--json` tiene la `branch` y una lista `repos` con `alias`, `path`, `result` (`created`, `existing` o `failed`), los comandos de post-creación omitidos y `error`.
- Cuando la carpeta del multirepo es un repositorio Git, es el repositorio de workspace del multirepo (alias `workspace` salvo que se declare otro): `show` lo lista, marcado `detected` hasta que `workspace set` lo declara (`--alias` lo renombra, `--default` lo añade a los repositorios por defecto) `workspace clear` desactiva la detección y `workspace detect` quita el ajuste para que vuelva a detectarse (sale con `1` si el alias `workspace` lo usa un repositorio declarado); en una carpeta que no es un repositorio `workspace set` y `workspace detect` salen con `1`. `new <id> --repos workspace,app` lo incluye, con su worktree en `<worktreesRoot>/workspace/<id>`. `project add` y `app` ejecutados en esa carpeta registran el repositorio de workspace como miembro del multirepo y ningún proyecto propio de la carpeta (`app` solo abre la app), salvo que la detección esté desactivada. El `--json` de `show` marca cada repositorio con `workspace` y `detected`.
- `multirepo env` lista las variables compartidas por repositorio (`--json`: `{"env": {<alias>: {<nombre>: <plantilla>}}}`, como en `show`); `env set <alias> <name> <template>` añade o sustituye una después de comprobarla (una plantilla inválida sale con `1`, un alias desconocido con `2`), y `env unset <alias> <name>` quita una (`1` si no está declarada). Ambos mantienen aprobado el multirepo y salen con `1` mientras necesita revisión; `env` sin subcomando las lista igualmente. `remove-repo`, y `workspace clear`, salen con `1` mientras una variable compartida de otro repositorio referencie el repositorio, nombrando las variables.
- `status` en la carpeta del multirepo imprime la rama de cada grupo seguida de una línea por repositorio, y después `ungrouped`, con los nombres de sesión de cada agente Claude como los muestra `status` (`agents=claude(working, app-task-123)`); `--json` imprime `groups` (cada uno con `branch`, `title` y `repos` con `alias`, `path`, `branch`, `base`, `folderMissing`, `uncommittedChanges`, `ahead`, `behind`, `merged`, `agents`), `ungrouped` y `failures`.
- `open <id> --herdr` abre cada worktree del grupo con el agente de su proyecto (`--agent` sale con `2`) e informa de cada uno como `open` con un solo worktree (abierto, agente arrancado en su espacio de trabajo abierto o enfocado, con su panel); un worktree que falla, o cuyo agente no arranca, se informa en la salida de error, los demás se abren igualmente y el código de salida es `1`. Un id que encaja con `feature/<id>` y `fix/<id>` a la vez sale con `2`, y sin coincidencias con `1`. `open --herdr` sin destino abre el agente del multirepo del mismo modo: enfoca su agente vivo, lo arranca en un panel nuevo de su espacio de trabajo abierto si no tiene ninguno, o abre un espacio de trabajo nuevo, y comprueba que arrancó. Cada sesión de Claude recibe un nombre como con `open` de un worktree: `<alias>-<id>` para un worktree del grupo, donde `<id>` es la rama del grupo sin su prefijo (`app-task-123`, `api-task-123`), y el nombre del multirepo para el agente del multirepo, así una sesión coordinadora distingue las sesiones del grupo y envía mensajes a cada una por su nombre.
- `open <id> --orca` abre cada worktree del grupo en Orca, uno tras otro, como `open --orca` con un solo worktree (agente arrancado o enfocado, con su terminal), con el agente de su proyecto (`--agent` sale con `2`) y los mismos nombres de sesión que con `--herdr`. Un fallo se informa en la salida de error, los demás se abren igualmente y el código de salida es `1`; los ids ambiguos o sin coincidencias salen como con `--herdr`. Sin Orca instalado, lo dice una vez y sale con `1`. `open --orca` sin destino ejecuta el agente del multirepo en el espacio de trabajo de Orca cuya ruta es la carpeta del multirepo: enfoca su agente vivo o lo arranca en una terminal nueva y comprueba que arrancó. Una carpeta de multirepo Git que Orca no conoce se registra; una carpeta que no es un repositorio Git y no tiene espacio de trabajo en Orca falla con código `1`, explicando cómo añadirla en la app de Orca, y no registra nada.
- `clean` lista los grupos candidatos (`would remove the group …`) y los que se conservan con su motivo (`kept feature/TASK-2 (api isn't merged)`), y acepta todas las opciones de `clean`. Después de los grupos lista los worktrees sueltos de cada repositorio (`would remove api <ruta>`) que están limpios, fusionados y sin agente en marcha, y con `--yes` los elimina; si no hay nada que eliminar imprime `Nothing to clean up.`
- `hook agent-state` guarda las sesiones de una carpeta de multirepo que no es un repositorio Git en un almacén bajo `~/.shogunai/agent-sessions/`; para cualquier otro directorio que no sea un repositorio sigue sin escribir nada.

## Proyectos y la app

### `project`

```text
shogun project add [<ruta>] [--json]
shogun project list [--json]
shogun project remove [<ruta>] [--json]
```

`add` registra el repositorio que contiene la ruta (el directorio actual por defecto, o su checkout principal si se ejecuta desde un worktree) como proyecto de la app de escritorio, con los valores que propone el formulario "Añadir proyecto": el nombre de la carpeta como nombre del proyecto, `<padre>/<nombre>-worktrees` como carpeta de worktrees, sin archivos locales y con la rama principal detectada. No escribe nada dentro del repositorio. Un repositorio que ya está registrado se deja exactamente como estaba (no se tocan sus ajustes) y el comando lo dice, con código de salida `0`.

`list` imprime una línea por proyecto (`nombre  ruta del repositorio`) y funciona fuera de un repositorio. `remove` da de baja el proyecto del repositorio y deja el repositorio y sus worktrees en disco; encuentra un proyecto cuya carpeta ya no existe por la ruta con la que se registró, y sale con `1` cuando el repositorio no tiene proyecto.

Una app abierta recoge el cambio cuando su ventana recupera el foco o con **Ver > Recargar**.

### `app`

```text
shogun app [<ruta>] [--settings]
```

Abre la app de escritorio en el proyecto del repositorio que contiene la ruta (el directorio actual por defecto), registrándolo antes si no tiene proyecto. Con `--settings` abre los ajustes del proyecto en lugar de sus worktrees, lo que exige un repositorio (código de salida `2` sin él). Fuera de un repositorio y sin ruta, solo abre la app o la trae al frente. Con una ruta que no es un repositorio sale con `1` y no pide nada.

El comando vuelve en cuanto se ha avisado a la app, sin esperarla:

- **La app está abierta**: atiende la orden en un segundo más o menos, con la misma navegación que la barra lateral. Se conservan su ventana, tamaño y posición, y un formulario con cambios sin guardar pregunta si descartarlos en lugar de ser sustituido. Si los ajustes del proyecto ya están abiertos, no cambia nada.
- **La app está cerrada**: `shogun` la lanza y la app se abre en el proyecto cuando ha arrancado. Funciona donde el lanzador de la propia app es el comando `shogun` (macOS con Homebrew, `.deb` de Linux, `.msi` de Windows). Desde el zip independiente, sin la app instalada, el comando dice que la app de escritorio no está disponible en esa instalación y sale con `1`.

## Entorno

### `env`

```text
shogun env [<path>] [--format sh|dotenv|json]
```

Imprime el entorno del worktree que contiene la ruta (o el directorio actual), a partir de `env` en `.shogunai.json` (una vez aprobado el repositorio) y de los providers, reservando sus puertos la primera vez. Las variables declaradas pendientes de aprobación no se imprimen; una nota por la salida de error las nombra junto con `shogun trust`. `sh` (por defecto) imprime líneas `export NOMBRE='valor'` para `eval "$(shogun env)"`; `dotenv` y `json` sirven para otras herramientas. Un entorno vacío no imprime nada.

## Stacks

### `stacks`

```text
shogun stacks
```

Imprime una línea por cada stack detectado en el repositorio (`Maven  evidence=pom.xml  maven=suggested`): los archivos que lo delataron y, por cada provider que sugiere, `suggested` o `declared` si `.shogunai.json` ya lo tiene. Los providers que el sistema operativo no admite no se mencionan. Tras los stacks imprime una línea `Secret  <ruta>  kind=<tipo>  link=<patrón>` por cada secreto móvil que aún no provisiona nada y una línea `Warning` por cada ruta de keystore que sale del repositorio. No cambia nada, imprime `No known stack detected.` y sale con `0` si no hay nada, y sale con `1` fuera de un repositorio Git. Ver [detección de stacks](providers.md#deteccion-de-stacks).

## Providers

Estos comandos actúan sobre los [providers](providers.md) que un repositorio declara en `.shogunai.json`. Cuando un comando necesita un provider que el repositorio no declara, lo dice y sale con `1`.

### `gradle`

```text
shogun gradle
shogun gradle stop [<pid>...]
```

`gradle` lista los daemons de Gradle y Kotlin de la máquina: pid, tipo y versión, `memory=`, `state=busy|idle|unknown` (con `idle-for=`) y `worktree=<path>|removed|other-project`, y después el total (`No Gradle or Kotlin daemons running.` si no hay ninguno). Funciona también fuera de un repositorio. Dentro de un repositorio Gradle sin la caché de build de Gradle, termina con un aviso.

`gradle stop` para todos los daemons ociosos, o solo los ids de proceso indicados; los ocupados y los de estado desconocido se conservan (`kept <pid> … (<motivo>)`). Un pid que no es un daemon sale con `1`.

### `maven`

```text
shogun maven
shogun maven stop [<pid>...]
```

`maven` lista los daemons de Maven (mvnd) de la máquina: pid, `memory=`, `state=busy|idle|unknown` (con `idle-for=` si está ocioso) y, dentro de un repositorio Git, `worktree=<ruta>|removed|other-project|unknown`, y luego el total (`No Maven daemons running.` si no hay ninguno). También funciona fuera de un repositorio.

`maven stop` para todos los daemons de Maven ociosos, o solo los ids de proceso indicados; los ocupados y los de estado desconocido se conservan (`kept <pid>  Maven  (<motivo>)`). Un pid que no es un daemon de Maven sale con `1`.

### `compose`

```text
shogun compose
shogun compose up [<worktree>] [--build] [-- <args>...]
shogun compose build [<worktree>] [-- <args>...]
shogun compose down [<worktree>] [--volumes]
shogun compose prune [--volumes] [--yes]
```

`compose` lista los proyectos de Docker Compose de los worktrees del repositorio: nombre, `status=` y `worktree=<ruta>|removed`, y después una línea `leftover <tipo> <nombre>  project=<proyecto>` por cada volumen, red o imagen que dejó un worktree eliminado, con el comando `compose prune` que los elimina.

`compose up` arranca los servicios del worktree indicado, o del que contiene el directorio actual, con `docker compose up --detach` ejecutado en ese worktree con su entorno (su `COMPOSE_PROJECT_NAME` y sus variables de puerto); `--build` añade `--build`, y los argumentos tras `--` llegan tal cual a Docker Compose. `compose build` ejecuta `docker compose build` del mismo modo. La salida de Docker Compose se muestra mientras se ejecuta, y después `started Compose project <nombre> for <ruta>` (o `built …`); un fallo se imprime en la salida de error con código de salida `1`.

`compose prune` lista las redes e imágenes construidas que dejaron los worktrees eliminados (`would remove …`) y los volúmenes que conservaría (`would keep …`); con `--yes` las elimina (`removed …`, `kept …`), y también sus volúmenes con `--volumes`. Nunca se fuerza nada, así que un recurso que aún usa un contenedor se informa en la salida de error y el código de salida es `1`. Si no queda nada imprime `Nothing to remove.` `compose down` baja los proyectos de worktrees eliminados, o el proyecto del worktree indicado (que se busca como en `remove`, admitiendo el principal), con `docker compose -p <nombre> down`: los volúmenes se conservan salvo que se pase `--volumes`. Imprime `took down <nombre> (volumes kept)`, informa de un fallo en la salida de error y sale con `1` por él. Si el repositorio no declara el provider `docker-compose`, o el worktree indicado no existe, sale con `1` y no ejecuta ningún comando de Docker.

### `emulator`

```text
shogun emulator start|stop|status [<path>] [--avd <name>] [--no-window] [--wait] [--wait-timeout <minutes>]
```

Arranca (y espera a), para o muestra el emulador de Android del worktree. `--avd` y `--no-window` solo se aplican a `start` y sustituyen los ajustes del proyecto en ese arranque. `status` imprime `emulator-5556 running|booting|stopped`, o `not acquired` antes del primer arranque. Cada `start` renueva la concesión del worktree e imprime `export ANDROID_SERIAL=…` con el serial que hay que usar. El primer `start` reserva el puerto de consola (liberando un emulador con la concesión caducada si el pool está lleno, lo que se informa por la salida de errores); ejecútalo antes de cada uso del dispositivo, ya que adquiere uno de nuevo si fue liberado. Tras una liberación, `status` imprime `not acquired (released for <worktree> at <hora>)`. Con todos los emuladores protegidos falla con el código de salida `1` nombrando los worktrees que los tienen y cuándo caducan sus concesiones.

Con `--wait`, `start` espera en cola en vez de fallar cuando el pool está lleno y no se puede liberar ninguno, y arranca el emulador en cuanto puede adquirir uno. La espera dura como mucho 30 minutos, o los minutos indicados con `--wait-timeout` (que implica `--wait`); después falla con el código de salida `1`, diciendo cuánto esperó y quién tiene los emuladores, sin haber reservado nada. Quienes esperan un pool se atienden por orden de llegada en todos los repositorios de la máquina, y mientras alguien espera, un `start` sin `--wait` (o desde la app de escritorio) no puede quedarse con el emulador que esperan: su error dice cuántos esperan. Quien espera y se interrumpe, o cuyo proceso termina, sale de la cola. Mientras espera, el comando imprime por la salida de errores su puesto en la cola, quién tiene cada emulador y cuándo caduca la próxima concesión, de nuevo solo cuando eso cambia, y `waited <tiempo>` cuando ya tiene el emulador. Los agentes deben ejecutar un `start` que espera en segundo plano, porque dura más que el límite de tiempo de su shell. `--wait` y `--wait-timeout` solo se aplican a `start`.

### `simulator`

```text
shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>] [--wait] [--wait-timeout <minutes>]
shogun simulator gradle-init install|uninstall [--yes|-y]
```

Crea si hace falta, arranca (y espera a) y abre, apaga o muestra el simulador de iOS del worktree (solo macOS). `--device` y `--runtime` solo se aplican cuando `start` crea el simulador. `--wait` y `--wait-timeout` esperan a un pool de simuladores lleno igual que en [`emulator`](#emulator). En un repositorio Kotlin Multiplatform, `status` dice además si los tests de KMP usan el simulador.

`gradle-init install` instala un init script de Gradle para toda la máquina que hace que los tests de simulador KMP de todos los proyectos usen el simulador del worktree; muestra el script y pregunta `[y/N]` salvo con `--yes`. `uninstall` lo elimina.

### `headless`

```bash
shogun headless start [<ruta>] [--visible] [--fresh] [--json]
shogun headless capture [<ruta>] [--output <archivo>] [--json]
shogun headless stop|status [<ruta>] [--json]
```

Ejecuta la app de Compose Desktop del worktree en segundo plano (solo macOS, repositorios con el [provider `desktop-app`](providers.md#app-de-compose-desktop)). `start` la lanza con Compose Hot Reload sin quitar el foco, espera a su ventana, la oculta e imprime `<pid> running hidden`; con la app ya en marcha no lanza nada y la vuelve a ocultar. `--visible` deja la ventana a la vista, detrás de la app que estés usando. `--fresh` vacía antes el home aislado de la app. Una compilación fallida sale con `1` y el final del log del lanzamiento; una app que termina antes de mostrar su ventana, con el final de su propio log. Si macOS no deja ocultarla, la app sigue en marcha visible y la salida de errores nombra el permiso de Accesibilidad.

`capture` guarda un PNG de la ventana de la app, tomado de la propia ventana, así que la muestra aunque esté oculta o tapada; sin `--output` va a un archivo nuevo en la carpeta de Git del worktree, y el comando imprime su ruta. Necesita el permiso de Grabación de pantalla para la aplicación que ejecuta `shogun`, como tu terminal. `stop` termina la app (`stopped <pid>`, o `stopped` si no estaba en marcha); `status` imprime su estado, el home aislado, el log del lanzamiento y los permisos que falten. Consulta [Comprobar una app de escritorio en segundo plano](headless-app.md).

### `desktop`

```bash
shogun desktop acquire [<ruta>] [--wait] [--wait-timeout <minutos>] [--json]
shogun desktop release [<ruta>] [--json]
shogun desktop status [--json]
```

El escritorio (pantalla, ratón, teclado y la app en primer plano) es un único recurso para toda la máquina, así que los agentes que comprueban algo con computer use se turnan: un solo worktree tiene el turno del escritorio a la vez, en cualquier plataforma. `acquire` da el turno al worktree que contiene la ruta o el directorio actual e imprime `acquired the desktop until 14:10`; si lo vuelve a ejecutar quien lo tiene, renueva el turno (`renewed the desktop until 14:15`). El turno caduca solo tras la duración del turno del escritorio (10 minutos por defecto, en Preferencias › Recursos) si no se renueva, así que un agente que se cuelga no se lo queda; que el agente esté trabajando no lo renueva. Los [hooks](../agents.md#claude-code) `Stop` y `SessionEnd` de Claude Code lo liberan cuando termina la respuesta o la sesión de quien lo tiene, y eliminar o limpiar un worktree libera su turno y quita sus esperas.

Mientras otro worktree tiene el turno, `acquire` falla con código de salida `1`, nombrando a quien lo tiene, cuándo caduca su turno y cuántos worktrees esperan. Con `--wait` espera en la cola en su lugar, como mucho 30 minutos o los minutos indicados con `--wait-timeout` (que implica `--wait`), e imprime en la salida de error su puesto en la cola y quién tiene el escritorio hasta cuándo, solo cuando eso cambia, y `waited <tiempo>` en cuanto tiene el turno. La cola se atiende por orden de llegada en todos los repositorios, y un `acquire` sin `--wait` no puede quedarse el turno mientras alguien espera. Los agentes ejecutan en segundo plano un `acquire` que espera.

`release` devuelve el turno (`released the desktop`), dice `nobody holds the desktop` cuando nadie lo tiene y falla con código de salida `1`, sin cambiar nada, cuando lo tiene otro worktree; la app de escritorio puede liberar el turno de cualquiera. `status` imprime quién lo tiene, desde cuándo y hasta cuándo, o `desktop free`, seguido de los que esperan por orden. El turno es cooperativo: evita que choquen los agentes que siguen las [reglas de flujo](../agents.md#reglas-de-flujo-de-shogunai), no impide nada a un programa que lo ignore.

### `pool`

```text
shogun pool status [--json]
shogun pool trim [--json]
```

`status` imprime cada pool (`emulators 1/2`, `simulators 0/1`, `gradle daemons 3/2  over the limit`) con el límite del perfil de capacidad de la máquina y, debajo, el proyecto, el worktree y el estado de cada recurso en uso, seguidos de los worktrees que esperan un emulador o un simulador (`waiting: main-repo · TASK-2 since 14:10`), en el orden de la cola. `trim` detiene los daemons de Gradle inactivos, primero los que llevan más tiempo inactivos, hasta quedar dentro del límite, e imprime lo que detuvo y conservó.

### `xcode`

```text
shogun xcode
shogun xcode clean
shogun xcode trim [--dry-run]
```

`xcode` lista las carpetas de DerivedData de los worktrees del repositorio con su tamaño y, si tienen paquetes Swift, `(packages <tamaño>)`, las carpetas huérfanas de la máquina (`orphans: N (tamaño)`), el tamaño de la caché de compilación cuando se declara, y los de las cachés compartidas de SwiftPM y CocoaPods cuando existen (`SwiftPM cache: <tamaño>`, `CocoaPods cache: <tamaño>`). `xcode clean` borra las carpetas huérfanas (`deleted <path>`, `freed <tamaño>`, o `Nothing to delete.`). Sin el provider de Xcode, o fuera de macOS, dice que el provider no está activo y sale con `0`.

`xcode trim` aplica la cuota de DerivedData fijada en Preferencias > Recursos, automática por defecto (una cuarta parte del espacio libre en disco más lo que ocupa DerivedData, entre 20 y 200 GB): cuando las carpetas suman más que la cuota, libera primero las carpetas huérfanas y después las de los worktrees de tus proyectos guardados que no están en uso, de la menos a la más recientemente accedida, y se detiene en cuanto el total cabe (`released <path>  <tamaño>`, `freed <tamaño>`, `usage <tamaño> of <cuota>`, seguido de `(automatic)` con la cuota automática). Un worktree está en uso mientras su agente trabaja o está bloqueado, o su carpeta se accedió hace menos de una hora, y nunca se liberan las carpetas de otros proyectos, de volúmenes no disponibles ni la caché de compilación; si solo quedan esas, imprime `quota still exceeded` y los worktrees en uso. `--dry-run` imprime `would release` y `would free` sin borrar. Con «Sin cuota» lo dice y no borra nada, y cuando la cuota automática no se puede calcular porque no se conoce el espacio libre en disco, lo dice en su lugar. Los comandos `xcode` recuerdan el tamaño de cada carpeta entre ejecuciones, compartido con la app, y solo vuelven a medir, varias a la vez, las carpetas que Xcode ha usado desde entonces.

## Salida JSON

Además de `status --json`, todos los comandos que informan de un resultado aceptan `--json`: `new`, `base`, `provision`, `trust`, `remove`, `repair`, `open`, `clean`, `gradle`, `gradle stop`, `stacks`, `maven`, `maven stop`, `compose`, `compose up`, `compose build`, `compose down`, `compose prune`, `pool status`, `pool trim`, `project add`, `project list`, `project remove`, `xcode`, `xcode clean`, `xcode trim`, `emulator`, `simulator`, `hooks` y `guide install|uninstall|status` (no `simulator gradle-init`, `env`, `hook`, `help` ni `--version`; en ellos es una opción desconocida y sale con `2`). La opción puede ir en cualquier posición tras el comando.

Las reglas son las mismas para todos:

- Un único objeto JSON compacto en una línea por la salida estándar, en vez del texto, con el mismo código de salida que la salida de texto.
- Un error anterior al resultado se imprime como texto por la salida de error, sin nada en la salida estándar.
- Nunca pregunta. Los comandos posteriores a la creación y las variables declaradas que no están aprobados en esta máquina se omiten y se listan (`shogun trust` los aprueba), y `trust` solo informa salvo que se indique `--yes`.
- Todos los campos documentados están siempre presentes, con `null` cuando no aplican, listas vacías en vez de ausentes y enumeraciones en minúsculas. Las rutas son absolutas. Las versiones posteriores solo añaden campos, nunca renombran, quitan ni cambian el tipo de uno.

| Comando | Objeto |
|---|---|
| `new` | `path`, `branch`, `created` (siempre `true`), `skippedPostCreateCommands` (lista de listas argv), `skippedVariables` (los nombres de las variables declaradas que se dejaron fuera), `postCreateFailure`: `null`, o `{ command, exitCode, stderr }` si falló un comando posterior a la creación, `skippedLinks`, los enlaces simbólicos omitidos dentro de carpetas locales, y `pods`: `{ cloned, installNeeded, error }` con las carpetas cuyos pods de CocoaPods se clonaron de la base, `{ folder, reason }` para las que necesitan `pod install` (`reason` `base-has-no-pods`, `base-pods-differ` o `clone-failed`) y `error` (`null` salvo que no se pudieran preparar los pods). En ese caso el objeto se imprime igualmente, porque el worktree existe, y el código de salida es `1`. |
| `provision` | `path`, `copied`, `linked` y `copiedInsteadOfLinking` (rutas de archivos), `skippedPostCreateCommands`, `skippedVariables` y `postCreateFailure`, como en `new`, `removed` (archivos borrados porque su carpeta local ya no los tiene), `skippedLinks` y `pods`, como en `new`. Si falló un comando, las tres listas de archivos van vacías. |
| `provision --check` | `path` y `drift`: lista de `{ path, state }` con `state` `missing`, `differs`, `not-linked` o `deleted-in-base`. El código de salida es `3` si `drift` no está vacío. |
| `trust` | `repository`, `commands` (listas argv), `env` (una lista de `{ name, template }`), `envFile` (o `null`), `approved` y `changed` (esta ejecución lo aprobó o revocó). Un repositorio que no declara nada es `approved: true` con listas vacías. `--revoke` informa `approved: false` si había algo que aprobar. |
| `remove` | `path`, `branch` (la del worktree, o `null` si está desacoplado), `branchDeleted` (el nombre, o `null` si se conservó o no se pidió borrar la rama), `branchKeptReason` (por qué no se borró una rama cuya eliminación se pidió, o `null`), `providers` (`emulatorsStopped` con los serial, `simulatorsStopped` con los udid y `gradleDaemonsStopped` con los pid) y `failures` (mensajes de lo que no se pudo detener, código de salida `1`). |
| `hooks install`, `hooks uninstall` | `path`, `applied` (escrito en esta ejecución) y los hooks `added` y `removed` como `{ event, command }` más `replaced` como `{ event, from, to }`. |
| `hooks status` | `user` y `project` (`null` fuera de un repositorio): `path`, `state` (`installed`, `outdated`, `missing`) y `events`. |
| `guide install`, `guide uninstall` | `path`, `applied` (escrito en esta ejecución), `state` (`installed`, `outdated`, `missing`) y `warnings` (mensajes). |
| `guide status` | `targets`: `{ target, path, state, duplicates }` con `target` `codex`, `gemini`, `project` o `file` y `duplicates` `true` cuando el fichero menciona `shogun new`, `open` o `clean` fuera del bloque; y `warnings`. |
| `repair` | `repaired`: las rutas de los worktrees re-enlazados. |
| `project add`, `project remove` | `action` (`added`, `alreadyRegistered` o `removed`), y el `id`, `name` y `repository` del proyecto. |
| `project list` | `projects`: `{ id, name, repository }`, vacío sin proyectos. |
| `open` | Con `--orca`: `path`, `branch`, `orca` (`started-agent` o `focused`), `terminal` (el identificador de la terminal del agente), `agent` y `session_name`. Con `--herdr`: `path`, `branch` (o `null` si está desacoplado), `herdr` (`opened`, `started-agent` o `focused`), `pane_id` (el panel del agente), `agent` (el nombre del agente arrancado, o el que Herdr informa en el panel enfocado) y `session_name` (el nombre confirmado de la sesión de Claude Code, o `null`). En la carpeta del multirepo con destino: `branch` y `repos`: `{ alias, path, result, pane_id, agent, session_name, error }` con `result` `opened`, `started-agent`, `focused` o `failed`. Sin destino: `path`, `opened` (`false` solo cuando se enfocó), `herdr`, `pane_id`, `agent` y `session_name`. Con `--orca` en la carpeta del multirepo, con destino: `branch` y `repos`: `{ alias, path, result, terminal, agent, session_name, error }` con `result` `started-agent`, `focused` o `failed`; sin destino: `path`, `orca` (`started-agent` o `focused`), `terminal`, `agent` y `session_name`. |
| `clean` | `dryRun`; `candidates`: `{ path, branch, reason }` con `reason` `merged` o `folder-missing`; `keptForAgents`: `{ path, agents }`; `skipped`: `{ path, reason }` (estado no disponible); `removed`; `deletedBranches`; `failures`: `{ path, message }`; y `providers`: `gradleDaemonsStopped`, `mavenDaemonsStopped`, `emulatorsStopped`, `simulatorsDeleted`, `derivedDataDeleted` y, con `--down-compose-projects`, `composeProjectsDown` y `composeResources` (`removed`, `kept`, `failures`, como en `compose prune`). Sin `--yes` (`dryRun: true`) `removed`, `deletedBranches` y las listas de `providers` van vacías. |
| `gradle` | `daemons`: `{ pid, kind, version, memoryBytes, state, idleSince, worktree }` con `kind` `gradle` o `kotlin`, `state` `busy`, `idle`, `unknown` o `untracked`, `idleSince` en ISO-8601 UTC (solo si está inactivo) y `worktree` una ruta, `removed`, `other-project` o `null` fuera de un repositorio; y `totalMemoryBytes`. |
| `gradle stop` | `stopped` (pids), `kept`: `{ pid, kind, reason }` con `reason` `busy`, `state-unknown` o `build-running`, `notDaemons` (pids) y `failures`: `{ pid, message }`. El código de salida es `1` si alguna de las dos últimas no está vacía. |
| `stacks` | `stacks`: `{ stack, evidence, providers }` con `stack` `gradle`, `android`, `xcode`, `maven` o `docker-compose`, `evidence` los archivos que lo delataron y `providers`: `{ id, declared }` por cada provider que sugiere; `secrets`: `{ path, kind, pattern }` con `kind` `local-properties`, `signing-properties`, `keystore` o `firebase-config`; y `keystoreWarnings`: `{ file, key, value }`. |
| `maven` | `daemons`: `{ pid, memoryBytes, state, idleSince, worktree }` con `state` `busy`, `idle` o `unknown`, `idleSince` en ISO-8601 UTC (solo si está ocioso), y `worktree` una ruta, `removed`, `other-project` o `null` (desconocido, o fuera de un repositorio); y `totalMemoryBytes`. |
| `maven stop` | `stopped` (pids), `kept`: `{ pid, reason }` con `reason` `busy` o `state-unknown`, `notDaemons` (pids) y `failures`: `{ pid, message }`. El código de salida es `1` si alguna de las dos últimas no está vacía. |
| `compose` | `projects`: `{ name, status, worktree }` con `worktree` una ruta o `removed`; `leftovers`: `{ kind, name, project }` con `kind` `network`, `image` o `volume`. |
| `compose up`, `compose build` | `worktree`, `project`, `succeeded` y, si falla, `message`. La salida de Docker Compose va a la salida de error. |
| `compose prune` | `dryRun`; `removed` y `kept`: `{ kind, name, project }`; `failures`: `{ resource, message }`. Sin `--yes`, `removed` es lo que eliminaría. |
| `compose down` | `takenDown` (nombres de proyecto) y `failures`: `{ name, message }`. El código de salida es `1` si `failures` no está vacío. |
| `xcode` | `active` (`false` si el provider no está activo, con el resto vacío), `folders`: `{ path, sizeBytes, sourcePackagesBytes, worktree }`, `orphans`: `{ path, sizeBytes, sourcePackagesBytes }`, `unavailableVolumeFolders` (un número), `compilationCacheBytes` (`null` salvo que esté declarada), y `swiftPmCacheBytes` y `cocoaPodsCacheBytes` (`null` si la caché no existe). |
| `xcode clean` | `active`, `deleted` (rutas), `freedBytes` y `failures`: `{ path, message }`. |
| `xcode trim` | `active`, `quotaMode` (`automatic`, `fixed` o `none`), `quotaBytes` (`null` sin cuota o cuando la automática no se puede calcular), `dryRun`, `usageBeforeBytes`, `usageAfterBytes`, `exceeded`, `released` (rutas; con `--dry-run`, las que se liberarían), `freedBytes`, `inUse` (rutas de worktrees) y `failures`: `{ path, message }`. |
| `emulator start\|stop\|status` | `worktree`, `serial` y `state` (`running`, `booting` o `stopped`). `serial` y `state` son `null` si el worktree aún no tiene puerto de emulador reservado. `waited` son los segundos enteros que un `start --wait` esperó capacidad (`0` si había sitio), `null` en otro caso. |
| `desktop acquire\|release\|status` | `holder` (`null`, o `{ repository, worktree, since, expiresAt }`) y `waiting`: `{ repository, worktree, since }` por orden de cola, con horas ISO-8601 UTC. `acquire` añade `renewed` y `waited` (segundos enteros de espera, `0` si el escritorio estaba libre, `null` sin `--wait`); `release` añade `released`. |
| `simulator start\|stop\|status` | `worktree`, `udid`, `name` y `state` (`shutdown`, `booting` o `booted`), `null` si el worktree aún no tiene simulador, y `waited` como en `emulator`. El aviso sobre los tests KMP no forma parte. |

```bash
shogun clean --json | jq -r '.candidates[].path'
shogun emulator status --json | jq -r '.serial'
shogun provision --check --json | jq -e '.drift | length == 0'
```

## Hooks de agentes

Estos comandos están pensados para que los llamen agentes y herramientas, no para escribirlos a mano. Cómo configurarlos está en [Uso con agentes](../agents.md).

| Comando | Lo llama |
|---|---|
| `shogun hook worktree-create` | El hook `WorktreeCreate` de Claude Code. Lee `name` y `cwd` de la entrada estándar, crea (o reutiliza) un worktree provisionado en `feature/<name>` que parte del HEAD del worktree de `cwd`, e imprime su ruta. Si falla un comando posterior a la creación, imprime igualmente la ruta como última línea y sale con 0, con el fallo en la salida de error, para que Claude Code use el worktree. Los comandos posteriores a la creación y las variables declaradas sin aprobar se omiten con una nota por la salida de error; nunca pregunta. |
| `shogun hook worktree-remove` | El hook `WorktreeRemove` de Claude Code. Elimina el worktree de `worktree_path`, borra su rama solo si está mergeada en la rama principal del proyecto (una rama mergeada solo en su propio upstream se conserva, con una nota en la salida de error) y cierra su espacio de Herdr y sus terminales de Orca. |
| `shogun hook session-start` | El hook `SessionStart` de Claude Code. Dentro de un repositorio Git, imprime el contexto. Solo cuando el repositorio está registrado como proyecto añade el entorno del worktree a `CLAUDE_ENV_FILE` (nada cuando el repositorio no declara `env`) e imprime una línea que nombra las variables, sin sus valores; las variables declaradas pendientes de aprobación se nombran en una línea aparte, que pide al agente que te pida revisarlas con `shogun trust`, y no se entregan. Después vienen las [reglas de flujo](#guide) cortas que aplican a esa ubicación, como máximo diez líneas, que empiezan con la línea de prioridad y terminan con un puntero a `shogun guide`. Sin Herdr omite las reglas sobre `shogun open --herdr` y sobre escribir a otras sesiones; las reglas del emulador y del simulador aparecen solo cuando el repositorio declara esos providers. Fuera de un repositorio Git no imprime nada y sale con `0`. |
| `shogun hook agent-state` | Los hooks `UserPromptSubmit`, `Notification`, `Stop` y `SessionEnd` de Claude Code. Lee el JSON del hook por la entrada estándar y registra el estado de la sesión (trabajando, bloqueada, terminada) para el worktree que contiene `cwd`, o borra el registro en `SessionEnd`; una `Notification` de tipo `idle_prompt` significa terminada y `auth_success` se ignora. Cuando el estado es trabajando, renueva además las concesiones del emulador y del simulador que tiene el worktree. No imprime nada, nunca bloquea la sesión y sale con 0 también fuera de un repositorio; una entrada que no es JSON sale con 2. Ver [Estados de los agentes fuera de Herdr](../agents.md#estados-de-los-agentes-fuera-de-herdr). |
| `shogun hook herdr` | El plugin de ShogunAi para Herdr, en `worktree.created` (provisiona el worktree, omitiendo con una nota los comandos posteriores a la creación sin aprobar) y `worktree.removed` (borra su rama si está mergeada en la rama principal del proyecto). Lee `HERDR_PLUGIN_EVENT`, `HERDR_PLUGIN_EVENT_JSON` y `HERDR_BIN_PATH`. |
| `shogun hook orca setup` | El `scripts.setup` de Orca en `orca.yaml`. Aprovisiona el worktree de `ORCA_WORKTREE_PATH` como `shogun provision` (`ORCA_ROOT_PATH` encuentra el repositorio cuando los metadatos Git del worktree no pueden), imprime los archivos copiados y, cuando el worktree queda fuera de la carpeta de worktrees del proyecto, una nota que lo dice. Sale con `2` sin `ORCA_WORKTREE_PATH` y con `1` si el aprovisionamiento falla. Ver [Orca](configuration.md#orca). |
| `shogun hook orca archive` | El `scripts.archive` de Orca. Libera el emulador, el simulador y los daemons de Gradle inactivos del worktree de `ORCA_WORKTREE_PATH`, deja el worktree y su rama a Orca y siempre sale con `0`, imprimiendo en la salida de error lo que no pudo liberar. Otra acción sale con `2`. |

## Ayuda y versión

| Comando | Imprime |
|---|---|
| `shogun help`, `-h`, `--help` | El resumen de uso. |
| `shogun <comando> --help`, `-h` | El uso y las opciones de ese comando, con código de salida 0. |
| `shogun version`, `-v`, `--version` | `shogun <versión>`, la misma versión que la app. |
