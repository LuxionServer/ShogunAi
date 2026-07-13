# Capa de infraestructura

Vive en `desktopApp/src/main/kotlin/app/luxion/shogunai/infrastructure`. Implementa los puertos definidos en el dominio.

## `ProcessBuilderShellCommandExecutor`

Implementación de `ShellCommandExecutor` sobre `ProcessBuilder`. Detalles de robustez:

- **Lee stdout y stderr de forma concurrente** (dos `async`). Si se leyeran en serie, un proceso que llena el buffer del stream no leído se bloquearía (deadlock clásico de `ProcessBuilder`).
- `waitFor()` se envuelve en `runInterruptible` para que la cancelación de la corrutina interrumpa la espera del proceso.
- El trabajo bloqueante se confina en `Dispatchers.IO` (inyectable por constructor para tests).
- En el `finally` se hace `process.destroy()` si el proceso sigue vivo, para no dejar procesos huérfanos si la corrutina se cancela.

## `NioFileManager`

Implementación de `FileManager` sobre `java.nio.file`. `copy` usa `REPLACE_EXISTING` y `COPY_ATTRIBUTES`. Funciona igual en macOS y Linux (targets soportados).

## `ProcessTerminalLauncher`

Implementación de `TerminalLauncher`: `ProcessBuilder(command).directory(File(workingDirectory)).start()`, envuelto en `runCatching`, sin esperar a que el proceso termine (fire-and-forget — a diferencia de `ProcessBuilderShellCommandExecutor`, que sí espera el exit code).

## `SystemTerminalEmulatorDetector`

Implementación de `TerminalEmulatorDetector`. Ramifica por `System.getProperty("os.name")`:

- **macOS**: comprueba existencia de `/Applications/iTerm.app` y `/Applications/Warp.app` vía `FileManager.exists`; `MACOS_TERMINAL` siempre se añade al final (Terminal.app viene con el sistema).
- **Linux**: `which <binario>` vía `ShellCommandExecutor.execute(...).isSuccess` para `gnome-terminal`, `konsole`, `xterm`.

## `TerminalCommandBuilder`

Objeto puro en `domain/usecase/` (no infraestructura, pero documentado aquí por construir el detalle de invocación por emulador): traduce `TerminalEmulator` + ruta + comando al argv concreto. Para `MACOS_TERMINAL`/`ITERM2` genera un script de AppleScript pasado a `osascript` (como varios argumentos `-e`, nunca como una única cadena de shell):

- `ITERM2`: abre una **pestaña nueva** — `create tab with default profile` en `current window` (o `create window with default profile` si no hay ninguna). El dictionary de iTerm2 soporta crear pestañas directamente, sin permisos extra.
- `MACOS_TERMINAL`: si no hay ninguna ventana de Terminal abierta, `do script` sin destino crea una directamente (fallback, sin pestañas). Si ya hay una, fuerza una **pestaña nueva** de forma determinista: `activate` + `System Events` simulando Cmd+T, y luego `do script ... in front window` — una pestaña recién creada siempre está idle, así que ese `do script` la usa sin ambigüedad. Terminal.app no tiene un comando de AppleScript para crear pestañas directamente; Cmd+T vía `System Events` exige permiso de Accesibilidad, que en modo dev (`./gradlew :desktopApp:run`, sin identidad de bundle propia) macOS no siempre llega a solicitar — el keystroke queda sin efecto y sin error visible, degradando a reutilizar la pestaña actual (el bug original: lanzamientos seguidos escribiendo uno sobre otro sin separador). Empaquetar la app como `.app`/dmg debería permitir que macOS pida el permiso correctamente (pendiente de verificar).

## Dobles de test

En `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/`:

- `FakeShellCommandExecutor` — sustituye `ShellCommandExecutor` en los tests de casos de uso.
- `FakeFileManager` — sustituye `FileManager`.
- `FakeTerminalLauncher` — sustituye `TerminalLauncher`.
- `FakeTerminalEmulatorDetector` — sustituye `TerminalEmulatorDetector`.

Gracias a que el dominio depende de interfaces, los tests de `CreateWorktreeUseCaseTest`, `RemoveWorktreeUseCaseTest` y `ListWorktreesUseCaseTest` no tocan disco ni lanzan procesos reales.
