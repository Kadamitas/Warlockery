Add-Type -AssemblyName System.Drawing

$sourcePath = Join-Path $PSScriptRoot '..\docs\concept_art\occult_items_and_statues_v2.png'
$targetRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\warlockery\textures\item'
$source = [System.Drawing.Bitmap]::new((Resolve-Path $sourcePath).Path)
$cells = [ordered]@{
    boline = @(0, 0)
    ritual_knife = @(1, 0)
    demonheart = @(0, 1)
}

function Saturation([System.Drawing.Color]$color) {
    $maximum = [Math]::Max($color.R, [Math]::Max($color.G, $color.B))
    $minimum = [Math]::Min($color.R, [Math]::Min($color.G, $color.B))
    if ($maximum -eq 0) { return 0.0 }
    return ($maximum - $minimum) / [double]$maximum
}

foreach ($entry in $cells.GetEnumerator()) {
    $cellWidth = [int]($source.Width / 4)
    $cellHeight = [int]($source.Height / 3)
    $left = $entry.Value[0] * $cellWidth
    $top = $entry.Value[1] * $cellHeight
    $cell = [System.Drawing.Bitmap]::new($cellWidth, $cellHeight, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($cell)
    $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0, 0, $cellWidth, $cellHeight), [System.Drawing.Rectangle]::new($left, $top, $cellWidth, $cellHeight), [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.Dispose()

    $minimumX = $cellWidth
    $minimumY = $cellHeight
    $maximumX = -1
    $maximumY = -1
    for ($y = 0; $y -lt $cellHeight; $y++) {
        for ($x = 0; $x -lt $cellWidth; $x++) {
            $pixel = $cell.GetPixel($x, $y)
            $brightness = ($pixel.R + $pixel.G + $pixel.B) / 3
            if ($brightness -gt 155 -and (Saturation $pixel) -lt 0.16) {
                $cell.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
            } else {
                $minimumX = [Math]::Min($minimumX, $x)
                $minimumY = [Math]::Min($minimumY, $y)
                $maximumX = [Math]::Max($maximumX, $x)
                $maximumY = [Math]::Max($maximumY, $y)
            }
        }
    }
    $bounds = [System.Drawing.Rectangle]::FromLTRB($minimumX, $minimumY, $maximumX + 1, $maximumY + 1)
    $scale = [Math]::Min(14.0 / $bounds.Width, 14.0 / $bounds.Height)
    $width = [Math]::Max(1, [int][Math]::Round($bounds.Width * $scale))
    $height = [Math]::Max(1, [int][Math]::Round($bounds.Height * $scale))
    $icon = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $iconGraphics = [System.Drawing.Graphics]::FromImage($icon)
    $iconGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $iconGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $iconGraphics.DrawImage($cell, [System.Drawing.Rectangle]::new([int]((16 - $width) / 2), [int]((16 - $height) / 2), $width, $height), $bounds, [System.Drawing.GraphicsUnit]::Pixel)
    $iconGraphics.Dispose()
    if ($entry.Key -eq 'ritual_knife') {
        $handle = $icon.GetPixel(4, 12)
        $icon.SetPixel(2, 14, $handle)
        $icon.SetPixel(3, 13, $handle)
    }
    if ($entry.Key -eq 'boline') {
        $blade = [System.Drawing.Color]::FromArgb(255, 191, 198, 200)
        $icon.SetPixel(13, 3, $blade)
        $icon.SetPixel(14, 2, $blade)
    }
    $icon.Save((Join-Path $targetRoot "$($entry.Key).png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $icon.Dispose()
    $cell.Dispose()
}
$source.Dispose()
