# AGENTS.md

Guidelines de estilo y de cómo documentar para agentes de IA que trabajen en este repositorio. **No es documentación del proyecto** — para eso está `docs/` (arquitectura, decisiones, proceso). Este archivo es la fuente única de guidelines; `CLAUDE.md` y cualquier otro archivo específico de herramienta redirigen aquí en vez de duplicar contenido.

No cargues `docs/` completo en contexto por defecto: busca la página concreta que necesites (`docs/architecture/*.md`, `docs/decisions/index.md`, `docs/process/openspec.md`) solo cuando la tarea lo requiera.

## Estilo de código

- Comentarios de dominio en **español**, coherente con el código existente.
- Los casos de uso devuelven `Result<T>` con errores tipados (`sealed class WorktreeError`), nunca excepciones sin tipar de cara al llamador.
- Los comandos de shell se ejecutan como **argv** (`listOf("git", ...)`), nunca como cadena para un shell.
- El dominio depende de puertos (`ShellCommandExecutor`, `FileManager`), nunca de implementaciones concretas directamente.
- **Nunca uses identificadores de la empresa** (nombres internos de repos, prefijos de tareas, etc.) en código, tests o ejemplos. Usa placeholders genéricos (`~/projects/main-repo`, `TASK-123`).

## Cómo documentar

| Qué documentas | Dónde |
|---|---|
| Arquitectura, modelos, capas del sistema | `docs/architecture/` (mkdocs) |
| Decisión de diseño de un cambio concreto | `design.md` del cambio en OpenSpec (`openspec/changes/<nombre>/`) |
| Decisiones previas a OpenSpec (histórico) | `docs/decisions/index.md` — no editar entradas viejas, solo lectura |
| Proceso de trabajo (cómo se planifica, cómo se hacen PRs, etc.) | `docs/process/` (mkdocs) |
| Guidelines de estilo/comportamiento para IA | Este archivo |

Reglas:

- `docs/` (mkdocs) es para humanos: explica el *qué* y el *por qué* del sistema, en español, sin jerga de una sola sesión.
- Este archivo (`AGENTS.md`) es solo para agentes: reglas de estilo y de proceso, no contenido del proyecto. Si vas a añadir una explicación de arquitectura o una decisión, va en `docs/` u OpenSpec, no aquí.
- Los cambios no triviales se planifican con OpenSpec (`/opsx:propose` → `/opsx:apply` → `/opsx:archive`) antes de implementarse — ver `docs/process/openspec.md`.

## Comandos

```bash
./gradlew :desktopApp:run            # ejecutar la app
./gradlew :desktopApp:hotRun --auto  # ejecutar con hot reload
./gradlew test                       # tests de todos los módulos
mkdocs serve                         # servir la documentación en local
```

## Git

- No añadir el trailer `Co-Authored-By: Claude` (ni de ninguna IA) en los commits de este proyecto.
