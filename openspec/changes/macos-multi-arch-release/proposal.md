## Why

El pipeline de release actual construye el `.dmg` de macOS en un runner `macos-latest` (Apple Silicon/arm64), embebiendo un runtime JDK arm64 vía jpackage. Ese instalador no arranca en Mac con procesador Intel (x86_64). Al preparar la distribución vía Homebrew Cask ([[homebrew-cask-distribution]]) se identificó esta limitación como bloqueante para usuarios Intel, que hoy no tienen forma de instalar la app salvo compilando desde fuente.

## What Changes

- Se añade al matrix de macOS en `.github/workflows/release.yml` un segundo job que construye el `.dmg` en un runner Intel (`macos-13` o equivalente x86_64), además del arm64 existente.
- Cada `.dmg` de macOS se publica como asset separado en la release, con un sufijo de arquitectura en el nombre de archivo (p. ej. `ShogunAi-<version>-arm64.dmg` / `ShogunAi-<version>-x64.dmg`) para que no se pisen entre sí.
- Si para entonces ya existe el Cask de Homebrew, se actualiza para seleccionar `url`/`sha256` según `Hardware::CPU.arch` (bloque `on_arm` / `on_intel` del DSL de Cask).

## Capabilities

### New Capabilities
- `macos-multi-arch-release-build`: generación y publicación de instaladores `.dmg` de macOS separados para arm64 e Intel (x86_64) en cada release.

### Modified Capabilities
(ninguna spec archivada existe todavía para distribución vía Homebrew — ese cambio, [[homebrew-cask-distribution]], sigue sin aplicar/archivar. Si se archiva antes que este, su spec pasará a `openspec/specs/homebrew-cask-distribution/` y este cambio deberá añadir un delta `MODIFIED` sobre ella para el selector de arquitectura del Cask.)

## Impact

- `.github/workflows/release.yml`: el matrix de build gana una entrada más (macOS Intel); el nombre de los assets `.dmg` cambia (pasa a incluir arquitectura), lo cual es **BREAKING** para cualquier link o script externo que asuma el nombre de archivo actual sin sufijo.
- Costo de CI: duplica el tiempo/minutos de build de macOS (dos jobs en vez de uno).
- Si el Cask de Homebrew ya existe, su archivo `Casks/shogunai.rb` y el job de actualización automática deben ajustarse para manejar dos pares `url`/`sha256`.
- No implica cambios en el código de la app (Compose/Kotlin) — es puramente de empaquetado/CI.
