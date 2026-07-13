## Why

La app aplica hoy un único `colorScheme` oscuro fijo (decisión explícita de `visual-theme`), sin adaptarse al modo claro/oscuro del sistema operativo. Los usuarios que trabajan en modo claro del SO quedan forzados a una interfaz oscura, y no existe forma de elegir. Queremos que la app siga el tema del sistema automáticamente y, opcionalmente, permita al usuario fijar un modo manual (claro/oscuro) que se recuerde entre sesiones.

## What Changes

- Se añade un esquema de color claro (`ShogunLightColorScheme`) junto al oscuro existente.
- Se introduce un modo de tema con tres valores: `Claro`, `Oscuro`, `Automático` (sigue el SO). `Automático` es el valor por defecto.
- `ShogunAiTheme` resuelve el esquema efectivo usando `isSystemInDarkTheme()` cuando el modo es `Automático`, o el esquema fijo correspondiente cuando el modo es `Claro`/`Oscuro`.
- Se persiste la preferencia de modo elegida por el usuario entre reinicios de la app (nuevo archivo de configuración local, siguiendo el patrón de persistencia JSON ya usado para proyectos).
- Se añade un control en la UI para que el usuario cambie entre `Claro` / `Oscuro` / `Automático`.
- **BREAKING**: reemplaza el requisito previo de `visual-theme` que fijaba un único `colorScheme` independiente del sistema operativo.

## Capabilities

### New Capabilities
(ninguna — el cambio extiende la capacidad `visual-theme` existente; la persistencia de la preferencia se documenta como parte de esa misma capacidad, no como una capacidad de "settings" genérica todavía inexistente)

### Modified Capabilities
- `visual-theme`: el requisito "Un único esquema de color (sin claro/oscuro)" se reemplaza por soporte de modo claro/oscuro/automático, con seguimiento del tema del sistema operativo y preferencia manual persistida.

## Impact

- `ui/theme/Color.kt`: añade la paleta clara.
- `ui/theme/Theme.kt`: `ShogunAiTheme` recibe el modo de tema y resuelve claro/oscuro/automático.
- `ui/AppRoot.kt`: expone el estado del modo de tema al árbol de composición.
- Nuevo modelo de dominio `ThemeMode` (`Light` / `Dark` / `System`) y un puerto de persistencia (`domain/io`) para leer/guardar la preferencia.
- Nueva implementación de infraestructura (JSON local, mismo patrón que `JsonProjectRepository`) y su cableado en `AppContainer`.
- Nuevo control de UI (menú o toggle) para cambiar el modo, visible desde alguna pantalla existente.
- No añade dependencias nuevas de Gradle (Compose ya expone `isSystemInDarkTheme()`).
