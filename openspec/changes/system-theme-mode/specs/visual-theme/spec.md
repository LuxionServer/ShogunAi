## REMOVED Requirements

### Requirement: Un único esquema de color (sin claro/oscuro)
**Reason**: Reemplazado por soporte de modo de tema Claro/Oscuro/Automático — ver requisitos "Modo de tema claro/oscuro/automático" y "Selección de modo de tema desde la UI" en esta misma capacidad.
**Migration**: No requiere migración de datos. La app pasa a arrancar en modo Automático por defecto (siguiendo el sistema operativo) en vez del único `colorScheme` oscuro fijo anterior.

## ADDED Requirements

### Requirement: Modo de tema claro/oscuro/automático
La app SHALL soportar tres modos de tema: `Claro`, `Oscuro` y `Automático`. En modo `Automático`, la app SHALL usar el `colorScheme` (claro u oscuro) que corresponda al tema activo del sistema operativo. En modo `Claro` u `Oscuro`, la app SHALL usar siempre el `colorScheme` correspondiente a ese modo, independientemente del tema del sistema operativo.

#### Scenario: Sistema operativo en modo oscuro con modo Automático
- **WHEN** el modo de tema es Automático y el sistema operativo tiene activado el modo oscuro
- **THEN** la app muestra su `colorScheme` oscuro

#### Scenario: Sistema operativo en modo claro con modo Automático
- **WHEN** el modo de tema es Automático y el sistema operativo tiene activado el modo claro
- **THEN** la app muestra su `colorScheme` claro

#### Scenario: Modo manual anula el sistema operativo
- **WHEN** el usuario fija el modo de tema en Claro u Oscuro
- **THEN** la app muestra siempre ese `colorScheme`, sin importar el modo del sistema operativo

#### Scenario: El sistema operativo cambia de tema en caliente con modo Automático activo
- **WHEN** el modo de tema es Automático y el usuario cambia el tema del sistema operativo mientras la app está abierta
- **THEN** la app actualiza su `colorScheme` sin necesidad de reiniciar

### Requirement: Selección de modo de tema desde la UI
La app SHALL exponer un control accesible desde cualquier pantalla (`ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen`) que permita al usuario elegir entre Claro, Oscuro y Automático.

#### Scenario: Usuario cambia el modo de tema
- **WHEN** el usuario selecciona un modo distinto en el control de tema
- **THEN** la app aplica el nuevo `colorScheme` inmediatamente, sin reiniciar

### Requirement: Persistencia de la preferencia de tema
La app SHALL recordar el modo de tema elegido por el usuario entre reinicios de la app. Si no hay preferencia guardada (primer arranque, o archivo de preferencia ausente/corrupto), el modo por defecto SHALL ser Automático.

#### Scenario: Reinicio de la app tras elegir un modo manual
- **WHEN** el usuario elige Oscuro y reinicia la app
- **THEN** la app arranca en modo Oscuro

#### Scenario: Primer arranque sin preferencia guardada
- **WHEN** la app arranca por primera vez sin archivo de preferencia de tema
- **THEN** la app arranca en modo Automático
