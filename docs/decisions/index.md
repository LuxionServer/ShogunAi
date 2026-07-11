# Decisiones de diseño

Registro de decisiones de arquitectura tomadas durante el desarrollo, junto con el motivo. Se añade una entrada nueva cuando se toma una decisión que no es obvia leyendo el código.

!!! info "A partir de OpenSpec"
    Desde la incorporación de [OpenSpec](../process/openspec.md), las decisiones de diseño de cada cambio se documentan en el `design.md` de ese cambio y quedan preservadas al archivarse en `openspec/changes/archive/`. Esta página cubre lo decidido **antes** de adoptar OpenSpec (iteración 1); para cambios posteriores, esa es la fuente de verdad.

## Iteración 1 — Dominio e infraestructura del flujo de worktrees (2026-07-10)

**Todo el código de dominio e infraestructura vive en `desktopApp`, no en `shared`.**
Motivo: el único target del proyecto es JVM/escritorio (`shared` usa `jvm()`). Poner lógica de dominio en un módulo "compartido" sin otros targets reales solo añade indirección.

**`ProjectConfig` es completamente configurable** (ruta del repo base, carpeta padre de worktrees, lista de archivos de secretos).
Motivo: que el flujo sea reutilizable entre proyectos distintos sin tocar la lógica de los casos de uso, y sin hardcodear nombres o identificadores propios de un proyecto concreto.

**`BranchType` = `FEATURE` / `FIX`**, con prefijo `feature/` o `fix/` sobre el ID de la tarea.
Cubre el flujo de ramas que se necesita hoy; no se añaden más tipos hasta que haga falta.

**Los casos de uso devuelven `Result<T>` con errores tipados (`WorktreeError`)**, no excepciones sin tipar ni códigos de error.
Motivo: que la futura capa de GUI pueda decidir qué mensaje mostrar inspeccionando el tipo del error, sin parsear strings.

**Los puertos de dominio (`ShellCommandExecutor`, `FileManager`) tienen implementación real y doble de test.**
`ProcessBuilderShellCommandExecutor` ejecuta el comando como argv (no `sh -c`) para evitar diferencias de shell entre macOS y Linux, y problemas de inyección/escapado. `NioFileManager` usa `java.nio.file`. Ambos tienen fakes (`FakeShellCommandExecutor`, `FakeFileManager`) para testear los casos de uso sin tocar disco ni lanzar procesos.

**GUI en Compose**: implementada en el cambio `project-worktree-ui` (catálogo de proyectos, formulario de configuración, gestión de worktrees). Decisiones de diseño de esa iteración en `openspec/changes/archive/`.
