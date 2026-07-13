## Context

`ShogunAiTheme` (`ui/theme/Theme.kt`) hoy aplica siempre `ShogunDarkColorScheme`, un `darkColorScheme()` fijo definido en `ui/theme/Color.kt`. `AppRoot.kt` es el único punto donde se invoca `ShogunAiTheme { ... }`, envolviendo el `when` que alterna entre `ProjectListScreen`, `ProjectConfigScreen` y `WorktreeScreen`. No existe hoy ningún mecanismo de persistencia de preferencias de usuario: el único precedente es `JsonProjectRepository` (`infrastructure/JsonProjectRepository.kt`), que persiste una lista de proyectos como JSON en `~/.shogunai/projects.json` con escritura atómica (archivo temporal + `Files.move` con `ATOMIC_MOVE`). El proyecto usa Compose Multiplatform 1.11.1 para desktop.

**Corrección post-implementación**: la suposición inicial de que `isSystemInDarkTheme()` es reactivo en runtime en Compose Desktop 1.11.1 era incorrecta. Su implementación (`androidx.compose.foundation.isSystemInDarkTheme` → `LocalSystemTheme`, `staticCompositionLocalOf`) calcula el valor una sola vez de forma perezosa y no se actualiza si el tema del sistema operativo cambia con la app abierta — no hay ningún listener/`CompositionLocalProvider` que lo mantenga vivo en `desktop-jvm` 1.11.1. Ver decisión 4 para la corrección aplicada (sondeo de `org.jetbrains.skiko.currentSystemTheme`, que sí consulta el estado real del SO — vía JNI nativo — en cada llamada).

**Estado abierto (pendiente)**: tras aplicar la corrección de la decisión 4, el usuario verificó manualmente en macOS (JDK Amazon Corretto) que el modo Automático sigue sin reflejar el tema real del SO, ni al arrancar ni en caliente. La causa raíz de por qué el sondeo de `currentSystemTheme` tampoco funciona no se ha investigado todavía — hipótesis no confirmadas incluyen: `currentSystemTheme` devolviendo un valor que no cambia con el JDK/binario nativo de Skiko usado en este entorno, algún problema de recomposición en el bucle `produceState`/`delay`, o una diferencia entre el proceso de desarrollo lanzado con `./gradlew :desktopApp:run` y otra instancia empaquetada de la app. Este bug queda documentado como pendiente (ver tarea 6.1/6.4 en `tasks.md`); no se ha vuelto a investigar activamente a petición del usuario.

## Goals / Non-Goals

**Goals:**
- Soportar tres modos de tema: Claro, Oscuro y Automático (sigue el SO).
- Que el modo Automático reaccione a cambios de tema del sistema operativo sin reiniciar la app.
- Persistir el modo elegido por el usuario entre reinicios, con Automático como valor por defecto si no hay preferencia guardada.
- Mantener el mismo patrón arquitectónico ya usado en el proyecto: dominio con puertos (`domain/io`), adaptador de infraestructura JSON, wiring manual en `AppContainer`.

**Non-Goals:**
- No se añade personalización de acentos/colores más allá de claro/oscuro (no hay selector de paleta).
- No se garantiza detección de tema del sistema en Linux si el entorno de escritorio no lo expone; el modo manual (Claro/Oscuro) sigue funcionando como fallback.
- No se introduce una capacidad genérica de "settings"/preferencias; esta preferencia se persiste con su propio archivo pequeño y dedicado, no un mecanismo de configuración extensible para uso futuro.

## Decisions

**1. `ThemeMode` como modelo de dominio, no como detalle de UI.**
Se añade `domain/model/ThemeMode.kt` con un enum `ThemeMode { LIGHT, DARK, SYSTEM }`. Vive en dominio (no en `ui/theme`) porque se persiste y se pasa a través de un puerto, siguiendo el mismo criterio que `ProjectConfig`.

**2. Puerto `ThemePreferenceRepository` + adaptador JSON dedicado.**
Nuevo puerto en `domain/io/ThemePreferenceRepository.kt` con `fun load(): ThemeMode` y `fun save(mode: ThemeMode)`. Implementación `infrastructure/JsonThemePreferenceRepository.kt` persiste en `~/.shogunai/settings.json` (archivo propio, no reutiliza `projects.json`) con el mismo patrón de escritura atómica que `JsonProjectRepository`. Si el archivo no existe o está corrupto/vacío, `load()` devuelve `ThemeMode.SYSTEM`.
- Alternativa descartada: guardar la preferencia dentro de `projects.json` o en un `Preferences` de Java (`java.util.prefs`) — se descarta por romper el patrón JSON ya establecido en el proyecto y por acoplar una preferencia de UI al repositorio de proyectos.

**3. Estado del modo de tema vive en `AppRoot`, no en un ViewModel dedicado.**
`AppRoot` carga el `ThemeMode` inicial desde `appContainer.themePreferenceRepository.load()` una vez (vía `remember` + `LaunchedEffect` o carga síncrona directa, dado que es una lectura de archivo pequeño y local), lo mantiene como `mutableStateOf`, y expone un callback `onThemeModeChange` que actualiza el estado y llama a `save()`. Se descarta crear un `ThemeViewModel` porque el estado es transversal a toda la app (no a una pantalla) y el proyecto no tiene hoy un contenedor de estado a ese nivel; añadir uno solo para tres valores sería sobre-ingeniería.

**4. `ShogunAiTheme` resuelve el esquema efectivo internamente, sondeando el tema real del SO.**
`ShogunAiTheme(themeMode: ThemeMode, content: @Composable () -> Unit)` calcula `val useDark = when (themeMode) { LIGHT -> false; DARK -> true; SYSTEM -> systemDark }`, donde `systemDark` viene de `rememberSystemInDarkTheme()` (`ui/theme/SystemDarkTheme.kt`), no de `isSystemInDarkTheme()`. `rememberSystemInDarkTheme()` usa `produceState` con un bucle `while (true) { value = currentSystemTheme == SystemTheme.DARK; delay(1000) }` sobre `org.jetbrains.skiko.currentSystemTheme` — una propiedad pública ya presente transitivamente vía `compose-desktop` (sin nueva dependencia de Gradle) que consulta el SO mediante una llamada nativa (JNI) en cada acceso, a diferencia del `CompositionLocal` estático de `isSystemInDarkTheme()`. El sondeo cada 1s da reactividad en caliente sin depender de hooks de AWT específicos de la distribución de JDK (que sí varían entre JetBrains Runtime y otras, p. ej. Corretto). La resolución de esquema queda encapsulada en `ShogunAiTheme` (no en `AppRoot`), igual que antes encapsulaba el único esquema fijo.

**5. Paleta clara (`ShogunLightColorScheme`) como contraparte de la oscura.**
Se añade a `Color.kt` un set `ShogunLight*` (background, surface, onSurface, etc.) manteniendo los mismos colores de acento (`ShogunRed`, `ShogunSlate`) pero con fondos/textos invertidos, validando contraste ~4:1 igual que se hizo para el esquema oscuro (ver correcciones de contraste en `app-icon-and-visual-style`, tareas 6.2–6.4).

**6. Control de cambio de modo ubicado en `AppRoot`, visible en todas las pantallas.**
En vez de añadir un control de tema a cada una de las tres pantallas (duplicación), `AppRoot` antepone una fila de cabecera propia (`Row` con `ThemeModeToggle` alineado al final) por encima del `when` existente, dentro de una `Column` que reparte el espacio con `Modifier.weight(1f)` en el contenedor del `when`. Se descartó superponer el control como overlay flotante (`Box` + `Alignment.TopEnd`) porque `ProjectListScreen` y `WorktreeScreen` ya tienen su propio botón anclado en la esquina superior derecha (`Nuevo proyecto`, `Proyectos`), y el overlay quedaba encima de ellos. La fila de cabecera dedicada evita la colisión sin tocar las pantallas individuales. Esto mantiene a `AppRoot` como el único punto de inyección de UI transversal, consistente con cómo ya envuelve todo en `ShogunAiTheme`.

## Risks / Trade-offs

- [Riesgo] La detección de tema del sistema operativo puede no funcionar de forma confiable en Linux según el entorno de escritorio → Mitigación: el modo Automático cae a claro/oscuro según lo que reporte Compose, y el usuario siempre puede forzar Claro/Oscuro manualmente.
- [Riesgo — confirmado, abierto] El modo Automático no funciona en macOS (Corretto) ni al arrancar ni en caliente, incluso tras reemplazar `isSystemInDarkTheme()` por el sondeo de `org.jetbrains.skiko.currentSystemTheme` descrito en la decisión 4. Causa raíz no identificada aún. Mitigación actual: el usuario puede forzar Claro/Oscuro manualmente, que sí funciona correctamente; Automático queda como funcionalidad incompleta hasta nueva investigación.
- [Riesgo] La paleta clara es nueva y no ha pasado por la misma iteración de contraste que la oscura (que requirió ajustes tras feedback visual) → Mitigación: aplicar el mismo criterio de contraste (~4:1) al definirla, antes de darla por cerrada.
- [Riesgo] Añadir el control de tema dentro de `AppRoot` acopla más lógica de UI a ese archivo → Mitigación: extraer el control a un composable propio (p. ej. `ThemeModeToggle`) en `ui/theme/`, para que `AppRoot` solo lo invoque.
