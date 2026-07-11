## Context

`ProjectListScreen` solo ofrece dos acciones globales ("Nuevo proyecto") y una acción por fila (tocar el `ListItem` navega a la gestión de worktrees). El dominio ya expone lo necesario para editar (`ProjectConfigViewModel(existingProject)`) y eliminar (`ProjectRepository.delete(id)`); falta exclusivamente la superficie de UI y el cableado en `AppRoot`.

## Goals / Non-Goals

**Goals:**
- Exponer "Editar" y "Eliminar" por cada proyecto en `ProjectListScreen`.
- Confirmar antes de eliminar, para evitar borrados accidentales de un proyecto (y su configuración) sin aviso.
- Reutilizar el flujo de edición existente (`Screen.ProjectConfigForm(existingProject)`) sin duplicar lógica de formulario.

**Non-Goals:**
- Eliminación múltiple/en lote.
- Deshacer eliminación (soft-delete) o papelera.
- Validaciones nuevas de negocio en el formulario de edición (ya cubiertas por `project-configuration`).
- Cambios en el modelo de persistencia (`ProjectRepository`, formato del JSON).

## Decisions

- **Acciones inline por fila, no menú contextual**: se añaden dos `IconButton` (lápiz/papelera) al final de cada `ListItem`, junto a `onClick` de la fila para seguir navegando a worktrees. Es consistente con el estilo minimalista existente (botones de texto/ícono directos) y evita introducir un componente de menú nuevo para dos acciones.
- **Confirmación como estado local de `ProjectListScreen`**: un `AlertDialog` de Material3 controlado con `remember { mutableStateOf<Project?>(null) }` en el composable, sin tocar el `ViewModel`. Mantiene el `ViewModel` libre de estado de presentación (diálogos) y es coherente con que hoy no hay estado de UI en los ViewModels de esta pantalla.
- **`ProjectListViewModel.delete(project)` hace `projectRepository.delete(id)` + `refresh()`**: mismo patrón que `refresh()` ya usa tras guardar, evitando mantener el estado `projects` sincronizado a mano (sin actualizaciones optimistas).
- **Edición navega por `AppRoot`, no dentro de `ProjectListScreen`**: se añade un callback `onEditProject: (Project) -> Unit` al composable, análogo a `onSelectProject`/`onNewProject`, y `AppRoot` lo mapea a `Screen.ProjectConfigForm(existingProject = project)`. Mantiene toda la navegación centralizada en `AppRoot`, como ya ocurre para las otras dos rutas.

## Risks / Trade-offs

- [Eliminar mientras el usuario tiene esa pantalla de worktrees abierta en otra navegación] → Fuera de alcance: la eliminación solo es alcanzable desde `ProjectListScreen`, que es la pantalla raíz; no hay forma de tener `WorktreeManagement` abierto para un proyecto y eliminarlo simultáneamente en esta UI de pantalla única.
- [Fallo silencioso si `ProjectRepository.delete` lanza una excepción de IO] → Fuera de alcance de este cambio; se documenta como limitación conocida, no se añade manejo de errores nuevo (no hay precedente de manejo de errores en esta pantalla hoy).
