## 1. Repo del tap

- [x] 1.1 Crear el repositorio `LuxionServer/homebrew-shogunai` en GitHub (público, sin plantilla)
- [x] 1.2 Añadir `Casks/shogunai.rb` con el Cask inicial (nombre, versión y sha256 de la última release existente, `url` apuntando al `.dmg`, `caveats` sobre Gatekeeper)
- [x] 1.3 Añadir un `README.md` mínimo al tap con las instrucciones `brew tap` + `brew install --cask`

## 2. Token de acceso para el workflow

- [x] 2.1 Crear un fine-grained PAT con permiso de escritura (Contents) limitado a `LuxionServer/homebrew-shogunai`
- [x] 2.2 Guardarlo como secret `HOMEBREW_TAP_TOKEN` en el repo `ShogunAi`

## 3. Job de actualización automática

- [x] 3.1 Añadir job `update-homebrew-cask` a `.github/workflows/release.yml`, dependiente de `publish-release`
- [x] 3.2 El job descarga el `.dmg` de la release recién publicada y calcula su `sha256`
- [x] 3.3 El job clona `LuxionServer/homebrew-shogunai` usando `HOMEBREW_TAP_TOKEN`, actualiza `version` y `sha256` en `Casks/shogunai.rb`
- [x] 3.4 El job commitea y pushea solo si hay diferencias (no-op si la versión ya está reflejada)

## 4. Documentación

- [x] 4.1 Añadir en `README.md` del repo principal la instalación vía `brew tap LuxionServer/shogunai && brew install --cask shogunai` como alternativa a la descarga manual
- [x] 4.2 Mencionar la limitación de arquitectura (solo arm64) y la advertencia de Gatekeeper por app no notarizada

## 5. Verificación

- [x] 5.1 Ejecutar `brew audit --cask shogunai` (o `brew style`) localmente contra el tap para validar el formato del Cask
- [x] 5.2 Probar instalación real: `brew tap LuxionServer/shogunai && brew install --cask shogunai` y confirmar que la app abre (con el workaround de Gatekeeper si aplica)
