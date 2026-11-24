# Remove BOM and fix color codes in GeminiTP mod

$files = @(
    "src\main\java\com\example\geminitp\FunctionExecutor.java",
    "src\main\java\com\example\geminitp\GeminiChatHandler.java",
    "src\main\java\com\example\geminitp\TeleportApprovalHandler.java",
    "src\main\java\com\example\geminitp\TeleportExecutor.java"
)

foreach ($file in $files) {
    if (Test-Path $file) {
        # Read content as bytes to preserve encoding
        $bytes = [System.IO.File]::ReadAllBytes($file)
        
        # Remove BOM if present (EF BB BF for UTF-8)
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            $bytes = $bytes[3..($bytes.Length-1)]
            Write-Host "Removed BOM from: $file"
        }
        
        # Convert to string and write back WITHOUT BOM
        $content = [System.Text.Encoding]::UTF8.GetString($bytes)
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($file, $content, $utf8NoBom)
        
        Write-Host "Fixed: $file"
    }
}

Write-Host "`nDone! Files have been fixed."
