# Multirepos y grupos

Algunos productos viven en varios repositorios: una app, su backend, su documentación. Un **multirepo** le dice a ShogunAi qué repositorios van juntos, para que un change abarque varios de ellos mientras cada repositorio conserva su propio worktree, su propio entorno y su propio agente, y un agente de todo el multirepo los coordina.

De un grupo no se guarda nada: ShogunAi calcula los grupos a partir de Git y de la declaración del multirepo, así que los worktrees creados por Herdr, Claude Code o un `git worktree add` se agrupan igual que los que crea ShogunAi.

## El multirepo

Un multirepo se declara con un fichero `.shogunai-multirepo.json` en una carpeta que eliges tú. Lo escriben los comandos `shogun multirepo` (o la app de escritorio), nunca a mano:

```json
{
  "repos": { "docs": "product-docs", "app": "product-app", "api": "product-api" },
  "worktreesRoot": "../product-worktrees",
  "defaultRepos": ["app", "api"],
  "workspace": { "alias": "workspace", "default": false }
}
```

- `repos` asocia un alias (minúsculas, dígitos y guiones) a la ruta de un repositorio, relativa a la carpeta o absoluta. Los repositorios no tienen por qué compartir carpeta padre.
- `worktreesRoot` es una carpeta común bajo la que se propone la carpeta de worktrees de cada repositorio como `<worktreesRoot>/<alias>`.
- `defaultRepos` son los repositorios que cubre `shogun new <id>` en la carpeta del multirepo.
- `workspace` es opcional: `false`, o un objeto con el `alias` del [repositorio de workspace](#un-repositorio-de-workspace) y si es un repositorio por defecto (`default`). Los archivos que no lo tienen siguen funcionando.

La carpeta está fuera de todo repositorio, o es la raíz de un **repositorio de workspace** que versiona lo que comparten los repositorios (un `AGENTS.md`, la declaración) e ignora los repositorios anidados. Nunca es una subcarpeta de un repositorio, así que la declaración nunca acaba dentro de un repositorio que no es tuyo.

ShogunAi recuerda los multirepos que conoce en `~/.shogunai/multirepos.json`, un fichero propio, junto con su aprobación y el agente del multirepo fijado en este equipo; `projects.json` y `settings.json` mantienen su formato, así que una versión anterior sigue funcionando con ellos.

El agente del multirepo, el comando que lanza `shogun open --herdr` en la carpeta del multirepo, se fija con `shogun multirepo agent` (o en los ajustes del multirepo en la app) y se guarda en este equipo; es `claude` con headroom hasta que fijes otro. Nunca se lee de la declaración: una declaración escrita por una versión anterior puede tener todavía un `agent`, que se ignora (`shogun multirepo show` lo indica) y desaparece la próxima vez que ShogunAi escribe el archivo.

```bash
cd ~/projects/product
shogun multirepo init                       # escribe .shogunai-multirepo.json, worktreesRoot ../product-worktrees
shogun multirepo add-repo app product-app   # reutiliza el proyecto de product-app, o registra uno
shogun multirepo add-repo api product-api
shogun multirepo defaults app,api
shogun multirepo agent claude
shogun multirepo show
```

Al añadir un repositorio se reutiliza su proyecto de ShogunAi sin cambios (ajustes, agentes, archivos locales). Si no tiene, se registra uno con los valores que propone la app de escritorio. Después ShogunAi propone `<worktreesRoot>/<alias>` como carpeta de worktrees del proyecto, para que el disco quede organizado como los espacios de Herdr (`product-worktrees/app/TASK-12`, `product-worktrees/api/TASK-12`). Lo pregunta en un terminal, acepta `--use-worktrees-root` o `--keep-worktrees-folder`, nunca lo cambia en modo `--json` y no pregunta si la carpeta ya coincide. Cambiar la carpeta solo afecta a los worktrees que se creen después; los que existen se quedan donde están.

`shogun multirepo remove-repo <alias>` y `shogun multirepo delete` solo cambian la declaración: proyectos, worktrees, ramas, puertos y workspaces de Herdr se quedan como estaban.

### Aprobar un multirepo

Una declaración puede llegar con un clon, por ejemplo en un repositorio de workspace, así que ShogunAi solo usa un multirepo que hayas aprobado en este equipo. Un `.shogunai-multirepo.json` que no está registrado, o que ha cambiado desde que lo aprobaste, se ignora: los comandos se comportan como fuera de un multirepo e imprimen un aviso con el archivo y `shogun multirepo trust` (los hooks no dicen nada).

```bash
shogun multirepo trust            # la declaración más cercana: la muestra y pregunta
shogun multirepo trust --yes      # aprobar sin preguntar
shogun multirepo trust --revoke   # olvidarlo; el archivo se queda
```

La revisión lista cada repositorio con la ruta a la que apunta, la carpeta de worktrees y el repositorio de workspace, y marca toda ruta que quede fuera de la carpeta padre del multirepo. La aprobación cubre exactamente eso: si un pull o una edición a mano cambia la ruta de un repositorio, `worktreesRoot` o el ajuste de workspace, el multirepo deja de aplicarse hasta que lo revises de nuevo. Cambiar `defaultRepos` no necesita otra aprobación. Lo que escribe el propio ShogunAi (`shogun multirepo init`, `add-repo`, `defaults`…, la app de escritorio) sigue aprobado, y esos comandos se niegan a escribir un multirepo que necesita revisión. Los multirepos registrados antes de que existiera esta comprobación necesitan una revisión tras actualizar.

## Grupos

Un **grupo** es una rama con exactamente el mismo nombre, por ejemplo `feature/TASK-12`, en worktrees de dos o más repositorios del multirepo, esté donde esté cada worktree. Los prefijos no se interpretan (`feature/TASK-9` y `fix/TASK-9` son ramas distintas), y los checkouts principales no se agrupan. Los worktrees que ya existen con ramas que coinciden forman un grupo donde están: no se mueve, renombra ni cambia nada, y los agentes en marcha reciben el contexto del grupo en su siguiente `SessionStart`.

```bash
cd ~/projects/product
shogun new TASK-12                  # app y api (los de por defecto) reciben feature/TASK-12
shogun new TASK-12 --repos docs     # más tarde: docs se une al grupo
shogun status                       # cada grupo, por repositorio, y los worktrees sin grupo
shogun open TASK-12 --herdr         # cada worktree bajo el espacio de su repositorio, con su agente
shogun open --herdr                 # el agente del multirepo, en un workspace de Herdr propio
shogun clean --yes                  # elimina los grupos cuyos worktrees están todos fusionados y sin agentes
```

- **Crear.** `shogun new <id>` hace en cada repositorio lo que `shogun new <id>` hace hoy en él (la carpeta de worktrees del proyecto, el nombre de carpeta de siempre, el provisionado), salvo que la rama parte del `origin/<base>` de ese repositorio. Los repositorios que ya están en la rama se informan y no se tocan, una rama que existe sin worktree se usa tal cual, y un repositorio que falla no deshace los demás. No abre nada. Dentro de un repositorio, `shogun new <id>` sin `--repos` sigue creando un solo worktree.
- **Estado.** En la carpeta del multirepo, `shogun status` lista cada grupo con, por repositorio, su ruta, cambios, adelanto y retraso respecto a su propia base, si está fusionado y sus agentes, y después los worktrees sin grupo.
- **Abrir.** `shogun open <id> --herdr` abre cada worktree del grupo como se abre un worktree suelto, bajo el espacio de su repositorio con su entorno y el agente de su proyecto; `<id>` es una rama, o un id de tarea cuando solo uno de `feature/<id>` y `fix/<id>` es un grupo.
- **Limpiar.** Un grupo es candidato solo cuando todos sus worktrees están limpios y fusionados en su base y no hay ningún agente en ninguno. `--yes` elimina sus worktrees con la limpieza habitual de cada repositorio y las mismas opciones que `shogun clean` (`--delete-branches`, `--stop-emulators`, `--down-compose-projects`…). Después de los grupos, `shogun clean` también lista los worktrees sueltos de cada repositorio (incluido el de workspace) que no pertenecen a ningún grupo, cuando están limpios, fusionados y sin agente, y los elimina con la misma confirmación.

## Contexto para los agentes

Los agentes no necesitan escribirse para saber del grupo: el hook `SessionStart` de Claude Code da a cada uno su contexto.

- En un worktree de un grupo, el contexto nombra el grupo, los demás repositorios con la ruta de su worktree, los repositorios que no están en el grupo y cómo añadirlos, y el `AGENTS.md` de los repositorios que tienen una carpeta `openspec`.
- En la carpeta del multirepo, lista los repositorios, señala el `AGENTS.md` de la carpeta si lo hay, y `shogun status`, `shogun new` y `shogun open`. En un repositorio de workspace, siguen las reglas de siempre del checkout principal.

## En la app de escritorio

La app de escritorio muestra los mismos multirepos y grupos, calculados igual: una carpeta en la barra lateral, una vista agrupada por cambio con el estado combinado y las acciones de cada grupo, etiquetas de grupo en las demás vistas y diálogos para añadir un multirepo, crear un cambio y editar sus ajustes, que escriben a través de los mismos casos de uso que `shogun multirepo` y `shogun new`. Consulta [Multirepos en la app de escritorio](desktop-app.md#multirepos).

## Un repositorio de workspace

Cuando la carpeta del multirepo es la raíz de un repositorio Git, `shogun new`, `status`, `open` y `clean` en su checkout principal actúan sobre el multirepo, y la carpeta se **detecta** como el repositorio de workspace del multirepo con el alias `workspace`, sin necesidad de declararlo:

- Entra en grupos como cualquier otro repositorio: `shogun new TASK-12 --repos workspace,app` crea su worktree en `<worktreesRoot>/workspace/TASK-12`. No es uno de los `defaultRepos` salvo que lo indiques.
- `shogun multirepo show` lo lista marcado como `detected`. `shogun multirepo workspace set [--alias <alias>] [--default]` lo declara en el archivo (`"workspace": { "alias": … }`), y `shogun multirepo workspace clear` desactiva la detección (`"workspace": false`). Una entrada de `repos` con la ruta `.` también convierte la carpeta en el repositorio de workspace, con ese alias.
- La carpeta no es un proyecto propio: la barra lateral lista sus worktrees bajo el multirepo, y `shogun project add` y `shogun app` ejecutados en la carpeta registran una sola vez el repositorio de workspace, como miembro del multirepo, en vez de un proyecto suelto. Con la detección desactivada, la carpeta vuelve a ser un proyecto normal.
- El agente del multirepo (el que corre en la carpeta) se muestra en la vista del multirepo. Los registros de sesión de Claude Code se guardan en el directorio Git de la carpeta cuando es un repositorio, y si no en un almacén bajo `~/.shogunai/agent-sessions/` con la carpeta como clave, así que el hook `agent-state` también funciona en una carpeta de multirepo que no es un repositorio. Solo cuenta como agente del multirepo el que tiene como directorio de trabajo exactamente la carpeta; uno en un repositorio miembro pertenece al worktree de ese repositorio.

En sus worktrees enlazados, los comandos se comportan como en cualquier repositorio.
