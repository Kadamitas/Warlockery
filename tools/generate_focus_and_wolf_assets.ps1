param(
    [string] $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$itemRoot = Join-Path $ProjectRoot 'src/main/resources/assets/warlockery/textures/item'
$blockRoot = Join-Path $ProjectRoot 'src/main/resources/assets/warlockery/textures/block'

function Color([string] $Hex) {
    return [Drawing.Color]::FromArgb(
        255,
        [Convert]::ToInt32($Hex.Substring(0, 2), 16),
        [Convert]::ToInt32($Hex.Substring(2, 2), 16),
        [Convert]::ToInt32($Hex.Substring(4, 2), 16)
    )
}

function Write-ArcaneFocus {
    $bitmap = [Drawing.Bitmap]::new(16, 16, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::None
            $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $outline = [Drawing.Point[]]@(
                [Drawing.Point]::new(8, 1), [Drawing.Point]::new(11, 2),
                [Drawing.Point]::new(13, 4), [Drawing.Point]::new(14, 8),
                [Drawing.Point]::new(13, 12), [Drawing.Point]::new(10, 14),
                [Drawing.Point]::new(8, 15), [Drawing.Point]::new(6, 14),
                [Drawing.Point]::new(3, 12), [Drawing.Point]::new(2, 8),
                [Drawing.Point]::new(3, 4), [Drawing.Point]::new(5, 2)
            )
            $rim = [Drawing.Point[]]@(
                [Drawing.Point]::new(8, 2), [Drawing.Point]::new(11, 3),
                [Drawing.Point]::new(12, 5), [Drawing.Point]::new(13, 8),
                [Drawing.Point]::new(12, 11), [Drawing.Point]::new(10, 13),
                [Drawing.Point]::new(8, 14), [Drawing.Point]::new(6, 13),
                [Drawing.Point]::new(4, 11), [Drawing.Point]::new(3, 8),
                [Drawing.Point]::new(4, 5), [Drawing.Point]::new(5, 3)
            )
            $center = [Drawing.Point[]]@(
                [Drawing.Point]::new(8, 4), [Drawing.Point]::new(11, 5),
                [Drawing.Point]::new(12, 8), [Drawing.Point]::new(11, 11),
                [Drawing.Point]::new(8, 12), [Drawing.Point]::new(5, 11),
                [Drawing.Point]::new(4, 8), [Drawing.Point]::new(5, 5)
            )
            $eyeOutline = [Drawing.Point[]]@(
                [Drawing.Point]::new(4, 8), [Drawing.Point]::new(8, 5),
                [Drawing.Point]::new(12, 8), [Drawing.Point]::new(8, 11)
            )
            $eye = [Drawing.Point[]]@(
                [Drawing.Point]::new(5, 8), [Drawing.Point]::new(8, 6),
                [Drawing.Point]::new(11, 8), [Drawing.Point]::new(8, 10)
            )
            $graphics.FillPolygon([Drawing.SolidBrush]::new((Color '171523')), $outline)
            $graphics.FillPolygon([Drawing.SolidBrush]::new((Color '7D4BB3')), $rim)
            $graphics.FillPolygon([Drawing.SolidBrush]::new((Color '244D55')), $center)
            $graphics.FillPolygon([Drawing.SolidBrush]::new((Color '15212A')), $eyeOutline)
            $graphics.FillPolygon([Drawing.SolidBrush]::new((Color '68DCD2')), $eye)
            $graphics.FillRectangle([Drawing.SolidBrush]::new((Color 'EAF7D4')), 7, 7, 2, 2)
        } finally {
            $graphics.Dispose()
        }
        $bitmap.Save((Join-Path $itemRoot 'arcane_focus.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

function Write-WolfTexture([string] $Name, [string[]] $Palette, [object[]] $DarkMarks, [object[]] $LightMarks) {
    $bitmap = [Drawing.Bitmap]::new(16, 16, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $base = Color $Palette[1]
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                $bitmap.SetPixel($x, $y, $base)
            }
        }
        foreach ($point in $DarkMarks) {
            $bitmap.SetPixel($point[0], $point[1], (Color $Palette[0]))
        }
        foreach ($point in $LightMarks) {
            $bitmap.SetPixel($point[0], $point[1], (Color $Palette[2]))
        }
        foreach ($point in @(@(3, 3), @(12, 5), @(7, 12), @(14, 14))) {
            $bitmap.SetPixel($point[0], $point[1], (Color $Palette[3]))
        }
        $bitmap.Save((Join-Path $blockRoot "$Name.png"), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

Write-ArcaneFocus
Write-WolfTexture 'wolfhead' @('2E2B2A', '554F4B', '746B64', '998E84') @(
    @(1, 2), @(2, 6), @(4, 1), @(5, 9), @(6, 4), @(8, 2), @(9, 7), @(10, 13),
    @(12, 1), @(13, 9), @(14, 4), @(1, 13), @(4, 14), @(7, 8), @(11, 10), @(15, 7)
) @(
    @(2, 10), @(4, 5), @(6, 13), @(8, 6), @(10, 3), @(11, 15), @(13, 6), @(15, 12),
    @(3, 8), @(5, 2), @(7, 15), @(9, 11), @(12, 12), @(14, 1)
)
Write-WolfTexture 'wolfhead_eye' @('24170E', 'C87922', 'F0B540', 'FFF0A0') @(
    @(2, 2), @(3, 11), @(7, 7), @(8, 7), @(7, 8), @(8, 8), @(12, 4), @(14, 13)
) @(
    @(1, 8), @(5, 3), @(10, 10), @(13, 7), @(15, 2)
)
