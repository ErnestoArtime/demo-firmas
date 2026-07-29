$ErrorActionPreference = 'Stop'

function New-DocxFromParagraphs {
  param(
    [string]$OutputPath,
    [string[]]$Paragraphs
  )

  $tempDir = Join-Path $env:TEMP ([Guid]::NewGuid().ToString())
  $relsDir = Join-Path $tempDir "_rels"
  $wordDir = Join-Path $tempDir "word"
  New-Item -ItemType Directory -Path $tempDir, $relsDir, $wordDir | Out-Null

  $contentTypes = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
"@
  $contentTypes | Out-File -LiteralPath (Join-Path $tempDir "[Content_Types].xml") -Encoding utf8

  $rels = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
"@
  $rels | Out-File -LiteralPath (Join-Path $relsDir ".rels") -Encoding utf8

  $sb = New-Object System.Text.StringBuilder
  $null = $sb.AppendLine('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>')
  $null = $sb.AppendLine('<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">')
  $null = $sb.AppendLine('  <w:body>')

  foreach ($p in $Paragraphs) {
    $text = [System.Security.SecurityElement]::Escape($p)
    if ([string]::IsNullOrWhiteSpace($text)) {
      $null = $sb.AppendLine('    <w:p/>')
    } else {
      $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">' + $text + '</w:t></w:r></w:p>')
    }
  }

  $null = $sb.AppendLine('    <w:sectPr/>')
  $null = $sb.AppendLine('  </w:body>')
  $null = $sb.AppendLine('</w:document>')

  $sb.ToString() | Out-File -LiteralPath (Join-Path $wordDir "document.xml") -Encoding utf8

  if (Test-Path $OutputPath) { Remove-Item $OutputPath -Force }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $OutputPath)
  Remove-Item $tempDir -Recurse -Force
}

$doc1 = @(
"Solucion de Plantillas y Firmas - Presupuesto preliminar",
"",
"Perfecto, con lo que comento Bea te dejo un presupuesto rapido y accionable (estimacion preliminar, no cerrada) para que puedas responder hoy mismo.",
"",
"Alcance interpretado",
"- Generacion documental ya no recibe lista de alumnos del cliente; se obtiene desde tu BD por courseId.",
"- Endpoint principal: POST /api/documents/generate-by-course con templateId + courseId (+ opciones de salida).",
"- Soporte de marcas para datos de alumnos en bloque (orden vertical) y firmas.",
"- Nueva API para cursos externos (no presentes en tu BD), con sesiones/tutor/alumnos y control de asistencia.",
"- Seguridad por API key.",
"- Facturacion por autonomo en Espana (Eduardo u otro).",
"",
"Diseno de marcas recomendado (alumnos)",
"Mejor que BD_NOMBRES# suelto, usar marcas estructuradas para evitar ambiguedad:",
"${ALUMNOS[].NOMBRE}",
"${ALUMNOS[].APELLIDOS}",
"${ALUMNOS[].NIF}",
"${ALUMNOS[].FIRMA}",
"Para DOCX: repetir fila/bloque por cada alumno.",
"Para PDF: layout fijo. Opciones: cupo fijo (ALUMNO_1_* ... ALUMNO_N_*) o generar PDF desde DOCX intermedio.",
"",
"Estimacion de horas y presupuesto",
"Bloque A: integracion con BD + generacion por templateId/courseId + marcas alumnos + validaciones",
"52-72 horas",
"2.600 EUR - 4.680 EUR (a 50-65 EUR/h)",
"",
"Bloque B: API cursos externos + sesiones + tutor + alumnos por sesion + asistencia + API key",
"80-120 horas",
"4.000 EUR - 7.800 EUR (a 50-65 EUR/h)",
"",
"Total estimado (A+B)",
"132-192 horas",
"6.600 EUR - 12.480 EUR + IVA",
"",
"Plazo orientativo",
"MVP operativo: 3-5 semanas.",
"Version robusta (idempotencia, auditoria, reintentos, trazabilidad): 5-8 semanas.",
"",
"Supuestos clave",
"- Acceso estable a BD de cursos/alumnos/firmas.",
"- Motor de firma para DOCX/PDF actual se reutiliza.",
"- Ambientes, CI/CD y despliegue no incluidos.",
"- Facturacion viable si Eduardo (u otro autonomo en Espana) emite factura.",
"",
"Documento de contexto y requisitos (resumen integrado)",
"Solicitud: Backend en Java para firma manuscrita, captura de rubrica e insercion en documentos.",
"- Firma manuscrita (dibujada).",
"- Varias personas firman el mismo documento.",
"- Cliente sube documentos con marcas predefinidas.",
"- Cada alumno tiene una columna de firma dentro de cada fila de la tabla.",
"",
"Problema: El cliente sube PDF o Word con marcas y huecos para firmas. Se requiere generar documento con firmas dinamicamente, sin Jasper.",
"",
"Suposiciones operativas",
"- Usuarios con identificador unico; cada usuario tiene su firma y puede estar en N cursos.",
"- Identificacion de firmas por Id de Usuario o NIF.",
"- Existe un consecutivo del alumno en el curso asociado a su firma.",
"",
"Tipos de marcas",
"1) Datos de BD (listado predefinido): ${NOMBRE}, ${FECHA}, ${CURSO}, ${DENOMINACION_FORMATIVA}, ${FECHA_INICIO_CURSO}, ${FECHA_FIN_CURSO}, ${FORMADOR_RESPONSABLE}, ${HORARIO}, etc.",
"2) Firmas de usuarios: ${FIRMA_1} ... ${FIRMA_N}",
"3) Otras firmas: ${FIRMA_RESPONSABLE_FORMACION}",
"",
"Validacion de marcas",
"- Se entrega al cliente un documento con: definicion de marcas, catalogo de datos de BD, listado de alumnos en orden ascendente.",
"- Si hay marcas invalidas: devolver error y no guardar la plantilla.",
"",
"Interpretacion del flujo",
"1. Cliente prepara plantilla con marcas segun documento de ayuda.",
"2. Cliente sube documento.",
"3. Sistema valida marcas y guarda plantilla si es correcta.",
"4. Usuarios firman; se marca firmado por usuario.",
"5. Cliente genera documento; se sustituyen marcas y firmas.",
"6. Cliente descarga el documento generado.",
"",
"Propuesta preliminar de endpoints",
"Plantillas:",
"- POST /api/templates (sube DOCX/PDF)",
"- GET /api/templates/{templateId}/requirements",
"- GET /api/templates/sample-docx",
"Documentos:",
"- POST /api/documents/generate",
"- GET /api/documents/{documentId}",
"",
"Tecnologias propuestas",
"- Java 21",
"- Spring Boot 3.5.11-SNAPSHOT",
"- docx4j 11.4.9",
"- pdfbox 2.0.30",
"- fontbox 2.0.30",
"- JAXB runtime + docx4j JAXB RI",
"",
"Dudas abiertas",
"1. Las plantillas siempre incluyen a todos los usuarios del curso?",
"2. Existen otras firmas del sistema fuera de los usuarios del curso?",
"3. La validacion de marcas la hacemos nosotros o habra un metodo externo?",
"4. Existira un metodo para obtener el contenido de las marcas?"
)

$doc2 = @(
"Solucion de Plantillas y Firmas - Presupuesto preliminar + Implementacion actual",
"",
"Este documento integra el presupuesto preliminar con el estado actual de implementacion del backend.",
"",
"Resumen de lo ya implementado en backend",
"- Carga de plantillas DOCX y PDF.",
"- Conversion de plantillas RTF/DOC a DOCX o PDF via LibreOffice (soffice).",
"- Generacion de documentos a partir de plantillas DOCX y PDF.",
"- Soporte de firmas manuscritas (base64) y reemplazo en DOCX/PDF.",
"- Requisitos de plantilla: listado de campos y firmas detectados.",
"- Mapeo de campos/firma hacia catalogo externo (persistente por plantilla).",
"- Descarga de documentos generados.",
"",
"Endpoints implementados (backend actual)",
"Plantillas:",
"- POST /api/templates (sube DOCX/PDF)",
"- POST /api/templates/convert?target=docx|pdf (convierte RTF/DOC a DOCX o PDF)",
"- GET /api/templates (lista plantillas)",
"- GET /api/templates/{templateId}/requirements",
"- GET /api/templates/{templateId}/mapping",
"- POST /api/templates/{templateId}/mapping",
"- GET /api/templates/catalog-fields",
"- GET /api/templates/sample-docx",
"",
"Documentos:",
"- POST /api/documents/generate (acepta outputType: docx|pdf)",
"- GET /api/documents/{documentId}",
"",
"Reglas de salida",
"- Si la plantilla es DOCX: salida DOCX o PDF (conversion DOCX -> PDF con LibreOffice).",
"- Si la plantilla es PDF: solo salida PDF.",
"",
"Motor DOCX",
"- Sustitucion de tokens ${CAMPO} y CAMPO#.",
"- Duplicacion de filas de alumnos usando ALUMNO_1_* / FIRMA_1 en la fila modelo.",
"- Insercion de firmas como imagen en placeholders de firma.",
"",
"Motor PDF",
"- Relleno de campos de formulario (AcroForm).",
"- Insercion de firmas en widgets de campos de firma.",
"- Aplanado (flatten) del PDF al guardar.",
"",
"Limitaciones actuales",
"- PDF requiere campos AcroForm predefinidos. No crece dinamicamente en altura.",
"- Conversion a PDF requiere LibreOffice instalado y disponible en PATH.",
"",
"A partir de aqui se mantiene el mismo presupuesto preliminar y alcance interpretado.",
"",
"Alcance interpretado",
"- Generacion documental ya no recibe lista de alumnos del cliente; se obtiene desde tu BD por courseId.",
"- Endpoint principal: POST /api/documents/generate-by-course con templateId + courseId (+ opciones de salida).",
"- Soporte de marcas para datos de alumnos en bloque (orden vertical) y firmas.",
"- Nueva API para cursos externos (no presentes en tu BD), con sesiones/tutor/alumnos y control de asistencia.",
"- Seguridad por API key.",
"- Facturacion por autonomo en Espana (Eduardo u otro).",
"",
"Diseno de marcas recomendado (alumnos)",
"${ALUMNOS[].NOMBRE}",
"${ALUMNOS[].APELLIDOS}",
"${ALUMNOS[].NIF}",
"${ALUMNOS[].FIRMA}",
"",
"Estimacion de horas y presupuesto",
"Bloque A: integracion con BD + generacion por templateId/courseId + marcas alumnos + validaciones",
"52-72 horas",
"2.600 EUR - 4.680 EUR (a 50-65 EUR/h)",
"",
"Bloque B: API cursos externos + sesiones + tutor + alumnos por sesion + asistencia + API key",
"80-120 horas",
"4.000 EUR - 7.800 EUR (a 50-65 EUR/h)",
"",
"Total estimado (A+B)",
"132-192 horas",
"6.600 EUR - 12.480 EUR + IVA",
"",
"Plazo orientativo",
"MVP operativo: 3-5 semanas.",
"Version robusta (idempotencia, auditoria, reintentos, trazabilidad): 5-8 semanas.",
"",
"Supuestos clave",
"- Acceso estable a BD de cursos/alumnos/firmas.",
"- Motor de firma para DOCX/PDF actual se reutiliza.",
"- Ambientes, CI/CD y despliegue no incluidos.",
"- Facturacion viable si Eduardo (u otro autonomo en Espana) emite factura.",
"",
"Dudas abiertas",
"1. Las plantillas siempre incluyen a todos los usuarios del curso?",
"2. Existen otras firmas del sistema fuera de los usuarios del curso?",
"3. La validacion de marcas la hacemos nosotros o habra un metodo externo?",
"4. Existira un metodo para obtener el contenido de las marcas?"
)

New-DocxFromParagraphs -OutputPath "C:\Proyectos\java\demo\presupuesto_preliminar.docx" -Paragraphs $doc1
New-DocxFromParagraphs -OutputPath "C:\Proyectos\java\demo\presupuesto_preliminar_con_backend.docx" -Paragraphs $doc2

Write-Output "ok"
