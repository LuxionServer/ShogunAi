# ShogunAi

Ejecuta varios agentes de IA en paralelo en la misma máquina sin que se pisen entre ellos. ShogunAi es un comando `shogun` y una torre de control de escritorio (Kotlin + Compose Multiplatform) que se encargan de:

- Creación y eliminación de **Git worktrees** aislados.
- Aprovisionamiento de los archivos locales necesarios para compilar (p. ej. `local.properties` de Android), declarados en un `.shogunai.json` versionado en el repositorio o en `.worktreeinclude`.
- Dar a cada worktree su propio entorno: puertos, un emulador de Android, un simulador de iOS, daemons de Gradle acotados y una caché de compilación de Xcode compartida.
- Preparación del estado exacto que necesita un agente de IA (Claude Code, Codex, Cursor, Herdr…) para empezar a trabajar de inmediato en una tarea, sin cambios de contexto manuales.

## Estado del proyecto

En desarrollo activo y publicado como app de escritorio con el comando `shogun` incluido (ver [Instalación](guide/installation.md)). Gestiona un catálogo de proyectos y, para cada uno, crea worktrees desde ramas nuevas, existentes o remotas, partiendo de la base que elijas, y los provisiona, muestra su estado, origen, tamaño y agentes en ejecución (con lo que está haciendo cada agente de Herdr), se actualiza solo y limpia los mergeados. Abre un worktree en el terminal, el IDE, el gestor de archivos o Herdr con uno de los agentes del proyecto, lista los worktrees recientes en un icono de la barra de menús o de la bandeja del sistema y avisa cuando sale una versión nueva. Sus providers añaden emuladores y simuladores por worktree y gestionan los daemons de Gradle y los DerivedData de Xcode, sus hooks permiten que Claude Code y Herdr creen worktrees a través de él, y todos los comandos de `shogun` pueden imprimir JSON para scripts. Los comandos post-creación que declara un repositorio solo se ejecutan cuando los apruebas en tu máquina. La interfaz está en inglés y español, con tema claro, oscuro o del sistema. Ver [decisiones de diseño](decisions/index.md) para lo decidido antes de OpenSpec, y `openspec/changes/archive/` para cada cambio desde entonces.

## Dónde mirar

| Quiero... | Ir a |
|---|---|
| Instalar ShogunAi y el comando `shogun` | [Guía de uso › Instalación](guide/installation.md) |
| Aprender a usar la app de escritorio | [Guía de uso › App de escritorio](guide/desktop-app.md) |
| Consultar un comando u opción de `shogun` | [Guía de uso › Referencia de la línea de comandos](guide/cli.md) |
| Escribir un `.shogunai.json` o encontrar un fichero que guarda ShogunAi | [Guía de uso › Configuración](guide/configuration.md) |
| Configurar Gradle, emuladores de Android, simuladores de iOS o Xcode | [Guía de uso › Providers](guide/providers.md) |
| Hacer que Claude Code, Herdr, Cursor o Codex creen worktrees provisionados | [Uso con agentes](agents.md) |
| Aterrizar en el repo por primera vez (requisitos, cómo ejecutarlo, cómo orientarme) | [Primeros pasos](getting-started.md) |
| Entender cómo está organizado el código | [Arquitectura › Visión general](architecture/overview.md) |
| Ver los modelos y casos de uso del dominio | [Arquitectura › Capa de dominio](architecture/domain.md) |
| Ver cómo se implementan los puertos (procesos, ficheros, simuladores, Herdr…) | [Arquitectura › Capa de infraestructura](architecture/infrastructure.md) |
| Ver cómo funcionan las pantallas y los ViewModels | [Arquitectura › Capa de UI](architecture/ui.md) |
| Ver cómo se construye y se distribuye el comando `shogun` | [Arquitectura › Línea de comandos](architecture/cli.md) |
| Saber de qué rama partir y hacia dónde abrir una PR | [Proceso de desarrollo › Ramas](process/branching.md) |
| Entender cómo se planifican los cambios | [Proceso de desarrollo › OpenSpec](process/openspec.md) |
| Saber por qué se tomó tal decisión | [Decisiones de diseño](decisions/index.md) |
| Ver qué está previsto | [Roadmap](roadmap.md) |
| Ver las guidelines de estilo que sigue la IA (no documentación del proyecto) | `AGENTS.md` en la raíz del repo (`CLAUDE.md` redirige a él) |

## Ejecutar el proyecto

```bash
./gradlew :desktopApp:run          # ejecución estándar
./gradlew :desktopApp:hotRun --auto  # hot reload
./gradlew :cli:run --args="status"   # el comando shogun
```

## Servir esta documentación en local

```bash
mkdocs serve
```
