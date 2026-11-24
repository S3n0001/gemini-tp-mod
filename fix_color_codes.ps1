# Fix corrupted color codes in GeminiTP mod
# Replaces Ã‚Â§ with § (section symbol)

$sourceDir = "src\main\java"
$files = Get-ChildItem -Path $sourceDir -Recurse -Filter "*.java"

$replacements = @(
    # All the corrupted variants I found
    @{ From = "Ã‚Â§"; To = "§" },           # Standard UTF-8 encoding issue
    @{ From = "Ãƒâ€šÃ‚Â§"; To = "§" }  # Double-encoded version
)

$totalFixed = 0

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    foreach ($replacement in $replacements) {
        $content = $content.Replace($replacement.From, $replacement.To)
    }
    
    if ($content -ne $originalContent) {
        # Save with UTF-8 encoding
        [System.IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
        Write-Host "Fixed: $($file.Name)"
        $totalFixed++
    }
}

Write-Host "`n========================================="
Write-Host "Total files fixed: $totalFixed"
Write-Host "========================================="
Write-Host "`nDone! Please rebuild the mod."
