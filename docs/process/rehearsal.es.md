# Ensayo en una cuenta de macOS limpia

El [smoke test de la app empaquetada](release.md#smoke-test-de-la-app-empaquetada) comprueba cada instalador en runners de CI recién creados, pero no abre ninguna ventana y nadie lee las pantallas. Antes de cada release, y antes de cada demo en otra máquina, ensaya una vez como lo haría un usuario nuevo: en una cuenta de usuario de macOS que nunca ha ejecutado ShogunAi, sin las instrucciones, los ajustes ni las herramientas del desarrollador, recorriendo el tour guiado hasta el final.

Lleva unos 20 minutos. Lo que no se comporte como se describe abajo bloquea la release o la demo hasta que se corrija o se explique.

## 1. Crear la cuenta limpia

1. En **Ajustes del Sistema → Usuarios y grupos**, añade un usuario **Estándar**, por ejemplo "Ensayo ShogunAi". No inicies sesión en una cuenta de Apple ni copies en ella dotfiles, `~/.claude`, `~/.shogunai`, `AGENTS.md` ni el perfil de la shell.
2. Cambia a ella con el cambio rápido de usuario (el menú de usuario de la barra de menús), así la sesión del desarrollador sigue abierta pero no se usa nada de ella.

Que sea estándar importa: no puede escribir en `/opt/homebrew` ni en `/usr/local`, como en un Mac donde otra persona instaló Homebrew o donde no está instalado.

## 2. Instalar

- **Release candidata**: descarga el artefacto `installer-macos-latest` (Apple Silicon) o `installer-macos-26-intel` del run `Release candidate` de la rama, en la pestaña Actions, descomprímelo y abre el `.dmg`.
- **Release publicada** (antes de una demo): descarga el `.dmg` de la página de la release, o instala con Homebrew desde una cuenta que lo tenga, como explica [Instalación](../guide/installation.md).

Arrastra ShogunAi a Aplicaciones (desde una cuenta estándar, macOS pide la contraseña de un administrador). La app no está notarizada, así que la primera apertura muestra el aviso de desarrollador no verificado: sáltalo como explica [Instalación](../guide/installation.md), y comprueba que ese aviso es el único obstáculo.

## 3. Primer arranque

Abre ShogunAi desde el Dock, el Launchpad o el Finder, nunca desde un terminal: solo así recibe el `PATH` mínimo que le da el Dock.

- La ventana muestra el estado vacío con **Hacer el recorrido** y **Añadir proyecto**.
- No aparece ningún aviso de error, y Preferencias se abre.

## 4. El tour guiado

Elige **Hacer el recorrido** y comprueba cada paso:

1. **Bienvenida**: dice qué se va a crear y dónde (`~/.shogunai/tour`). Elige **Empezar**.
2. **Herramienta de línea de comandos**: en esta cuenta falta `shogun`, así que el paso ofrece **Instalar herramienta de línea de comandos** y **Omitir**. Instálala: la app dice dónde la ha puesto. En una cuenta estándar sin una carpeta escribible en el `PATH`, es `~/.local/bin`, y el paso muestra la línea exacta que hay que añadir al perfil de la shell. Añádela, como haría un usuario, y continúa. Si `shogun` lo instaló el Cask de Homebrew y está en el `PATH` de la shell de login, este paso no debe aparecer.
3. **Creación del ejemplo**: termina sin errores, la barra lateral muestra "ShogunAi tour" y su vista, agrupada por estado, muestra worktrees en "Te necesitan", "Nuevos", "Parados", "Fusionados", "Inactivos" y "Carpeta no encontrada", y una fila con la etiqueta "bloqueado".
4. **Abrir el agente**: el paso ofrece las formas disponibles en esta máquina. Sin ningún agente instalado, elegir una debe explicar qué falta en lugar de fallar en silencio. Para recorrer el guion del tour, instala Claude Code en esta cuenta, inicia sesión y abre el agente en el terminal: pide confiar en la carpeta del ejemplo y luego empieza el tour en el idioma de la app. Síguelo por el estado, la limpieza, el aprovisionamiento, la línea de comandos, los hooks, el entorno y los providers.

## 5. Más allá del tour

- **Hooks de Claude Code**: en Preferencias, la sección de Claude Code ofrece **Instalar los hooks** cuando el comando ya se puede usar (tras reiniciar la app si se instaló en una carpeta que no estaba en el `PATH`). Instálalos y comprueba que aparecen como instalados.
- **Un proyecto con ficheros locales**: crea un repositorio Git pequeño con un `.env.local` ignorado, añádelo con **Añadir proyecto**, lista `.env.local` como fichero local, crea un worktree y comprueba que el fichero está en él. Después, en un terminal nuevo, ejecuta `shogun new segunda-tarea` en el repositorio y comprueba que se copia el mismo fichero.
- **Copiar informe de diagnóstico** (menú Ayuda) copia un informe sin más rutas ni nombres de la cuenta de los que necesita.

## 6. Terminar y limpiar

1. En el banner del proyecto del tour, elige **Terminar recorrido** y confirma: el proyecto desaparece y `~/.shogunai/tour` ya no existe.
2. Cierra la sesión y, desde la cuenta del desarrollador, borra el usuario en **Ajustes del Sistema → Usuarios y grupos**, eligiendo borrar su carpeta de inicio. Nada de lo que creó vive fuera de ella, salvo la app en `/Applications`, que la siguiente release reemplaza.

## Cuando algo falla

Copia el informe de diagnóstico desde el menú Ayuda, anota el paso y lo que se mostró, y corrígelo en su propio cambio antes de publicar; después vuelve a ensayar desde una cuenta nueva. Si el smoke test pasó pero el ensayo falló, añade lo que se escapó al smoke test o a los tests end-to-end en la misma corrección, para que la próxima vez se detecte solo.
