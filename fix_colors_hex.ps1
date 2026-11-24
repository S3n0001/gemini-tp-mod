# Fix corrupted color codes using hex replacement

$sourceDir = "src\main\java"
$files = Get-ChildItem -Path $sourceDir -Recurse -Filter "*.java"

$totalFixed = 0

foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $content = [System.Text.Encoding]::UTF8.GetString($bytes)
    $originalContent = $content
    
    # Replace C3 82 C2 A7 (Ã‚Â§) with C2 A7 (§)
    # This is the UTF-8 double-encoding issue
    $content = $content.Replace([char]0xC3 + [char]0x82 + [char]0xC2 + [char]0xA7, [char]0xA7)
    
    # Also try simple string replace
    $search = [char]195 + [char]130 + [char]194 + [char]167  # Ã‚Â§
    $replace = [char]167  # §
    $content = $content.Replace($search, $replace)
    
    if ($content -ne $originalContent) {
        # Save without BOM
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
        
        Write-Host "Fixed: $($file.Name)"
        $totalFixed++
    }
}

Write-Host "`nTotal files fixed: $totalFixed"
