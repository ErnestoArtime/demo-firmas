# Entorno De Firma Manuscrita (Backend)

## Objetivo
Backend en Spring Boot para:
- subir plantillas (`.docx` y `.pdf`)
- detectar campos y posiciones de firma
- insertar datos y firmas manuscritas (imagenes base64)
- generar y descargar el documento final
- convertir archivos legacy (`.rtf`/`.doc`) a `.docx` con LibreOffice

## Estado Implementado
- Soporte de plantillas `DOCX` y `PDF`.
- Soporte de multiples firmas (`FIRMA_1`, `FIRMA_2`, `FIRMA_3`, ...).
- Deteccion dinamica de campos requeridos por plantilla.
- Validacion de firmas requeridas antes de generar.
- Almacenamiento local de plantillas y resultados.
- Endpoint de conversion automatica a `.docx` desde `.rtf`/`.doc`.

## Tecnologias Y Dependencias
- Java 21
- Spring Boot 3.5.11-SNAPSHOT
- docx4j 11.4.9
- pdfbox 2.0.30
- fontbox 2.0.30 (alineado para evitar conflictos)
- JAXB runtime + docx4j JAXB RI

`pom.xml` incluye:
- `org.docx4j:docx4j-core:11.4.9`
- `org.docx4j:docx4j-openxml-objects:11.4.9`
- `org.docx4j:docx4j-JAXB-ReferenceImpl:11.4.9`
- `org.apache.pdfbox:pdfbox:2.0.30`
- `org.apache.pdfbox:fontbox:2.0.30`
- `org.glassfish.jaxb:jaxb-runtime`

## Estructura Principal
- `src/main/java/com/example/demo/controller/TemplateController.java`
- `src/main/java/com/example/demo/controller/DocumentController.java`
- `src/main/java/com/example/demo/service/DocumentGenerationService.java`
- `src/main/java/com/example/demo/service/FileStorageService.java`
- `src/main/java/com/example/demo/service/TemplateRequirementsService.java`
- `src/main/java/com/example/demo/service/TemplateConversionService.java`
- `src/main/java/com/example/demo/service/engine/DocxTemplateEngine.java`
- `src/main/java/com/example/demo/service/engine/PdfTemplateEngine.java`
- `src/main/java/com/example/demo/exception/ApiExceptionHandler.java`
- `scripts/probar-endpoints.ps1`

## Configuracion (`application.properties`)
```properties
spring.application.name=demo
app.storage.templates-dir=storage/templates
app.storage.output-dir=storage/output
app.samples.dir=storage/samples
app.convert.soffice-command=soffice
app.convert.timeout-seconds=90
```

Si `soffice` no esta en PATH:
```properties
app.convert.soffice-command=C:\\Program Files\\LibreOffice\\program\\soffice.exe
```

## Endpoints
### Plantillas
- `POST /api/templates`  
  Sube plantilla `.docx` o `.pdf`

- `POST /api/templates/convert`  
  Convierte `.rtf` o `.doc` a `.docx` y la guarda como plantilla

- `GET /api/templates/{templateId}/requirements`  
  Devuelve:
  - `requiredFields`
  - `requiredSignatures`

- `GET /api/templates/sample-docx`  
  Descarga plantilla demo

### Documentos
- `POST /api/documents/generate`  
  Genera documento con datos y firmas

- `GET /api/documents/{documentId}`  
  Descarga documento generado

## Contrato De Generacion
```json
{
  "templateId": "uuid",
  "fields": {
    "NOMBRE": "Juan Perez",
    "FECHA": "11/02/2026",
    "CURSO": "2A"
  },
  "signatures": {
    "FIRMA_1": "base64_png...",
    "FIRMA_2": "base64_png...",
    "FIRMA_3": "base64_png..."
  }
}
```

## Regla De Marcado En Plantillas
### DOCX
Placeholders de texto:
- `${NOMBRE}`, `${FECHA}`, etc.
- `${FIRMA_1}`, `${FIRMA_2}`, `${FIRMA_3}`, ...

### PDF
Campos AcroForm con nombres exactos:
- Texto: `NOMBRE`, `FECHA`, ...
- Firma: `FIRMA_1`, `FIRMA_2`, ...

## Flujo Recomendado
1. Subir plantilla
2. Consultar requisitos (`/requirements`)
3. Preparar payload con campos y firmas requeridas
4. Generar documento
5. Descargar resultado

## Scripts De Prueba
Prueba E2E:
```powershell
.\scripts\probar-endpoints.ps1 -BaseUrl "http://localhost:8080" -TemplatePath "storage/samples/plantilla_demo.docx"
```

Para PDF:
```powershell
.\scripts\probar-endpoints.ps1 -BaseUrl "http://localhost:8080" -TemplatePath "storage/samples/plantilla_demo.pdf"
```

## Casos Importantes Resueltos
- Cambiar solo la extension (`.rtf.doc` -> `.docx`) no convierte el archivo.
- Se corrigio insercion DOCX cuando placeholder venia dentro de texto (`Firma 1: ${FIRMA_1}`).
- Se resolvio conflicto `pdfbox/fontbox` que causaba `NoSuchMethodError`.
- Se implemento validacion de firmas requeridas por plantilla.

## Limitaciones Actuales
- Para conversion automatica se requiere LibreOffice instalado.
- En PDF, las posiciones dependen de campos AcroForm bien definidos.
- No hay base de datos; metadatos y archivos se guardan en disco local.

## Proximo Paso Sugerido
- Añadir autenticacion, auditoria y almacenamiento en BD/S3 para entorno productivo.
