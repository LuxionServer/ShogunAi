# ShogunAi

[English](./README.md) · **Español**

Orquestador de escritorio que automatiza la creación y gestión de **Git worktrees** aislados, con aprovisionamiento automático de secretos locales — pensado para eliminar el cambio de contexto manual cuando trabajas (o un agente de IA como Claude/Copilot trabaja) en varias tareas a la vez.

## Qué hace

- Crea y elimina **Git worktrees** aislados para cada tarea, cada uno en su propia rama nueva (`feature/` o `fix/`) o en una rama que ya exista en local.
- Copia automáticamente a cada worktree nuevo los **archivos locales** que necesita para compilar (p. ej. `local.properties` de Android, ficheros `.env`), según lo que declare un `.shogunai.json` versionado en el repositorio o el `.worktreeinclude` de Claude Code.
- Gestiona varios proyectos desde un catálogo, cada uno con su propia configuración (repositorio base, carpeta de worktrees, comando del agente).
- Copia el comando para arrancar tu agente de IA en un worktree (`cd <worktree> && claude`, opcionalmente envuelto con Headroom), listo para pegar en cualquier terminal.
- Si mueves un repositorio, actualiza su ruta en el proyecto y la app vuelve a enlazar sus worktrees (`git worktree repair`).
- Incluye el comando `shogun`, para crear, provisionar, consultar y limpiar worktrees desde un terminal, un script o un hook de un agente.

## Instalación

### macOS (Homebrew)

```bash
brew tap LuxionServer/shogunai
brew install --cask shogunai
```

> [!NOTE]
> Homebrew elige automáticamente el instalador para la arquitectura de tu Mac (Apple Silicon o Intel). La app no está notarizada por Apple: si al abrirla por primera vez macOS avisa que "no se puede verificar el desarrollador", hazlo con clic derecho → Abrir, o ejecuta `xattr -dr com.apple.quarantine "/Applications/ShogunAi.app"`.

### Descarga manual

Descarga el instalador para tu sistema operativo desde la [última versión publicada](https://github.com/LuxionServer/ShogunAi/releases/latest):

| Sistema operativo | Instalador |
|---|---|
| macOS (Apple Silicon) | `-arm64.dmg` |
| macOS (Intel) | `-x64.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

Instálalo como cualquier otra app de escritorio y ábrelo. No necesitas tener JDK ni herramientas de desarrollo instaladas: el instalador incluye su propio runtime.

### El comando `shogun`

- **Homebrew**: se instala con el Cask; `shogun --version` funciona en un terminal nuevo.
- **Linux (`.deb`)**: el lanzador de la app es el comando. Enlázalo una vez: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`.
- **Windows, o sin instalar la app**: descarga `shogun-<versión>.zip` de la release, descomprímelo y añade su carpeta `bin` al `PATH`. Necesita Java 21 o posterior.

## Uso básico

1. **Crea un proyecto** — al abrir la app por primera vez verás el catálogo de proyectos vacío. Pulsa "Nuevo proyecto" e indica un nombre, la ruta del repositorio base y la carpeta donde se crearán los worktrees. Opcionalmente añade archivos locales que deben copiarse a cada worktree nuevo en esta máquina.
2. **Entra a un proyecto** — selecciónalo desde el catálogo para ver sus worktrees existentes.
3. **Crea un worktree** — indica el id de la tarea y el tipo de rama (`feature` o `fix`); la app crea el worktree, la rama y copia los archivos que declaran el repositorio y el proyecto. Para trabajar en una rama que ya tienes, cambia a "Rama existente" y elígela de la lista.
   Para compartir lo que necesita un worktree con todo el que clone el repositorio, añade un `.shogunai.json` en su raíz con patrones al estilo gitignore; cada archivo que Git no versiona y coincide con alguno se copia a cada worktree nuevo, en la misma ruta. Un `.worktreeinclude` (el fichero de Claude Code) se lee igual. El botón "Mover a .shogunai.json" de la configuración del proyecto escribe ahí tus archivos locales.

   ```json
   { "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"] }
   ```

4. **Ponte a trabajar** — pulsa "Copiar comando" en un worktree y pégalo en tu terminal para abrir allí tu agente de IA.
5. **Elimina un worktree** — cuando termines una tarea, elimínalo desde la lista, con la opción de borrar también la rama local. El worktree principal no se puede eliminar.

La app está en inglés por defecto; puedes cambiarla a español desde el selector de idioma de la barra superior (junto al selector de tema). La elección se recuerda entre reinicios.

## Línea de comandos

`shogun` trabaja sobre el repositorio Git que contiene el directorio actual, usando su proyecto de la app de escritorio si lo hay:

```bash
shogun new TASK-123                # worktree nuevo en feature/TASK-123, provisionado; imprime su ruta
shogun new --branch hotfix/login   # worktree para una rama existente
shogun provision ../otro-worktree  # copia los archivos declarados en un worktree creado por otra herramienta
shogun status                      # todos los worktrees con sus cambios, divergencia y si están mergeados
shogun clean --yes                 # elimina los worktrees mergeados o sin carpeta (sin --yes solo los lista)
```

Ejecuta `shogun help` para ver todas las opciones. Sin proyecto en la app, `shogun new` necesita `--worktrees-root <dir>`.

### Con agentes de IA

Haz que `claude --worktree` cree sus worktrees a través de ShogunAi añadiendo los hooks a `.claude/settings.json`:

```json
{
  "hooks": {
    "WorktreeCreate": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }],
    "WorktreeRemove": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }]
  }
}
```

Las recetas para Cursor, Codex y cualquier otra herramienta están en [Uso con agentes](./docs/agents.es.md).

## Repositorios Gradle

Declara un repositorio Gradle (Android, KMP o JVM) en su `.shogunai.json`:

```json
{ "copy": ["/local.properties"], "providers": { "gradle": {} } }
```

Cada build en un worktree arranca un daemon de Gradle (y normalmente un daemon de compilación de Kotlin) cuando no hay uno ocioso que reutilizar, y cada daemon conserva su memoria durante horas después de la build. En un repositorio Gradle, la pantalla de worktrees muestra una sección "Daemons de Gradle" con todos los daemons de la máquina: su memoria, si están ocupados u ociosos y el worktree de su última build. Desde ahí puedes parar los ociosos, y "Limpiar" ofrece parar los daemons ociosos de los worktrees que elimina. Un daemon ocupado nunca se para.

Si el repositorio no activa la caché de build de Gradle (`org.gradle.caching=true` en `gradle.properties`), ShogunAi te lo dice: con ella activada, la primera build de un worktree nuevo reutiliza lo que otros worktrees ya compilaron.

```bash
shogun gradle                      # lista los daemons, su memoria, estado y worktree
shogun gradle stop                 # para todos los daemons ociosos (o solo los ids de proceso indicados)
shogun clean --yes --stop-gradle-daemons  # para también los daemons ociosos de los worktrees eliminados
```

Inspeccionar los daemons necesita `ps` (macOS y Linux).

## Desarrollo

¿Quieres compilar el proyecto desde el código fuente o contribuir? Empieza por [`docs/getting-started.es.md`](./docs/getting-started.es.md).

## Documentación

Arquitectura y decisiones de diseño en [`/docs`](./docs) (en inglés y español), servida con MkDocs:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## Licencia

[GNU GPLv3](./LICENSE)
