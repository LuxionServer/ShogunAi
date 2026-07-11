## Why

Al eliminar un worktree con cambios sin guardar (modificados o sin trackear), `git worktree remove` falla y el usuario solo ve el mensaje de error crudo de git (`fatal: ... contains modified or untracked files, use --force to delete it`), sin ninguna forma de reintentar desde la UI. El caso de uso ya soporta `force`, pero la pantalla nunca lo ofrece.

## What Changes

- `WorktreeViewModel.remove` detecta cuando un fallo de `RemoveWorktreeUseCase` es un `WorktreeError.GitCommandFailed` causado por cambios sin guardar (stderr menciona `--force`) y expone ese estado como pendiente de confirmación en vez de solo un mensaje de error.
- `WorktreeScreen` muestra un `AlertDialog` (mismo patrón que `ProjectListScreen`) preguntando si se desea forzar el borrado cuando ocurre ese fallo específico; al confirmar, reintenta `viewModel.remove(worktree, branch, force = true)`.
- Otros fallos de `RemoveWorktreeUseCase` (que no son por cambios sin guardar) siguen mostrándose como mensaje de error simple, sin diálogo.

## Capabilities

### New Capabilities
(ninguna)

### Modified Capabilities
- `worktree-workspace`: el requisito "Remove a worktree" gana un nuevo escenario — cuando el remove falla por cambios sin guardar, la UI ofrece confirmar el borrado forzado en vez de dejar al usuario sin acción posible.

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/worktree/WorktreeViewModel.kt`
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/worktree/WorktreeScreen.kt`
- Tests correspondientes de `WorktreeViewModel` (si existen) y/o nuevos tests de UI.
- No afecta `RemoveWorktreeUseCase` ni la capa de dominio, que ya soportan `force`.
