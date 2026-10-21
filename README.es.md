# ShogunAi

[English](./README.md) · **Español**

Ejecuta varios agentes de IA en paralelo en la misma máquina sin que se pisen entre ellos.

Un worktree de Git aísla el código de un agente, pero un agente necesita más que código para trabajar: los ficheros locales que espera la build, sus propios puertos, su propio emulador o simulador, y daemons y cachés de compilación que no choquen con los de su vecino. ShogunAi da a cada worktree todo ese entorno, listo en cuanto arranca el agente.

Tiene dos partes:

- **El comando `shogun` y los hooks**, donde ocurre el trabajo. Claude Code, Codex, Cursor, Herdr o un simple script crean, provisionan y limpian sus worktrees a través de él.
- **La app de escritorio, una torre de control**, para verlo todo. En todos tus proyectos, muestra qué agente trabaja dónde, cuál te está esperando y el estado de cada worktree, incluidos los que crearon otras herramientas.

## Qué recibe cada agente

- **Su propio worktree y su rama**: una rama `feature/` o `fix/` nueva, o una que ya exista.
- **Los ficheros locales que necesita para compilar**, como el `local.properties` de Android o los ficheros `.env`, declarados en un `.shogunai.json` versionado en el repositorio o en el `.worktreeinclude` de Claude Code.
- **Sus propios puertos y variables**, declarados una vez en `.shogunai.json` y entregados a la shell del agente con `shogun env` o el hook de sesión.
- **Sus propios dispositivos y recursos de compilación acotados**, mediante providers: un emulador Android, un simulador iOS, daemons de Gradle limitados y una caché de compilación de Xcode compartida. Los repositorios que no los necesitan nunca los ven.
- **Una salida limpia**: los worktrees ya mergeados o desaparecidos se listan y se eliminan con `shogun clean`.

## La torre de control

- Una vista "Activos", de todos tus proyectos, con los worktrees que tienen un agente trabajando, trabajo esperándote o en curso.
- Un catálogo de proyectos, cada uno con su repositorio base, su carpeta de worktrees y su comando de agente.
- Un clic para abrir un worktree en la terminal, el IDE, el gestor de archivos o Herdr, o para copiar el comando que arranca tu agente en él.
- Si mueves un repositorio, actualiza su ruta y la app vuelve a enlazar sus worktrees (`git worktree repair`).

## Instalación

### macOS (Homebrew)

```bash
brew tap LuxionServer/shogunai
brew trust --tap LuxionServer/shogunai
brew install --cask shogunai
```

> [!IMPORTANT]
> Las versiones recientes de Homebrew solo cargan casks de taps no oficiales en los que confías. Ejecuta `brew trust --tap LuxionServer/shogunai` después de `brew tap` y antes de `brew install`, o Homebrew se negará a cargar el cask.

> [!NOTE]
> Homebrew elige automáticamente el instalador para la arquitectura de tu Mac (Apple Silicon o Intel). La app no está notarizada por Apple: si al abrirla por primera vez macOS avisa que "no se puede verificar el desarrollador", hazlo con clic derecho → Abrir, o ejecuta `xattr -dr com.apple.quarantine "/Applications/ShogunAi.app"`.

### Windows (winget o Scoop)

```powershell
winget install LuxionServer.ShogunAi
```

```powershell
scoop bucket add shogunai https://github.com/LuxionServer/scoop-shogunai
scoop install shogunai
```

> [!NOTE]
> Scoop también proporciona el comando `shogun`, sin necesitar Java.

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
- **Scoop**: se instala con la app; `shogun --version` funciona en un terminal nuevo, sin necesitar Java.
- **Linux (`.deb`)**: el lanzador de la app es el comando. Enlázalo una vez: `sudo ln -s /opt/shogunai/bin/ShogunAi /usr/local/bin/shogun`.
- **Windows (winget o `.msi`), o sin instalar la app**: descarga `shogun-<versión>.zip` de la release, descomprímelo y añade su carpeta `bin` al `PATH`. Necesita Java 21 o posterior.

## Uso básico

1. **Crea un proyecto** — al abrir la app por primera vez te ofrece añadir uno. Pulsa "Añadir proyecto" (también en la barra lateral y en Archivo, Mayús+Cmd/Ctrl+N) y elige la carpeta del repositorio con el selector, o suelta la carpeta sobre la ventana. El nombre, la carpeta de worktrees junto al repositorio y la rama principal se rellenan a partir de ella, y puedes editarlos antes de añadir el proyecto. Opcionalmente añade archivos locales que deben copiarse a cada worktree nuevo en esta máquina.
2. **Mira qué pasa** — la app se abre en "Activos": los worktrees, de todos tus proyectos, con un agente trabajando, con trabajo esperándote o todavía en curso, agrupados por estado. "Todos los worktrees" lista todo, y al seleccionar un proyecto en la barra lateral ves sus worktrees. Con clic derecho puedes editarlo o quitarlo; quitar un proyecto nunca toca su repositorio ni sus worktrees.
3. **Crea un worktree** — indica el id de la tarea y el tipo de rama (`feature` o `fix`); la app crea el worktree, la rama y copia los archivos que declaran el repositorio y el proyecto. Para trabajar en una rama que ya tienes, cambia a "Rama existente" y elígela de la lista.
   Para compartir lo que necesita un worktree con todo el que clone el repositorio, añade un `.shogunai.json` en su raíz con patrones al estilo gitignore; cada archivo que Git no versiona y coincide con alguno se copia a cada worktree nuevo, en la misma ruta. Un `.worktreeinclude` (el fichero de Claude Code) se lee igual. El botón "Mover a .shogunai.json" de la configuración del proyecto escribe ahí tus archivos locales, y su sección "Configuración del repositorio" activa providers y edita el entorno de cada worktree sin escribir el JSON a mano.

   ```json
   { "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"] }
   ```

4. **Ponte a trabajar** — pulsa "Copiar comando" en un worktree y pégalo en tu terminal para abrir allí tu agente de IA.
5. **Elimina un worktree** — cuando termines una tarea, elimínalo desde la lista, con la opción de borrar también la rama local. El worktree principal no se puede eliminar.

La app está en inglés por defecto; puedes cambiarla a español en Preferencias (Ajustes… en el menú ShogunAi en macOS, Archivo > Preferencias en el resto, Cmd/Ctrl+Coma), junto al tema. La elección se recuerda entre reinicios, igual que el último proyecto abierto, el tamaño y la posición de la ventana y si la barra lateral está contraída (Ver > Mostrar u ocultar barra lateral, Cmd/Ctrl+Barra invertida).

### Atajos de teclado

Usa Cmd en macOS y Ctrl en el resto.

| Atajo | Acción |
|---|---|
| Cmd/Ctrl+K | Paleta de comandos: ir a cualquier vista, proyecto, worktree o acción |
| Cmd/Ctrl+1, Cmd/Ctrl+2 | Mostrar Activos, mostrar Todos los worktrees |
| Cmd/Ctrl+N | Nuevo worktree |
| Mayús+Cmd/Ctrl+N | Añadir proyecto |
| Cmd/Ctrl+S | Guardar los ajustes del proyecto |
| Cmd/Ctrl+F | Buscar en la lista de worktrees |
| Mayús+Cmd/Ctrl+C | Copiar el comando de lanzamiento del worktree seleccionado |
| Cmd/Ctrl+Retroceso | Eliminar el worktree seleccionado (pregunta antes) |
| Cmd/Ctrl+R | Recargar |
| Cmd/Ctrl+Barra invertida | Mostrar u ocultar la barra lateral |
| Cmd/Ctrl+Coma | Preferencias |

En la paleta de comandos, las flechas recorren los resultados, Intro ejecuta el resaltado y Cmd/Ctrl+Intro copia el comando de lanzamiento de un worktree.

## Línea de comandos

`shogun` trabaja sobre el repositorio Git que contiene el directorio actual, usando su proyecto de la app de escritorio si lo hay:

```bash
shogun new TASK-123                # worktree nuevo en feature/TASK-123, provisionado; imprime su ruta
shogun new --branch hotfix/login   # worktree para una rama existente
shogun provision ../otro-worktree  # copia los archivos declarados en un worktree creado por otra herramienta
shogun status                      # todos los worktrees con sus cambios, divergencia y si están mergeados
shogun clean --yes                 # elimina los worktrees mergeados o sin carpeta (sin --yes solo los lista)
eval "$(shogun env)"               # carga las variables de este worktree desde "env" en .shogunai.json (puertos, …)
```

Ejecuta `shogun help` para ver todas las opciones, o consulta la [referencia de la línea de comandos](./docs/guide/cli.es.md). Sin proyecto en la app, `shogun new` necesita `--worktrees-root <dir>`. Todo lo que admite `.shogunai.json` está en [Configuración](./docs/guide/configuration.es.md).

### Con agentes de IA

Haz que `claude --worktree` cree sus worktrees a través de ShogunAi añadiendo los hooks a `.claude/settings.json`:

```json
{
  "hooks": {
    "WorktreeCreate": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }],
    "WorktreeRemove": [{ "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }],
    "SessionStart": [{ "hooks": [{ "type": "command", "command": "shogun hook session-start" }] }]
  }
}
```

`SessionStart` da a cada sesión las variables propias de su worktree, como el puerto de un servidor de desarrollo, declaradas en `.shogunai.json` (`"env": { "PORT": "${port}" }`). Las recetas para Herdr, Cursor, Codex, direnv y cualquier otra herramienta están en [Uso con agentes](./docs/agents.es.md).

## Providers

Un repositorio activa providers en su `.shogunai.json` para dar a cada worktree lo que necesita su stack:

```json
{ "providers": { "gradle": { "daemonMaxHeap": "2g" }, "emulator": {}, "ios-simulator": {}, "xcode": { "compilationCache": true } } }
```

- **Gradle**: ver y parar los daemons de Gradle y Kotlin que deja cada build de un worktree, y acotar su memoria y su vida.
- **Emulador de Android**: cada worktree recibe su propio emulador y `ANDROID_SERIAL` la primera vez que lo arranca, así que los agentes en paralelo no instalan en los dispositivos de los demás.
- **Simulador de iOS** (macOS): cada worktree recibe su propio simulador e `IOS_SIMULATOR_UDID` la primera vez que lo arranca, para `xcodebuild` y los tests de KMP.
- **Xcode** (macOS): ver y borrar los DerivedData huérfanos, y compartir compilaciones entre worktrees.

Los emuladores, los simuladores y los daemons de Gradle comparten pools de toda la máquina cuyos límites siguen un perfil de capacidad elegido en Preferencias (Automático por defecto, según la memoria y los núcleos de la máquina): cuando un pool está lleno, se libera el dispositivo inactivo usado menos recientemente para dejar sitio.

Cómo configurar cada uno está en [Providers](./docs/guide/providers.es.md). Los repositorios sin un provider no ven nada de esto.

## Desarrollo

¿Quieres compilar el proyecto desde el código fuente o contribuir? Empieza por [`docs/getting-started.es.md`](./docs/getting-started.es.md).

## Documentación

La guía de uso, la arquitectura y las decisiones de diseño están en [`/docs`](./docs) (en inglés y español), servida con MkDocs:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## Licencia

[GNU GPLv3](./LICENSE)
