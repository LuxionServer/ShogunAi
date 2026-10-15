## Why

Cuando el repositorio base se mueve de sitio en disco y el usuario actualiza su ruta en la configuración del proyecto, varias cosas se quedan apuntando a la ubicación antigua:

- Los archivos de secretos introducidos como ruta absoluta (p. ej. pegados a mano) siguen apuntando al repositorio viejo, y además `ProjectConfig.baseSecretPath` los concatena mal con la nueva ruta base.
- `worktreesRoot` no se actualiza aunque los worktrees vivieran dentro o junto al repositorio base (p. ej. `~/projects/main-repo` → `~/projects`).
- Git guarda rutas absolutas en ambos sentidos entre el repositorio base y sus worktrees, así que al mover el repositorio (y, con él, los worktrees) los enlaces quedan rotos y `git worktree list` sigue mostrando rutas viejas.
- La pantalla de worktrees cachea su `ViewModel` por id de proyecto, de modo que tras editar el proyecto se siguen usando los casos de uso construidos con la configuración antigua.

## What Changes

- Las entradas de `secretFiles` absolutas dentro del repositorio base se normalizan a rutas relativas al cargar el proyecto en el formulario y al añadirlas, para que sigan al repositorio cuando se mueva.
- Al cambiar la ruta del repositorio base en el formulario, `worktreesRoot` se mueve con ella si estaba dentro del repositorio, era su carpeta padre o una carpeta hermana, salvo que el usuario la haya editado a mano.
- Nuevo caso de uso `RepairWorktreesUseCase` que ejecuta `git worktree repair` desde el repositorio base, pasando la nueva ruta de los worktrees que se movieron junto con `worktreesRoot`.
- Al guardar un proyecto existente con otra ruta base u otro `worktreesRoot`, la pantalla de worktrees repara los enlaces antes de listar.
- Los `ViewModel` del formulario y de la pantalla de worktrees se cachean también por la configuración, no solo por el id del proyecto.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `project-configuration`: los archivos de secretos y la raíz de worktrees siguen al repositorio base cuando cambia su ruta.
- `worktree-workspace`: los worktrees se reparan tras mover el repositorio y la pantalla usa siempre la configuración vigente.

## Impact

- `domain/usecase/RepairWorktreesUseCase.kt` (nuevo) y su cableado en `AppContainer`/`WorktreeUseCases`.
- `ui/projectconfig/ProjectConfigViewModel.kt`, `ui/projectconfig/RepositoryRelocation.kt` (nuevo), `ui/FilePicker.kt`.
- `ui/AppRoot.kt`, `ui/Screen.kt`, `ui/worktree/WorktreeViewModel.kt`.
- Tests: `RepairWorktreesUseCaseTest`, `ProjectConfigViewModelTest` (nuevos) y `WorktreeViewModelTest`.
