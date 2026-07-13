# app-icon Specification

## Purpose
TBD - created by archiving change app-icon-and-visual-style. Update Purpose after archive.
## Requirements
### Requirement: Icono de ventana
La aplicación de escritorio SHALL mostrar un icono propio (no el icono por defecto de Java/AWT) en la barra de título y en el dock/taskbar mientras la ventana principal está abierta.

#### Scenario: Ventana con icono personalizado
- **WHEN** se lanza la app y se crea la ventana principal
- **THEN** la ventana usa el icono propio de la app como `windowIcon`, en vez del icono por defecto de Compose/Java

### Requirement: Icono en el paquete nativo
El artefacto empaquetado de cada plataforma (Dmg en macOS, Msi en Windows, Deb en Linux) SHALL incluir el icono propio de la app en el formato nativo que espera esa plataforma.

#### Scenario: Instalación en macOS
- **WHEN** se genera el paquete `.dmg` y se instala la app
- **THEN** el icono que aparece en Finder/Dock es el `.icns` propio de la app, no el icono genérico de Compose

#### Scenario: Instalación en Windows
- **WHEN** se genera el paquete `.msi` y se instala la app
- **THEN** el ejecutable y el acceso directo muestran el `.ico` propio de la app

#### Scenario: Instalación en Linux
- **WHEN** se genera el paquete `.deb` y se instala la app
- **THEN** el icono mostrado en el lanzador de aplicaciones es el `.png` propio de la app

