## ADDED Requirements

### Requirement: Tema Material3 propio
La aplicación SHALL aplicar un esquema de color y una tipografía Material3 propios de la app (no los valores por defecto de `MaterialTheme`) a todas las pantallas, de forma consistente.

#### Scenario: Pantallas usan el tema propio
- **WHEN** se navega entre `ProjectListScreen`, `ProjectConfigScreen` y `WorktreeScreen`
- **THEN** todas heredan el mismo `colorScheme` y `typography` propios, sin quedar ninguna con los valores por defecto de Material3

### Requirement: Un único esquema de color (sin claro/oscuro)
La app SHALL usar un único `colorScheme` fijo, independiente del tema claro/oscuro del sistema operativo. El soporte de múltiples esquemas conmutables queda explícitamente fuera de esta capacidad.

#### Scenario: Sistema operativo en modo oscuro
- **WHEN** el sistema operativo tiene activado el modo oscuro
- **THEN** la app sigue mostrando su único `colorScheme` propio, sin adaptarse automáticamente al modo del sistema
