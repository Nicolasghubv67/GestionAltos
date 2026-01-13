# GestionAltos (Android)

App Android (Java + Views + Material) para gestionar stock en ubicaciones de racks (“Altos”) usando QR.

## Estado actual
- ✅ Navegación base con Bottom Navigation y 4 pantallas (Scan / Racks / Map / Search)
- ✅ Pantalla "Crear rack" con:
  - Validación de código `E###`
  - Dropdowns de Sección y Pasillo (cargados desde Firestore)
  - Generación de QR
  - Guardado en Firestore
  - Exportar/Compartir QR como PNG

## Requisitos
- Android Studio
- Java
- Firebase project configurado (Firestore)

## Configuración Firebase
1. Crear proyecto en Firebase Console
2. Añadir app Android con package: `com.ldm.gestionaltos`
3. Descargar `google-services.json` y colocarlo en `/app`
4. Activar Firestore

### Datos mínimos para probar
Crear estas colecciones en Firestore:

**sections**
- `sec_sanitario` -> `{ "name": "Sanitario" }`
- `sec_madera` -> `{ "name": "Madera" }`

**aisles**
- `ais_san_01` -> `{ "sectionId": "sec_sanitario", "name": "Pasillo 1" }`
- `ais_san_02` -> `{ "sectionId": "sec_sanitario", "name": "Pasillo 2" }`
- `ais_mad_01` -> `{ "sectionId": "sec_madera", "name": "Pasillo 1" }`

## Estructura Firestore actual
**racks/{rackId}**
- code: "E340"
- sectionId: "sec_sanitario"
- aisleId: "ais_san_01"
- createdAt: timestamp
