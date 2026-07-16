## Context

La release actual (`.github/workflows/release.yml`) genera `.dmg` (macOS), `.msi` (Windows) y `.deb` (Linux) vía `./gradlew :desktopApp:packageRelease<Formato>` con Compose Desktop/jpackage, y los sube como assets de una GitHub Release. El `.dmg` no está firmado ni notarizado. El runner usado es `macos-latest`, que en GitHub Actions es Apple Silicon (arm64); el `.dmg` resultante embebe un runtime JDK arm64 y no corre en Mac Intel.

Homebrew Cask es la forma estándar de instalar apps `.dmg` de escritorio en macOS. Un Cask vive en un *tap* (repo con prefijo `homebrew-`); no hace falta pasar por el tap oficial `homebrew/cask` para empezar a distribuir.

## Goals / Non-Goals

**Goals:**
- Que `brew tap LuxionServer/shogunai && brew install --cask shogunai` instale la última versión publicada.
- Que el Cask se actualice automáticamente (versión + sha256) en cada release, sin paso manual.
- Documentar la limitación de arquitectura y de firma/notarización para que el usuario no se sorprenda con Gatekeeper.

**Non-Goals:**
- Firmar/notarizar el `.dmg` (Apple Developer ID) — queda fuera de este cambio.
- Soporte multi-arquitectura (Intel + Apple Silicon) — el pipeline actual solo produce un `.dmg` arm64.
- Publicar en el tap oficial `homebrew/cask` — se evalúa más adelante si el proyecto gana tracción.

## Decisions

- **Cask, no Formula**: es una app GUI empaquetada como `.dmg`, no un binario CLI construible desde fuente vía `brew install`. Formula no aplica.
- **Tap propio `LuxionServer/homebrew-shogunai`**: nombre obligado por convención de Homebrew (`homebrew-<nombre>` para que `brew tap LuxionServer/shogunai` lo resuelva). Se crea como repo nuevo, separado de `ShogunAi`, porque Homebrew espera un repo dedicado por tap y así el historial de bumps de versión no ensucia el repo principal.
- **Actualización vía job de CI, no `livecheck` reactivo**: se añade un job `update-homebrew-cask` a continuación de `publish-release` en el workflow existente. Descarga el `.dmg` recién publicado, calcula su `sha256`, clona el tap, reescribe `version` y `sha256` en `Casks/shogunai.rb`, commitea y pushea. Se prefiere esto (push activo) sobre depender de que un mantenedor de homebrew-core corra `livecheck`/`brew bump-cask-pr`, ya que el tap es propio y no hay proceso de review externo que lo bloquee.
- **Autenticación con PAT dedicado**: el `GITHUB_TOKEN` por defecto no tiene permiso de escritura sobre otro repositorio. Se usa un *fine-grained personal access token* con acceso de escritura limitado a `LuxionServer/homebrew-shogunai`, guardado como secret `HOMEBREW_TAP_TOKEN` en `ShogunAi`.
- **Cask con `caveats`**: como el `.dmg` no está notarizado, se añade un bloque `caveats` en el Cask avisando que macOS puede bloquear la app al primer arranque (Gatekeeper) y cómo abrirla (clic derecho → Abrir, o `xattr -dr com.apple.quarantine`).
- **`url` parametrizado con `#{version}`, no reescrito por CI (descubierto durante la implementación)**: al inspeccionar los assets reales de las releases se encontró que jpackage nombra el `.dmg` de macOS con una versión "mangled" (`0.2.2` → `app.luxion.shogunai-1.2.2.dmg`; el major pasa de `0` a `1`, minor/patch quedan igual). `brew audit` rechaza un Cask con `sha256` fijo y `url` sin `#{version}` ("unversioned URL"). En vez de que el job de CI reescriba también la línea `url` (lo que rompería el audit en cada bump), el Cask deriva `dmg_version` en Ruby a partir de `version` (`"1." + version.split(".", 2).last`) y el job de CI reproduce la misma transformación en bash solo para descargar el `.dmg` y calcular su `sha256` — nunca toca la línea `url`. Si el naming de jpackage cambia en el futuro, hay que actualizar esta transformación en ambos lugares (documentado con comentarios en el Cask y en el workflow).

## Risks / Trade-offs

- [El `.dmg` no notarizado dispara Gatekeeper] → Mitigado con el bloque `caveats` del Cask explicando el workaround; no bloquea la instalación, solo el primer arranque.
- [Solo hay build arm64] → Documentado como Non-Goal; usuarios Intel deben seguir usando la descarga manual (o esperar a que se añada matrix multi-arch en un cambio futuro).
- [PAT del tap expira o se revoca] → El job fallará de forma visible en Actions (push rechazado); se documenta en el propio workflow qué secret rotar. No hay fallback automático — se prioriza simplicidad sobre resiliencia dado el volumen bajo de releases.
- [Reintento manual de un release ya publicado] → El job de actualización recalcula el sha256 y sobreescribe `version`/`sha256`; si no hay diferencias, el commit es un no-op (git no crea commit vacío), así que es seguro re-ejecutar.
- [Alguna release no publica `.dmg` de macOS (se observó en releases históricas con patch `0`, p. ej. `v0.1.0` y `v0.2.0`, sin asset `.dmg`)] → El job usa `curl -f`, así que si el `.dmg` no existe falla de forma visible en Actions en vez de commitear un sha256 corrupto calculado sobre una página de error. Sigue sin haber fallback automático; es un bug preexistente del pipeline de build de macOS, fuera de alcance de este cambio.
