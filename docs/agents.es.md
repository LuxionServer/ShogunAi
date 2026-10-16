# Uso con agentes

Cada vez más agentes de IA crean sus propios worktrees. Estas recetas hacen que los worktrees que crean queden listos para compilar, con los archivos que tu repositorio declara en `.shogunai.json` (y `.worktreeinclude`) y los archivos locales de tu proyecto, y que sigan visibles y se puedan limpiar desde ShogunAi. Todas necesitan el [comando `shogun`](architecture/cli.md) en el `PATH`.

## Claude Code

El hook `WorktreeCreate` de Claude Code **sustituye** su propia creación de worktrees para `claude --worktree`, `EnterWorktree`, los subagentes con `isolation: "worktree"` y las sesiones en segundo plano. Apúntalo a `shogun`, junto con `WorktreeRemove` para que la limpieza también pase por ShogunAi. En el `.claude/settings.json` del repositorio (compartido con el equipo) o en tu `~/.claude/settings.json` (todos los repositorios):

```json
{
  "hooks": {
    "WorktreeCreate": [
      { "hooks": [{ "type": "command", "command": "shogun hook worktree-create" }] }
    ],
    "WorktreeRemove": [
      { "hooks": [{ "type": "command", "command": "shogun hook worktree-remove" }] }
    ]
  }
}
```

Qué pasa entonces:

- `claude -w TASK-123` le pide el worktree a `shogun`. Se crea en `feature/TASK-123` dentro de la carpeta de worktrees de tu proyecto de ShogunAi, o en `.claude/worktrees/` (donde los pone normalmente Claude Code) si el repositorio no tiene proyecto, y se provisiona. Claude Code arranca la sesión dentro.
- Ejecutar otra vez `claude -w TASK-123` reutiliza ese worktree.
- Cuando Claude Code elimina el worktree (eliges eliminarlo al salir, o no tenía cambios), `shogun` lo elimina y borra su rama solo si está mergeada; una rama con trabajo se conserva.
- Con este hook Claude Code deja de procesar `.worktreeinclude` por su cuenta; lo lee ShogunAi, así que sus archivos se siguen copiando.

El worktree nuevo parte del commit actual del checkout principal, como los worktrees creados desde ShogunAi, y el ajuste `worktree.baseRef` de Claude Code no se aplica. Comprobado con Claude Code 2.1.301.

## Cursor

Los agentes en paralelo de Cursor ejecutan los comandos de configuración de `.cursor/worktrees.json` dentro de cada worktree nuevo. Provisiónalo ahí:

```json
{
  "setup-worktree": ["shogun provision"]
}
```

Añade después tus otros comandos de preparación (`npm ci`, …). Cursor crea y nombra el worktree por su cuenta; ShogunAi lo muestra como creado en otra ubicación.

## Codex

Los entornos locales de Codex ejecutan un script de preparación cuando crean un worktree para un hilo nuevo. Usa `shogun provision` como ese script (o al principio de él). La documentación de Codex no dice en qué directorio se ejecuta el script; si no es el worktree nuevo, pásale su ruta: `shogun provision <ruta del worktree>`. Esta receta aún no se ha verificado con Codex.

## Cualquier otro agente o herramienta

- **Que ShogunAi cree el worktree y luego arrancar el agente dentro:**

    ```bash
    cd "$(shogun new TASK-123)" && codex
    ```

- **La herramienta crea el worktree y puede ejecutar un comando después:** ejecuta `shogun provision <ruta del worktree>` (o `shogun provision` a secas desde dentro).
- **Limpiar lo que se haya acumulado, venga de donde venga:** `shogun clean` lista los worktrees mergeados y los que ya no tienen carpeta; `shogun clean --yes` los elimina.
