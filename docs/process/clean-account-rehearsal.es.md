# Ensayo en una cuenta limpia

Un guion para recorrer ShogunAi como lo hace alguien nuevo, en una cuenta de usuario de macOS sin ninguna de las instrucciones ni la configuración del desarrollador, antes de cada release o demo. Detecta lo que la configuración del propio desarrollador oculta: una demo en otro Mac descubrió que la app abierta desde el Dock daba `shogun` por ausente, se negaba a empezar el recorrido guiado, veía los hooks de Claude Code como inservibles y no copiaba los archivos locales de un worktree, y nada de eso pasaba en la máquina del desarrollador.

El smoke test de la app empaquetada en CI comprueba el mismo camino de forma automática en un runner limpio; el ensayo añade lo que solo puede juzgar una persona: lo que muestra la app, si sus mensajes se entienden y si arranca el agente del recorrido. Lleva una media hora.

## Cuándo

- **Antes de una release**: con la pull request de la release (`release/X.X.X` → `main`) abierta, y con el `.dmg` compilado desde esa rama. Un fallo impide mergearla hasta que se arregla o el mantenedor decide, en su issue, que no la bloquea.
- **Antes de una demo**: con la última release publicada, en el Mac donde se hará la demo siempre que se pueda.

## 1. Conseguir el `.dmg`

- Antes de una release, compílalo desde la rama de la release con el workflow manual `macOS installer`, que lo firma y lo notariza igual que el publicado sin tocar ninguna release, tag ni gestor de paquetes (ver [Firma y notarización en macOS](release.md#firma-y-notarizacion-en-macos)):

    ```bash
    gh workflow run macos-installer.yml -R LuxionServer/ShogunAi --ref release/X.X.X -f version=X.X.X
    gh run download <run-id> -R LuxionServer/ShogunAi -n installer-macos-latest
    ```

    `installer-macos-latest` es el `.dmg` para Apple Silicon (`-arm64`) e `installer-macos-26-intel` el de Intel (`-x64`); coge el del Mac en el que ensayas.

- Antes de una demo, descarga el `.dmg` de la arquitectura del Mac desde la [última release](https://github.com/LuxionServer/ShogunAi/releases/latest).

Cópialo a `/Users/Shared` para que la cuenta nueva pueda llegar a él.

Instálalo desde el `.dmg`, nunca con Homebrew: el Cask enlaza `shogun` por su cuenta y ocultaría la instalación del comando desde la app.

## 2. Crear la cuenta

1. En Ajustes del Sistema › Usuarios y grupos, pulsa "Añadir usuario…" y crea una cuenta **Estándar** (no de administrador), por ejemplo "ShogunAi Rehearsal". Desde Terminal es lo mismo que `sudo sysadminctl -addUser rehearsal -fullName "ShogunAi Rehearsal" -password -`.
2. Entra en ella (basta el cambio rápido de usuario desde la barra de menús o el Centro de control) y salta todos los pasos opcionales del asistente de configuración de macOS.
3. Crea una cuenta nueva cada vez y bórrala al terminar (ver [Limpiar](#7-limpiar)): una cuenta que queda de un ensayo anterior ya tiene `~/.shogunai`, `~/.claude`, `~/.local/bin` y cambios en el perfil del shell, que es justo el estado que el ensayo no debe tener.

Lo que está instalado para toda la máquina sigue siendo visible desde la cuenta nueva: Homebrew en `/opt/homebrew`, el Git de las Command Line Tools de Xcode y las apps de `/Applications`. Antes de seguir, abre Terminal en la cuenta nueva y comprueba:

- `git --version` funciona (si macOS ofrece instalar las Command Line Tools, instálalas: Git es lo único que ShogunAi necesita).
- `which shogun` no imprime nada. Si imprime `/opt/homebrew/bin/shogun`, el Cask está instalado en este Mac y el paso del comando no se puede ensayar: desinstala el Cask desde la cuenta del desarrollador (`brew uninstall --cask shogunai`) o ensaya en otro Mac.
- `ls ~/AGENTS.md ~/.claude ~/.shogunai` no encuentra ninguno.

Después instala Claude Code con su [instalador oficial](https://docs.anthropic.com/en/docs/claude-code/setup) e inicia sesión, sin configurar nada más, para que los únicos hooks sean los que instala la app. El último paso del recorrido lo abre.

## 3. Instalar y abrir desde el Dock

1. Abre el `.dmg` desde `/Users/Shared` y arrastra ShogunAi a `~/Applications` (crea la carpeta si no existe), no a `/Applications`, para no sustituir la copia del desarrollador.
2. Ábrela desde el Finder. **Comprueba**: macOS la abre sin ningún aviso de desarrollador no identificado ni de app dañada (está firmada y notarizada).
3. Déjala en el Dock (clic derecho en su icono › Opciones › Mantener en el Dock), ciérrala y, a partir de aquí, ábrela **solo desde el Dock**: así recibe el `PATH` mínimo de macOS, el entorno que ocultaba los problemas originales.
4. **Comprueba**: la ventana muestra el estado vacío con "Añadir proyecto" y "Hacer el recorrido", y ShogunAi › Acerca de ShogunAi muestra la versión que se ensaya.

## 4. El recorrido guiado

Pulsa "Hacer el recorrido" (o Ayuda › Recorrido de ShogunAi). En [App de escritorio](../guide/desktop-app.md) se explica qué hace cada paso.

1. **Bienvenida**: dice qué va a crear y dónde (`~/.shogunai/tour`). Pulsa "Empezar".
2. **Herramienta de línea de comandos**: como `shogun` no está instalado, el paso lo muestra como ausente y ofrece "Instalar herramienta de línea de comandos". **Comprueba**: no dice que el comando está instalado pero fuera del `PATH`, y no se salta. Instálalo. **Comprueba**: el paso lo da por disponible, o muestra la línea que añadir al perfil del shell cuando lo enlazó en `~/.local/bin` y esa carpeta no está en el `PATH` del shell de login; en ese caso añade la línea, reinicia la app desde el Dock y confirma que el paso ya no lo muestra.
3. **Repositorio de ejemplo**: **Comprueba**: se crea sin errores y aparece el proyecto "ShogunAi tour" con worktrees en cada estado ("Nuevos", "Sin terminar", "Parados", "Fusionados", "Inactivos", uno bloqueado y "Carpeta no encontrada").
4. **Abrir el agente del recorrido**: elige el terminal. **Comprueba**: Terminal se abre en el worktree principal del ejemplo y Claude Code arranca con "Start the ShogunAi tour"; acepta "Yes, I trust this folder". Haz al menos los dos primeros pasos del recorrido con el agente y comprueba que habla el idioma de la app y te espera entre pasos.

## 5. El comando y los hooks

1. Abre una ventana **nueva** de Terminal y ejecuta `shogun --version`. **Comprueba**: imprime la versión que se ensaya.
2. En Preferencias (Cmd+Coma), busca Claude Code. **Comprueba**: ya no se ofrece "Instalar herramienta de línea de comandos" y el "Instalar" de los hooks está habilitado. Pulsa "Instalar".
3. En Terminal, ejecuta `shogun hooks status`. **Comprueba**: dice que los hooks están `installed` en los ajustes del usuario.
4. Arranca Claude Code en cualquier worktree del recorrido (`claude` desde su carpeta) y envíale un mensaje. **Comprueba**: la fila del worktree en la app muestra el agente con un estado (trabajando y luego terminado), que viene de los hooks.

## 6. Un proyecto con archivos locales y `shogun new`

1. En Terminal, crea un repositorio pequeño con un archivo que Git no sigue:

    ```bash
    mkdir -p ~/projects/main-repo && cd ~/projects/main-repo
    git init -b main
    echo "local.properties" > .gitignore
    echo "sdk.dir=/placeholder" > local.properties
    git add .gitignore && git commit -m "Initial commit"
    ```

2. En la app, pulsa "Añadir proyecto", elige `~/projects/main-repo` y, en "Archivos locales", añade `local.properties`. Guarda. **Comprueba**: el proyecto aparece en la barra lateral con su worktree principal.
3. En Terminal, desde `~/projects/main-repo`, ejecuta `shogun new TASK-123`. **Comprueba**: imprime la ruta del worktree nuevo sin errores, y `cat <ruta>/local.properties` imprime `sdk.dir=/placeholder`.
4. **Comprueba**: la app muestra el worktree nuevo bajo el proyecto, en "Nuevos", sin reiniciarla.
5. De vuelta en la app, crea un segundo worktree con "Nuevo worktree" en el proyecto. **Comprueba**: se crea y también recibe `local.properties`.

## Informar de un fallo

Para en el primer fallo: los pasos siguientes suelen fallar por su culpa.

1. Apunta el número del paso (por ejemplo "4.2"), lo que esperaba el guion y lo que has visto.
2. Haz una captura de la app o de Terminal.
3. En la app, elige Ayuda › Copiar informe de diagnóstico y pégalo en el informe. Lleva la versión de la app, la versión y la arquitectura de macOS y las últimas entradas del log de diagnóstico, con las rutas, el nombre de usuario, los nombres de los proyectos y las ramas sustituidos por marcadores (ver [Diagnóstico](../guide/desktop-app.md#diagnostico)).
4. Si falló un comando, añade el comando y su salida completa.
5. Abre una issue en GitHub con todo ello, titulada con el paso, por ejemplo "Rehearsal 0.11.0, step 4.2: the tour says shogun isn't on the PATH". Antes de publicarla, comprueba que las capturas y las salidas no muestran nada privado; fuera del informe de diagnóstico, el nombre de usuario de la cuenta aparece en las rutas.

Antes de una release, enlaza la issue desde la pull request de la release. Cuando el fallo esté arreglado, vuelve a ensayar desde el principio en una cuenta nueva.

## 7. Limpiar

1. Cierra la sesión de la cuenta del ensayo.
2. Desde la cuenta del desarrollador, bórrala en Ajustes del Sistema › Usuarios y grupos, eligiendo borrar su carpeta de inicio (o `sudo sysadminctl -deleteUser rehearsal`).
3. Borra el `.dmg` de `/Users/Shared` y, si desinstalaste el Cask en el paso 2, vuelve a instalarlo en la cuenta del desarrollador.
