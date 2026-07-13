## Why

`git worktree add` puede terminar con código de salida distinto de cero cuando el repositorio tiene Git LFS configurado (hook `post-checkout` instalado) pero el binario `git-lfs` no está en el `PATH` del usuario. Git sí crea el worktree y hace el checkout de los archivos, pero propaga el código de salida del hook (2) como si el comando hubiera fallado. Hoy `CreateWorktreeUseCase` interpreta cualquier código distinto de cero como `WorktreeError.GitCommandFailed`, aborta antes de copiar los secretos y no limpia el worktree que Git sí dejó creado en disco. El resultado es un estado a medias: el directorio y la rama existen, pero el caso de uso reporta un fallo genérico con el stderr crudo de Git, y un reintento choca con `WorktreeAlreadyExists`.

## What Changes

- `CreateWorktreeUseCase` detecta específicamente el fallo de `git worktree add` causado por el hook de Git LFS cuando `git-lfs` no está instalado (a partir del stderr del comando).
- En ese caso concreto, se revierte el worktree parcialmente creado (`git worktree remove --force`, best-effort, igual que en el rollback de secretos) para no dejar el directorio a medias.
- Se añade un nuevo error de dominio `WorktreeError.GitLfsNotFound` con un mensaje claro y accionable (instalar Git LFS y reintentar), en vez de exponer el stderr crudo de `GitCommandFailed`.
- Otros fallos de `git worktree add` no relacionados con Git LFS siguen reportándose como `WorktreeError.GitCommandFailed`, sin cambios.

## Capabilities

### New Capabilities
(ninguna)

### Modified Capabilities
- `worktree-workspace`: la creación de un worktree ahora distingue el fallo por Git LFS no instalado y responde con `WorktreeError.GitLfsNotFound` en vez de `WorktreeError.GitCommandFailed`, revirtiendo el worktree parcial antes de devolver el error.

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/usecase/CreateWorktreeUseCase.kt`
- `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/model/WorktreeError.kt`
- `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/usecase/CreateWorktreeUseCaseTest.kt`
- No afecta la UI: `WorktreeViewModel` ya muestra `error.message` de forma genérica para cualquier `WorktreeError`.
