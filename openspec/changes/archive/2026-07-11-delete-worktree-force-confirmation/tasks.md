## 1. ViewModel

- [x] 1.1 En `WorktreeViewModel`, agregar una función privada/extensión que determine si un `WorktreeError.GitCommandFailed` indica que el remove necesita `--force` (busca el substring `--force` en `errorOutput`, case-insensitive).
- [x] 1.2 Agregar estado `var worktreePendingForceRemoval by mutableStateOf<Worktree?>(null)` y guardar también el `branchToDelete` asociado al intento fallido (p.ej. como parte del mismo estado o uno auxiliar).
- [x] 1.3 Modificar `remove(worktree, branchToDelete, force)`: en `onFailure`, si `force == false` y el error indica que necesita force, setear `worktreePendingForceRemoval` (y el branch asociado) en vez de (o además de) `errorMessage`; si no, comportarse como hoy.
- [x] 1.4 Agregar función `confirmForceRemoval()` que invoque `remove(worktree, branchToDelete, force = true)` con los datos guardados y limpie `worktreePendingForceRemoval`.
- [x] 1.5 Agregar función `dismissForceRemoval()` que limpie `worktreePendingForceRemoval` sin ejecutar ningún comando.

## 2. UI

- [x] 2.1 En `WorktreeScreen`, importar `AlertDialog` y `TextButton` de `androidx.compose.material3`.
- [x] 2.2 Renderizar un `AlertDialog` cuando `viewModel.worktreePendingForceRemoval != null`, con título/mensaje explicando que el worktree tiene cambios sin guardar y preguntando si se desea forzar el borrado.
- [x] 2.3 Botón de confirmación llama a `viewModel.confirmForceRemoval()`; botón de cancelar (o `onDismissRequest`) llama a `viewModel.dismissForceRemoval()`.

## 3. Tests

- [x] 3.1 Test de `WorktreeViewModel`: remove falla con `GitCommandFailed` cuyo `errorOutput` sugiere `--force` → `worktreePendingForceRemoval` queda seteado y `errorMessage` no se usa para mostrar el diálogo (o se limpia según corresponda).
- [x] 3.2 Test de `WorktreeViewModel`: remove falla con `GitCommandFailed` que NO sugiere `--force` → `errorMessage` se setea y `worktreePendingForceRemoval` permanece `null`.
- [x] 3.3 Test de `WorktreeViewModel`: `confirmForceRemoval()` invoca el caso de uso con `force = true`, y en éxito remueve el worktree de la lista y limpia `worktreePendingForceRemoval`.
- [x] 3.4 Test de `WorktreeViewModel`: `dismissForceRemoval()` limpia el estado sin ejecutar ningún comando adicional.

## 4. Verificación manual

- [x] 4.1 Ejecutar la app, crear un worktree, modificar un archivo dentro de él sin commitear, e intentar eliminarlo desde la UI: confirmar que aparece el diálogo y que "Eliminar"/confirmar borra el worktree.
- [x] 4.2 Confirmar que cancelar el diálogo deja el worktree intacto en la lista.
- [x] 4.3 Confirmar que otros errores de `RemoveWorktreeUseCase` (p.ej. path inexistente) siguen mostrando solo el mensaje de error, sin diálogo.
