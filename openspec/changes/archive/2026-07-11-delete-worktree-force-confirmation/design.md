## Context

`RemoveWorktreeUseCase` ya acepta un flag `force` y lo traduce a `git worktree remove --force <path>` (`RemoveWorktreeUseCase.kt:24-52`). Cuando el worktree tiene cambios sin guardar, git rechaza el remove sin `--force` con un mensaje de error que siempre contiene el texto `--force` (p.ej. `fatal: '<path>' contains modified or untracked files, use --force to delete it`). Ese fallo llega a la UI como `WorktreeError.GitCommandFailed`, pero `WorktreeScreen` (`WorktreeScreen.kt:81`) solo invoca `remove(worktree, worktree.branch)` una vez, sin `force` ni reintento, así que el usuario queda bloqueado.

`ProjectListScreen` ya resuelve un flujo de confirmación análogo (borrar proyecto) con un `AlertDialog` controlado por un estado `remember { mutableStateOf<Project?>(null) }` en el propio Composable (`ProjectListScreen.kt:38,79-98`).

## Goals / Non-Goals

**Goals:**
- Cuando el borrado de un worktree falla porque tiene cambios sin guardar, ofrecer al usuario un diálogo de confirmación para reintentar con `--force`.
- Reutilizar el patrón de `AlertDialog` ya existente en `ProjectListScreen` para mantener consistencia visual y de código.
- No tocar `RemoveWorktreeUseCase` ni `WorktreeError`: la capa de dominio ya expone todo lo necesario (`force` como parámetro, `GitCommandFailed` con `errorOutput`).

**Non-Goals:**
- No se pide confirmación *antes* de cada borrado (solo cuando git efectivamente falla por cambios sin guardar). Pedir confirmación siempre añadiría fricción al caso feliz.
- No se distingue entre "modified" vs "untracked" vs "locked worktree"; cualquier fallo de git que sugiera `--force` se trata igual (se ofrece reintentar forzando).
- No se traduce ni se reformatea el mensaje de git; se sigue mostrando tal cual cuando no aplica el diálogo.

## Decisions

- **Dónde detectar "necesita force"**: en la capa de UI (`WorktreeViewModel`), inspeccionando `WorktreeError.GitCommandFailed.errorOutput` en busca del substring `--force` (case-insensitive), no en el dominio.
  - Alternativa considerada: agregar un nuevo tipo `WorktreeError.RemovalRequiresForce` en el dominio, detectado dentro de `RemoveWorktreeUseCase`. Se descarta para este cambio porque el dominio ya modela correctamente el concepto (`force` es un parámetro explícito del caso de uso); el problema es puramente de UX en la pantalla, y agregar un tipo de error nuevo movería lógica de interpretación de mensajes de git al dominio sin necesidad. Si en el futuro se necesita este matiz en más de un lugar, vale la pena reconsiderarlo.
  - Riesgo aceptado: el texto exacto de git podría variar entre versiones o locales. Ver Riesgos.

- **Dónde vive el estado de confirmación pendiente**: en `WorktreeViewModel`, como `var worktreePendingForceRemoval: Worktree?` (siguiendo el mismo rol que `errorMessage`), en vez de estado local del Composable como en `ProjectListScreen`. Motivo: la decisión de "requiere force" depende del resultado de una llamada asíncrona al caso de uso, que ya vive en el ViewModel; mantenerlo ahí evita que el Composable tenga que inspeccionar `WorktreeError` directamente.

- **Reintento**: al confirmar el diálogo, se llama a `viewModel.remove(worktree, branchToDelete, force = true)` de nuevo (mismo `branchToDelete` que el intento original). No se agrega una función separada `forceRemove`; se reutiliza `remove` con el parámetro existente.

## Risks / Trade-offs

- [Riesgo] Detectar el caso "requiere force" por substring en `stderr` es frágil si cambia el mensaje de git (versión distinta, salida localizada). → Mitigación: encapsular la detección en una función pura y testeada (`GitCommandFailed` → `Boolean`) dentro de `WorktreeViewModel` o como extensión, para que sea fácil de ajustar si el texto cambia; si falla la detección, el usuario simplemente ve el mensaje de error crudo como hoy (no hay regresión, solo se pierde la mejora de UX).
- [Trade-off] No se ofrece el diálogo para otros fallos de `GitCommandFailed` (p.ej. problemas de permisos) aunque también mencionen `--force` de forma incidental. Se acepta como aceptable dado que git solo sugiere `--force` en escenarios donde efectivamente forzar resuelve el problema.
