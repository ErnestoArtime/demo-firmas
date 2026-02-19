param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TemplatePath = "storage/samples/plantilla_demo.docx",
    [string]$Signature1Path = "storage/samples/firma1.png",
    [string]$Signature2Path = "storage/samples/firma2.png",
    [string]$Signature3Path = "storage/samples/firma3.png",
    [string]$OutputDir = "storage/output"
)

$ErrorActionPreference = "Stop"

function Get-SignatureBase64OrEmpty {
    param([string]$Path)

    if ($Path -and (Test-Path $Path)) {
        $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $Path))
        return [Convert]::ToBase64String($bytes)
    }
    return ""
}

if (-not (Test-Path $TemplatePath)) {
    throw "No existe la plantilla en '$TemplatePath'. Usa GET /api/templates/sample-docx o ajusta -TemplatePath."
}

New-Item -ItemType Directory -Force $OutputDir | Out-Null

Write-Host "1) Subiendo plantilla..."
$uploadRaw = & curl.exe -sS -X POST -F ("file=@" + (Resolve-Path $TemplatePath)) "$BaseUrl/api/templates"
$upload = $uploadRaw | ConvertFrom-Json

$templateId = $upload.templateId
Write-Host "   templateId: $templateId"

$signature1 = Get-SignatureBase64OrEmpty -Path $Signature1Path
$signature2 = Get-SignatureBase64OrEmpty -Path $Signature2Path
$signature3 = Get-SignatureBase64OrEmpty -Path $Signature3Path

$signatures = @{}
if ($signature1) { $signatures["FIRMA_1"] = $signature1 }
if ($signature2) { $signatures["FIRMA_2"] = $signature2 }
if ($signature3) { $signatures["FIRMA_3"] = $signature3 }

$payload = @{
    templateId = $templateId
    fields = @{
        NOMBRE = "Juan Perez"
        FECHA  = (Get-Date -Format "dd/MM/yyyy")
        CURSO  = "Demo"
    }
    signatures = $signatures
} | ConvertTo-Json -Depth 6

Write-Host "2) Generando documento..."
$generated = $null
try {
    $generated = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/documents/generate" -ContentType "application/json" -Body $payload
} catch {
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        Write-Error ("Fallo al generar documento. HTTP " + [int]$_.Exception.Response.StatusCode + " BODY: " + $errorBody)
    }
    throw
}
$documentId = $generated.documentId
$ext = if ($generated.type -eq "PDF") { "pdf" } else { "docx" }

Write-Host "   documentId: $documentId"
Write-Host "   downloadUrl: $($generated.downloadUrl)"

$outputPath = Join-Path $OutputDir ("resultado_" + $documentId + "." + $ext)

Write-Host "3) Descargando resultado..."
Invoke-WebRequest -Uri "$BaseUrl/api/documents/$documentId" -OutFile $outputPath

Write-Host ""
Write-Host "OK. Archivo generado: $outputPath"
