# Instalación

ShogunAi se distribuye como una app de escritorio con el comando `shogun` incluido. No necesitas JDK ni herramientas de desarrollo: cada instalador trae su propio runtime.

## macOS (Homebrew)

```bash
brew tap LuxionServer/shogunai
brew install --cask shogunai
```

Homebrew elige automáticamente el instalador para la arquitectura de tu Mac (Apple Silicon o Intel) y enlaza el comando `shogun`.

!!! note "Aviso de desarrollador no verificado"
    La app no está notarizada por Apple. Si macOS avisa de que "no se puede verificar el desarrollador" la primera vez que la abres, ábrela con clic derecho → Abrir, o ejecuta:

    ```bash
    xattr -dr com.apple.quarantine "/Applications/ShogunAi.app"
    ```

## Windows (winget)

```powershell
winget install LuxionServer.ShogunAi
```

`winget upgrade LuxionServer.ShogunAi` lo actualiza a cada nueva release. Esto instala solo la app; para el comando `shogun` consulta más abajo.

## Windows (Scoop)

```powershell
scoop bucket add shogunai https://github.com/LuxionServer/scoop-shogunai
scoop install shogunai
```

Scoop instala la app sin permisos de administrador, añade un acceso directo al menú Inicio y proporciona el comando `shogun`, que usa el runtime incluido en la app (no necesita Java). `scoop update shogunai` actualiza ambos.

## Descarga manual

Descarga el instalador para tu sistema operativo desde la [última release](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Sistema operativo | Instalador |
|---|---|
| macOS (Apple Silicon) | `-arm64.dmg` |
| macOS (Intel) | `-x64.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Instálala como cualquier otra app de escritorio y ábrela.

## Actualizaciones

La app de escritorio te avisa cuando hay una versión nueva y te recuerda cómo actualizarla según cómo la instalaste; consulta [Actualizaciones](desktop-app.es.md#actualizaciones) para ver qué comprueba y cómo desactivarlo. Con Homebrew, Scoop y winget también puedes actualizar cuando quieras: `brew upgrade --cask shogunai`, `scoop update shogunai` o `winget upgrade LuxionServer.ShogunAi`. Una versión nueva puede tardar unos minutos en llegar a esos gestores de paquetes tras publicarse.

## El comando `shogun`

La app de escritorio puede instalarlo por ti, sea cual sea la forma en que instalaste ShogunAi: abre Preferencias, busca Claude Code y activa "Instalar herramienta de línea de comandos". Usa el runtime que incluye la propia app, así que no necesita Java ni descargas, y el mismo botón aparece junto al "Instalar" de los hooks cuando falta el comando (los hooks no se pueden instalar hasta que esté disponible). En macOS y Linux enlaza el lanzador de la app como `shogun` en una carpeta escribible por tu usuario que ya esté en el `PATH`, o en `~/.local/bin` (la app muestra la línea que añadir al perfil de tu shell si esa carpeta no está en el `PATH`). Ahí, «el `PATH`» es el de tu shell de login: una app abierta desde el Dock o el Finder recibe un `PATH` mínimo, así que al arrancar la app le pide el suyo a tu shell (`$SHELL -l -i`) y, si el shell no responde en unos segundos, usa su propio `PATH` más `/opt/homebrew/bin`, `/usr/local/bin`, `~/.local/bin` y `~/.cargo/bin`. La app lo lee una vez, así que reiníciala después de cambiar el perfil de tu shell. Todo lo que arranca la app recibe ese mismo `PATH`: Git, los comandos posteriores a la creación, un servidor de Herdr y sus paneles, así que ahí también se encuentran `shogun` y tus demás herramientas. El comando `shogun` usa el `PATH` con el que se arrancó, seguido de esas mismas carpetas habituales. En Windows escribe `shogun.cmd` en `%USERPROFILE%\.shogunai\bin` y añade esa carpeta al `PATH` de tu usuario, sin permisos de administrador; reinicia el terminal y Claude Code después. "Quitar herramienta de línea de comandos" deshace solo lo que creó la app. Una compilación de desarrollo (`./gradlew run`) no tiene lanzador que exponer y muestra los pasos manuales.

A mano:

| Cómo instalaste ShogunAi | Cómo obtener `shogun` |
|---|---|
| Homebrew | Ya se instala con el Cask; `shogun --version` funciona en un terminal nuevo. |
| Scoop | Ya se instala con la app; `shogun --version` funciona en un terminal nuevo, sin necesitar Java. |
| Linux (`.deb`) | El lanzador de la app es el comando. Enlázalo una vez: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`. |
| Windows (winget o `.msi`), o sin instalar la app | Descarga `shogun-<versión>.zip` de la release, descomprímelo y añade su carpeta `bin` al `PATH`. Necesita Java 21 o posterior. |

El lanzador del `.msi` de Windows no tiene consola, así que con winget o el `.msi` usa el zip para el comando, o instala con Scoop. Consulta la [referencia de la línea de comandos](cli.md) para ver qué hace.

## Herramientas opcionales

ShogunAi solo necesita Git. Algunas funciones usan otras herramientas cuando están disponibles, y se ocultan o dan un error claro cuando no:

| Herramienta | Para qué |
|---|---|
| Git LFS | Repositorios que usan LFS: sin él, crear un worktree falla y pide instalarlo. |
| `ps`, `lsof` (macOS, Linux) | Detectar los agentes que se ejecutan en un worktree y listar los daemons de Gradle. |
| Android SDK (`emulator`, `adb`) | El [provider de emulador de Android](providers.md#emulador-de-android). |
| Xcode y `simctl` (macOS) | Los providers de [simulador de iOS](providers.md#simulador-de-ios) y de [Xcode](providers.md#xcode). |
| [Herdr](https://herdr.dev) | "Abrir en Herdr" y el plugin de Herdr; consulta [Uso con agentes](../agents.md#herdr). |
| [Orca](https://onorca.dev) 1.4.243 o posterior | "Abrir en Orca", los estados de los agentes de Orca y los hooks de `orca.yaml`; consulta [Uso con agentes](../agents.md#orca). ShogunAi busca `orca` (y, en Linux, solo `orca-ide`, porque allí `orca` suele ser el lector de pantalla de GNOME). Abrir un worktree puede hacer que su repositorio muestre en Orca los worktrees ajenos a Orca. Los estados de los agentes de Orca vienen de sus propios hooks de Claude Code; `orca agent hooks off` los quita si usas Orca sin sus estados, y ShogunAi recurre entonces al registro de sesiones de Claude Code. |
