# Configuración

ShogunAi lee dos tipos de configuración: lo que un **repositorio** declara para todo el que lo clona, versionado en el propio repositorio, y lo que **esta máquina** guarda para ti en `~/.shogunai/`.

## Configuración del repositorio (`.shogunai.json`)

Un `.shogunai.json` en la raíz del repositorio declara lo que necesita cada worktree. Todas las claves son opcionales, y un fichero en blanco cuenta como `{}`:

```json
{
  "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"],
  "link": ["/keys/"],
  "hooks": { "postCreate": [["npm", "install"], ["direnv", "allow"]] },
  "env": {
    "PORT": "${port}",
    "API_URL": "http://localhost:${port:api}"
  },
  "envFile": ".env.shogunai",
  "providers": {
    "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 },
    "emulator": { "avd": "Pixel_8_API_35" }
  }
}
```

| Clave | Tipo | Significado |
|---|---|---|
| `copy` | lista de cadenas | Patrones al estilo gitignore. Cada archivo que Git no versiona y coincide con alguno se copia a cada worktree nuevo, en la misma ruta. |
| `link` | lista de cadenas | Los mismos patrones que `copy`, pero cada archivo que coincide se enlaza con un enlace simbólico al archivo del repositorio base en vez de copiarse, así que al rotarlo allí se actualiza en todos los worktrees. Un archivo que coincide con ambas listas se enlaza. Donde no se pueden crear enlaces simbólicos (Windows sin el privilegio), el archivo se copia. |
| `hooks.postCreate` | lista de comandos | Comandos que se ejecutan dentro de cada worktree tras crearlo o provisionarlo, cuando sus archivos ya están en su sitio y su entorno preparado. Cada comando es una lista de cadenas dada como argv, no una cadena de shell; una única lista de cadenas se lee como un solo comando. |
| `env` | objeto de cadenas | Variables de entorno por worktree. Los nombres usan letras, dígitos y `_`, y no empiezan por un dígito. Los nombres que cambian el comportamiento de los shells, los entornos de ejecución, el cargador dinámico o Git están reservados e invalidan el archivo: `PATH`, `HOME`, `SHELL`, `IFS`, `ENV`, `BASH_ENV`, `ZDOTDIR`, `PROMPT_COMMAND`, `NODE_OPTIONS`, `NODE_PATH`, `PYTHONPATH`, `PYTHONSTARTUP`, `PYTHONHOME`, `RUBYOPT`, `RUBYLIB`, `PERL5OPT`, `PERL5LIB`, `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`, `CLASSPATH` y cualquier nombre que empiece por `LD_`, `DYLD_` o `GIT_`. Un script del proyecto puede definirlos por su cuenta. |
| `envFile` | cadena | Una ruta dentro del worktree donde se escribe su entorno en formato dotenv. Nunca se escribe sobre un archivo que Git versiona, así que añádelo a `.gitignore`. |
| `providers` | objeto | Los [providers](providers.md) que usa el repositorio, cada uno con sus opciones. |

Los valores de `env` pueden usar estas plantillas:

| Plantilla | Se convierte en |
|---|---|
| `${port}` | Un puerto reservado para esa variable. |
| `${port:<clave>}` | Un puerto compartido por todas las variables que usan la misma clave. |
| `${worktree}` | El nombre de la carpeta del worktree. |
| `$$` | Un `$` literal. |

Los puertos no se repiten entre los worktrees del repositorio, están libres al reservarse y no cambian después. [Uso con agentes](../agents.md#entorno-por-worktree) explica cómo reciben estas variables los agentes y las shells.

Enlazar comparte el archivo: una herramienta de un worktree que sobrescriba un secreto enlazado sobrescribe también el del repositorio base. Deja `copy` para los archivos que un worktree pueda modificar. Los archivos locales del proyecto siempre se copian.

Los comandos posteriores a la creación se ejecutan en orden y se detienen en el primero que falla. Un fallo hace fallar la creación o el provisionado con un error que nombra el comando, su código de salida y su salida de error, pero el worktree se conserva, porque su checkout y sus archivos están bien. Los comandos se ejecutan al crear y en cada `shogun provision`, así que hazlos seguros de repetir, y declara solo comandos que ejecutarías desde un repositorio de confianza. No reciben las variables de entorno del worktree; un comando que las necesite puede ejecutar `shogun env`.

### Aprobar los comandos posteriores a la creación

Como `.shogunai.json` se versiona, quien controla una rama controla sus comandos. Por eso solo se ejecutan cuando los has aprobado en este equipo, para ese repositorio. La aprobación cubre exactamente la lista que viste: si un pull cambia un comando, añade uno o los reordena, la lista cuenta como no aprobada y ShogunAi vuelve a preguntar. Las aprobaciones se guardan en `~/.shogunai/trust.json`, legible solo por ti.

- La app de escritorio muestra los comandos en el diálogo de creación y ofrece «Ejecutar y recordar» u «Omitir». Omitir crea el worktree sin ejecutarlos. Los ajustes del proyecto listan los comandos del repositorio, si están aprobados en este equipo, y permiten aprobarlos o revocarlos.
- `shogun new` y `shogun provision` imprimen los comandos y preguntan en un terminal. Donde no pueden preguntar, y siempre en los hooks de agentes, omiten los comandos e imprimen una nota por la salida de error que nombra `shogun trust`.
- `shogun trust` muestra los comandos y los aprueba (`--yes` omite la pregunta) o revoca la aprobación (`--revoke`); consulta [la referencia de la línea de comandos](cli.md#trust).

Los archivos `copy` y `link` no necesitan aprobación: solo se leen del repositorio base y, tras seguir los enlaces simbólicos, solo se escriben dentro del worktree. Un archivo cuyo destino resuelve fuera del worktree, a través de una carpeta enlazada desde la rama, hace fallar la operación.

Opciones de los providers:

| Provider | Opciones |
|---|---|
| `gradle` | `daemonMaxHeap`, `kotlinDaemonMaxHeap`: un tamaño como `512m` o `2g` (un número seguido de `k`, `m` o `g`). `idleTimeoutMinutes`: un número entero positivo, no una cadena. |
| `emulator` | `avd`: el nombre de la AVD preferida. |
| `ios-simulator` | `device`, `runtime`: los nombres del tipo de dispositivo y del runtime preferidos. |
| `xcode` | `compilationCache`: `true` o `false`. |
| `maven` | `mavenMaxHeap`: un tamaño como `512m` o `1g`. |
| `docker-compose` | `portVariables`: una lista de nombres de variable únicos que siguen las reglas de nombres de `env` y no son reservados. |

Las claves y los providers desconocidos se ignoran. Un fichero que no es JSON válido, o una clave conocida con el tipo equivocado, hace que crear y provisionar worktrees falle con un mensaje que indica el fichero y el problema, sin crear nada.

### Editarlo desde la app

No hace falta escribir este archivo a mano. Los ajustes del proyecto tienen una sección "Configuración del repositorio" donde activas los providers con sus opciones, editas las variables de `env` (con ayuda sobre las plantillas `${port}`, `${port:key}` y `${worktree}`) y fijas la ruta de `envFile`. La sección también sugiere los providers que encajan con los archivos que encuentra en el repositorio (ver [detección de stacks](providers.md#deteccion-de-stacks)); usar una sugerencia solo enciende el interruptor, y no se escribe nada hasta que guardas. Los valores se comprueban mientras escribes con las mismas reglas de arriba. Antes del primer guardado que escribe el archivo, la app muestra el `.shogunai.json` resultante y recuerda que pertenece al repositorio y conviene hacer commit.

Guardar solo cambia `providers`, `env`, `envFile` y, con "Mover a .shogunai.json", `copy`: cualquier otra clave, provider desconocido u opción desconocida de un provider se queda como estaba (las claves conservan su orden, la indentación se normaliza a dos espacios). La app nunca escribe sobre un archivo que no pudo leer, ni sobre uno que cambió en disco desde que se abrieron los ajustes: en el primer caso la sección explica el problema, en el segundo ofrece recargar. Los ajustes que solo valen para esta máquina (el AVD a usar, el modo sin ventana, el dispositivo y runtime del simulador) siguen en sus secciones, marcados como "Solo en esta máquina".

## `.worktreeinclude`

El `.worktreeinclude` de Claude Code se lee como `copy`: un patrón al estilo gitignore por línea, saltando las líneas en blanco y las que empiezan por `#`. Se pueden usar los dos ficheros a la vez.

## Archivos de la máquina (`~/.shogunai/`)

| Archivo | Contenido |
|---|---|
| `projects.json` | El catálogo de proyectos de la app de escritorio: el nombre, repositorio base, carpeta de worktrees, rama principal, archivos locales, comandos de los agentes, IDE elegido para el proyecto en esta máquina y ajustes de emulador y simulador de cada proyecto. El comando `shogun` lo lee para encontrar el proyecto de un repositorio. |
| `settings.json` | Las preferencias de la app: tema (`LIGHT`, `DARK` o `SYSTEM`, el valor por defecto), idioma (`ENGLISH`, por defecto, o `SPANISH`), posición y tamaño de la ventana, si la barra lateral está plegada, los proyectos plegados, el último destino, el último proyecto con el que se creó un worktree, los elementos recientes de la paleta de comandos y la agrupación de la lista de worktrees en cada vista, y el terminal elegido (`TERMINAL`, `ITERM2`, `WINDOWS_TERMINAL`, `TMUX` o `ZELLIJ`) y el IDE predeterminado (`VS_CODE`, `INTELLIJ_IDEA` o `ANDROID_STUDIO`), sin valor hasta que se elijan, y el último agente elegido para cada worktree. |
| `reservations.json`, `reservations.lock` | Un índice de los worktrees, de cualquier repositorio, que tienen puertos reservados, y el bloqueo que se toma al reservar, para que dos worktrees nunca reciban el mismo puerto. |
| `device-queue.json` | Los worktrees que esperan un emulador o un simulador con `--wait`, en orden. Desaparece cuando nadie espera. |
| `logs/emulator-<puerto>.log` | La salida de cada emulador que arrancó ShogunAi. |
| `xcode/compilation-cache.xcconfig` | Los ajustes compartidos de Xcode que escribe la opción `compilationCache` del provider de Xcode. |

Si `projects.json` o `settings.json` no se pueden leer (por ejemplo, no son JSON válido), ShogunAi nunca los sobrescribe en silencio: la app de escritorio muestra un aviso con la ruta del archivo, el comando `shogun` falla con una línea que lo nombra, y antes de que nada escriba un archivo nuevo el ilegible se conserva a su lado como `projects.json.unreadable-<marca de tiempo>` (o `settings.json.unreadable-<marca de tiempo>`). Un `settings.json` ilegible hace que la app arranque con los ajustes por defecto. Las escrituras en ambos archivos se serializan entre la app y `shogun` con un archivo `projects.json.lock` o `settings.json.lock` a su lado, así que dos escritores nunca pierden el cambio del otro.

Estos archivos contienen rutas de tu máquina; no los subas a ningún repositorio.

## Estado por worktree

Los puertos y recursos reservados de cada worktree (su simulador de iOS, por ejemplo) se guardan en `shogunai-env.json` dentro de la carpeta que Git tiene para ese worktree (`.git/worktrees/<nombre>/`), no en el propio worktree. Al eliminar el worktree se eliminan con él.

## Otras ubicaciones

- `$GRADLE_USER_HOME/init.d/shogunai-ios-simulator.init.gradle.kts` (`~/.gradle/init.d/` por defecto): el init script que instala `shogun simulator gradle-init install`.
- El `config.toml` de Herdr (`$XDG_CONFIG_HOME/herdr/`, o `~/.config/herdr/`): ShogunAi solo lee su `[worktrees] directory`, para reconocer los worktrees creados desde Herdr (`~/.herdr/worktrees` si no está definido).

## Rangos de puertos

| Uso | Rango |
|---|---|
| `${port}` y `${port:<clave>}` | 20000–29999 |
| Consolas de los emuladores de Android | 5554–5584, solo números pares |
