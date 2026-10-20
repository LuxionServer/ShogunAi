# Uso con agentes

Cada vez más agentes de IA crean sus propios worktrees. Estas recetas hacen que los worktrees que crean queden listos para compilar, con los archivos que tu repositorio declara en `.shogunai.json` (y `.worktreeinclude`) y los archivos locales de tu proyecto, y que sigan visibles y se puedan limpiar desde ShogunAi. Todas necesitan el [comando `shogun`](guide/cli.md) en el `PATH` (ver [Instalación](guide/installation.md#el-comando-shogun)).

## Entorno por worktree

Un worktree aísla ficheros, no puertos. Cuando el repositorio declara variables en `.shogunai.json`, cada worktree recibe sus propios valores:

```json
{
  "env": {
    "PORT": "${port}",
    "API_URL": "http://localhost:${port:api}",
    "APP_SUFFIX": "-${worktree}"
  },
  "envFile": ".env.shogunai"
}
```

- `${port}` reserva un puerto para esa variable y `${port:<clave>}` uno compartido por todas las variables que usan la clave. Los puertos empiezan en 20000, no se repiten entre los worktrees del repositorio, están libres al reservarse y no cambian después. Se guardan en la carpeta que Git tiene para el worktree, así que al eliminarlo se liberan.
- `${worktree}` es el nombre de la carpeta del worktree; `$$` es un `$` literal.
- `shogun env` imprime las variables del worktree actual como líneas `export` (`--format dotenv` o `json` para otras herramientas), así que `eval "$(shogun env)"` las define en una shell.
- `envFile` (opcional) se escribe en cada worktree al crearlo o provisionarlo, en formato dotenv, para las herramientas que leen uno. Nunca se escribe sobre un fichero que Git versiona, así que añádelo a `.gitignore`.

En repositorios Android, `"providers": { "emulator": {} }` añade `ANDROID_SERIAL=emulator-<puerto>` al entorno de cada worktree, así que `./gradlew installDebug`, `connectedAndroidTest` y los comandos `adb` sin más solo usan el emulador de ese worktree. El servidor de adb sí es compartido por todos los worktrees, así que un agente nunca debe reiniciarlo (`adb kill-server`) ni forzar un dispositivo con `-s`, `-e` o `-d`; el hook `SessionStart` se lo dice a Claude. Los agentes lo arrancan con `shogun emulator start` (espera a que el emulador termine de arrancar); los ajustes de AVD y ventana están en [Providers › Emulador de Android](guide/providers.md#emulador-de-android). Del mismo modo, `"providers": { "ios-simulator": {} }` da a cada worktree su propio simulador de iOS e `IOS_SIMULATOR_UDID`; los agentes lo arrancan con `shogun simulator start` y compilan con `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`, y los tests KMP lo usan cuando el script de build (o el init script de la máquina) lo pasa a `device` ([Providers › Tests de KMP](guide/providers.md#tests-de-kmp)).

En repositorios Gradle, los ajustes del provider de Gradle (`"providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 } }`) añaden el mismo `GRADLE_OPTS` al entorno de cada worktree, así que los daemons que arrancan las builds de los agentes usan ese heap y ese timeout de inactividad en vez de los de `gradle.properties`, y se pueden compartir entre worktrees. Sustituye un `GRADLE_OPTS` que ya tuviera la shell y no llega a las builds lanzadas desde un IDE ([Providers › Memoria y vida de los daemons por worktree](guide/providers.md#memoria-y-vida-de-los-daemons-por-worktree)).

En repositorios Xcode, `"providers": { "xcode": { "compilationCache": true } }` añade `XCODE_XCCONFIG_FILE` al entorno de cada worktree, apuntando a un `.xcconfig` de máquina que activa la caché de compilación con prefix mapping, así que el primer `xcodebuild` de un agente en un worktree nuevo reutiliza lo que otros worktrees ya compilaron. Sustituye un `XCODE_XCCONFIG_FILE` que ya tuviera la shell, no llega a Xcode abierto desde el Dock y no ayuda a los paquetes Swift. Los agentes pueden consultar los DerivedData con `shogun xcode` y borrar las carpetas huérfanas con `shogun xcode clean` ([Providers › Xcode](guide/providers.md#xcode)).

Las recetas de abajo muestran cómo recibe cada agente estas variables.

## Aprobar los comandos posteriores a la creación

Los hooks de agentes y el plugin de Herdr no pueden preguntarte nada, así que nunca ejecutan los comandos `hooks.postCreate` de un repositorio hasta que los hayas aprobado en este equipo: provisionan el worktree, omiten los comandos e imprimen una nota por la salida de error que nombra `shogun trust`. Ejecútalo una vez en cada repositorio de confianza:

```bash
shogun trust        # muestra los comandos y pregunta
shogun trust --yes  # aprueba sin preguntar
```

Después los hooks los ejecutan, hasta que cambien los comandos del repositorio; entonces vuelves a aprobar. Detalles: [Aprobar los comandos posteriores a la creación](guide/configuration.md#aprobar-los-comandos-posteriores-a-la-creacion).

## Claude Code

El hook `WorktreeCreate` de Claude Code **sustituye** su propia creación de worktrees para `claude --worktree`, `EnterWorktree`, los subagentes con `isolation: "worktree"` y las sesiones en segundo plano. Apúntalo a `shogun`, junto con `WorktreeRemove` para que la limpieza también pase por ShogunAi. En el `.claude/settings.json` del repositorio (compartido con el equipo) o en tu `~/.claude/settings.json` (todos los repositorios):

```json
{
  "hooks": {
    "WorktreeCreate": [
      { "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }
    ],
    "WorktreeRemove": [
      { "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }
    ],
    "SessionStart": [
      { "hooks": [{ "type": "command", "command": "shogun hook session-start" }] }
    ]
  }
}
```

Qué pasa entonces:

- `claude -w TASK-123` le pide el worktree a `shogun`. Se crea en `feature/TASK-123` dentro de la carpeta de worktrees de tu proyecto de ShogunAi, o en `.claude/worktrees/` (donde los pone normalmente Claude Code) si el repositorio no tiene proyecto, y se provisiona. Claude Code arranca la sesión dentro.
- Ejecutar otra vez `claude -w TASK-123` reutiliza ese worktree.
- Cuando Claude Code elimina el worktree (eliges eliminarlo al salir, o no tenía cambios), `shogun` lo elimina y borra su rama solo si está mergeada; una rama con trabajo se conserva.
- Con este hook Claude Code deja de procesar `.worktreeinclude` por su cuenta; lo lee ShogunAi, así que sus archivos se siguen copiando.
- `SessionStart` añade las variables `env` del worktree al `CLAUDE_ENV_FILE` de la sesión, así que todos los comandos Bash que ejecuta Claude las ven (`$PORT`), y le dice a Claude qué valores usa este worktree, incluido cómo arrancar su emulador o su simulador de iOS cuando el repositorio usa esos providers. Funciona en cualquier sesión que arranque dentro de un worktree, también sin `--worktree`, y no hace nada en repositorios sin `env`.

Claude Code solo ejecuta los hooks de proyecto de un repositorio cuando has aceptado confiar en su carpeta. Si los hooks están en el `.claude/settings.json` del repositorio y nunca has ejecutado `claude` ahí, `claude -w` se detiene con "Workspace trust not yet accepted": ejecuta `claude` una vez en el repositorio, acepta el diálogo de confianza y vuelve a intentarlo. Los hooks de `~/.claude/settings.json` no lo necesitan en los repositorios en los que ya confías.

El worktree nuevo parte del HEAD del worktree donde se ejecuta Claude Code (el commit actual del checkout principal si empiezas ahí), como `shogun new`, y el ajuste `worktree.baseRef` de Claude Code no se aplica. Comprobado con Claude Code 2.1.301.

## Herdr

[Herdr](https://herdr.dev) es un multiplexor de terminal para agentes de IA: mantiene sus sesiones en segundo plano y muestra cuál está trabajando, bloqueado o ha terminado. Herdr ejecuta los agentes y ShogunAi crea y prepara los worktrees en los que trabajan.

**Flujo recomendado.** Crea el worktree desde ShogunAi (la app de escritorio o `shogun new`), para que quede en la carpeta de worktrees del proyecto, provisionado, con sus puertos y su entorno. Después usa la acción "Abrir en Herdr" de su fila en la app de escritorio: Herdr lo abre como worktree de su repositorio, agrupado bajo el espacio del repositorio como su propio "Open worktree…", enfocado, con un único panel cuya shell ya tiene el entorno del worktree (`PORT`, `ANDROID_SERIAL`, …, todo lo que imprime `shogun env`), y ejecuta ahí el comando del agente del proyecto, el mismo que copia "Copiar comando". Si Herdr ya tiene ese worktree abierto, la acción solo lo enfoca y no escribe nada en sus paneles. El servidor de Herdr tiene que estar en marcha (`herdr`); la acción llama directamente a `herdr` y no necesita `shogun` en ningún `PATH`. Desde una terminal, o desde un agente, `shogun open <id de tarea> --herdr` hace lo mismo (`--agent <nombre>` elige otro de los agentes del proyecto).

**Estados de los agentes.** Mientras Herdr está en ejecución, la app de escritorio y `shogun status` muestran el estado que Herdr informa de cada agente de un worktree: trabajando, bloqueado (esperándote, por ejemplo ante una petición de permiso), terminado o inactivo. Un worktree cuyo agente está bloqueado o terminado aparece en "Te necesitan", y "Abrir en Herdr" enfoca el panel de ese agente en lugar de solo abrir el workspace. Los agentes que ve Herdr cuentan como en ejecución aunque la lista de procesos no los vea, así que la limpieza conserva sus worktrees. Si Herdr no está instalado o su servidor no está en marcha, los agentes se muestran sin estado y no se informa de ningún error; tras un fallo, ShogunAi consulta a Herdr como mucho cada 30 segundos.

No uses el "New worktree" de Herdr (ni `herdr worktree create`) en proyectos de ShogunAi: Herdr coloca siempre esos checkouts en su propia carpeta (`~/.herdr/worktrees/<repo>/<rama>`, o el `[worktrees] directory` de su `config.toml`), fuera de la carpeta de worktrees del proyecto, y ShogunAi los muestra con el origen Herdr.

**Camino de rescate: el plugin de ShogunAi para Herdr.** Para los worktrees que se creen desde Herdr de todos modos, por error o porque un agente usa la API de Herdr, instala una vez el plugin:

```bash
herdr plugin install LuxionServer/ShogunAi/integrations/herdr
```

El plugin se instala desde la rama por defecto del repositorio, `main`, que solo recibe código publicado, así que coincide con el `shogun` publicado. El manifiesto y las notas del plugin están en [`integrations/herdr/`](https://github.com/LuxionServer/ShogunAi/tree/main/integrations/herdr) en el repositorio. En cada worktree que crea Herdr ejecuta `shogun hook herdr`, que lo provisiona igual que `shogun provision`: ficheros de `.shogunai.json` y `.worktreeinclude`, los ficheros locales de tu proyecto, puertos reservados y `envFile`. Cuando el repositorio tiene un proyecto de ShogunAi y el worktree queda fuera de su carpeta de worktrees, además muestra una notificación de Herdr que lo avisa y recomienda crear los worktrees desde ShogunAi.

El plugin también escucha `worktree.removed`: cuando quitas un worktree desde Herdr ("Remove worktree" o `herdr worktree remove`) en un repositorio con un proyecto de ShogunAi, `shogun hook herdr` borra su rama si Git la considera mergeada, y la conserva si no, igual que al eliminarlo desde ShogunAi. Ejecuta `herdr plugin update` para tenerlo en una instalación existente.

En el otro sentido no hace falta el plugin: eliminar un worktree desde ShogunAi (la acción de eliminar de la fila, "Limpiar", `shogun clean --yes` o la eliminación de Claude Code) también cierra su workspace de Herdr, y el agente que se ejecuta en él, una vez eliminado el worktree. Los diálogos avisan antes de qué worktrees están abiertos en Herdr; si Herdr no está instalado o en marcha no cambia nada, y si falla el cierre del workspace la eliminación sigue contando y un aviso muestra el mensaje de Herdr.

- El plugin ejecuta `shogun`, así que tiene que estar en el `PATH` de la shell desde la que arrancas Herdr (el servidor en segundo plano de Herdr conserva el entorno de esa shell; reinícialo con `herdr server stop` después de instalar `shogun`). `herdr plugin log` muestra cada ejecución con los ficheros copiados, o el error.
- Herdr abre el workspace nuevo un instante antes de que se ejecute el plugin; un agente que arranques al momento podría encontrarse los ficheros todavía copiándose.
- El plugin no escribe nada en la shell del workspace nuevo. Sus variables llegan a los agentes por los caminos de siempre: el hook `SessionStart` de Claude Code de arriba, el `envFile` o `direnv` (ver más abajo).

## Cursor

Los agentes en paralelo de Cursor ejecutan los comandos de configuración de `.cursor/worktrees.json` dentro de cada worktree nuevo. Provisiónalo ahí:

```json
{
  "setup-worktree": ["shogun provision"]
}
```

Añade después tus otros comandos de preparación (`npm ci`, …). Cursor crea y nombra el worktree por su cuenta; ShogunAi lo muestra como creado en otra ubicación. Los comandos de preparación no dejan variables en la shell del agente, así que para el entorno por worktree declara un `envFile` (el provisionado lo escribe) o empieza los comandos con `eval "$(shogun env)" && …`.

## Codex

Los entornos locales de Codex ejecutan un script de preparación cuando crean un worktree para un hilo nuevo. Usa `shogun provision` como ese script (o al principio de él). La documentación de Codex no dice en qué directorio se ejecuta el script; si no es el worktree nuevo, pásale su ruta: `shogun provision <ruta del worktree>`. Esta receta aún no se ha verificado con Codex. Como con Cursor, usa un `envFile` o `eval "$(shogun env)"` para el entorno por worktree.

## Cualquier otro agente o herramienta

- **Que ShogunAi cree el worktree y luego arrancar el agente dentro:**

    ```bash
    cd "$(shogun new TASK-123)" && codex
    ```

- **La herramienta crea el worktree y puede ejecutar un comando después:** ejecuta `shogun provision <ruta del worktree>` (o `shogun provision` a secas desde dentro).
- **Entorno por worktree en tu propia shell:** con [direnv](https://direnv.net), versiona un `.envrc` que contenga `eval "$(shogun env)"`; cada worktree carga sus propios valores al entrar con `cd`. Sin direnv, ejecuta tú `eval "$(shogun env)"`.
- **Limpiar lo que se haya acumulado, venga de donde venga:** `shogun clean` lista los worktrees mergeados y los que ya no tienen carpeta; `shogun clean --yes` los elimina.
