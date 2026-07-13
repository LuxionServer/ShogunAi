## 1. Dominio: modo de tema y puerto de persistencia

- [x] 1.1 Crear `domain/model/ThemeMode.kt` con `enum class ThemeMode { LIGHT, DARK, SYSTEM }`
- [x] 1.2 Crear puerto `domain/io/ThemePreferenceRepository.kt` con `fun load(): ThemeMode` y `fun save(mode: ThemeMode)`

## 2. Infraestructura: persistencia JSON

- [x] 2.1 Crear `infrastructure/JsonThemePreferenceRepository.kt` que persiste en `~/.shogunai/settings.json`, siguiendo el patrón de escritura atómica de `JsonProjectRepository` (archivo temporal + `Files.move` con `ATOMIC_MOVE`)
- [x] 2.2 `load()` devuelve `ThemeMode.SYSTEM` si el archivo no existe, está vacío o no se puede parsear
- [x] 2.3 Cablear `JsonThemePreferenceRepository` en `AppContainer` como `themePreferenceRepository`

## 3. Paleta clara

- [x] 3.1 En `ui/theme/Color.kt`, añadir el set `ShogunLight*` (background, surface, surfaceVariant, onBackground, onSurface, onSurfaceVariant) manteniendo `ShogunRed`/`ShogunSlate` como acentos
- [x] 3.2 Validar contraste ~4:1 entre textos y fondos de la paleta clara (mismo criterio aplicado a la oscura en `app-icon-and-visual-style`)

## 4. Tema resuelve claro/oscuro/automático

- [x] 4.1 En `ui/theme/Theme.kt`, añadir `ShogunLightColorScheme` (`lightColorScheme(...)`) junto al `ShogunDarkColorScheme` existente
- [x] 4.2 Cambiar la firma de `ShogunAiTheme` a `ShogunAiTheme(themeMode: ThemeMode, content: @Composable () -> Unit)`, resolviendo `useDark` con `isSystemInDarkTheme()` cuando `themeMode == SYSTEM`, y aplicando el `colorScheme` correspondiente
- [x] 4.3 Quitar el comentario en `Theme.kt`/`Color.kt` que marcaba el esquema como "único, sin variante clara/oscura"

## 5. Estado del modo de tema y control de UI

- [x] 5.1 En `AppRoot.kt`, cargar el `ThemeMode` inicial desde `appContainer.themePreferenceRepository.load()` y mantenerlo en `mutableStateOf`
- [x] 5.2 Al cambiar el modo desde la UI, actualizar el estado y llamar a `themePreferenceRepository.save(mode)`
- [x] 5.3 Crear composable `ThemeModeToggle` en `ui/theme/` (menú o segmented button con las 3 opciones: Claro/Oscuro/Automático)
- [x] 5.4 Anclar `ThemeModeToggle` en `AppRoot.kt` de forma visible en las tres pantallas (`ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen`), sin modificar cada pantalla individualmente
- [x] 5.5 Pasar el `themeMode` resuelto a `ShogunAiTheme` en `AppRoot.kt`

## 6. Verificación

- [ ] 6.1 **BLOQUEADO** — Ejecutar `./gradlew :desktopApp:run`, confirmar que arranca en modo Automático por defecto (sin `settings.json` previo) y que refleja el tema actual del sistema operativo. Verificado manualmente por el usuario: no funciona (ver nota abajo).
- [x] 6.2 Cambiar manualmente a Claro y a Oscuro desde el control de UI y confirmar que el `colorScheme` cambia de inmediato en las tres pantallas — confirmado por el usuario (el control ya no se superpone con otros botones y cambia el esquema al seleccionar Claro/Oscuro).
- [ ] 6.3 Reiniciar la app tras elegir un modo manual y confirmar que arranca respetando esa preferencia (lee `~/.shogunai/settings.json`) — no verificado aún.
- [ ] 6.4 **BLOQUEADO** — Cambiar el tema del sistema operativo con la app abierta en modo Automático y confirmar que la app se actualiza sin reiniciar. Verificado manualmente por el usuario: no funciona.
- [x] 6.5 Ejecutar `./gradlew test` y confirmar que los tests existentes siguen pasando
- [x] 6.6 Confirmar que no se introdujeron dependencias nuevas de Gradle

### Known issue — Modo Automático no funciona (pendiente)

El modo Automático (`ThemeMode.SYSTEM`) no refleja el tema real del sistema operativo, ni al arrancar ni en caliente. Se implementó un intento de corrección (`rememberSystemInDarkTheme()` sondeando `org.jetbrains.skiko.currentSystemTheme` cada segundo, ver `ui/theme/SystemDarkTheme.kt` y decisión 4 de `design.md`) que no resolvió el problema en pruebas manuales del usuario sobre macOS. Queda como bug abierto pendiente de investigar; no se debe archivar este cambio hasta resolverlo o decidir explícitamente aceptar la limitación. Ver "Risks / Trade-offs" en `design.md` para el detalle.
