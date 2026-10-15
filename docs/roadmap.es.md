# Roadmap

Ideas aceptadas para el futuro de ShogunAi, agrupadas por tema. Esta página marca una dirección, no un compromiso de fechas: cada punto se convierte en un cambio de OpenSpec (`/opsx:propose`) cuando se aborda, y su diseño se decide allí. Cuando un cambio se archiva, su punto se elimina de esta página.

## Planificado

### Estado de los worktrees de un vistazo

Hoy la lista de worktrees solo muestra la ruta y la rama de cada uno. Cuando varios agentes trabajan en paralelo, hace falta ver cuáles han terminado y cuáles siguen con cambios sin abrir una terminal. Cada worktree mostraría:

- Cambios sin commitear (`git status --porcelain`).
- Commits por delante y por detrás de su upstream y de la rama principal (`git rev-list --count`).
- Fecha del último commit.
- Si su rama ya está mergeada en la rama principal.

### Limpieza de worktrees obsoletos

- Una acción para eliminar todos los worktrees cuya rama ya está mergeada, borrando opcionalmente también las ramas.
- `git worktree prune` para los worktrees cuya carpeta ya no existe.

Depende del estado de los worktrees para saber qué ramas están mergeadas.

### Elegir la base de un worktree nuevo

Hoy una rama nueva parte del `HEAD` del repositorio base, así que crear un worktree desde una rama actualizada obliga a pasar antes por otro cliente de Git. En su lugar:

- Elegir la rama base (`develop`, `main`, …) al crear un worktree, con un `git fetch` previo opcional.
- Crear un worktree desde una rama remota (`origin/...`), no solo desde las locales.

### Provisionado de worktrees más completo

Copiar archivos de secretos es solo una parte de dejar un worktree listo para compilar. Cada proyecto podría configurar además:

- Un hook post-creación ejecutado como argv (p. ej. `npm install`, `direnv allow`).
- Carpetas y patrones glob a copiar (p. ej. `.env*`, `.idea/runConfigurations`), no solo archivos sueltos.
- Enlazar un secreto con un symlink en vez de copiarlo, para que al rotarlo se actualice en todos los worktrees.
- Detectar secretos de un worktree que difieren de los del repositorio base.

### Lanzar el agente y las herramientas directamente

Copiar el comando de lanzamiento sigue siendo la opción por defecto, pero la app también podría abrirlo directamente con integraciones concretas y fiables en lugar de un lanzamiento genérico de terminal:

- Terminales: iTerm2 y Terminal en macOS, Windows Terminal, tmux y Zellij.
- «Abrir en IDE» (VS Code, IntelliJ IDEA, Android Studio) y «Abrir carpeta».

### Modo multiagente

Hay quien alterna entre agentes de IA en una misma tarea, por ejemplo cuando a uno se le agota la cuota. Un proyecto guardaría varios comandos de agente en vez de uno (Claude, Antigravity, Codex, …), cada uno con sus argumentos por defecto, y el usuario elegiría el agente al copiar o lanzar el comando. La configuración actual de un solo agente sigue siendo la opción por defecto para quien no lo necesite.

### Ergonomía de escritorio

- Icono en la barra de menús / bandeja del sistema con los proyectos y worktrees recientes.
- Paleta de comandos (`⌘K` / `Ctrl+K`) para saltar a cualquier proyecto o worktree.
- Refresco automático vigilando los worktrees del repositorio, en lugar de recargar a mano.
- Importar y exportar la configuración de un proyecto sin rutas absolutas, p. ej. un `.shogunai.json` en el repositorio que defina sus secretos y hooks, para que un equipo pueda compartirla.

### Distribución

- Comprobación de actualizaciones contra GitHub Releases: la app detecta una versión nueva y ofrece descargarla (o indica `brew upgrade` si se instaló con Homebrew).
- Gestores de paquetes de Windows: publicar en winget y Scoop junto al `.msi`.

## En estudio

### Integración con GitHub y gestores de tareas

Crear un worktree desde un issue de GitHub o una tarea de un gestor (derivando el id y el título de la tarea) y mostrar el estado del pull request de cada rama (abierto, en revisión, CI en rojo, mergeado). El valor está claro, pero el modelo de integración todavía no: qué proveedores, cómo autenticarse y cuánto depender de CLIs externas como `gh`. Hay que pensarlo más antes de convertirlo en un cambio.

## Fuera de alcance por ahora

### Firma y notarización en macOS

Queda fuera por ahora: requiere el programa de desarrollador de Apple, que es de pago. Mientras tanto, el README explica cómo abrir la app sin notarizar.
