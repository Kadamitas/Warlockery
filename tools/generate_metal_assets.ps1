Add-Type -AssemblyName System.Drawing

$resourceRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\warlockery\textures'
$itemRoot = Join-Path $resourceRoot 'item'
$blockRoot = Join-Path $resourceRoot 'block'
$equipmentRoot = Join-Path $resourceRoot 'entity\equipment'

function New-Canvas([int]$width, [int]$height) {
    return [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Set-Pixel($image, [int]$x, [int]$y, [string]$color) {
    if ($x -ge 0 -and $y -ge 0 -and $x -lt $image.Width -and $y -lt $image.Height) {
        $image.SetPixel($x, $y, [System.Drawing.ColorTranslator]::FromHtml($color))
    }
}

function Fill-Rect($image, [int]$x, [int]$y, [int]$width, [int]$height, [string]$color) {
    for ($px = $x; $px -lt $x + $width; $px++) {
        for ($py = $y; $py -lt $y + $height; $py++) {
            Set-Pixel $image $px $py $color
        }
    }
}

function Draw-Line($image, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [string]$color, [int]$width = 1) {
    $dx = [Math]::Abs($x1 - $x0)
    $sx = if ($x0 -lt $x1) { 1 } else { -1 }
    $dy = -[Math]::Abs($y1 - $y0)
    $sy = if ($y0 -lt $y1) { 1 } else { -1 }
    $error = $dx + $dy
    while ($true) {
        Fill-Rect $image ($x0 - [Math]::Floor($width / 2)) ($y0 - [Math]::Floor($width / 2)) $width $width $color
        if ($x0 -eq $x1 -and $y0 -eq $y1) { break }
        $twice = 2 * $error
        if ($twice -ge $dy) { $error += $dy; $x0 += $sx }
        if ($twice -le $dx) { $error += $dx; $y0 += $sy }
    }
}

function Save-Image($image, [string]$path) {
    $directory = Split-Path $path
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $image.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $image.Dispose()
}

function Draw-Handle($image) {
    Draw-Line $image 4 13 11 6 '#3B241B' 3
    Draw-Line $image 4 13 11 6 '#9A6A3D' 1
}

function New-Tool([string]$kind, [string]$dark, [string]$mid, [string]$light) {
    $image = New-Canvas 16 16
    Draw-Handle $image
    switch ($kind) {
        'sword' {
            Draw-Line $image 5 11 12 4 $dark 4
            Draw-Line $image 6 10 13 3 $mid 2
            Draw-Line $image 8 8 13 3 $light 1
            Draw-Line $image 3 10 7 14 '#3B241B' 2
            Fill-Rect $image 2 13 3 2 '#74502E'
        }
        'axe' {
            Fill-Rect $image 8 2 5 5 $dark
            Fill-Rect $image 7 3 6 3 $mid
            Fill-Rect $image 10 2 3 2 $light
            Set-Pixel $image 13 5 $dark
        }
        'pickaxe' {
            Draw-Line $image 3 4 12 3 $dark 3
            Draw-Line $image 4 3 12 3 $mid 1
            Set-Pixel $image 13 4 $light
        }
        'shovel' {
            Fill-Rect $image 9 2 4 4 $dark
            Fill-Rect $image 10 2 3 3 $mid
            Fill-Rect $image 11 2 2 1 $light
        }
        'hoe' {
            Fill-Rect $image 8 2 5 2 $dark
            Fill-Rect $image 9 2 4 1 $light
            Fill-Rect $image 8 3 2 2 $mid
        }
    }
    return $image
}

function New-ArmorIcon([string]$kind, [string]$dark, [string]$mid, [string]$light, [string]$spark) {
    $image = New-Canvas 16 16
    switch ($kind) {
        'helm' {
            Fill-Rect $image 3 3 10 7 $dark
            Fill-Rect $image 4 2 8 7 $mid
            Fill-Rect $image 5 3 6 2 $light
            Fill-Rect $image 5 7 2 3 '#00000000'
            Fill-Rect $image 9 7 2 3 '#00000000'
        }
        'chestplate' {
            Fill-Rect $image 2 4 12 9 $dark
            Fill-Rect $image 4 3 8 10 $mid
            Fill-Rect $image 5 4 6 3 $light
            Fill-Rect $image 7 3 2 3 '#00000000'
        }
        'leggings' {
            Fill-Rect $image 3 2 10 6 $dark
            Fill-Rect $image 4 3 8 4 $mid
            Fill-Rect $image 3 7 4 7 $dark
            Fill-Rect $image 9 7 4 7 $dark
            Fill-Rect $image 4 7 2 5 $mid
            Fill-Rect $image 10 7 2 5 $mid
        }
        'boots' {
            Fill-Rect $image 3 5 4 8 $dark
            Fill-Rect $image 9 5 4 8 $dark
            Fill-Rect $image 4 5 2 6 $mid
            Fill-Rect $image 10 5 2 6 $mid
            Fill-Rect $image 2 11 5 3 $mid
            Fill-Rect $image 9 11 5 3 $mid
        }
    }
    Set-Pixel $image 5 5 $spark
    Set-Pixel $image 10 8 $spark
    return $image
}

function New-Ingot([string]$dark, [string]$mid, [string]$light) {
    $image = New-Canvas 16 16
    Fill-Rect $image 3 6 10 5 $dark
    Fill-Rect $image 4 5 8 5 $mid
    Fill-Rect $image 5 5 6 2 $light
    Set-Pixel $image 3 6 $mid
    Set-Pixel $image 12 6 $dark
    return $image
}

function New-Nugget([string]$dark, [string]$mid, [string]$light) {
    $image = New-Canvas 16 16
    Fill-Rect $image 5 5 6 6 $dark
    Fill-Rect $image 6 4 4 7 $mid
    Fill-Rect $image 7 5 3 2 $light
    Set-Pixel $image 4 7 $mid
    Set-Pixel $image 11 8 $dark
    return $image
}

function New-Ore([string]$stoneDark, [string]$stoneMid, [string]$stoneLight) {
    $image = New-Canvas 16 16
    Fill-Rect $image 0 0 16 16 $stoneMid
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $value = ($x * 13 + $y * 7 + $x * $y) % 17
            if ($value -eq 0 -or $value -eq 3) { Set-Pixel $image $x $y $stoneDark }
            if ($value -eq 8) { Set-Pixel $image $x $y $stoneLight }
        }
    }
    foreach ($point in @(@(2,3),@(3,3),@(3,4),@(9,2),@(10,2),@(10,3),@(6,8),@(7,8),@(7,9),@(12,11),@(13,11),@(12,12),@(3,13),@(4,12))) {
        Set-Pixel $image $point[0] $point[1] '#B9C7D1'
    }
    foreach ($point in @(@(2,2),@(9,1),@(6,7),@(12,10),@(3,12))) {
        Set-Pixel $image $point[0] $point[1] '#F5FBFF'
    }
    return $image
}

function Recolor-Equipment([string]$sourcePath, [string]$targetPath, [string]$mode) {
    $source = [System.Drawing.Bitmap]::new((Resolve-Path $sourcePath).Path)
    $target = New-Canvas $source.Width $source.Height
    for ($y = 0; $y -lt $source.Height; $y++) {
        for ($x = 0; $x -lt $source.Width; $x++) {
            $pixel = $source.GetPixel($x, $y)
            if ($pixel.A -eq 0) { continue }
            $brightness = [int](($pixel.R + $pixel.G + $pixel.B) / 3)
            if ($mode -eq 'silver') {
                $value = [Math]::Min(244, [Math]::Max(78, $brightness + 55))
                $color = [System.Drawing.Color]::FromArgb($pixel.A, [Math]::Min(255, $value + 8), [Math]::Min(255, $value + 14), [Math]::Min(255, $value + 20))
            } else {
                $green = [Math]::Min(105, [Math]::Max(35, [int]($brightness * 0.45)))
                $color = [System.Drawing.Color]::FromArgb($pixel.A, [int]($green * 0.32), $green, [int]($green * 0.58))
                if ((($x * 11 + $y * 17) % 97) -eq 0) {
                    $color = [System.Drawing.Color]::FromArgb($pixel.A, 116, 214, 111)
                }
            }
            $target.SetPixel($x, $y, $color)
        }
    }
    $source.Dispose()
    Save-Image $target $targetPath
}

$silver = @{ Dark = '#66747F'; Mid = '#B9C7D1'; Light = '#F5FBFF'; Spark = '#D8E7F0' }
$goblinite = @{ Dark = '#102F24'; Mid = '#245D3A'; Light = '#4C9860'; Spark = '#86D66E' }

foreach ($kind in @('sword','axe','pickaxe','shovel','hoe')) {
    $silverId = if ($kind -eq 'sword') { 'silversword' } else { "silver$kind" }
    Save-Image (New-Tool $kind $silver.Dark $silver.Mid $silver.Light) (Join-Path $itemRoot "$silverId.png")
    Save-Image (New-Tool $kind $goblinite.Dark $goblinite.Mid $goblinite.Light) (Join-Path $itemRoot "delvealloy$kind.png")
}

$armorNames = @{ helm = 'helm'; chestplate = 'chestplate'; leggings = 'leggings'; boots = 'boots' }
foreach ($kind in $armorNames.Keys) {
    Save-Image (New-ArmorIcon $kind $silver.Dark $silver.Mid $silver.Light $silver.Spark) (Join-Path $itemRoot "silver$($armorNames[$kind]).png")
    Save-Image (New-ArmorIcon $kind $goblinite.Dark $goblinite.Mid $goblinite.Light $goblinite.Spark) (Join-Path $itemRoot "delvealloy$($armorNames[$kind]).png")
}

Save-Image (New-Ingot $silver.Dark $silver.Mid $silver.Light) (Join-Path $itemRoot 'silver_ingot.png')
Save-Image (New-Nugget $silver.Dark $silver.Mid $silver.Light) (Join-Path $itemRoot 'silver_nugget.png')
Save-Image (New-Nugget '#46525A' '#8C9BA5' '#E8F4FA') (Join-Path $itemRoot 'raw_silver.png')
Save-Image (New-Ore '#675F56' '#8A8176' '#ADA397') (Join-Path $blockRoot 'silver_ore.png')
Save-Image (New-Ore '#24282C' '#3D4348' '#5B6268') (Join-Path $blockRoot 'deepslate_silver_ore.png')

$silverBlock = New-Canvas 16 16
Fill-Rect $silverBlock 0 0 16 16 '#AAB9C4'
for ($index = 0; $index -lt 16; $index += 4) {
    Fill-Rect $silverBlock $index 0 1 16 '#DCE8EF'
    Fill-Rect $silverBlock 0 $index 16 1 '#72818C'
}
Save-Image $silverBlock (Join-Path $blockRoot 'silver_block.png')

$rawBlock = New-Ore '#58636B' '#8797A2' '#C8D5DD'
Save-Image $rawBlock (Join-Path $blockRoot 'raw_silver_block.png')

$ring = New-Canvas 16 16
foreach ($point in @(@(6,4),@(7,3),@(8,3),@(9,4),@(5,5),@(10,5),@(4,6),@(11,6),@(4,7),@(11,7),@(5,8),@(10,8),@(6,9),@(7,10),@(8,10),@(9,9))) {
    Set-Pixel $ring $point[0] $point[1] '#D89C2B'
}
Fill-Rect $ring 7 1 2 2 '#D46CFF'
Set-Pixel $ring 7 1 '#F4C6FF'
Save-Image $ring (Join-Path $itemRoot 'wedding_ring.png')

$sourceEquipment = Join-Path $equipmentRoot 'humanoid\delvealloy.png'
foreach ($layer in @('humanoid','humanoid_baby','humanoid_leggings')) {
    $source = Join-Path $equipmentRoot "$layer\werewolf_hunter_silvered.png"
    $gobliniteTarget = Join-Path $equipmentRoot "$layer\delvealloy.png"
    Recolor-Equipment $source (Join-Path $equipmentRoot "$layer\silver.png") 'silver'
    $temporary = Join-Path $equipmentRoot "$layer\delvealloy.generated.png"
    Recolor-Equipment $source $temporary 'goblinite'
    Move-Item -LiteralPath $temporary -Destination $gobliniteTarget -Force
}
