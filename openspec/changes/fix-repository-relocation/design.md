## Context

`secretFiles` ya se guardaban relativos al repositorio base cuando se eligen con el selector de archivos, pero una entrada escrita a mano como ruta absoluta se guardaba tal cual. `worktreesRoot` es un campo independiente. Git enlaza repositorio base y worktrees con rutas absolutas (`<worktree>/.git` y `.git/worktrees/<nombre>/gitdir`).

## Decisions

- **Normalizar secretos al cargar, no al cambiar la ruta.** Al abrir el formulario la ruta base guardada aún es la buena para interpretar las entradas absolutas, así que convertirlas a relativas en ese momento basta para que sigan a cualquier ruta nueva, aunque se escriba carácter a carácter. Las rutas absolutas fuera del repositorio se dejan intactas.
- **`worktreesRoot` sigue a la ruta base solo si estaba ligada a ella.** Se calcula el desplazamiento relativo inicial (`.worktrees`, `..`, `../worktrees`); si contiene más de un `..` se considera una ubicación independiente (otro disco, otra carpeta) y no se toca. Solo se actualiza mientras el valor actual coincida con el derivado de la ruta base anterior, así una edición manual del usuario nunca se sobrescribe.
- **Reparar con `git worktree repair` tras guardar.** Se ejecuta solo cuando cambian la ruta base o `worktreesRoot` de un proyecto existente. `git worktree list` sigue devolviendo las rutas viejas de los worktrees; las que ya no existen pero sí existen bajo el nuevo `worktreesRoot` se pasan como argumento para que Git arregle también el enlace repositorio → worktree.
- **Clave de `ViewModel` por configuración.** Los casos de uso se construyen con la `ProjectConfig`; incluir su `hashCode()` en la clave evita reutilizar un `ViewModel` con rutas antiguas.

## Risks / Trade-offs

- Si el usuario mueve los worktrees a una ubicación no derivable del `worktreesRoot` anterior, `git worktree repair` sin rutas no podrá reenlazarlos; se muestra el error de Git o los worktrees aparecen como antes (`prunable`).
