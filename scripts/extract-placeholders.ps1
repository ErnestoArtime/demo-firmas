$docxPath = "C:\Proyectos\java\demo\storage\samples\plantilla_demo2.docx"
$tempDir = Join-Path $env:TEMP ([Guid]::NewGuid().ToString())
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($docxPath, $tempDir)

$placeholders = New-Object System.Collections.Generic.HashSet[string]
$xmlFiles = Get-ChildItem -Path $tempDir -Filter "*.xml" -Recurse

foreach ($file in $xmlFiles) {
    $content = Get-Content -Path $file.FullName -Raw
    # Standard placeholders ${VAR}
    $matches = [regex]::Matches($content, '\$\{([A-Za-z0-9_]+)\}')
    foreach ($m in $matches) { $null = $placeholders.Add($m.Groups[1].Value) }
    
    # Hash placeholders VAR#
    $matchesHash = [regex]::Matches($content, '\b([A-Za-z][A-Za-z0-9_]*)#')
    foreach ($m in $matchesHash) { $null = $placeholders.Add($m.Groups[1].Value) }

    # Handle fragmented placeholders (strip tags)
    $textContent = [regex]::Replace($content, '<[^>]+>', '')
    $matchesFrag = [regex]::Matches($textContent, '\$\{([A-Za-z0-9_]+)\}')
    foreach ($m in $matchesFrag) { $null = $placeholders.Add($m.Groups[1].Value) }
}

Write-Host "PLACEHOLDERS_FOUND:"
foreach ($p in $placeholders) { Write-Host $p }

Remove-Item $tempDir -Recurse -Force
