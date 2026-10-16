# Uso con agentes

Cada vez más agentes de IA crean sus propios worktrees. Estas recetas hacen que los worktrees que crean queden listos para compilar, con los archivos que tu repositorio declara en `.shogunai.json` (y `.worktreeinclude`) y los archivos locales de tu proyecto, y que sigan visibles y se puedan limpiar desde ShogunAi. Todas necesitan el [comando `shogun`](architecture/cli.md) en el `PATH`.

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

En repositorios Android, `"providers": { "emulator": {} }` añade `ANDROID_SERIAL=emulator-<puerto>` al entorno de cada worktree, así que `./gradlew installDebug` y `connectedAndroidTest` solo usan el emulador de ese worktree. Los agentes lo arrancan con `shogun emulator start` (espera a que el emulador termine de arrancar); los ajustes de AVD y ventana están en la sección "Emuladores de Android" del README. Del mismo modo, `"providers": { "ios-simulator": {} }` da a cada worktree su propio simulador de iOS e `IOS_SIMULATOR_UDID`; los agentes lo arrancan con `shogun simulator start` y compilan con `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`, y los tests KMP lo usan cuando el script de build (o el init script de la máquina) lo pasa a `device` (README, "Simuladores de iOS").

En repositorios Gradle, los ajustes del provider de Gradle (`"providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 } }`) añaden el mismo `GRADLE_OPTS` al entorno de cada worktree, así que los daemons que arrancan las builds de los agentes usan ese heap y ese timeout de inactividad en vez de los de `gradle.properties`, y se pueden compartir entre worktrees. Sustituye un `GRADLE_OPTS` que ya tuviera la shell y no llega a las builds lanzadas desde un IDE (README, "Memoria y vida de los daemons por worktree").

Las recetas de abajo muestran cómo recibe cada agente estas variables.

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

El worktree nuevo parte del commit actual del checkout principal, como los worktrees creados desde ShogunAi, y el ajuste `worktree.baseRef` de Claude Code no se aplica. Comprobado con Claude Code 2.1.301.

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
