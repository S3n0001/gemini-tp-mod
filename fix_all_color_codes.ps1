# Comprehensive fix for ALL corrupted color codes in all Java files

$sourceDir = "src\main\java"
$files = Get-ChildItem -Path $sourceDir -Recurse -Filter "*.java"

$totalFixed = 0
$totalReplacements = 0

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    # Replace all corrupted color code variants
    $content = $content -replace 'Ã‚Â§', '§'
    $content = $content -replace 'Ãƒâ€šÃ‚Â§', '§'
    
    if ($content -ne $originalContent) {
        # Count replacements
        $replacements = ([regex]::Matches($originalContent, 'Ã‚Â§|Ãƒâ€šÃ‚Â§')).Count
        $totalReplacements += $replacements
        
        # Save without BOM
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        
        Write-Host "Fixed $replacements instances in: $($file.Name)"
        $totalFixed++
    }
}

Write-Host "`n========================================="
Write-Host "Total files fixed: $totalFixed"
Write-Host "Total color codes replaced: $totalReplacements"
Write-Host "========================================="
