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

## Descarga manual

Descarga el instalador para tu sistema operativo desde la [última release](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Sistema operativo | Instalador |
|---|---|
| macOS (Apple Silicon) | `-arm64.dmg` |
| macOS (Intel) | `-x64.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Instálala como cualquier otra app de escritorio y ábrela.

## El comando `shogun`

| Cómo instalaste ShogunAi | Cómo obtener `shogun` |
|---|---|
| Homebrew | Ya se instala con el Cask; `shogun --version` funciona en un terminal nuevo. |
| Linux (`.deb`) | El lanzador de la app es el comando. Enlázalo una vez: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`. |
| Windows, o sin instalar la app | Descarga `shogun-<versión>.zip` de la release, descomprímelo y añade su carpeta `bin` al `PATH`. Necesita Java 21 o posterior. |

El lanzador del `.msi` de Windows no tiene consola, así que en Windows usa el zip para el comando. Consulta la [referencia de la línea de comandos](cli.md) para ver qué hace.

## Herramientas opcionales

ShogunAi solo necesita Git. Algunas funciones usan otras herramientas cuando están disponibles, y se ocultan o dan un error claro cuando no:

| Herramienta | Para qué |
|---|---|
| Git LFS | Repositorios que usan LFS: sin él, crear un worktree falla y pide instalarlo. |
| `ps`, `lsof` (macOS, Linux) | Detectar los agentes que se ejecutan en un worktree y listar los daemons de Gradle. |
| Android SDK (`emulator`, `adb`) | El [provider de emulador de Android](providers.md#emulador-de-android). |
| Xcode y `simctl` (macOS) | Los providers de [simulador de iOS](providers.md#simulador-de-ios) y de [Xcode](providers.md#xcode). |
| [Herdr](https://herdr.dev) | "Abrir en Herdr" y el plugin de Herdr; consulta [Uso con agentes](../agents.md#herdr). |
