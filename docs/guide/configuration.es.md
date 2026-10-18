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
| `env` | objeto de cadenas | Variables de entorno por worktree. Los nombres usan letras, dígitos y `_`, y no empiezan por un dígito. |
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

Opciones de los providers:

| Provider | Opciones |
|---|---|
| `gradle` | `daemonMaxHeap`, `kotlinDaemonMaxHeap`: un tamaño como `512m` o `2g` (un número seguido de `k`, `m` o `g`). `idleTimeoutMinutes`: un número entero positivo, no una cadena. |
| `emulator` | `avd`: el nombre de la AVD preferida. |
| `ios-simulator` | `device`, `runtime`: los nombres del tipo de dispositivo y del runtime preferidos. |
| `xcode` | `compilationCache`: `true` o `false`. |

Las claves y los providers desconocidos se ignoran. Un fichero que no es JSON válido, o una clave conocida con el tipo equivocado, hace que crear y provisionar worktrees falle con un mensaje que indica el fichero y el problema, sin crear nada.

## `.worktreeinclude`

El `.worktreeinclude` de Claude Code se lee como `copy`: un patrón al estilo gitignore por línea, saltando las líneas en blanco y las que empiezan por `#`. Se pueden usar los dos ficheros a la vez.

## Archivos de la máquina (`~/.shogunai/`)

| Archivo | Contenido |
|---|---|
| `projects.json` | El catálogo de proyectos de la app de escritorio: el nombre, repositorio base, carpeta de worktrees, rama principal, archivos locales, comando del agente y ajustes de emulador y simulador de cada proyecto. El comando `shogun` lo lee para encontrar el proyecto de un repositorio. |
| `settings.json` | El tema de la app (`LIGHT`, `DARK` o `SYSTEM`, el valor por defecto) y su idioma (`ENGLISH`, por defecto, o `SPANISH`). |
| `reservations.json`, `reservations.lock` | Un índice de los worktrees, de cualquier repositorio, que tienen puertos reservados, y el bloqueo que se toma al reservar, para que dos worktrees nunca reciban el mismo puerto. |
| `logs/emulator-<puerto>.log` | La salida de cada emulador que arrancó ShogunAi. |
| `xcode/compilation-cache.xcconfig` | Los ajustes compartidos de Xcode que escribe la opción `compilationCache` del provider de Xcode. |

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
