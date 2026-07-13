## Context

`desktopApp` es una app Compose Desktop (JVM-only; `shared` está vacío por ahora). Hoy `AppRoot` envuelve todo en `MaterialTheme {}` sin `colorScheme` ni `typography` propios, y ni `Window` (`main.kt`) ni `nativeDistributions` (`desktopApp/build.gradle.kts`) definen un icono. La máquina de desarrollo (macOS) tiene `iconutil` y `sips` disponibles, pero no ImageMagick, Inkscape ni `rsvg-convert` — no hay forma de rasterizar SVG a PNG sin añadir una dependencia nueva.

## Goals / Non-Goals

**Goals:**
- Dar a la app un icono propio, generado (no una referencia de marca existente), usable como `windowIcon` de la ventana y en el empaquetado nativo (macOS/Windows/Linux).
- Definir un esquema de color y tipografía Material3 propios, aplicados globalmente desde `AppRoot`, sin rediseñar el layout de las pantallas existentes.
- Mantener el cambio sin dependencias nuevas de Gradle (todo con JDK/AWT + herramientas ya presentes en el sistema).

**Non-Goals:**
- Toggle claro/oscuro ni persistencia de preferencia de tema — se deja para una spec futura (`theme-mode-switching` o similar).
- Rediseño de layout/spacing de `ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen` — solo heredan el nuevo `colorScheme`/`typography`.
- Icono "final" de marca — es una primera versión simple, reemplazable más adelante sin fricción.

## Decisions

**Icono adaptado de un diseño propio del usuario, no generado desde cero.** El usuario aportó `shogun-app-icon.png`, un mockup de presentación (1408×768) con dos variantes de icono (casco samurái + insignia "S") sobre tarjetas con texto; se eligió la variante izquierda (gris/rojo). Al no haber `rsvg-convert`/ImageMagick/Inkscape instalados, todo el procesado se hizo con `jshell` + `java.awt.Graphics2D`/`ImageIO` (sin dependencia nueva): recorte manual de la región del glyph, eliminación de fondo con flood-fill por distancia de color, decontaminación de alpha en los bordes (para evitar halo claro en el contorno) y reescalado bicúbico a un master PNG cuadrado de 1024×1024 con fondo transparente. La resolución efectiva de partida es limitada (~300–390px de contenido real dentro del mockup), aceptado explícitamente por el usuario para esta primera versión.

**Formatos derivados del master PNG:**
- macOS (`.icns`): generar un `.iconset` con los tamaños estándar (16–1024, @1x/@2x) y convertir con `iconutil -c icns` (ya disponible en el sistema).
- Windows (`.ico`): sin ImageMagick disponible, escribir a mano el contenedor ICO (formato "PNG-in-ICO", soportado desde Windows Vista) embebiendo 4 tamaños (16/32/48/256) como PNG — evita depender de una librería externa (p. ej. TwelveMonkeys) solo para esto.
- Linux (`.png`): usar directamente el PNG de 512px, formato esperado por `nativeDistributions.linux.iconFile`.
- Icono de ventana (`windowIcon`): un PNG (256px) en `desktopApp/src/main/resources/icons/`, cargado con `androidx.compose.ui.res.painterResource(...)` (API clásica de Compose Desktop para recursos de classpath; no requiere Compose Resources multiplatform, ya que este módulo es JVM-only).

**Tema en paquete propio `ui/theme/`** (`Color.kt`, `Type.kt`, `Theme.kt` con un composable `ShogunAiTheme`), siguiendo la convención habitual de Compose Material3, en vez de definir el `ColorScheme`/`Typography` inline en `AppRoot`. Mantiene `AppRoot` legible y deja un punto único de extensión cuando llegue el soporte claro/oscuro.

**Un solo `colorScheme`, explícitamente de modo oscuro (no `lightColorScheme`/`darkColorScheme` condicionados)** — el toggle de tema sigue siendo no-goal de este cambio, pero tras feedback del usuario se confirmó que la paleta actual (fondo casi negro, rojo como acento) está pensada para modo oscuro; el estilo previo (`MaterialTheme {}` por defecto, claro) funcionaba bien como modo claro. Para dejar el terreno preparado sin adelantar trabajo de la spec futura: el `ColorScheme` interno de `Theme.kt` se nombra `ShogunDarkColorScheme` (antes `ShogunColorScheme`) y queda documentado en el propio archivo que la spec `theme-mode-switching` deberá añadir un `ShogunLightColorScheme` análogo más un parámetro `darkTheme` en `ShogunAiTheme` para alternar entre ambos. No se construye toggle ni esquema claro en este cambio — solo el nombrado/comentario que deja el punto de extensión claro.

**Corrección post-feedback: `Surface` raíz + recalibrado de contraste + `Taskbar` para el icono del Dock.** Tras una primera pasada, el usuario reportó que no veía el icono en el Dock de macOS al lanzar la app y que los botones se veían con muy poco contraste. Diagnóstico: (1) `ShogunAiTheme` fijaba `colorScheme`/`typography` en `MaterialTheme` pero nunca pintaba un `Surface` con `colorScheme.background`, por lo que la ventana mostraba el lienzo por defecto de Compose Desktop en vez del fondo oscuro diseñado; (2) `ShogunOutline` (borde de `OutlinedButton`, muy usado en las tres pantallas) tenía ~1.8:1 de contraste contra el fondo, prácticamente invisible; (3) `ShogunRed` como `primary` — y por tanto como color de texto por defecto de `OutlinedButton`/`TextButton` — solo alcanzaba ~3:1 de contraste como texto sobre el fondo oscuro. Se corrigió envolviendo `content` en un `Surface` dentro de `ShogunAiTheme`, aclarando `ShogunOutline` y `ShogunRed`, y añadiendo `ShogunOnAccent` (blanco puro) para `onPrimary`/`onSecondary`/`onTertiary`. Para el Dock, se añadió `java.awt.Taskbar.setIconImage(...)` en `main.kt` en vez de depender solo del flag `-Xdock:icon` (variable según JDK/launcher). Ver tarea 6 en `tasks.md`.

## Risks / Trade-offs

- [Contenedor `.ico` escrito a mano puede quedar malformado] → Mitigación: usar el formato PNG-in-ICO (estructura simple: cabecera + directorio + PNG crudo por entrada), limitarlo a 4 tamaños de uso común, y verificar abriendo el archivo resultante (`file icon.ico`, inspección de cabecera) antes de darlo por bueno.
- [El icono parte de un mockup de baja resolución efectiva, no de un asset vectorial limpio] → Aceptado explícitamente por el usuario ("esto es todo lo que tengo"); queda documentado como reemplazable sin fricción (un solo master PNG del que se derivan el resto de tamaños/formatos).
- [Paleta única sin adaptación al tema del sistema puede verse mal en modo oscuro del SO] → Mitigación: elegir tonos de contraste moderado; el non-goal de toggle claro/oscuro se resuelve en una spec futura, no en esta.

## Migration Plan

Sin datos ni estado persistido de por medio. Orden de implementación: generar master PNG → derivar `.icns`/`.ico`/PNG de Linux/PNG de ventana → cablear `iconFile` en `desktopApp/build.gradle.kts` y `icon` en `main.kt` → crear `ui/theme/` → aplicar `ShogunAiTheme` en `AppRoot` reemplazando `MaterialTheme {}` → verificar visualmente con `./gradlew :desktopApp:run` (ventana con icono correcto, pantallas con la nueva paleta/tipografía).

## Open Questions

- Ninguna bloqueante. Tamaño del PNG de Linux (512px) y de `windowIcon` (256px) son suposiciones razonables por defecto de Compose Desktop; ajustables sin cambio de diseño si algo no rinde bien en algún DE.
