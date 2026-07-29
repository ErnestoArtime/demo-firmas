$ErrorActionPreference = 'Stop'

function New-TestDocx {
  param(
    [string]$OutputPath
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

  # Page 1
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">PAGINA 1: DATOS DEL CURSO</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">Curso: ${CURSO_NOMBRE}</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">Fecha: ${CURSO_FECHA}</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">Tutor: ${CURSO_TUTOR}</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p/>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">LISTADO DE ALUMNOS (BLOQUE):</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">NOMBRES:</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">BD_NOMBRES#</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p/>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">APELLIDOS:</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">BD_APELLIDOS#</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p/>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">NIFS:</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">BD_NIFS#</w:t></w:r></w:p>')

  # Page Break
  $null = $sb.AppendLine('    <w:p><w:r><w:br w:type="page"/></w:r></w:p>')

  # Page 2
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">PAGINA 2: DETALLE ALUMNO 1 Y FIRMA</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">Nombre: ${ALUMNO_1_NOMBRE} ${ALUMNO_1_APELLIDOS}</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">NIF: ${ALUMNO_1_NIF}</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p/>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">Por favor, firme abajo:</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">${FIRMA_1}</w:t></w:r></w:p>')

  $null = $sb.AppendLine('    <w:sectPr/>')
  $null = $sb.AppendLine('  </w:body>')
  $null = $sb.AppendLine('</w:document>')

  $sb.ToString() | Out-File -LiteralPath (Join-Path $wordDir "document.xml") -Encoding utf8

  if (Test-Path $OutputPath) { Remove-Item $OutputPath -Force }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $OutputPath)
  Remove-Item $tempDir -Recurse -Force
  Write-Host "Template generated at: $OutputPath"
}

New-TestDocx -OutputPath "C:\Proyectos\java\demo\storage\samples\test_template_2pages.docx"
