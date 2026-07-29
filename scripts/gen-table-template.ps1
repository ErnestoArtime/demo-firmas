$ErrorActionPreference = 'Stop'

function New-TableDocx {
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

  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">LISTADO DE ASISTENCIA - TABLA DINAMICA</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t xml:space="preserve">Curso: ${CURSO_NOMBRE}</w:t></w:r></w:p>')
  $null = $sb.AppendLine('    <w:p/>')

  # Start Table
  $null = $sb.AppendLine('    <w:tbl>')
  $null = $sb.AppendLine('      <w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:tblBorders><w:top w:val="single" w:sz="4"/><w:left w:val="single" w:sz="4"/><w:bottom w:val="single" w:sz="4"/><w:right w:val="single" w:sz="4"/><w:insideH w:val="single" w:sz="4"/><w:insideV w:val="single" w:sz="4"/></w:tblBorders></w:tblPr>')
  
  # Header Row
  $null = $sb.AppendLine('      <w:tr>')
  $null = $sb.AppendLine('        <w:tc><w:p><w:r><w:b/><w:t>Nombre</w:t></w:r></w:p></w:tc>')
  $null = $sb.AppendLine('        <w:tc><w:p><w:r><w:b/><w:t>Apellidos</w:t></w:r></w:p></w:tc>')
  $null = $sb.AppendLine('        <w:tc><w:p><w:r><w:b/><w:t>NIF</w:t></w:r></w:p></w:tc>')
  $null = $sb.AppendLine('        <w:tc><w:p><w:r><w:b/><w:t>Firma</w:t></w:r></w:p></w:tc>')
  $null = $sb.AppendLine('      </w:tr>')

  # Row 1 (Repeater Row)
  $null = $sb.AppendLine('      <w:tr>')
  $null = $sb.AppendLine('        <w:tc><w:p><w:r><w:t>${ALUMNO_1_NOMBRE}</w:t></w:r></w:p></w:tc>')
  $null = $sb.AppendLine('        <w:tc><w:p><w:r><w:t>${ALUMNO_1_APELLIDOS}</w:t></w:r></w:p></w:tc>')
  $null = $sb.AppendLine('        <w:tc><w:p><w:r><w:t>${ALUMNO_1_NIF}</w:t></w:r></w:p></w:tc>')
  $null = $sb.AppendLine('        <w:tc><w:p><w:r><w:t>${FIRMA_1}</w:t></w:r></w:p></w:tc>')
  $null = $sb.AppendLine('      </w:tr>')
  
  $null = $sb.AppendLine('    </w:tbl>')
  # End Table

  $null = $sb.AppendLine('    <w:p/>')
  $null = $sb.AppendLine('    <w:p><w:r><w:t>Fin del listado.</w:t></w:r></w:p>')

  $null = $sb.AppendLine('    <w:sectPr/>')
  $null = $sb.AppendLine('  </w:body>')
  $null = $sb.AppendLine('</w:document>')

  $sb.ToString() | Out-File -LiteralPath (Join-Path $wordDir "document.xml") -Encoding utf8

  if (Test-Path $OutputPath) { Remove-Item $OutputPath -Force }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::CreateFromDirectory($tempDir, $OutputPath)
  Remove-Item $tempDir -Recurse -Force
  Write-Host "Table template generated at: $OutputPath"
}

New-TableDocx -OutputPath "C:\Proyectos\java\demo\storage\samples\plantilla_tabla_2pag.docx"
