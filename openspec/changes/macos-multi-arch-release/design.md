## Context

`compose.desktop` (jpackage) genera un `.dmg` autocontenido con el runtime JDK del runner donde corre el build; no produce binarios universales (`arm64` + `x86_64` en un mismo paquete) sin pasos adicionales. GitHub Actions ofrece runners `macos-latest` (Apple Silicon) y también imágenes Intel (`macos-13`, última generación de runner Intel soportada por GitHub). Este cambio depende de que [[homebrew-cask-distribution]] exista o no en el momento de aplicarse: si el Cask ya está publicado, hay que tocarlo también; si no, este cambio solo toca el pipeline de build/release.

## Goals / Non-Goals

**Goals:**
- Publicar en cada release un `.dmg` que arranque nativamente tanto en Mac Apple Silicon como en Mac Intel.
- Mantener nombres de asset predecibles y sin colisión entre arquitecturas.
- Si el Cask ya existe, que `brew install --cask shogunai` instale el binario correcto según la arquitectura del usuario automáticamente.

**Non-Goals:**
- Generar un `.dmg` "universal" (fat binary) — jpackage no lo soporta de forma nativa para JVM; se opta por dos artefactos separados en vez de investigar herramientas de terceros para binarios universales.
- Firmar/notarizar los instaladores — sigue fuera de alcance, igual que en [[homebrew-cask-distribution]].
- Añadir arquitecturas más allá de macOS (Windows/Linux ya son single-arch en este proyecto y no se ven afectados).

## Decisions

- **Dos `.dmg` separados por arquitectura, no un universal binary**: jpackage empaqueta el JDK del runner tal cual; construir un universal requeriría un JDK universal y pasos manuales de `lipo` no soportados out-of-the-box por el plugin de Compose. Dos artefactos es la opción soportada por la toolchain actual sin dependencias nuevas.
- **Runner Intel: `macos-13`**: es la última imagen Intel mantenida por GitHub Actions (`macos-14`+ son solo Apple Silicon). Se fija la versión explícita en vez de `macos-latest` para no perder el runner Intel silenciosamente si GitHub cambia qué alias apunta a qué imagen.
- **Sufijo de arquitectura en el nombre de archivo**: `ShogunAi-<version>-arm64.dmg` / `-x64.dmg`, generado vía la opción de jpackage/Compose para nombre de artefacto (`packageName`/`macOS.dmgPackageVersion` o renombrado post-build si el plugin no expone el sufijo directamente). Es **BREAKING** para quien ya tenga scripteada la descarga del `.dmg` sin sufijo.
- **Selector de arquitectura en el Cask vía `on_arm`/`on_intel`**: es el mecanismo estándar del DSL de Homebrew Cask para servir binarios distintos según `Hardware::CPU.arch`; no requiere dos casks separados ni lógica custom.
- **El job de actualización automática del Cask (de [[homebrew-cask-distribution]]) debe calcular y escribir dos pares versión/sha256**, uno por arquitectura, en la misma pasada.

## Risks / Trade-offs

- [Duplica minutos de CI en macOS] → Aceptado: el volumen de releases es bajo; no se justifica optimizar todavía.
- [Cambio de nombre de asset rompe enlaces/scripts existentes que apunten al `.dmg` sin sufijo] → Mitigado documentando el cambio en el changelog de la release y, si se detecta uso real de la URL vieja, se podría mantener temporalmente un alias sin sufijo apuntando al arm64 (a decidir en la implementación, no bloqueante para la propuesta).
- [`macos-13` podría quedar deprecado por GitHub en el futuro] → Requiere revisar periódicamente la matriz de runners soportados; no hay mitigación automática, es mantenimiento manual.
- [Si [[homebrew-cask-distribution]] ya fue archivado antes de aplicar este cambio, hace falta un delta `MODIFIED` sobre su spec en vez de solo `ADDED` aquí] → Se deja como nota para quien aplique este cambio; no se resuelve ahora porque depende del orden real de implementación.

## Open Questions

- ¿Se mantiene temporalmente un alias de descarga sin sufijo de arquitectura para no romper enlaces existentes, o se acepta el cambio de nombre como breaking desde ya?
- ¿Vale la pena evaluar en el futuro un build de binario universal (herramientas de terceros) para volver a un solo `.dmg`, dado el costo doble de CI?
