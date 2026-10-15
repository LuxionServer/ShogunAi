## 1. Archivos de secretos

- [x] 1.1 Normalizar a relativas las entradas absolutas de `secretFiles` dentro del repositorio base al cargar el proyecto en `ProjectConfigViewModel` y al añadirlas
- [x] 1.2 Tests en `ProjectConfigViewModelTest`

## 2. Raíz de worktrees

- [x] 2.1 Hacer que `worktreesRoot` siga a `baseRepositoryPath` cuando estaba dentro, era su padre o una carpeta hermana, salvo edición manual
- [x] 2.2 Tests en `ProjectConfigViewModelTest`

## 3. Reparar worktrees

- [x] 3.1 Crear `RepairWorktreesUseCase` (`git worktree repair` con las nuevas rutas de los worktrees movidos) y cablearlo en `AppContainer`
- [x] 3.2 Ejecutarlo desde la pantalla de worktrees tras guardar un proyecto con otra ruta base o raíz de worktrees
- [x] 3.3 Tests en `RepairWorktreesUseCaseTest`

## 4. Configuración vigente

- [x] 4.1 Incluir la configuración en la clave de los `ViewModel` del formulario y de la pantalla de worktrees

## 5. Verificación

- [ ] 5.1 `./gradlew test` en verde
