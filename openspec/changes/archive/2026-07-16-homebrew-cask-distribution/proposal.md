## Why

Actualmente la app solo se distribuye descargando manualmente el `.dmg`/`.msi`/`.deb` desde la página de releases de GitHub. En macOS, la vía habitual para instalar y actualizar apps de escritorio es Homebrew. Ofrecer un Cask propio permite `brew install --cask` y futuras actualizaciones con `brew upgrade`, sin depender de que el usuario recuerde ir a buscar el instalador a mano.

## What Changes

- Se crea un tap propio en un nuevo repositorio GitHub `LuxionServer/homebrew-shogunai`, con un Cask (`Casks/shogunai.rb`) que apunta al `.dmg` publicado en cada release de `LuxionServer/ShogunAi`.
- Se añade un job al workflow de release (`.github/workflows/release.yml`) que, tras publicar la release, actualiza automáticamente la versión y el `sha256` del Cask en el repo del tap (commit automatizado con un token con permiso de escritura sobre ese repo).
- Se documenta en el `README.md` la instalación vía `brew tap` + `brew install --cask`, como alternativa a la descarga manual.

## Capabilities

### New Capabilities
- `homebrew-cask-distribution`: publicación y actualización automática de un Cask de Homebrew propio que instala la app desde los artefactos `.dmg` de las releases de GitHub.

### Modified Capabilities
(ninguna — no existe spec previa relacionada con distribución/instalación)

## Impact

- Nuevo repositorio externo: `LuxionServer/homebrew-shogunai` (tap).
- `.github/workflows/release.yml`: nuevo job posterior a `publish-release` que calcula el sha256 del `.dmg` y actualiza el Cask vía commit/push al repo del tap.
- Requiere un secret nuevo en este repo (token con permiso de escritura sobre `homebrew-shogunai`) para que el workflow pueda hacer push al tap.
- `README.md`: sección de instalación actualizada con las instrucciones de `brew`.
