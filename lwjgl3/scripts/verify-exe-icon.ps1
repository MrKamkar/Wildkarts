param(
    [Parameter(Mandatory = $true)]
    [string]$ExePath
)

Add-Type -AssemblyName System.Drawing

if (-not (Test-Path $ExePath)) {
    Write-Error "File not found: $ExePath"
    exit 1
}

$icon = [System.Drawing.Icon]::ExtractAssociatedIcon($ExePath)
$bitmap = $icon.ToBitmap()

# Default jpackage icon is mostly white; the game icon has strong red tones.
$redScore = 0
$whiteScore = 0
$points = @(
    @(4, 4), @(10, 10), @(20, 12), @(12, 20), @(22, 22)
)

foreach ($point in $points) {
    $pixel = $bitmap.GetPixel($point[0], $point[1])
    if ($pixel.R -gt 180 -and $pixel.G -lt 120 -and $pixel.B -lt 120) {
        $redScore++
    }
    if ($pixel.R -gt 220 -and $pixel.G -gt 220 -and $pixel.B -gt 220) {
        $whiteScore++
    }
}

if ($redScore -lt 2 -or $whiteScore -gt 2) {
    Write-Error @"
WildKarts.exe still has the default Java icon.
Rebuild: .\gradlew :lwjgl3:packageWinZip
Then delete the old WildKarts folder and extract the new ZIP.
If Explorer still shows the old icon, close the window, delete the folder, and extract again.
"@
    exit 1
}

Write-Host "OK: game icon embedded (redScore=$redScore, whiteScore=$whiteScore)"
