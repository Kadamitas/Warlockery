Set-StrictMode -Version Latest

Add-Type -AssemblyName System.Drawing

$assetRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../src/main/resources/assets/warlockery/textures'))
$blockRoot = Join-Path $assetRoot 'block'
$itemRoot = Join-Path $assetRoot 'item'

function PixelColor([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function New-Canvas([string]$background = $null) {
    $bitmap = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    if ($null -ne $background) {
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.Clear((PixelColor $background))
        $graphics.Dispose()
    }
    return $bitmap
}

function Save-Canvas([System.Drawing.Bitmap]$bitmap, [string]$path) {
    [System.IO.Directory]::CreateDirectory((Split-Path -Parent $path)) | Out-Null
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function Set-Pixel([System.Drawing.Bitmap]$bitmap, [int]$x, [int]$y, [string]$hex) {
    if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) {
        $bitmap.SetPixel($x, $y, (PixelColor $hex))
    }
}

function Fill-Rectangle([System.Drawing.Bitmap]$bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [string]$hex) {
    for ($row = $y; $row -lt $y + $height; $row++) {
        for ($column = $x; $column -lt $x + $width; $column++) {
            Set-Pixel $bitmap $column $row $hex
        }
    }
}

function New-AsciiSprite([string[]]$rows, [hashtable]$palette, [string]$path) {
    if ($rows.Count -ne 16 -or $rows.Where({ $_.Length -ne 16 }).Count -gt 0) {
        throw "Sprite rows must describe a 16 by 16 image: $path"
    }
    $bitmap = New-Canvas
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $symbol = [string]$rows[$y][$x]
            if ($symbol -ne '.') {
                Set-Pixel $bitmap $x $y $palette[$symbol]
            }
        }
    }
    Save-Canvas $bitmap $path
}

function New-Planks([string]$path, [string[]]$palette, [int]$offset) {
    $bitmap = New-Canvas $palette[2]
    $seams = @(3, 7, 11, 15)
    foreach ($y in $seams) {
        Fill-Rectangle $bitmap 0 $y 16 1 $palette[0]
    }
    for ($band = 0; $band -lt 4; $band++) {
        $top = $band * 4
        Fill-Rectangle $bitmap 0 $top 16 1 $palette[3]
        $joint = (($band * 5) + $offset) % 13 + 2
        Fill-Rectangle $bitmap $joint $top 1 3 $palette[1]
        if (($band + $offset) % 2 -eq 0) {
            Fill-Rectangle $bitmap (($joint + 5) % 14) ($top + 2) 3 1 $palette[1]
        }
    }
    Save-Canvas $bitmap $path
}

function New-LogSide([string]$path, [string[]]$palette, [int]$offset) {
    $bitmap = New-Canvas $palette[2]
    foreach ($x in @(0, 4, 9, 14)) {
        $column = ($x + $offset) % 16
        Fill-Rectangle $bitmap $column 0 1 16 $palette[0]
    }
    foreach ($x in @(2, 7, 12)) {
        $column = ($x + $offset) % 16
        Fill-Rectangle $bitmap $column 0 1 16 $palette[3]
    }
    Fill-Rectangle $bitmap (($offset + 5) % 13) 4 3 2 $palette[1]
    Fill-Rectangle $bitmap (($offset + 10) % 13) 11 3 2 $palette[1]
    Save-Canvas $bitmap $path
}

function New-LogTop([string]$path, [string[]]$palette, [int]$offset) {
    $bitmap = New-Canvas $palette[0]
    Fill-Rectangle $bitmap 1 1 14 14 $palette[2]
    Fill-Rectangle $bitmap 3 3 10 10 $palette[1]
    Fill-Rectangle $bitmap 4 4 8 8 $palette[3]
    Fill-Rectangle $bitmap 6 6 4 4 $palette[1]
    Set-Pixel $bitmap (6 + ($offset % 3)) 7 $palette[0]
    Set-Pixel $bitmap 10 (5 + ($offset % 3)) $palette[0]
    Save-Canvas $bitmap $path
}

function New-Leaves([string]$path, [string[]]$palette, [int]$offset) {
    $bitmap = New-Canvas $palette[1]
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $value = ($x * 3 + $y * 5 + $offset) % 17
            if ($value -eq 0) {
                $bitmap.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
            } elseif ($value -lt 5) {
                Set-Pixel $bitmap $x $y $palette[0]
            } elseif ($value -gt 12) {
                Set-Pixel $bitmap $x $y $palette[2]
            }
        }
    }
    foreach ($origin in @(@(2, 2), @(9, 1), @(5, 9), @(11, 10))) {
        Fill-Rectangle $bitmap $origin[0] $origin[1] 3 2 $palette[2]
    }
    Save-Canvas $bitmap $path
}

function New-Door([string]$family, [string[]]$palette) {
    $bottom = New-Canvas $palette[1]
    Fill-Rectangle $bottom 0 0 16 1 $palette[0]
    Fill-Rectangle $bottom 0 15 16 1 $palette[0]
    Fill-Rectangle $bottom 0 0 1 16 $palette[0]
    Fill-Rectangle $bottom 15 0 1 16 $palette[0]
    Fill-Rectangle $bottom 3 2 10 5 $palette[2]
    Fill-Rectangle $bottom 3 9 10 5 $palette[2]
    Fill-Rectangle $bottom 3 7 10 2 $palette[0]
    Set-Pixel $bottom 12 8 $palette[3]
    Save-Canvas $bottom (Join-Path $blockRoot ($family + 'wooddoor_bottom.png'))

    $top = New-Canvas $palette[1]
    Fill-Rectangle $top 0 0 16 1 $palette[0]
    Fill-Rectangle $top 0 15 16 1 $palette[0]
    Fill-Rectangle $top 0 0 1 16 $palette[0]
    Fill-Rectangle $top 15 0 1 16 $palette[0]
    Fill-Rectangle $top 3 2 4 8 $palette[2]
    Fill-Rectangle $top 9 2 4 8 $palette[2]
    Fill-Rectangle $top 3 12 10 2 $palette[2]
    Fill-Rectangle $top 7 1 2 12 $palette[0]
    Save-Canvas $top (Join-Path $blockRoot ($family + 'wooddoor_top.png'))

    $iconRows = @(
        '................',
        '...dddddddddd...',
        '...dllllllllk...',
        '...dlhhllhhlk...',
        '...dlhhllhhlk...',
        '...dlhhllhhlk...',
        '...dllllllllk...',
        '...dkkkkkkkkk...',
        '...dllllllllk...',
        '...dllnlllllk...',
        '...dllllllllk...',
        '...dllnlllglk...',
        '...dllllllllk...',
        '...dllllllllk...',
        '...dkkkkkkkkk...',
        '................'
    )
    New-AsciiSprite $iconRows @{
        d = $palette[0]
        k = $palette[1]
        l = $palette[2]
        h = $palette[3]
        n = $palette[1]
        g = '#d8c36a'
    } (Join-Path $itemRoot ($family + 'wooddoor.png'))
}

$woods = [ordered]@{
    alder = @('#56331f', '#7b4b2e', '#a46c42', '#c88f5d')
    hawthorn = @('#443b36', '#6e6157', '#958579', '#b7a89b')
    rowan = @('#54261f', '#7d382b', '#a9533d', '#ca7956')
}
$woodIndex = 0
foreach ($wood in $woods.GetEnumerator()) {
    New-Planks (Join-Path $blockRoot ($wood.Key + '_planks.png')) $wood.Value $woodIndex
    New-LogSide (Join-Path $blockRoot ($wood.Key + '_log.png')) $wood.Value $woodIndex
    New-LogTop (Join-Path $blockRoot ($wood.Key + '_log_top.png')) $wood.Value $woodIndex
    $leaves = switch ($wood.Key) {
        alder { @('#244b27', '#356b34', '#579451') }
        hawthorn { @('#2f4923', '#506b32', '#7c9148') }
        rowan { @('#31511f', '#4d742a', '#73933a') }
    }
    New-Leaves (Join-Path $blockRoot ($wood.Key + '_leaves.png')) $leaves $woodIndex
    $woodIndex++
}
New-Door 'alder' $woods.alder
New-Door 'rowan' $woods.rowan
New-Planks (Join-Path $blockRoot 'hexwood.png') @('#160f1d', '#2e1d3d', '#4c2b5d', '#714486') 2
New-LogSide (Join-Path $blockRoot 'hex_log.png') @('#110d18', '#251833', '#3b2549', '#664078') 3
New-Leaves (Join-Path $blockRoot 'hex_leaves.png') @('#18223b', '#263956', '#385b73') 4

$ice = New-Canvas '#315b7a'
Fill-Rectangle $ice 0 0 16 1 '#78b4d0'
Fill-Rectangle $ice 0 15 16 1 '#1c3d5a'
Fill-Rectangle $ice 0 0 1 16 '#78b4d0'
Fill-Rectangle $ice 15 0 1 16 '#1c3d5a'
for ($step = 1; $step -lt 14; $step++) {
    if ($step -notin @(4, 9)) {
        Set-Pixel $ice $step (14 - $step) '#5d91ad'
    }
}
Fill-Rectangle $ice 3 3 5 1 '#91c8dd'
Fill-Rectangle $ice 9 10 4 1 '#203f5c'
Set-Pixel $ice 5 11 '#9bd2e3'
Set-Pixel $ice 11 4 '#214664'
Save-Canvas $ice (Join-Path $blockRoot 'perpetualice.png')

$web = New-Canvas '#e8eeef'
foreach ($point in @(@(1, 3), @(5, 1), @(9, 4), @(13, 2), @(3, 10), @(7, 7), @(12, 12), @(4, 14))) {
    Set-Pixel $web $point[0] $point[1] '#ffffff'
}
foreach ($point in @(@(2, 7), @(6, 12), @(10, 1), @(14, 8), @(9, 13))) {
    Set-Pixel $web $point[0] $point[1] '#b9c4c7'
}
Save-Canvas $web (Join-Path $blockRoot 'web.png')

$webIcon = @(
    '................',
    '.w....w..w....w.',
    '..w...w..w...w..',
    '...w..w..w..w...',
    '....w.w..w.w....',
    '.wwwwwwwwwwwwww.',
    '......w..w......',
    '.wwwwwwwwwwwwww.',
    '......w..w......',
    '.wwwwwwwwwwwwww.',
    '....w.w..w.w....',
    '...w..w..w..w...',
    '..w...w..w...w..',
    '.w....w..w....w.',
    '................',
    '................'
)
New-AsciiSprite $webIcon @{ w = '#eef4f4' } (Join-Path $itemRoot 'ingredient_web.png')
New-AsciiSprite $webIcon @{ w = '#eef4f4' } (Join-Path $itemRoot 'web.png')

$wormyApple = @(
    '................',
    '.......bb.......',
    '......bbgg......',
    '.....ggg........',
    '...drrrrrrd.....',
    '..drrhhrrrrd....',
    '.drrhhrrrrrrd...',
    '.drrrrrrrrrrdw..',
    '.drrrrrrrrrrdqw.',
    '.drrrrrrrrrrdw..',
    '..drrrrrrrrd....',
    '..drrrrrrrrd....',
    '...drrrrrrd.....',
    '....ddrrdd......',
    '................',
    '................'
)
New-AsciiSprite $wormyApple @{
    b = '#5a351e'
    g = '#5d8c32'
    d = '#641923'
    r = '#c6343d'
    h = '#ed6a55'
    w = '#45671d'
    q = '#9ac33c'
} (Join-Path $itemRoot 'ingredient_apple_wormy.png')

function New-Bolt([string]$path, [string]$shaft, [string]$highlight, [string]$tip, [string]$fletching, [switch]$split) {
    $bitmap = New-Canvas
    for ($index = 0; $index -lt 9; $index++) {
        $x = 3 + $index
        $y = 13 - $index
        Set-Pixel $bitmap $x $y $shaft
        if ($index % 2 -eq 0) {
            Set-Pixel $bitmap $x ($y - 1) $highlight
        }
    }
    Set-Pixel $bitmap 11 3 $tip
    Set-Pixel $bitmap 12 3 $tip
    Set-Pixel $bitmap 12 4 $tip
    Set-Pixel $bitmap 13 3 $tip
    Set-Pixel $bitmap 12 2 $tip
    Set-Pixel $bitmap 2 12 $fletching
    Set-Pixel $bitmap 2 13 $fletching
    Set-Pixel $bitmap 3 14 $fletching
    Set-Pixel $bitmap 4 14 $fletching
    if ($split) {
        Set-Pixel $bitmap 10 2 $tip
        Set-Pixel $bitmap 11 2 $tip
        Set-Pixel $bitmap 13 4 $tip
        Set-Pixel $bitmap 13 5 $tip
    }
    Save-Canvas $bitmap $path
}

New-Bolt (Join-Path $itemRoot 'ingredient_bolt_stake.png') '#704525' '#a67543' '#3b2519' '#c69b58'
New-Bolt (Join-Path $itemRoot 'ingredient_bolt_holy.png') '#d6b94e' '#fff0a1' '#f6f3dd' '#f3db67'
New-Bolt (Join-Path $itemRoot 'ingredient_bolt_silver.png') '#8faeb8' '#d9f1f3' '#edfafa' '#668793'
New-Bolt (Join-Path $itemRoot 'ingredient_bolt_splitting.png') '#6a462c' '#b9804c' '#d4c08b' '#9a6040' -split
New-Bolt (Join-Path $itemRoot 'ingredient_bolt_anti_magic.png') '#3e294f' '#8456a2' '#b887db' '#25202e'

$leapingLily = @(
    '................',
    '.......p........',
    '......pwp.......',
    '.....pwwwp......',
    '......py........',
    '...ddggggddd....',
    '..dgggggggggd...',
    '.dgggggggggggd..',
    '.dggggggggg.....',
    '.dggggggg.......',
    '..dgggggggd.....',
    '...ddgggdd......',
    '......s.........',
    '......s.........',
    '................',
    '................'
)
New-AsciiSprite $leapingLily @{
    d = '#173f2d'
    g = '#4f9b49'
    p = '#ca79bc'
    w = '#f0dce9'
    y = '#f5d365'
    s = '#4d7137'
} (Join-Path $itemRoot 'leapinglily.png')

Copy-Item -LiteralPath (Join-Path $itemRoot 'ingredient_broom.png') -Destination (Join-Path $itemRoot 'ingredient_broom_enchanted.png') -Force
