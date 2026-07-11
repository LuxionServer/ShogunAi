## 1. Icono maestro

- [x] 1.1 Extraer y limpiar el glyph elegido (variante izquierda) desde `shogun-app-icon.png` (aportado por el usuario) con `jshell` + `java.awt`/`ImageIO`: recorte, eliminación de fondo (flood-fill) y decontaminación de alpha en bordes, exportado como PNG maestro cuadrado de 1024×1024 con fondo transparente
- [x] 1.2 Revisar visualmente el PNG resultante a distintos tamaños (16, 32, 256px) para confirmar que sigue siendo legible/reconocible al reducirlo

## 2. Derivar assets por plataforma

- [x] 2.1 Generar el `.iconset` (16–1024, @1x/@2x) a partir del master y convertirlo a `.icns` con `iconutil -c icns`
- [x] 2.2 Escribir el contenedor `.ico` (formato PNG-in-ICO) embebiendo tamaños 16/32/48/256 a partir del master, y verificar la cabecera del archivo resultante
- [x] 2.3 Exportar el PNG de 512px para Linux y el PNG de 256px para `windowIcon`
- [x] 2.4 Ubicar los assets finales: `.icns`/`.ico`/PNG de Linux en `desktopApp/icons/`, y el PNG de ventana en `desktopApp/src/main/resources/icons/`

## 3. Cablear el icono en la app

- [x] 3.1 En `desktopApp/build.gradle.kts`, configurar `nativeDistributions { macOS { iconFile }, windows { iconFile }, linux { iconFile } }` apuntando a los assets generados
- [x] 3.2 En `main.kt`, cargar el PNG de ventana con `painterResource(...)` y pasarlo como `icon` al `Window`
- [x] 3.3 Ejecutar `./gradlew :desktopApp:run` y confirmar que la ventana muestra el icono propio (no el de Java por defecto)

## 4. Tema Material3 propio

- [x] 4.1 Crear `ui/theme/Color.kt` con la paleta de color propia (contraste moderado, sin blanco/negro puros)
- [x] 4.2 Crear `ui/theme/Type.kt` con la `Typography` propia
- [x] 4.3 Crear `ui/theme/Theme.kt` con el composable `ShogunAiTheme` que arma el `colorScheme` + `typography` y envuelve `content` en `MaterialTheme`
- [x] 4.4 En `AppRoot.kt`, reemplazar `MaterialTheme { ... }` por `ShogunAiTheme { ... }`, sin tocar la lógica de navegación ni los ViewModels

## 5. Verificación

- [ ] 5.1 Ejecutar `./gradlew :desktopApp:run` y navegar por `ProjectListScreen`, `ProjectConfigScreen` y `WorktreeScreen`, confirmando que las tres heredan el nuevo `colorScheme`/`typography` (verificado que la app arranca sin errores y usa el icono propio; falta inspección visual manual de cada pantalla, sin herramienta de captura disponible en esta sesión)
- [x] 5.2 Ejecutar `./gradlew test` y confirmar que los tests existentes siguen pasando (el cambio no debería afectarlos al ser puramente de presentación)
- [x] 5.3 Confirmar que no se introdujeron dependencias nuevas de Gradle

## 6. Correcciones tras feedback de usuario (icono en el Dock + contraste de botones)

- [x] 6.1 `ShogunAiTheme` no envolvía `content` en un `Surface`: el `MaterialTheme` fija tokens de color pero no pinta ningún fondo, así que la ventana quedaba con el lienzo por defecto de Compose Desktop en vez de `colorScheme.background`. Se añadió `Surface(color = colorScheme.background, contentColor = colorScheme.onBackground)` en `Theme.kt` envolviendo `content`
- [x] 6.2 `ShogunOutline` (borde de `OutlinedButton`) tenía un ratio de contraste ~1.8:1 contra `ShogunBackground` (prácticamente invisible); se subió su luminosidad (`0xFF3E434D` → `0xFF70788A`, ~3.7:1)
- [x] 6.3 `ShogunRed` (usado como `primary`, y por defecto como color de texto de `OutlinedButton`/`TextButton`) solo daba ~3:1 de contraste como texto sobre `background`; se aclaró (`0xFFB3382C` → `0xFFD6473C`) para acercarse a ~4:1 en ambos sentidos (como texto sobre fondo oscuro, y como relleno bajo texto claro)
- [x] 6.4 `onPrimary`/`onSecondary`/`onTertiary` reutilizaban `ShogunOnSurface` (gris claro, no blanco puro); se introdujo `ShogunOnAccent` (blanco puro) para maximizar el contraste sobre los rellenos de color de acento, reforzando la identidad rojo/negro/blanco
- [x] 6.5 El icono del Dock en macOS dependía únicamente de `-Xdock:icon` (flag JVM vía `nativeDistributions.macOS.iconFile`), poco fiable según el JDK/launcher usado; se añadió `java.awt.Taskbar.setIconImage(...)` en `main.kt` como mecanismo adicional en runtime, independiente de cómo se lance el proceso
- [ ] 6.6 Confirmación visual del usuario tras relanzar: icono visible en el Dock y contraste de botones aceptable
