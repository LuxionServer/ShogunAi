## 1. Pipeline de build

- [ ] 1.1 Añadir al matrix de macOS en `.github/workflows/release.yml` una entrada con `os: macos-13` (Intel) junto a la existente `macos-latest` (Apple Silicon)
- [ ] 1.2 Ajustar `desktopApp/build.gradle.kts` o el paso del workflow para que el `.dmg` generado incluya el sufijo de arquitectura (`arm64`/`x64`) en el nombre de archivo
- [ ] 1.3 Actualizar el `artifact-glob` de cada entrada del matrix para que apunte al `.dmg` con su sufijo correspondiente

## 2. Cask de Homebrew (solo si [[homebrew-cask-distribution]] ya está aplicado)

- [ ] 2.1 Convertir `url`/`sha256` de `Casks/shogunai.rb` a bloques `on_arm do ... end` / `on_intel do ... end`
- [ ] 2.2 Actualizar el job `update-homebrew-cask` para calcular y escribir sha256 de ambos `.dmg` (arm64 y x64) en la misma pasada
- [ ] 2.3 Si [[homebrew-cask-distribution]] ya fue archivado, crear el delta `MODIFIED Requirements` correspondiente sobre `openspec/specs/homebrew-cask-distribution/spec.md` en vez de solo `ADDED` en este cambio

## 3. Documentación

- [ ] 3.1 Actualizar `README.md` para aclarar que hay instaladores separados por arquitectura de macOS
- [ ] 3.2 Documentar en el changelog/notas de la release el cambio de nombre de archivo (breaking) del `.dmg`

## 4. Verificación

- [ ] 4.1 Descargar y abrir el `.dmg` arm64 en un Mac Apple Silicon, confirmar arranque nativo
- [ ] 4.2 Descargar y abrir el `.dmg` x64 en un Mac Intel (o VM), confirmar arranque sin Rosetta
- [ ] 4.3 Si aplica el Cask, correr `brew install --cask shogunai` en ambas arquitecturas y confirmar que cada una descarga el `.dmg` correcto
