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

## Dobles de test

En `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/`:

- `FakeShellCommandExecutor` — sustituye `ShellCommandExecutor` en los tests de casos de uso.
- `FakeFileManager` — sustituye `FileManager`.

Gracias a que el dominio depende de interfaces, los tests de `CreateWorktreeUseCaseTest`, `RemoveWorktreeUseCaseTest` y `ListWorktreesUseCaseTest` no tocan disco ni lanzan procesos reales.
