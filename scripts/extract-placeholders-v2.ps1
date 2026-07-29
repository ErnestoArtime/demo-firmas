$docxPath = "C:\Proyectos\java\demo\storage\samples\plantilla_demo2.docx"
$copyPath = Join-Path $env:TEMP "temp_template_copy.docx"
Copy-Item $docxPath $copyPath -Force

$tempDir = Join-Path $env:TEMP ([Guid]::NewGuid().ToString())
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($copyPath, $tempDir)

$placeholders = New-Object System.Collections.Generic.HashSet[string]
$xmlFiles = Get-ChildItem -Path $tempDir -Filter "*.xml" -Recurse

foreach ($file in $xmlFiles) {
    if ($file.FullName -notmatch "word/document.xml" -and $file.FullName -notmatch "word/header" -and $file.FullName -notmatch "word/footer") {
        # continue
    }
    try {
        $content = Get-Content -Path $file.FullName -Raw
        $matches = [regex]::Matches($content, '\$\{([A-Za-z0-9_]+)\}')
        foreach ($m in $matches) { $null = $placeholders.Add($m.Groups[1].Value) }
        
        $matchesHash = [regex]::Matches($content, '\b([A-Za-z][A-Za-z0-9_]*)#')
        foreach ($m in $matchesHash) { $null = $placeholders.Add($m.Groups[1].Value) }

        $textContent = [regex]::Replace($content, '<[^>]+>', '')
        $matchesFrag = [regex]::Matches($textContent, '\$\{([A-Za-z0-9_]+)\}')
        foreach ($m in $matchesFrag) { $null = $placeholders.Add($m.Groups[1].Value) }
    } catch {}
}

Write-Host "PLACEHOLDERS_FOUND:"
foreach ($p in $placeholders) { Write-Host $p }

Remove-Item $tempDir -Recurse -Force
Remove-Item $copyPath -Force
