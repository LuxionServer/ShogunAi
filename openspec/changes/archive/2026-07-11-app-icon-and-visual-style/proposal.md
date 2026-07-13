## Why

La app funciona bien pero no tiene identidad visual: usa `MaterialTheme {}` sin personalizar (paleta y tipografía por defecto de Compose Material3) y no tiene ningún icono — ni como `windowIcon` en la ventana ni en el empaquetado nativo (Dmg/Msi/Deb). Esto la hace lucir genérica y difícil de distinguir de otras ventanas/apps abiertas.

## What Changes

- Se genera un icono de app propio (SVG/PNG fuente) y se deriva a los formatos que necesita cada plataforma (PNG para `windowIcon`, ICNS/ICO/PNG para `nativeDistributions`).
- Se define un tema Material3 propio (esquema de color y tipografía) que reemplaza el `MaterialTheme {}` por defecto en `AppRoot`.
- El soporte de modo claro/oscuro (toggle, persistencia de preferencia) queda fuera de alcance: se deja como spec futura separada. Este cambio define un único esquema de color por defecto.
- No se tocan flujos, casos de uso ni modelos de dominio — es un cambio puramente de capa de presentación.

## Capabilities

### New Capabilities
- `app-icon`: icono de la aplicación (fuente + variantes por plataforma) usado como `windowIcon` de la ventana Compose y en el empaquetado nativo (Dmg/Msi/Deb).
- `visual-theme`: esquema de color y tipografía Material3 propios de la app, aplicados globalmente desde `AppRoot`.

### Modified Capabilities
(ninguna — no cambian requisitos de `project-catalog`, `project-configuration` ni `worktree-workspace`, solo su presentación visual)

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/main.kt`: añade `icon = painterResource(...)` (o `BufferedImage`) al `Window`.
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/AppRoot.kt`: reemplaza `MaterialTheme {}` por un tema propio (`ShogunAiTheme` o similar).
- `desktopApp/build.gradle.kts`: configura `nativeDistributions { macOS { iconFile }, windows { iconFile }, linux { iconFile } }`.
- Nuevos recursos: archivos de icono en `desktopApp/src/main/resources` (o carpeta equivalente de Compose Desktop).
- Sin impacto en `shared/`, dominio, casos de uso ni tests existentes.
