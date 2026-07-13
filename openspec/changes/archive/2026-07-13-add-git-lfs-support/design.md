## Context

`CreateWorktreeUseCase` ejecuta `git worktree add <path> -b <branch>` y trata cualquier `exitCode != 0` como `WorktreeError.GitCommandFailed`. Se confirmó (probando contra un repo real con el hook `post-checkout` que instala `git lfs install`) que cuando `git-lfs` no está en el `PATH`:

- Git **sí** completa la creación del worktree y el checkout de los archivos (el directorio, la rama y el contenido no-LFS quedan en disco).
- El hook `post-checkout` de Git LFS imprime a stderr el texto `"This repository is configured for Git LFS but 'git-lfs' was not found on your path."` y termina con código de salida `2`.
- Git propaga ese código 2 como el exit code del propio `git worktree add`, aunque la operación ya haya completado su trabajo.

Hoy, al recibir ese `exitCode = 2`, el caso de uso lanza `GitCommandFailed` con el stderr crudo y no copia los secretos ni limpia nada. El worktree queda huérfano: existe en disco y en `git worktree list`, pero para nuestro dominio la operación "falló". Un reintento con el mismo `taskId` choca con `WorktreeError.WorktreeAlreadyExists`, dejando al usuario sin salida clara.

## Goals / Non-Goals

**Goals:**
- Detectar de forma específica el fallo de `git worktree add` causado por el hook de Git LFS cuando falta el binario.
- Devolver un error de dominio (`WorktreeError.GitLfsNotFound`) con un mensaje accionable en español, en vez del stderr crudo de Git.
- No dejar el worktree a medias: revertir tanto el directorio (`git worktree remove --force`) como la rama que Git ya creó (`git branch -D`), ambos best-effort, antes de devolver el error.

**Non-Goals:**
- No se intenta instalar `git-lfs` automáticamente ni resolver los punteros LFS por el usuario.
- No se cambia el comportamiento para repositorios sin Git LFS configurado, ni para otros fallos de `git worktree add` (siguen siendo `GitCommandFailed`).
- No se toca la UI: `WorktreeViewModel` ya expone `error.message` de forma genérica, así que el nuevo mensaje se muestra sin cambios adicionales.

## Decisions

- **Detección por contenido de stderr, no solo por exit code.** El código 2 de por sí no es exclusivo de Git LFS (podría coincidir con otro hook o comando). Se detecta buscando el texto estable que imprime el hook de Git LFS (`"git-lfs' was not found on your path"`) en `add.stderr`. Es el mismo patrón que ya usa `WorktreeViewModel.suggestsForceRetry()` para inspeccionar `errorOutput` de `GitCommandFailed`, así que es consistente con el estilo del código existente.
- **Rollback de worktree y rama antes de lanzar el error.** Como Git ya dejó el worktree y la rama creados en disco, hay que limpiar ambos (`git worktree remove --force` y `git branch -D`) para que el dominio mantenga la garantía de "si `CreateWorktreeUseCase` falla, no queda nada a medias" y un reintento con el mismo `taskId` no choque ni con `WorktreeAlreadyExists` ni con "a branch named '...' already exists". Verificado contra un repo real: `git worktree remove --force` por sí solo deja la rama huérfana, lo que rompía el reintento con un `GitCommandFailed` distinto pero igual de confuso. El rollback es best-effort (se ignora su resultado), igual que el existente en `copySecretsOrRollback`.
- **Nuevo tipo de error dedicado (`WorktreeError.GitLfsNotFound`) en vez de reutilizar `GitCommandFailed`.** Permite un mensaje claro y accionable ("instalá Git LFS y reintentá") sin parsear stderr en la capa de UI, y sigue el patrón ya establecido en `WorktreeError` de una subclase por causa de fallo.
- **La detección vive en `CreateWorktreeUseCase`, no en `ShellCommandExecutor` ni en un puerto nuevo.** Es una interpretación de dominio de un resultado de Git específico de este caso de uso; no aplica a `ListWorktreesUseCase` ni `RemoveWorktreeUseCase`, que no ejecutan checkouts.

## Risks / Trade-offs

- [El texto del mensaje de stderr de Git LFS cambia entre versiones] → Se matchea con un substring corto y estable (`"git-lfs' was not found on your path"`) presente en el hook desde hace varias versiones de `git-lfs`; si no matchea, el fallo cae al caso genérico `GitCommandFailed` (degradación segura, no rompe nada).
- [El rollback (`git worktree remove --force`) podría fallar si el worktree quedó en un estado inusual] → Es best-effort, igual que el rollback existente de `copySecretsOrRollback`; el usuario puede limpiar manualmente si hace falta, pero el error reportado ya lo orienta a instalar Git LFS primero.
