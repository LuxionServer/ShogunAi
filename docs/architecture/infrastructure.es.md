# Capa de infraestructura

Vive en `desktopApp/src/main/kotlin/app/luxion/shogunai/infrastructure`. Implementa los puertos definidos en el dominio.

## `ProcessBuilderShellCommandExecutor`

Implementación de `ShellCommandExecutor` sobre `ProcessBuilder`. Detalles de robustez:

- **Lee stdout y stderr de forma concurrente** (dos `async`). Si se leyeran en serie, un proceso que llena el buffer del stream no leído se bloquearía (deadlock clásico de `ProcessBuilder`).
- `waitFor()` se envuelve en `runInterruptible` para que la cancelación de la corrutina interrumpa la espera del proceso.
- El trabajo bloqueante se confina en `Dispatchers.IO` (inyectable por constructor para tests).
- En el `finally` se hace `process.destroy()` si el proceso sigue vivo, para no dejar procesos huérfanos si la corrutina se cancela.
- **Directorios de Homebrew en el `PATH`**: las apps lanzadas desde el Finder o el Dock de macOS no heredan el `PATH` del shell, así que no encontrarían herramientas instaladas con Homebrew (p. ej. `git-lfs`). `HomebrewPath.merge` antepone `/opt/homebrew/bin`, `/opt/homebrew/sbin` y `/usr/local/bin` al `PATH` del proceso hijo, sin duplicados.
- Si el proceso no llega a arrancar, la `IOException` se envuelve en `CommandExecutionException`.

## `NioFileManager`

Implementación de `FileManager` sobre `java.nio.file`. `copy` usa `REPLACE_EXISTING` y `COPY_ATTRIBUTES`. `directorySize` recorre el árbol con `Files.walkFileTree` sin seguir enlaces simbólicos, suma el tamaño de los archivos regulares, se salta las carpetas excluidas e ignora los archivos que no puede leer. Funciona igual en todos los sistemas operativos soportados.

## `AwtClipboardWriter`

Implementación de `ClipboardWriter` con el portapapeles del sistema de AWT (`Toolkit.getDefaultToolkit().systemClipboard`), envuelta en `runCatching`.

## Catálogo de proyectos (`projects.json`)

`JsonProjectRepository` guarda los proyectos en `~/.shogunai/projects.json` con `kotlinx.serialization` (`ignoreUnknownKeys`, para que se sigan cargando ficheros con campos de versiones anteriores). Cada escritura reemplaza el fichero completo de forma atómica: se escribe en un temporal de la misma carpeta y se mueve sobre el original con `ATOMIC_MOVE`, así un cierre inesperado nunca deja el catálogo a medio escribir.

## Preferencias de usuario (`settings.json`)

`JsonSettingsFile` es el fichero `~/.shogunai/settings.json`, compartido por `JsonThemePreferenceRepository` (puerto `ThemePreferenceRepository`) y `JsonLanguagePreferenceRepository` (puerto `LanguagePreferenceRepository`). Guarda `{ "themeMode": ..., "language": ... }`.

- **Cada guardado reescribe solo su campo**: `update` lee el contenido actual, aplica el cambio y escribe el fichero completo de forma atómica (mismo patrón que `JsonProjectRepository`). Así cambiar el idioma no pisa el tema y viceversa.
- **Valores por defecto**: `SYSTEM` para el tema e `ENGLISH` para el idioma, si el fichero no existe, está vacío o no se puede parsear. Los ficheros escritos por versiones anteriores (solo `themeMode`) se leen sin problema: el idioma toma su valor por defecto.

## Dobles de test

En `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/`:

- `FakeShellCommandExecutor` — sustituye `ShellCommandExecutor`: responde a cada argv con un `CommandResult` preparado y registra los comandos ejecutados.
- `FakeFileManager` — sustituye `FileManager` con un conjunto en memoria de rutas existentes.
- `FakeClipboardWriter` — sustituye `ClipboardWriter`.
- `FakeProjectRepository` — sustituye `ProjectRepository` en los tests de ViewModels.

Gracias a que el dominio depende de interfaces, los tests de casos de uso (`*UseCaseTest`) y los de ViewModels no tocan disco ni lanzan procesos reales. La propia infraestructura se cubre con `ProcessBuilderShellCommandExecutorTest`, `JsonProjectRepositoryTest` y `JsonSettingsFileTest`, que sí usan procesos reales y ficheros temporales.
