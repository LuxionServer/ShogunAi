# Multirepos y grupos

Algunos productos viven en varios repositorios: una app, su backend, su documentación. Un **multirepo** le dice a ShogunAi qué repositorios van juntos, para que un change abarque varios de ellos mientras cada repositorio conserva su propio worktree, su propio entorno y su propio agente, y un agente de todo el multirepo los coordina.

De un grupo no se guarda nada: ShogunAi calcula los grupos a partir de Git y de la declaración del multirepo, así que los worktrees creados por Herdr, Claude Code o un `git worktree add` se agrupan igual que los que crea ShogunAi.

## El multirepo

Un multirepo se declara con un fichero `.shogunai-multirepo.json` en una carpeta que eliges tú. Lo escriben los comandos `shogun multirepo` (o la app de escritorio), nunca a mano:

```json
{
  "name": "Product",
  "repos": { "docs": "product-docs", "app": "product-app", "api": "product-api" },
  "worktreesRoot": "../product-worktrees",
  "defaultRepos": ["app", "api"],
  "workspace": { "alias": "workspace", "default": false },
  "env": { "app": { "API_URL": "http://localhost:${port:api.http}" } }
}
```

- `name` es opcional: el nombre con el que se muestra el multirepo en la barra lateral, en `shogun multirepo show`, en el contexto de los agentes y en Herdr. Sin él, el multirepo se llama como su carpeta, lo que viene bien cuando la carpeta de un repositorio de workspace no se llama como el producto. Las rutas y los alias nunca dependen de él. Puede tener como máximo 80 caracteres y ningún carácter de control ni de dirección del texto.
- `repos` asocia un alias (minúsculas, dígitos y guiones) a la ruta de un repositorio, relativa a la carpeta o absoluta. Los repositorios no tienen por qué compartir carpeta padre.
- `worktreesRoot` es una carpeta común bajo la que se propone la carpeta de worktrees de cada repositorio como `<worktreesRoot>/<alias>`.
- `defaultRepos` son los repositorios que cubre `shogun new <id>` en la carpeta del multirepo.
- `workspace` es opcional: `false`, o un objeto con el `alias` del [repositorio de workspace](#un-repositorio-de-workspace) y si es un repositorio por defecto (`default`). Los archivos que no lo tienen siguen funcionando.
- `env` es opcional: las [variables que el multirepo comparte](#variables-compartidas) con los worktrees de cada repositorio.

La carpeta está fuera de todo repositorio, o es la raíz de un **repositorio de workspace** que versiona lo que comparten los repositorios (un `AGENTS.md`, la declaración) e ignora los repositorios anidados. Nunca es una subcarpeta de un repositorio, así que la declaración nunca acaba dentro de un repositorio que no es tuyo.

ShogunAi recuerda los multirepos que conoce en `~/.shogunai/multirepos.json`, un fichero propio, junto con su aprobación y el agente del multirepo fijado en este equipo; `projects.json` y `settings.json` mantienen su formato, así que una versión anterior sigue funcionando con ellos.

El agente del multirepo, el comando que lanza `shogun open --herdr` en la carpeta del multirepo, se fija con `shogun multirepo agent` (o en los ajustes del multirepo en la app) y se guarda en este equipo; es `claude`, sin Headroom, hasta que fijes otro (`--headroom` lo envuelve con `headroom wrap`). Nunca se lee de la declaración: una declaración escrita por una versión anterior puede tener todavía un `agent`, que se ignora (`shogun multirepo show` lo indica) y desaparece la próxima vez que ShogunAi escribe el archivo.

Si la carpeta de un multirepo se mueve, ejecuta `shogun multirepo init` en su nuevo sitio (o añade la carpeta desde la app de escritorio): en vez de negarse, muestra la declaración para revisarla como hace `shogun multirepo trust` y, cuando la apruebas, la registra tal como está y registra un proyecto para cada repositorio declarado que no lo tenga, proponiendo `<worktreesRoot>/<alias>` como hace `add-repo`. Tanto `init` como la app de escritorio olvidan además las entradas de `~/.shogunai/multirepos.json` cuya carpeta ya no existe; una entrada cuya carpeta existe pero no tiene declaración se conserva y se ignora. Un multirepo en un volumen que no está montado también se olvida, y `shogun multirepo init` en él lo recupera.

```bash
cd ~/projects/product
shogun multirepo init                       # escribe .shogunai-multirepo.json, worktreesRoot ../product-worktrees
shogun multirepo name "Product"             # opcional; --clear vuelve al nombre de la carpeta
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

La revisión lista el nombre declarado, cada repositorio con la ruta a la que apunta, la carpeta de worktrees, el repositorio de workspace y las variables compartidas con sus plantillas, y marca toda ruta que quede fuera de la carpeta padre del multirepo. La aprobación cubre exactamente eso: si un pull o una edición a mano cambia el nombre, la ruta de un repositorio, `worktreesRoot`, el ajuste de workspace o una variable compartida, el multirepo deja de aplicarse hasta que lo revises de nuevo. Cambiar `defaultRepos` no necesita otra aprobación. Lo que escribe el propio ShogunAi (`shogun multirepo init`, `add-repo`, `defaults`…, la app de escritorio) sigue aprobado, y esos comandos se niegan a escribir un multirepo que necesita revisión. Los multirepos registrados antes de que existiera esta comprobación necesitan una revisión tras actualizar.

## Grupos

Un **grupo** es una rama con exactamente el mismo nombre, por ejemplo `feature/TASK-12`, en worktrees de dos o más repositorios del multirepo, esté donde esté cada worktree. Los prefijos no se interpretan (`feature/TASK-9` y `fix/TASK-9` son ramas distintas), y los checkouts principales no se agrupan. Los worktrees que ya existen con ramas que coinciden forman un grupo donde están: no se mueve, renombra ni cambia nada, y los agentes en marcha reciben el contexto del grupo en su siguiente `SessionStart`.

```bash
cd ~/projects/product
shogun new TASK-12                  # app y api (los de por defecto) reciben feature/TASK-12
shogun new TASK-12 --repos docs     # más tarde: docs se une al grupo
shogun status                       # cada grupo, por repositorio, y los worktrees sin grupo
shogun open TASK-12 --herdr         # cada worktree bajo el espacio de su repositorio, con su agente
shogun open --herdr                 # el agente del multirepo, en un workspace de Herdr propio
shogun open TASK-12 --orca          # lo mismo en Orca, cada worktree bajo su propio repositorio de Orca
shogun open --orca                  # el agente del multirepo, en el espacio de trabajo de Orca de la carpeta
shogun clean --yes                  # elimina los grupos cuyos worktrees están todos fusionados y sin agentes
```

- **Crear.** `shogun new <id>` hace en cada repositorio lo que `shogun new <id>` hace hoy en él (la carpeta de worktrees del proyecto, el nombre de carpeta de siempre, el provisionado), salvo que la rama parte del `origin/<base>` de ese repositorio. Los repositorios que ya están en la rama se informan y no se tocan, una rama que existe sin worktree se usa tal cual, y un repositorio que falla no deshace los demás. No abre nada. Dentro de un repositorio, `shogun new <id>` sin `--repos` sigue creando un solo worktree.
- **Estado.** En la carpeta del multirepo, `shogun status` lista cada grupo con, por repositorio, su ruta, cambios, adelanto y retraso respecto a su propia base, si está fusionado y sus agentes, y después los worktrees sin grupo.
- **Abrir.** `shogun open <id> --herdr` abre cada worktree del grupo como se abre un worktree suelto, bajo el espacio de su repositorio con su entorno y el agente de su proyecto: un worktree con su agente vivo se enfoca, uno cuyo espacio de trabajo está abierto sin agente arranca su agente en un panel nuevo, y cada uno se informa con el panel de su agente. `<id>` es una rama, o un id de tarea cuando solo uno de `feature/<id>` y `fix/<id>` es un grupo. `shogun open --herdr` trata igual el espacio de trabajo del agente del multirepo.
- **Abrir en Orca.** `shogun open <id> --orca` abre cada worktree del grupo, uno tras otro, como `shogun open --orca` abre uno: bajo su propio repositorio de Orca (que se registra si Orca no lo conoce), con su entorno, el agente de su proyecto y su nombre de sesión del grupo. Cada uno se informa como arrancado o enfocado con su terminal de Orca, y uno que falla no detiene a los demás. Si importaste el multirepo en Orca como grupo ("¿Agrupar estos repositorios?"), sus worktrees aparecen anidados bajo ese grupo; ShogunAi nunca mueve repositorios entre los grupos de Orca. `shogun open --orca` ejecuta el agente del multirepo en el espacio de trabajo de Orca cuya ruta es la carpeta del multirepo, enfocando su agente vivo o arrancando el agente en una terminal nueva allí. Una carpeta de multirepo que es un repositorio Git no necesita nada: ShogunAi la registra en Orca cuando hace falta y usa su worktree principal. Una carpeta que no es un repositorio Git solo puede recibir un espacio de trabajo desde la app de Orca, una vez: añade la carpeta como proyecto, acepta importar sus repositorios como grupo y añade un espacio de trabajo de carpeta a ese grupo; o añádela y elige "Abrir como carpeta". Hasta entonces `open --orca` falla explicando estos pasos y no cambia nada en Orca.
- **Nombres de sesión.** Cuando el agente es Claude Code, cada sesión arranca con un nombre: `<alias>-<id>` para cada worktree del grupo (`app-task-123`, `api-task-123`) y el nombre del multirepo para el agente del multirepo, hecho único con `-2`, `-3`… si una sesión viva ya lo tiene. `open` informa de los nombres (`as session app-task-123`, `session_name` en `--json`) y `shogun status` los muestra en la línea de cada repositorio (`agents=claude(working, app-task-123)`, `sessions` en `--json`), así la sesión coordinadora ve qué sesión corre en qué repositorio y envía mensajes a cada miembro del grupo por su nombre.
- **Limpiar.** Un grupo es candidato solo cuando todos sus worktrees están limpios y fusionados en su base y no hay ningún agente en ninguno. `--yes` elimina sus worktrees con la limpieza habitual de cada repositorio y las mismas opciones que `shogun clean` (`--delete-branches`, `--stop-emulators`, `--down-compose-projects`…). Después de los grupos, `shogun clean` también lista los worktrees sueltos de cada repositorio (incluido el de workspace) que no pertenecen a ningún grupo, cuando están limpios, fusionados y sin agente, y los elimina con la misma confirmación.

## Variables compartidas

Los repositorios de un grupo suelen necesitar los puertos de los demás: el worktree de la app en `feature/TASK-12` debe hablar con el backend que corre en su propio worktree de `api`, no con lo que escuche en un puerto fijo. El `env` del multirepo da ese enlace. Asocia un alias a las variables que el multirepo añade a los worktrees de ese repositorio, y un valor puede usar `${port:<alias>.<clave>}`, el puerto que ese repositorio reserva para su clave `<clave>`:

```json
// product-api/.shogunai.json
{ "env": { "SERVER_PORT": "${port:http}" } }

// .shogunai-multirepo.json
{ "env": { "app": { "API_URL": "http://localhost:${port:api.http}" } } }
```

- **Qué puerto.** Un worktree de `app` recibe el puerto del worktree de `api` en la misma rama, su pareja en el grupo. Cuando `api` no tiene worktree en esa rama (un worktree de `app` suelto, un grupo sin `api`, o el checkout principal de `app`), recibe el puerto del checkout principal de `api`. El puerto es la reserva propia de `api`: resolver el entorno de `app` lo reserva en el worktree de `api` exactamente como lo haría su propio `shogun env`, así que ambos ven el mismo número.
- **Plantillas.** Además de `${port:<alias>.<clave>}`, los valores pueden usar `${worktree}` (la carpeta del worktree que recibe la variable) y `$$`. Los puertos propios de un repositorio, `${port}` y `${port:<clave>}`, se declaran en su `.shogunai.json`, no aquí. Los nombres y valores siguen las reglas del `env` de un repositorio, [nombres reservados](configuration.md#nombres-de-variable-reservados) incluidos.
- **Entrega.** Las variables compartidas llegan al worktree allí donde llegan las suyas: `shogun env`, el hook `SessionStart`, abrirlo en la terminal o en Herdr, y el `envFile`. Una variable que declara el propio `.shogunai.json` del repositorio gana a una compartida con el mismo nombre, y una compartida gana a la de un provider.
- **Aprobación.** Se aplican mientras el multirepo esté aprobado, sea cual sea la aprobación del repositorio que las recibe. El repositorio referenciado sí debe estar aprobado, porque sus puertos solo se reservan cuando lo está.
- **Cuando no se puede resolver.** Si `api` no está aprobado, no declara la clave o su worktree no se puede leer, la variable se deja fuera: `shogun env` dice por qué en la salida de error y el contexto de la sesión la nombra; el resto se entrega.
- **Refresco.** Una sesión conserva el valor con el que empezó. Si el worktree pareja de `api` se crea después de empezar la sesión de `app`, el siguiente `shogun env`, la siguiente sesión o la siguiente apertura lo recogen.

Las variables compartidas se editan con `shogun multirepo env` o en los ajustes del multirepo de la app de escritorio:

```bash
shogun multirepo env                                                      # listarlas
shogun multirepo env set app API_URL 'http://localhost:${port:api.http}'  # añadir o sustituir una
shogun multirepo env unset app API_URL                                    # quitarla
```

Pon la plantilla entre comillas simples para que la shell no toque `${…}`. Cada cambio se comprueba con las reglas de arriba antes de escribir nada, y mantiene aprobado el multirepo como cualquier otra escritura de ShogunAi; un multirepo que necesita revisión hay que revisarlo antes. Editar el fichero a mano sigue funcionando, y entonces se revisa con `shogun multirepo trust`.

Renombrar un repositorio (o declarar el repositorio de workspace con otro alias) mueve sus variables compartidas y reescribe las referencias `${port:<alias>.<clave>}` a él. Quitar un repositorio, o desactivar el repositorio de workspace, elimina sus propias variables compartidas, y se rechaza mientras una variable de otro repositorio lo referencie: cámbialas o quítalas antes. Una versión anterior de ShogunAi que reescriba la declaración pierde las variables compartidas.

## Contexto para los agentes

Los agentes no necesitan escribirse para saber del grupo: el hook `SessionStart` de Claude Code da a cada uno su contexto.

- En un worktree de un grupo, el contexto nombra el grupo, los demás repositorios con la ruta de su worktree, los repositorios que no están en el grupo y cómo añadirlos, y el `AGENTS.md` de los repositorios que tienen una carpeta `openspec`.
- En la carpeta del multirepo, lista los repositorios, señala el `AGENTS.md` de la carpeta si lo hay, y `shogun status`, `shogun new` y `shogun open`. En un repositorio de workspace, siguen las reglas de siempre del checkout principal.

## En la app de escritorio

La app de escritorio muestra los mismos multirepos y grupos, calculados igual: una carpeta en la barra lateral, una vista agrupada por cambio con el estado combinado y las acciones de cada grupo, etiquetas de grupo en las demás vistas y diálogos para añadir un multirepo, crear un cambio y editar sus ajustes, variables compartidas incluidas, que escriben a través de los mismos casos de uso que `shogun multirepo` y `shogun new`. Consulta [Multirepos en la app de escritorio](desktop-app.md#multirepos).

## Un repositorio de workspace

Cuando la carpeta del multirepo es la raíz de un repositorio Git, `shogun new`, `status`, `open` y `clean` en su checkout principal actúan sobre el multirepo, y la carpeta se **detecta** como el repositorio de workspace del multirepo con el alias `workspace`, sin necesidad de declararlo:

- Entra en grupos como cualquier otro repositorio: `shogun new TASK-12 --repos workspace,app` crea su worktree en `<worktreesRoot>/workspace/TASK-12`. No es uno de los `defaultRepos` salvo que lo indiques.
- `shogun multirepo show` lo lista marcado como `detected`. `shogun multirepo workspace set [--alias <alias>] [--default]` lo declara en el archivo (`"workspace": { "alias": … }`), `shogun multirepo workspace clear` desactiva la detección (`"workspace": false`) y `shogun multirepo workspace detect` quita el campo, de modo que la carpeta vuelve a detectarse como `workspace` (y deja de ser un repositorio por defecto). Una entrada de `repos` con la ruta `.` también convierte la carpeta en el repositorio de workspace, con ese alias.
- La carpeta no es un proyecto propio: la barra lateral lista sus worktrees bajo el multirepo, y `shogun project add` y `shogun app` ejecutados en la carpeta registran una sola vez el repositorio de workspace, como miembro del multirepo, en vez de un proyecto suelto. Con la detección desactivada, la carpeta vuelve a ser un proyecto normal.
- El agente del multirepo (el que corre en la carpeta) se muestra en la vista del multirepo. Los registros de sesión de Claude Code se guardan en el directorio Git de la carpeta cuando es un repositorio, y si no en un almacén bajo `~/.shogunai/agent-sessions/` con la carpeta como clave, así que el hook `agent-state` también funciona en una carpeta de multirepo que no es un repositorio. Solo cuenta como agente del multirepo el que tiene como directorio de trabajo exactamente la carpeta; uno en un repositorio miembro pertenece al worktree de ese repositorio.

En sus worktrees enlazados, los comandos se comportan como en cualquier repositorio.
