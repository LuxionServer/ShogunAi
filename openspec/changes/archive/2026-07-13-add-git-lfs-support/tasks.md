## 1. Dominio

- [x] 1.1 Añadir `WorktreeError.GitLfsNotFound` en `WorktreeError.kt`, con un mensaje en español que indique instalar Git LFS (https://git-lfs.com) y reintentar
- [x] 1.2 En `CreateWorktreeUseCase`, tras un `add.isSuccess == false`, detectar si `add.stderr` contiene el texto del hook de Git LFS (`"git-lfs' was not found on your path"`)
- [x] 1.3 Cuando se detecte, ejecutar `git worktree remove --force <worktreePath>` y `git branch -D <branch>` (ambos best-effort, ignorando el resultado) y lanzar `WorktreeError.GitLfsNotFound`. La rama también hay que borrarla: verificado que `git worktree remove` sola deja la rama huérfana y rompe el reintento.
- [x] 1.4 Cuando no se detecte, mantener el comportamiento actual (`WorktreeError.GitCommandFailed`)

## 2. Tests

- [x] 2.1 Test en `CreateWorktreeUseCaseTest`: `git worktree add` falla con stderr de Git LFS → el resultado es `WorktreeError.GitLfsNotFound` y se invoca `git worktree remove --force` con el path del worktree
- [x] 2.2 Test en `CreateWorktreeUseCaseTest`: `git worktree add` falla con un stderr no relacionado con Git LFS → el resultado sigue siendo `WorktreeError.GitCommandFailed` y no se invoca ningún rollback
- [x] 2.3 Correr `./gradlew test` y confirmar que pasa toda la suite

## 3. Verificación manual

- [x] 3.1 Reproducir el escenario original (worktree con hook de Git LFS y `git-lfs` fuera del PATH) y confirmar que la app muestra el nuevo mensaje y que un reintento no choca con `WorktreeAlreadyExists`
