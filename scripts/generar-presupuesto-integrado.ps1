$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Escape-Xml {
  param([string]$Value)
  if ($null -eq $Value) { return '' }
  return [System.Security.SecurityElement]::Escape($Value)
}

function Add-Paragraph {
  param(
    [System.Text.StringBuilder]$Sb,
    [string]$Text,
    [string]$Style = '',
    [string]$Align = '',
    [int]$Before = 0,
    [int]$After = 60,
    [string]$Color = '',
    [int]$Size = 22,
    [bool]$Bold = $false,
    [bool]$Italic = $false
  )

  $null = $Sb.Append('<w:p><w:pPr>')
  if ($Style) {
    $null = $Sb.Append('<w:pStyle w:val="' + $Style + '"/>')
  }
  $null = $Sb.Append('<w:spacing w:before="' + $Before + '" w:after="' + $After + '"/>')
  if ($Align) {
    $null = $Sb.Append('<w:jc w:val="' + $Align + '"/>')
  }
  $null = $Sb.Append('</w:pPr><w:r><w:rPr>')
  if ($Bold) { $null = $Sb.Append('<w:b/><w:bCs/>') }
  if ($Italic) { $null = $Sb.Append('<w:i/><w:iCs/>') }
  if ($Color) { $null = $Sb.Append('<w:color w:val="' + $Color + '"/>') }
  $null = $Sb.Append('<w:sz w:val="' + $Size + '"/><w:szCs w:val="' + $Size + '"/>')
  $null = $Sb.Append('</w:rPr><w:t xml:space="preserve">' + (Escape-Xml $Text) + '</w:t></w:r></w:p>')
}

function Add-Heading1 {
  param([System.Text.StringBuilder]$Sb, [string]$Text)
  Add-Paragraph -Sb $Sb -Text $Text -Style 'Heading1' -Before 260 -After 120 -Size 28 -Bold $true -Color '1F3864'
}

function Add-Bullet {
  param([System.Text.StringBuilder]$Sb, [string]$Text)
  Add-Paragraph -Sb $Sb -Text ('• ' + $Text) -Style 'ListParagraph' -Before 20 -After 40 -Size 22
}

function Add-Table {
  param(
    [System.Text.StringBuilder]$Sb,
    [string[]]$Headers,
    [object[]]$Rows
  )

  $colCount = $Headers.Count
  $null = $Sb.Append('<w:tbl><w:tblPr><w:tblW w:type="pct" w:w="100%"/>')
  $null = $Sb.Append('<w:tblBorders><w:top w:val="single" w:sz="4"/><w:left w:val="single" w:sz="4"/><w:bottom w:val="single" w:sz="4"/><w:right w:val="single" w:sz="4"/><w:insideH w:val="single" w:sz="4"/><w:insideV w:val="single" w:sz="4"/></w:tblBorders></w:tblPr><w:tblGrid>')
  for ($i = 0; $i -lt $colCount; $i++) {
    $null = $Sb.Append('<w:gridCol w:w="100"/>')
  }
  $null = $Sb.Append('</w:tblGrid>')

  # Header row
  $null = $Sb.Append('<w:tr><w:trPr><w:tblHeader/></w:trPr>')
  foreach ($h in $Headers) {
    $null = $Sb.Append('<w:tc><w:tcPr><w:shd w:fill="2E75B6" w:val="clear"/></w:tcPr><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:bCs/><w:color w:val="FFFFFF"/><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr><w:t xml:space="preserve">' + (Escape-Xml $h) + '</w:t></w:r></w:p></w:tc>')
  }
  $null = $Sb.Append('</w:tr>')

  for ($rowIndex = 0; $rowIndex -lt $Rows.Count; $rowIndex++) {
    $row = $Rows[$rowIndex]
    $shade = if (($rowIndex % 2) -eq 0) { 'D9E1F2' } else { '' }
    $null = $Sb.Append('<w:tr>')
    for ($c = 0; $c -lt $colCount; $c++) {
      $cell = ''
      if ($c -lt $row.Count) { $cell = [string]$row[$c] }
      $null = $Sb.Append('<w:tc><w:tcPr>')
      if ($shade) {
        $null = $Sb.Append('<w:shd w:fill="' + $shade + '" w:val="clear"/>')
      }
      $null = $Sb.Append('</w:tcPr><w:p><w:pPr><w:jc w:val="left"/></w:pPr><w:r><w:rPr><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr><w:t xml:space="preserve">' + (Escape-Xml $cell) + '</w:t></w:r></w:p></w:tc>')
    }
    $null = $Sb.Append('</w:tr>')
  }
  $null = $Sb.Append('</w:tbl>')
}

function Build-DocumentXml {
  param([bool]$WithBackendDetail)

  $sb = New-Object System.Text.StringBuilder
  $null = $sb.Append('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>')
  $null = $sb.Append('<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>')

  Add-Paragraph -Sb $sb -Text 'PRESUPUESTO CONSOLIDADO - SOLUCION DE PLANTILLAS Y FIRMAS' -Align 'center' -After 80 -Size 34 -Bold $true -Color '1F3864'
  Add-Paragraph -Sb $sb -Text 'Documento integrado de intercambios, alcance, estimacion y compromiso' -Align 'center' -After 60 -Size 24 -Color '2E75B6'
  Add-Paragraph -Sb $sb -Text 'Fecha de emision: 20/02/2026 | Version: Preliminar comercial' -Align 'center' -After 340 -Size 20 -Italic $true -Color '666666'

  Add-Heading1 -Sb $sb -Text '1. Resumen Ejecutivo'
  Add-Paragraph -Sb $sb -Text 'El objetivo es implantar una solucion backend en Java para insertar firmas manuscritas en documentos DOCX y PDF subidos por el cliente, con plantillas parametrizadas por marcas, generacion dinamica por curso y flujo operativo trazable.'
  Add-Paragraph -Sb $sb -Text 'Este documento consolida el entendimiento funcional, tecnico y comercial para formalizar la contratacion y ejecucion del proyecto.'

  Add-Heading1 -Sb $sb -Text '2. Solicitud y Requisitos Confirmados'
  Add-Bullet -Sb $sb -Text 'La captura de firma manuscrita ya existe y se entrega como imagen.'
  Add-Bullet -Sb $sb -Text 'Firmaran varias personas por documento.'
  Add-Bullet -Sb $sb -Text 'El cliente subira sus propias plantillas con marcas.'
  Add-Bullet -Sb $sb -Text 'Cada alumno debe firmar en su fila o posicion correspondiente.'
  Add-Bullet -Sb $sb -Text 'El sistema debe permitir generar y descargar documento final con datos y firmas.'

  Add-Heading1 -Sb $sb -Text '3. Alcance Interpretado'
  Add-Paragraph -Sb $sb -Text 'Bloque A - Generacion documental por curso desde BD' -Color '2E75B6' -Bold $true -Before 140 -After 80 -Size 24
  Add-Bullet -Sb $sb -Text 'La generacion deja de recibir lista de alumnos del cliente y usa BD por courseId.'
  Add-Bullet -Sb $sb -Text 'Nuevo endpoint objetivo: POST /api/documents/generate-by-course con templateId + courseId + opciones de salida.'
  Add-Bullet -Sb $sb -Text 'Sustitucion de marcas de datos y firmas en orden correcto por alumno.'
  Add-Bullet -Sb $sb -Text 'Validaciones de entrada, errores funcionales y control de consistencia.'
  Add-Paragraph -Sb $sb -Text 'Bloque B - API de cursos externos' -Color '2E75B6' -Bold $true -Before 140 -After 80 -Size 24
  Add-Bullet -Sb $sb -Text 'Alta de cursos externos no presentes en BD principal.'
  Add-Bullet -Sb $sb -Text 'Gestion de tutor, sesiones, alumnos por sesion y asistencia.'
  Add-Bullet -Sb $sb -Text 'Seguridad con API key para integraciones externas.'

  Add-Heading1 -Sb $sb -Text '4. Diseno de Marcas Recomendado'
  Add-Paragraph -Sb $sb -Text 'Para evitar ambiguedad se recomienda usar marcas estructuradas de alumno en lugar de variables agregadas sueltas.'
  Add-Table -Sb $sb -Headers @('Marca', 'Uso') -Rows @(
    @('${ALUMNOS[].NOMBRE}', 'Nombre del alumno'),
    @('${ALUMNOS[].APELLIDOS}', 'Apellidos del alumno'),
    @('${ALUMNOS[].NIF}', 'Documento/NIF del alumno'),
    @('${ALUMNOS[].FIRMA}', 'Firma manuscrita del alumno')
  )
  Add-Paragraph -Sb $sb -Text 'Comportamiento esperado: DOCX permite crecimiento dinamico por repeticion de fila. PDF requiere cupo fijo o conversion desde DOCX intermedio.' -Before 80

  Add-Heading1 -Sb $sb -Text '5. Flujo Operativo Acordado'
  Add-Bullet -Sb $sb -Text 'Cliente prepara plantilla segun guia de marcas.'
  Add-Bullet -Sb $sb -Text 'Sistema valida marcas al subir plantilla.'
  Add-Bullet -Sb $sb -Text 'Si hay error de marcas, se rechaza y no se registra.'
  Add-Bullet -Sb $sb -Text 'Usuarios firman durante el flujo del curso.'
  Add-Bullet -Sb $sb -Text 'Cliente solicita generacion y descarga del documento final.'

  Add-Heading1 -Sb $sb -Text '6. Estimacion de Horas y Presupuesto'
  Add-Table -Sb $sb -Headers @('Bloque', 'Descripcion', 'Horas', 'Precio (50-65 EUR/h)') -Rows @(
    @('A', 'Integracion BD + generacion por templateId/courseId + marcas + validaciones', '52-72 h', '2.600 EUR - 4.680 EUR'),
    @('B', 'API cursos externos + sesiones + tutor + asistencia + API key', '80-120 h', '4.000 EUR - 7.800 EUR'),
    @('TOTAL', 'Bloques A + B', '132-192 h', '6.600 EUR - 12.480 EUR + IVA')
  )
  Add-Paragraph -Sb $sb -Text 'Plazo orientativo: MVP 3-5 semanas. Version robusta 5-8 semanas.' -Before 80

  Add-Heading1 -Sb $sb -Text '7. Supuestos, Exclusiones y Riesgos'
  Add-Bullet -Sb $sb -Text 'Acceso estable a BD de cursos, alumnos y firmas.'
  Add-Bullet -Sb $sb -Text 'Reutilizacion del motor actual DOCX/PDF.'
  Add-Bullet -Sb $sb -Text 'No incluye CI/CD, infraestructura ni despliegue productivo.'
  Add-Bullet -Sb $sb -Text 'Cambios de alcance fuera de este documento se estiman aparte.'
  Add-Bullet -Sb $sb -Text 'Riesgo principal: definicion ambigua de marcas en plantillas del cliente.'

  Add-Heading1 -Sb $sb -Text '8. Estado Ya Implementado (Base Actual)'
  Add-Bullet -Sb $sb -Text 'Carga de plantillas DOCX/PDF.'
  Add-Bullet -Sb $sb -Text 'Conversion de RTF/DOC a DOCX o PDF.'
  Add-Bullet -Sb $sb -Text 'Generacion de documentos con firmas en DOCX/PDF.'
  Add-Bullet -Sb $sb -Text 'Mapeo de campos y firmas por plantilla.'
  Add-Bullet -Sb $sb -Text 'Seleccion de formato de salida DOCX/PDF en frontend.'

  if ($WithBackendDetail) {
    Add-Heading1 -Sb $sb -Text '9. Anexo Tecnico Backend Implementado'
    Add-Paragraph -Sb $sb -Text 'Endpoints de plantillas:' -Bold $true
    Add-Bullet -Sb $sb -Text 'POST /api/templates'
    Add-Bullet -Sb $sb -Text 'POST /api/templates/convert?target=docx|pdf'
    Add-Bullet -Sb $sb -Text 'GET /api/templates'
    Add-Bullet -Sb $sb -Text 'GET /api/templates/{templateId}/requirements'
    Add-Bullet -Sb $sb -Text 'GET /api/templates/{templateId}/mapping'
    Add-Bullet -Sb $sb -Text 'POST /api/templates/{templateId}/mapping'
    Add-Bullet -Sb $sb -Text 'GET /api/templates/catalog-fields'
    Add-Bullet -Sb $sb -Text 'GET /api/templates/sample-docx'
    Add-Paragraph -Sb $sb -Text 'Endpoints de documentos:' -Bold $true -Before 80
    Add-Bullet -Sb $sb -Text 'POST /api/documents/generate (outputType: docx|pdf)'
    Add-Bullet -Sb $sb -Text 'GET /api/documents/{documentId}'
    Add-Paragraph -Sb $sb -Text 'Regla de salida:' -Bold $true -Before 80
    Add-Bullet -Sb $sb -Text 'Plantilla DOCX -> salida DOCX o PDF.'
    Add-Bullet -Sb $sb -Text 'Plantilla PDF -> salida solo PDF.'
  }

  Add-Heading1 -Sb $sb -Text ($(if ($WithBackendDetail) { '10. Compromiso de Ejecucion y Contratacion' } else { '9. Compromiso de Ejecucion y Contratacion' }))
  Add-Paragraph -Sb $sb -Text 'Compromiso comercial propuesto: se garantiza la toma del proyecto y reserva de capacidad de equipo al confirmar aceptacion escrita y primer hito economico dentro de la vigencia de esta propuesta.'
  Add-Bullet -Sb $sb -Text 'Inicio estimado: dentro de 5 dias habiles desde aceptacion formal.'
  Add-Bullet -Sb $sb -Text 'Bloque A como primera entrega contractual y validacion con cliente.'
  Add-Bullet -Sb $sb -Text 'Bloque B ejecutable en continuidad sin recambio de arquitectura.'
  Add-Bullet -Sb $sb -Text 'Seguimiento semanal con entregables verificables.'

  Add-Heading1 -Sb $sb -Text ($(if ($WithBackendDetail) { '11. Hitos de Pago Propuestos' } else { '10. Hitos de Pago Propuestos' }))
  Add-Table -Sb $sb -Headers @('Hito', 'Porcentaje', 'Condicion') -Rows @(
    @('Inicio', '30 %', 'Aceptacion formal + acceso tecnico confirmado'),
    @('Entrega Bloque A', '40 %', 'Entrega funcional en entorno de pruebas'),
    @('Entrega final Bloque B', '30 %', 'Aceptacion final del alcance contratado')
  )

  Add-Heading1 -Sb $sb -Text ($(if ($WithBackendDetail) { '12. Aceptacion' } else { '11. Aceptacion' }))
  Add-Paragraph -Sb $sb -Text 'Con esta propuesta quedan reflejados los intercambios, el alcance y la estimacion economica para proceder con la contratacion.'
  Add-Paragraph -Sb $sb -Text 'Validez de oferta: 30 dias desde el 20/02/2026.' -Italic $true -Color '666666'

  $null = $sb.Append('<w:sectPr><w:pgSz w:w="11906" w:h="16838" w:orient="portrait"/><w:pgMar w:top="1440" w:right="1800" w:bottom="1440" w:left="1800" w:header="708" w:footer="708" w:gutter="0"/><w:docGrid w:linePitch="360"/></w:sectPr>')
  $null = $sb.Append('</w:body></w:document>')
  return $sb.ToString()
}

function Build-DocxFromTemplate {
  param(
    [string]$TemplatePath,
    [string]$OutputPath,
    [string]$DocumentXml
  )

  $work = Join-Path $env:TEMP ([Guid]::NewGuid().ToString())
  New-Item -ItemType Directory -Path $work | Out-Null
  $zipPath = Join-Path $work 'base.zip'
  Copy-Item -LiteralPath $TemplatePath -Destination $zipPath
  [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, (Join-Path $work 'unzipped'))

  $docPath = Join-Path $work 'unzipped\word\document.xml'
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($docPath, $DocumentXml, $utf8NoBom)

  if (Test-Path $OutputPath) { Remove-Item $OutputPath -Force }
  [System.IO.Compression.ZipFile]::CreateFromDirectory((Join-Path $work 'unzipped'), $OutputPath)
  Remove-Item $work -Recurse -Force
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$template = Join-Path $projectRoot 'presupuesto-generacion-documental.docx'
$out1 = Join-Path $projectRoot 'presupuesto_integrado_cliente.docx'
$out2 = Join-Path $projectRoot 'presupuesto_integrado_cliente_con_backend.docx'

Build-DocxFromTemplate -TemplatePath $template -OutputPath $out1 -DocumentXml (Build-DocumentXml -WithBackendDetail $false)
Build-DocxFromTemplate -TemplatePath $template -OutputPath $out2 -DocumentXml (Build-DocumentXml -WithBackendDetail $true)

Write-Output 'ok'
