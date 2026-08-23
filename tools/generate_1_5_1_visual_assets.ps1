Set-StrictMode -Version Latest

Add-Type -AssemblyName System.Drawing

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$textureRoot = Join-Path $repoRoot 'src/main/resources/assets/warlockery/textures'
$blockRoot = Join-Path $textureRoot 'block'
$reportRoot = Join-Path $repoRoot 'build/reports/visual-audit'

function Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function New-Canvas([int]$width, [int]$height) {
    return [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Set-Pixel([System.Drawing.Bitmap]$bitmap, [int]$x, [int]$y, [string]$hex) {
    if ($x -ge 0 -and $x -lt $bitmap.Width -and $y -ge 0 -and $y -lt $bitmap.Height) {
        $bitmap.SetPixel($x, $y, (Color $hex))
    }
}

function Fill-Rectangle(
    [System.Drawing.Bitmap]$bitmap,
    [int]$x,
    [int]$y,
    [int]$width,
    [int]$height,
    [string]$hex
) {
    for ($row = $y; $row -lt $y + $height; $row++) {
        for ($column = $x; $column -lt $x + $width; $column++) {
            Set-Pixel $bitmap $column $row $hex
        }
    }
}

function Draw-Line(
    [System.Drawing.Bitmap]$bitmap,
    [int]$x0,
    [int]$y0,
    [int]$x1,
    [int]$y1,
    [string]$hex
) {
    $dx = [Math]::Abs($x1 - $x0)
    $sx = if ($x0 -lt $x1) { 1 } else { -1 }
    $dy = -[Math]::Abs($y1 - $y0)
    $sy = if ($y0 -lt $y1) { 1 } else { -1 }
    $errorValue = $dx + $dy
    while ($true) {
        Set-Pixel $bitmap $x0 $y0 $hex
        if ($x0 -eq $x1 -and $y0 -eq $y1) { break }
        $twiceError = 2 * $errorValue
        if ($twiceError -ge $dy) { $errorValue += $dy; $x0 += $sx }
        if ($twiceError -le $dx) { $errorValue += $dx; $y0 += $sy }
    }
}

function Dot([System.Drawing.Bitmap]$bitmap, [int]$x, [int]$y, [string]$dark, [string]$light) {
    Set-Pixel $bitmap $x $y $dark
    Set-Pixel $bitmap ($x + 1) $y $light
    Set-Pixel $bitmap $x ($y + 1) $light
}

function Save-Canvas([System.Drawing.Bitmap]$bitmap, [string]$path) {
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $path)) | Out-Null
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function New-Crop([string]$family, [int]$stage) {
    $bitmap = New-Canvas 16 16
    $height = @(3, 5, 8, 11, 14)[$stage]
    $top = 15 - $height
    switch ($family) {
        'belladonna' {
            $stem = '#31512b'; $leaf = '#49783c'; $light = '#6e9850'
            Draw-Line $bitmap 8 15 8 $top $stem
            for ($level = 0; $level -le $stage; $level++) {
                $y = 14 - $level * 2
                $reach = [Math]::Min(2 + $level, 5)
                Draw-Line $bitmap 8 $y (8 - $reach) ($y - 1) $stem
                Draw-Line $bitmap 8 ($y - 1) (8 + $reach) ($y - 2) $stem
                Dot $bitmap (7 - $reach) ($y - 2) $leaf $light
                Dot $bitmap (8 + $reach) ($y - 3) $leaf $light
            }
            if ($stage -ge 3) { Dot $bitmap 5 5 '#35234f' '#7a4d8f' }
            if ($stage -eq 4) { Dot $bitmap 10 3 '#2b183f' '#a45cae'; Set-Pixel $bitmap 8 1 '#7d447e' }
        }
        'snowbell' {
            $stem = '#37684a'; $leaf = '#4f8d62'; $light = '#76ae7f'
            Draw-Line $bitmap 7 15 7 $top $stem
            Draw-Line $bitmap 7 14 5 13 $leaf
            Draw-Line $bitmap 7 14 9 13 $light
            if ($stage -ge 1) { Draw-Line $bitmap 8 15 9 ($top + 1) $stem }
            for ($level = 0; $level -le $stage; $level++) {
                $y = 14 - $level * 2
                $side = if (($level % 2) -eq 0) { -1 } else { 1 }
                Draw-Line $bitmap 7 $y (7 + $side * (2 + [Math]::Min($level, 2))) ($y - 1) $leaf
            }
            if ($stage -ge 2) { Dot $bitmap 5 ($top + 1) '#b8d8d5' '#eef7ec' }
            if ($stage -ge 3) { Dot $bitmap 9 $top '#b8d8d5' '#ffffff' }
            if ($stage -eq 4) { Dot $bitmap 7 1 '#a8cecc' '#f6ffff'; Set-Pixel $bitmap 11 4 '#d7efeb' }
        }
        'wolfsbane' {
            $stem = '#294d35'; $leaf = '#3b7043'; $light = '#5d9251'
            Draw-Line $bitmap 8 15 8 $top $stem
            for ($level = 0; $level -le $stage + 1; $level++) {
                $y = 14 - $level * 2
                $reach = [Math]::Min(2 + $level, 5)
                Draw-Line $bitmap 8 $y (8 - $reach) ($y + 1) $leaf
                Draw-Line $bitmap 8 $y (8 + $reach) ($y + 1) $light
            }
            if ($stage -ge 2) {
                for ($y = $top; $y -le [Math]::Min(9, $top + $stage + 2); $y += 2) {
                    Set-Pixel $bitmap 7 $y '#5d4b9b'; Set-Pixel $bitmap 9 ($y + 1) '#8a72c4'
                }
            }
            if ($stage -eq 4) { Set-Pixel $bitmap 8 1 '#b9a1e2'; Set-Pixel $bitmap 6 3 '#7258aa' }
        }
        'wormwood' {
            $stem = '#49634a'; $leaf = '#6e8261'; $light = '#9ca282'
            Draw-Line $bitmap 8 15 8 $top $stem
            for ($level = 0; $level -le $stage + 1; $level++) {
                $y = 14 - $level * 2
                $reach = [Math]::Min(2 + $stage, 6)
                Draw-Line $bitmap 8 $y (8 - $reach) ($y - 2 - ($level % 2)) $leaf
                Draw-Line $bitmap 8 ($y - 1) (8 + $reach) ($y - 3 + ($level % 2)) $light
                Set-Pixel $bitmap (8 - $reach) ($y - 3) '#b1b28d'
                Set-Pixel $bitmap (8 + $reach) ($y - 3) '#7d916c'
            }
            if ($stage -eq 4) { Draw-Line $bitmap 7 5 4 1 $light; Draw-Line $bitmap 9 5 12 2 $leaf }
        }
    }
    Save-Canvas $bitmap (Join-Path $blockRoot ($family + '_stage_' + $stage + '.png'))
}

function New-Plant([string]$id) {
    $bitmap = New-Canvas 16 16
    switch ($id) {
        'bloodrose' {
            Draw-Line $bitmap 8 15 8 5 '#385333'; Draw-Line $bitmap 8 11 4 8 '#557143'; Draw-Line $bitmap 8 9 12 7 '#4d683b'
            foreach ($point in @(@(8,3),@(6,4),@(10,4),@(7,2),@(9,2))) { Dot $bitmap $point[0] $point[1] '#771c2d' '#ce3847' }
            Set-Pixel $bitmap 8 4 '#f0a23c'
        }
        'bramble_wild' {
            Draw-Line $bitmap 1 15 14 8 '#4b5a2b'; Draw-Line $bitmap 2 11 13 15 '#6b7434'; Draw-Line $bitmap 4 15 11 7 '#3f4b24'
            foreach ($point in @(@(3,12),@(6,10),@(9,12),@(12,9),@(13,14))) { Dot $bitmap $point[0] $point[1] '#395b2d' '#73934a' }
            foreach ($point in @(@(5,12),@(10,9),@(12,13))) { Set-Pixel $bitmap $point[0] $point[1] '#c8a06a' }
        }
        'embermoss' {
            foreach ($end in @(@(2,13),@(4,10),@(7,9),@(10,10),@(13,12),@(5,14),@(11,15))) { Draw-Line $bitmap 8 14 $end[0] $end[1] '#76432d' }
            foreach ($point in @(@(2,13),@(4,10),@(7,9),@(10,10),@(13,12),@(5,14),@(11,15))) { Dot $bitmap $point[0] $point[1] '#a84c27' '#ef8732' }
        }
        'glint_weed' {
            Draw-Line $bitmap 8 15 8 3 '#355c5c'; Draw-Line $bitmap 8 12 4 9 '#417f77'; Draw-Line $bitmap 8 9 12 6 '#4e8e80'
            foreach ($point in @(@(8,2),@(4,8),@(12,5),@(6,6),@(10,10))) { Dot $bitmap $point[0] $point[1] '#65a7a0' '#d9e878' }
        }
        'grassper' {
            Fill-Rectangle $bitmap 3 13 10 2 '#304e2d'; Fill-Rectangle $bitmap 4 11 8 2 '#4f763d'
            foreach ($x in 2..13) { if (($x % 2) -eq 0) { Draw-Line $bitmap $x 13 ($x - 1) (8 + ($x % 3)) '#72a64f' } }
        }
        'leapinglily' {
            Fill-Rectangle $bitmap 3 12 10 2 '#355d3a'; Fill-Rectangle $bitmap 5 10 6 2 '#5b8d4f'; Draw-Line $bitmap 8 11 8 6 '#3f6b3f'
            foreach ($point in @(@(8,5),@(6,6),@(10,6),@(7,4),@(9,4))) { Dot $bitmap $point[0] $point[1] '#7b4c93' '#d18bd0' }
            Set-Pixel $bitmap 8 6 '#f1d06b'
        }
        'plantmine' { Draw-Line $bitmap 8 15 8 10 '#3b6137'; Fill-Rectangle $bitmap 5 9 7 4 '#557844'; Fill-Rectangle $bitmap 6 8 5 2 '#789253'; Set-Pixel $bitmap 8 10 '#d0b04b' }
        'somnian_cotton' {
            Draw-Line $bitmap 8 15 8 4 '#446653'; Draw-Line $bitmap 8 10 4 7 '#587b62'; Draw-Line $bitmap 8 8 12 6 '#587b62'
            foreach ($point in @(@(3,5),@(5,4),@(4,7),@(11,4),@(12,6),@(8,2),@(9,3))) { Dot $bitmap $point[0] $point[1] '#a9c9c6' '#edf4e8' }
        }
        'spanish_moss' {
            Draw-Line $bitmap 2 0 13 0 '#516a46';
            foreach ($x in @(2,4,6,9,11,13)) { $end = 8 + (($x * 3) % 8); Draw-Line $bitmap $x 0 ($x + (($x % 3) - 1)) $end '#617b53'; Set-Pixel $bitmap ($x + 1) ([Math]::Min(15,$end-2)) '#91a574' }
        }
        'vine' {
            Draw-Line $bitmap 6 0 9 15 '#3c6b36'; Draw-Line $bitmap 7 4 3 6 '#4e8541'; Draw-Line $bitmap 8 8 13 6 '#5a9348'; Draw-Line $bitmap 9 12 4 14 '#477c3b'
            foreach ($point in @(@(3,5),@(12,5),@(4,13),@(8,9))) { Dot $bitmap $point[0] $point[1] '#477c3b' '#7aaa57' }
        }
        'plantmine_ink' {
            Draw-Line $bitmap 8 15 8 10 '#33472e'; Fill-Rectangle $bitmap 5 9 7 4 '#415b3c'; Fill-Rectangle $bitmap 6 8 5 2 '#283e37'
            Dot $bitmap 7 10 '#192b55' '#496cc6'; Set-Pixel $bitmap 10 14 '#334f9e'; Set-Pixel $bitmap 5 15 '#253a75'
        }
        'plantmine_sprouting' {
            Fill-Rectangle $bitmap 5 11 7 4 '#4d6338'; Draw-Line $bitmap 8 11 8 5 '#4b7d3d'; Draw-Line $bitmap 8 8 4 6 '#6ba84f'; Draw-Line $bitmap 8 7 12 4 '#78b75a'
        }
        'plantmine_thorns' {
            Fill-Rectangle $bitmap 5 11 7 4 '#4d5731'; foreach ($end in @(@(1,10),@(3,6),@(8,4),@(13,6),@(15,10))) { Draw-Line $bitmap 8 12 $end[0] $end[1] '#9d7647'; Set-Pixel $bitmap $end[0] $end[1] '#e0c088' }
        }
        'plantmine_unarmed' { Fill-Rectangle $bitmap 5 11 7 4 '#5c7044'; Fill-Rectangle $bitmap 6 9 5 3 '#80945a'; Fill-Rectangle $bitmap 7 8 3 2 '#a5ae70'; Set-Pixel $bitmap 8 11 '#ded29d' }
        'plantmine_webs' {
            Fill-Rectangle $bitmap 5 11 7 4 '#45563b'; foreach ($end in @(@(1,8),@(3,5),@(8,4),@(13,5),@(15,9))) { Draw-Line $bitmap 8 12 $end[0] $end[1] '#d9dfd6' }
            Draw-Line $bitmap 3 7 13 7 '#f1f4ee'; Draw-Line $bitmap 5 5 11 10 '#bcc8c2'
        }
        'grassper_occupied' {
            Fill-Rectangle $bitmap 3 13 10 2 '#2d492b'; Fill-Rectangle $bitmap 4 10 8 3 '#4c723d'; Fill-Rectangle $bitmap 5 7 6 4 '#6d874c'
            Set-Pixel $bitmap 6 8 '#f1df83'; Set-Pixel $bitmap 9 8 '#f1df83'; Set-Pixel $bitmap 6 9 '#1b2119'; Set-Pixel $bitmap 9 9 '#1b2119'
            Draw-Line $bitmap 4 11 1 8 '#70a14d'; Draw-Line $bitmap 11 11 14 7 '#70a14d'
        }
        'glint_weed_hanging' {
            Draw-Line $bitmap 3 0 12 0 '#315754'; foreach ($x in @(3,6,9,12)) { $end=9+(($x*2)%7); Draw-Line $bitmap $x 0 ($x + (($x % 2)*2-1)) $end '#4d8880'; Dot $bitmap ($x - 1) ([Math]::Min(14,$end-1)) '#70aaa1' '#e1e77b' }
        }
    }
    Save-Canvas $bitmap (Join-Path $blockRoot ($id + '.png'))
}

foreach ($family in @('belladonna','snowbell','wolfsbane','wormwood')) {
    foreach ($stage in 0..4) { New-Crop $family $stage }
}

$plants = @(
    'bloodrose','bramble_wild','embermoss','glint_weed','grassper','leapinglily','plantmine',
    'somnian_cotton','spanish_moss','vine','plantmine_ink','plantmine_sprouting','plantmine_thorns',
    'plantmine_unarmed','plantmine_webs','grassper_occupied','glint_weed_hanging'
)
$plants | ForEach-Object { New-Plant $_ }

$contactIds = @()
foreach ($family in @('belladonna','snowbell','wolfsbane','wormwood')) {
    foreach ($stage in 0..4) { $contactIds += $family + '_stage_' + $stage }
}
$contactIds += $plants
$columns = 6; $cellWidth = 176; $cellHeight = 104
$sheet = New-Canvas ($columns * $cellWidth) ([Math]::Ceiling($contactIds.Count / $columns) * $cellHeight)
$graphics = [System.Drawing.Graphics]::FromImage($sheet)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$font = [System.Drawing.Font]::new('Consolas', 8)
for ($index = 0; $index -lt $contactIds.Count; $index++) {
    $x = ($index % $columns) * $cellWidth; $y = [Math]::Floor($index / $columns) * $cellHeight
    $graphics.FillRectangle([System.Drawing.Brushes]::White, $x, $y, 80, 80)
    $graphics.FillRectangle([System.Drawing.Brushes]::Black, $x + 80, $y, 80, 80)
    $texture = [System.Drawing.Bitmap]::new((Join-Path $blockRoot ($contactIds[$index] + '.png')))
    $graphics.DrawImage($texture, $x + 8, $y + 8, 64, 64)
    $graphics.DrawImage($texture, $x + 88, $y + 8, 64, 64)
    $graphics.FillRectangle([System.Drawing.Brushes]::DimGray, $x, $y + 80, $cellWidth, 24)
    $graphics.DrawString($contactIds[$index], $font, [System.Drawing.Brushes]::White, $x + 3, $y + 84)
    $texture.Dispose()
}
$font.Dispose(); $graphics.Dispose()
Save-Canvas $sheet (Join-Path $reportRoot '1.5.1-plant-contact-sheet.png')
