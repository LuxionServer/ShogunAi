## Why

El dominio ya soporta editar (`ProjectConfigViewModel` acepta un `existingProject`) y eliminar (`ProjectRepository.delete`) proyectos, pero la pantalla de listado (`ProjectListScreen`) no expone ninguna acción para llegar a esos flujos: tocar un proyecto solo navega a la gestión de worktrees. Como resultado, hoy no es posible editar ni eliminar un proyecto ya configurado desde la app.

## What Changes

- Añadir una acción "Editar" por proyecto en `ProjectListScreen` que navegue a `ProjectConfigScreen` con el proyecto existente precargado (reutilizando el flujo de edición ya soportado por `ProjectConfigViewModel`).
- Añadir una acción "Eliminar" por proyecto en `ProjectListScreen` que pida confirmación antes de borrar y luego invoque `ProjectRepository.delete` y refresque la lista.
- Cablear ambas acciones en `AppRoot` (navegación a `Screen.ProjectConfigForm(existingProject)` para editar; sin navegación para eliminar, solo refresco de la lista).

## Capabilities

### New Capabilities
(ninguna)

### Modified Capabilities
- `project-catalog`: la lista de proyectos gana acciones de edición y eliminación por proyecto, incluyendo confirmación antes de eliminar.

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/projectlist/ProjectListScreen.kt`: nueva UI de acciones por item (editar/eliminar) y diálogo de confirmación.
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/projectlist/ProjectListViewModel.kt`: método para eliminar un proyecto y refrescar la lista.
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/AppRoot.kt`: nuevo callback `onEditProject` hacia `Screen.ProjectConfigForm`.
- No hay cambios en el dominio (`ProjectRepository`, `ProjectConfigViewModel`) ni en persistencia: ya soportan ambas operaciones.
