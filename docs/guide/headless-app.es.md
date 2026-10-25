# Comprobar una app de escritorio en segundo plano

Un agente que comprueba una app de Compose Desktop con computer use se queda con tu pantalla, tu ratón y tu teclado mientras trabaja, y solo un agente puede hacerlo a la vez. En macOS, ShogunAi permite en su lugar que el agente de cada worktree ejecute su propia app en segundo plano: la app nunca pasa al frente, su ventana está oculta y varios worktrees pueden tener sus apps en marcha a la vez mientras tú sigues trabajando.

## Qué hace cada parte

| Parte | Quién la ejecuta | Qué hace |
|---|---|---|
| `shogun headless start` | El agente | Lanza la app del worktree sin quitar el foco, con su propio home de usuario, y la oculta. |
| El servidor MCP de Compose Hot Reload | El agente, como servidor MCP | Lee el árbol semántico de la app y hace clic, escribe y desplaza a través de él, en segundo plano. |
| `shogun headless capture` | El agente | Guarda un PNG de la ventana de la app, aunque esté oculta o tapada. |
| `shogun headless stop` | El agente, o la eliminación del worktree | Termina la app. |

## Preparar un repositorio

1. La app debe usar Compose Multiplatform 1.12 o posterior, que incluye Compose Hot Reload y su servidor MCP.
2. Declara el módulo Gradle de la app en `.shogunai.json`:

    ```json
    { "providers": { "desktop-app": { "module": ":desktopApp" } } }
    ```

3. Declara el servidor MCP para los agentes, por ejemplo en el archivo de proyecto `.mcp.json` de Claude Code:

    ```json
    { "mcpServers": { "compose-hot-reload": { "command": "./gradlew", "args": ["-q", ":desktopApp:hotMcpServer"] } } }
    ```

    Arrancado desde un worktree, el servidor solo encuentra la app de ese worktree, así que cada agente maneja su propia app.

4. Si la app muestra algo fuera de su ventana, como un icono en la barra de menús, omítelo cuando la propiedad de sistema de la JVM `shogunai.headless` sea `true`: ocultar la app no llega hasta ahí. ShogunAi lo hace con su propio icono de la barra de menús.

## Permisos

macOS pide dos permisos, concedidos a la aplicación que ejecuta `shogun` (tu terminal, o la app que ejecuta el agente), no a ShogunAi:

- **Grabación de pantalla** (Ajustes del Sistema › Privacidad y seguridad › Grabación de pantalla), para capturar la ventana.
- **Accesibilidad** (Ajustes del Sistema › Privacidad y seguridad › Accesibilidad), para ocultar la app. Sin él la app sigue en segundo plano, visible detrás de tus ventanas.

`shogun headless status` indica los que faltan.

## Cómo trabaja el agente con ella

- Arranca la app con `shogun headless start`; imprime el id del proceso una vez la ventana está oculta.
- Lee el árbol semántico antes de actuar, y vuelve a leerlo tras cada cambio de vista: los ids de los nodos cambian.
- Los clics ejecutan directamente la acción del elemento, así que no comprueban lo que comprobaría un clic real (capas superpuestas, áreas de pulsación, hover).
- Mira la app con `shogun headless capture`, nunca con el `take_screenshot` del MCP: ese copia la región de la pantalla bajo la ventana, así que con la ventana oculta o tapada captura lo que haya encima, como la terminal de otra sesión.
- El `restart` del MCP vuelve a mostrar la ventana; ejecuta `shogun headless start` después para ocultarla.
- La app guarda sus datos en su propio home, que se conserva entre arranques; `shogun headless start --fresh` la arranca como la primera vez.

El hook `SessionStart` le da a Claude estas reglas en los repositorios que declaran el provider.

## Lo que todavía necesita el escritorio

La entrada real de teclado no llega a una app en segundo plano (su ventana no está activa y el toolkit descarta los eventos de teclado para ella), y tampoco los atajos de teclado, los menús nativos, la barra de menús, los diálogos del sistema ni el hover. Comprobar eso sigue requiriendo manejar el escritorio real con computer use.

## Otras plataformas

De momento, ejecutar una app en segundo plano solo está disponible en macOS; en Windows y Linux los comandos `shogun headless` lo indican.
