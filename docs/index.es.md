# ShogunAi

Orquestador de escritorio (Kotlin Multiplatform) para automatizar workflows de desarrollo complejos:

- Creación y eliminación de **Git worktrees** aislados.
- Aprovisionamiento de secretos locales necesarios para compilar (p. ej. `local.properties` de Android).
- Preparación del estado exacto que necesita un agente de IA (Copilot/Claude) para empezar a trabajar de inmediato en una tarea, sin cambios de contexto manuales.

## Estado del proyecto

En desarrollo activo y publicado como app de escritorio (ver el README para instalarla). Gestiona un catálogo de proyectos y, para cada uno, crea worktrees desde ramas nuevas o existentes, les copia los secretos locales, copia el comando para lanzar un agente de IA en un worktree y repara los worktrees cuando se mueve el repositorio. La interfaz está en inglés y español. Ver [decisiones de diseño](decisions/index.md) para lo decidido antes de OpenSpec, y `openspec/changes/archive/` para cada cambio desde entonces.

## Dónde mirar

| Quiero... | Ir a |
|---|---|
| Aterrizar en el repo por primera vez (requisitos, cómo ejecutarlo, cómo orientarme) | [Primeros pasos](getting-started.md) |
| Entender cómo está organizado el código | [Arquitectura › Visión general](architecture/overview.md) |
| Ver los modelos y casos de uso del dominio | [Arquitectura › Capa de dominio](architecture/domain.md) |
| Ver cómo se implementan los puertos (Git, ficheros) | [Arquitectura › Capa de infraestructura](architecture/infrastructure.md) |
| Entender cómo se planifican los cambios | [Proceso de desarrollo › OpenSpec](process/openspec.md) |
| Saber por qué se tomó tal decisión | [Decisiones de diseño](decisions/index.md) |
| Ver las guidelines de estilo que sigue la IA (no documentación del proyecto) | `AGENTS.md` en la raíz del repo (`CLAUDE.md` redirige a él) |

## Ejecutar el proyecto

```bash
./gradlew :desktopApp:run          # ejecución estándar
./gradlew :desktopApp:hotRun --auto  # hot reload
```

## Servir esta documentación en local

```bash
mkdocs serve
```
