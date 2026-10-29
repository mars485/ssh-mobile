# Пересобирает скриншоты приложения в формат RuStore: 1080x1920 (9:16), PNG, до 5 МБ.
# Исходники (1080x2412) остаются без изменений, результат кладётся в store/screenshots/rustore.
[CmdletBinding()]
param(
    [string]$Source,
    [string]$Target,
    [int]$Width = 1080,
    [int]$Height = 1920,
    [int]$MaxShotWidth = 810,
    [int]$MaxShotHeight = 1800
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not $Source) { $Source = Join-Path $PSScriptRoot '..\store\screenshots' }
if (-not $Target) { $Target = Join-Path $PSScriptRoot '..\store\screenshots\rustore' }

if (-not (Test-Path $Target)) { New-Item -ItemType Directory -Path $Target | Out-Null }

$sources = Get-ChildItem (Join-Path $Source '*.png') | Where-Object { $_.DirectoryName -ne (Resolve-Path $Target).Path }
if (-not $sources) { throw "Не найдены PNG в $Source" }

foreach ($file in $sources) {
    $img = [System.Drawing.Image]::FromFile($file.FullName)
    try {
        $bmp = New-Object System.Drawing.Bitmap($Width, $Height)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

        $canvas = New-Object System.Drawing.Rectangle(0, 0, $Width, $Height)
        $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            $canvas,
            [System.Drawing.Color]::FromArgb(255, 10, 26, 18),
            [System.Drawing.Color]::FromArgb(255, 5, 9, 7),
            90)
        $g.FillRectangle($brush, $canvas)

        $scale = [Math]::Min($MaxShotWidth / $img.Width, $MaxShotHeight / $img.Height)
        $shotW = [int]($img.Width * $scale)
        $shotH = [int]($img.Height * $scale)
        $x = [int](($Width - $shotW) / 2)
        $y = [int](($Height - $shotH) / 2)

        $radius = 28
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $path.AddArc([single]$x, [single]$y, [single]($radius * 2), [single]($radius * 2), 180, 90)
        $path.AddArc([single]($x + $shotW - $radius * 2), [single]$y, [single]($radius * 2), [single]($radius * 2), 270, 90)
        $path.AddArc([single]($x + $shotW - $radius * 2), [single]($y + $shotH - $radius * 2), [single]($radius * 2), [single]($radius * 2), 0, 90)
        $path.AddArc([single]$x, [single]($y + $shotH - $radius * 2), [single]($radius * 2), [single]($radius * 2), 90, 90)
        $path.CloseFigure()

        $g.SetClip($path)
        $g.DrawImage($img, $x, $y, $shotW, $shotH)
        $g.ResetClip()

        $border = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(70, 255, 255, 255), 2)
        $g.DrawPath($border, $path)
        $border.Dispose()
        $path.Dispose()
        $brush.Dispose()
        $g.Dispose()

        $out = Join-Path $Target $file.Name
        $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()

        $check = [System.Drawing.Image]::FromFile($out)
        $kb = [int]((Get-Item $out).Length / 1KB)
        Write-Host ("{0} -> {1}x{2}, {3} КБ" -f $file.Name, $check.Width, $check.Height, $kb)
        if ($kb -gt 5120) { Write-Warning "$($file.Name) больше 5 МБ, RuStore отклонит" }
        $check.Dispose()
    } finally {
        $img.Dispose()
    }
}
