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
  "agent": { "agentCommand": "claude" }
}
```

- `repos` asocia un alias (minúsculas, dígitos y guiones) a la ruta de un repositorio, relativa a la carpeta o absoluta. Los repositorios no tienen por qué compartir carpeta padre.
- `worktreesRoot` es una carpeta común bajo la que se propone la carpeta de worktrees de cada repositorio como `<worktreesRoot>/<alias>`.
- `defaultRepos` son los repositorios que cubre `shogun new <id>` en la carpeta del multirepo.
- `agent` es el comando que lanza `shogun open --herdr` en la carpeta del multirepo; por defecto, `claude`.

La carpeta está fuera de todo repositorio, o es la raíz de un **repositorio de workspace** que versiona lo que comparten los repositorios (un `AGENTS.md`, la declaración) e ignora los repositorios anidados. Nunca es una subcarpeta de un repositorio, así que la declaración nunca acaba dentro de un repositorio que no es tuyo.

ShogunAi recuerda los multirepos que conoce en `~/.shogunai/multirepos.json`, un fichero propio; `projects.json` y `settings.json` mantienen su formato, así que una versión anterior sigue funcionando con ellos.

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
- **Limpiar.** Un grupo es candidato solo cuando todos sus worktrees están limpios y fusionados en su base y no hay ningún agente en ninguno. `--yes` elimina sus worktrees con la limpieza habitual de cada repositorio y las mismas opciones que `shogun clean` (`--delete-branches`, `--stop-emulators`, `--down-compose-projects`…).

## Contexto para los agentes

Los agentes no necesitan escribirse para saber del grupo: el hook `SessionStart` de Claude Code da a cada uno su contexto.

- En un worktree de un grupo, el contexto nombra el grupo, los demás repositorios con la ruta de su worktree, los repositorios que no están en el grupo y cómo añadirlos, y el `AGENTS.md` de los repositorios que tienen una carpeta `openspec`.
- En la carpeta del multirepo, lista los repositorios, señala el `AGENTS.md` de la carpeta si lo hay, y `shogun status`, `shogun new` y `shogun open`. En un repositorio de workspace, siguen las reglas de siempre del checkout principal.

## En la app de escritorio

La app de escritorio muestra los mismos multirepos y grupos, calculados igual: una carpeta en la barra lateral, una vista agrupada por cambio con el estado combinado y las acciones de cada grupo, etiquetas de grupo en las demás vistas y diálogos para añadir un multirepo, crear un cambio y editar sus ajustes, que escriben a través de los mismos casos de uso que `shogun multirepo` y `shogun new`. Consulta [Multirepos en la app de escritorio](desktop-app.md#multirepos).

## Un repositorio de workspace

Cuando la carpeta del multirepo es la raíz de un repositorio, `shogun new`, `status`, `open` y `clean` en su checkout principal actúan sobre el multirepo. El repositorio de workspace puede entrar en grupos como cualquier otro una vez declarado con la ruta `.` (`shogun multirepo add-repo workspace .`, y luego `--repos workspace,app`); en sus worktrees enlazados, los comandos se comportan como en cualquier repositorio.
