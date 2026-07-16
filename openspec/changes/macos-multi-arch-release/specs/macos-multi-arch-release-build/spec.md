## ADDED Requirements

### Requirement: Build de macOS por arquitectura
El sistema SHALL construir y publicar, en cada release, un `.dmg` para Apple Silicon (arm64) y un `.dmg` separado para Mac Intel (x86_64), cada uno ejecutable nativamente en su arquitectura sin requerir Rosetta.

#### Scenario: Release con dos instaladores de macOS
- **WHEN** se publica una nueva release
- **THEN** la release incluye exactamente dos assets `.dmg` de macOS, uno para arm64 y otro para x86_64, cada uno con el sufijo de arquitectura en el nombre de archivo

#### Scenario: Instalador arm64 en Mac Apple Silicon
- **WHEN** un usuario con Mac Apple Silicon descarga y abre el `.dmg` con sufijo `arm64`
- **THEN** la app arranca de forma nativa, sin advertencias de arquitectura incompatible

#### Scenario: Instalador x64 en Mac Intel
- **WHEN** un usuario con Mac Intel descarga y abre el `.dmg` con sufijo `x64`
- **THEN** la app arranca de forma nativa, sin necesidad de Rosetta

### Requirement: Selección automática de arquitectura en el Cask de Homebrew
Cuando exista un Cask de Homebrew para la app, el sistema SHALL servir automáticamente el `.dmg` correspondiente a la arquitectura del usuario (arm64 o Intel) sin que el usuario deba elegir manualmente.

#### Scenario: Instalación por Homebrew en Mac Apple Silicon
- **WHEN** un usuario con Mac Apple Silicon ejecuta `brew install --cask shogunai`
- **THEN** Homebrew descarga el `.dmg` arm64 y verifica su sha256 correspondiente

#### Scenario: Instalación por Homebrew en Mac Intel
- **WHEN** un usuario con Mac Intel ejecuta `brew install --cask shogunai`
- **THEN** Homebrew descarga el `.dmg` x86_64 y verifica su sha256 correspondiente
