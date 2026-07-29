$ErrorActionPreference = 'Stop'

$tmp = Join-Path $env:TEMP 'docx_style_ref'
if (Test-Path $tmp) {
  Remove-Item $tmp -Recurse -Force
}

New-Item -ItemType Directory -Path $tmp | Out-Null
Copy-Item -LiteralPath 'presupuesto-generacion-documental.docx' -Destination (Join-Path $tmp 'ref.zip')
Expand-Archive -LiteralPath (Join-Path $tmp 'ref.zip') -DestinationPath (Join-Path $tmp 'unzipped') -Force

Get-Content -Path (Join-Path $tmp 'unzipped\word\document.xml') -TotalCount 160
Write-Output '---STYLES---'
Get-Content -Path (Join-Path $tmp 'unzipped\word\styles.xml') -TotalCount 260
