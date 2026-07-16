# homebrew-cask-distribution Specification

## Purpose
TBD - created by archiving change homebrew-cask-distribution. Update Purpose after archive.
## Requirements
### Requirement: Instalación vía Homebrew Cask
El sistema SHALL ofrecer un Cask de Homebrew en el tap `LuxionServer/homebrew-shogunai` que instale la última versión publicada de la app en macOS a partir del `.dmg` de la GitHub Release correspondiente.

#### Scenario: Instalación fresca
- **WHEN** un usuario ejecuta `brew tap LuxionServer/shogunai` seguido de `brew install --cask shogunai`
- **THEN** Homebrew descarga el `.dmg` de la última release y deja la app instalada en `/Applications`

#### Scenario: Advertencia de Gatekeeper documentada
- **WHEN** el usuario instala el Cask
- **THEN** Homebrew muestra un aviso (`caveats`) explicando que el primer arranque puede ser bloqueado por Gatekeeper por no estar la app notarizada, y cómo abrirla igualmente

### Requirement: Actualización automática del Cask en cada release
El sistema SHALL actualizar automáticamente la versión y el `sha256` del Cask en el tap cada vez que se publica una nueva release en `LuxionServer/ShogunAi`, sin intervención manual.

#### Scenario: Release publicada con éxito
- **WHEN** el workflow de release publica una nueva GitHub Release con su `.dmg`
- **THEN** un job de CI calcula el `sha256` del `.dmg`, actualiza `version` y `sha256` en `Casks/shogunai.rb` del tap, y commitea/pushea ese cambio al repo del tap

#### Scenario: Re-ejecución sin cambios
- **WHEN** el job de actualización se vuelve a ejecutar para una versión ya reflejada en el Cask
- **THEN** no se genera un commit nuevo (no hay diferencias que commitear) y el job termina sin error

#### Scenario: Falla la autenticación contra el tap
- **WHEN** el token usado para escribir en `LuxionServer/homebrew-shogunai` es inválido o ha expirado
- **THEN** el job falla de forma visible en GitHub Actions, sin afectar la publicación de la release ya completada
