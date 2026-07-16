# ShogunAi

Orquestador de escritorio que automatiza la creación y gestión de **Git worktrees** aislados, con aprovisionamiento automático de secretos locales — pensado para eliminar el cambio de contexto manual cuando trabajas (o un agente de IA como Claude/Copilot trabaja) en varias tareas a la vez.

## Qué hace

- Crea y elimina **Git worktrees** aislados para cada tarea, cada uno en su propia rama (`feature/` o `fix/`).
- Copia automáticamente a cada worktree nuevo los **archivos de secretos locales** que necesites para compilar (p. ej. `local.properties` de Android).
- Gestiona varios proyectos desde un catálogo, cada uno con su propia configuración (repositorio base, carpeta de worktrees, secretos).

## Instalación

### macOS (Homebrew)

```bash
brew tap LuxionServer/shogunai
brew install --cask shogunai
```

> [!NOTE]
> El instalador de macOS solo soporta Apple Silicon (arm64) por ahora. Además, la app no está notarizada por Apple: si al abrirla por primera vez macOS avisa que "no se puede verificar el desarrollador", hazlo con clic derecho → Abrir, o ejecuta `xattr -dr com.apple.quarantine "/Applications/ShogunAi.app"`.

### Descarga manual

Descarga el instalador para tu sistema operativo desde la [última versión publicada](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Sistema operativo | Instalador |
|---|---|
| macOS | `.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Instálalo como cualquier otra app de escritorio y ábrelo. No necesitas tener JDK ni herramientas de desarrollo instaladas: el instalador incluye su propio runtime.

## Uso básico

1. **Crea un proyecto** — al abrir la app por primera vez verás el catálogo de proyectos vacío. Pulsa "Nuevo proyecto" e indica un nombre, la ruta del repositorio base y la carpeta donde se crearán los worktrees. Opcionalmente añade los archivos de secretos que deben copiarse a cada worktree nuevo.
2. **Entra a un proyecto** — selecciónalo desde el catálogo para ver sus worktrees existentes.
3. **Crea un worktree** — indica el id de la tarea y el tipo de rama (`feature` o `fix`); la app crea el worktree, la rama y copia los secretos configurados.
4. **Elimina un worktree** — cuando termines una tarea, elimínalo desde la lista, con la opción de borrar también la rama local. El worktree principal no se puede eliminar.

## Desarrollo

¿Quieres compilar el proyecto desde el código fuente o contribuir? Empieza por [`docs/getting-started.md`](./docs/getting-started.md).

## Documentación

Arquitectura y decisiones de diseño en [`/docs`](./docs), servida con MkDocs:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## Licencia

[GNU GPLv3](./LICENSE)
