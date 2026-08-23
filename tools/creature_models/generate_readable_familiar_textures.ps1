Add-Type -AssemblyName System.Drawing

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$entityTextureDirectory = Join-Path $repositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function New-Color([string] $hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function New-Texture([int] $width, [int] $height) {
    $bitmap = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $bitmap.SetResolution(96.0, 96.0)
    return $bitmap
}

function Fill-Rectangle(
    [System.Drawing.Bitmap] $bitmap,
    [int] $x,
    [int] $y,
    [int] $width,
    [int] $height,
    [System.Drawing.Color] $color
) {
    if ($width -le 0 -or $height -le 0) {
        return
    }
    for ($pixelY = $y; $pixelY -lt $y + $height; $pixelY++) {
        for ($pixelX = $x; $pixelX -lt $x + $width; $pixelX++) {
            $bitmap.SetPixel($pixelX, $pixelY, $color)
        }
    }
}

function Paint-Cube(
    [System.Drawing.Bitmap] $bitmap,
    [int] $u,
    [int] $v,
    [int] $sizeX,
    [int] $sizeY,
    [int] $sizeZ,
    [System.Drawing.Color] $top,
    [System.Drawing.Color] $bottom,
    [System.Drawing.Color] $side,
    [System.Drawing.Color] $front,
    [System.Drawing.Color] $back
) {
    Fill-Rectangle $bitmap ($u + $sizeZ) $v $sizeX $sizeZ $top
    Fill-Rectangle $bitmap ($u + $sizeZ + $sizeX) $v $sizeX $sizeZ $bottom
    Fill-Rectangle $bitmap $u ($v + $sizeZ) $sizeZ $sizeY $side
    Fill-Rectangle $bitmap ($u + $sizeZ) ($v + $sizeZ) $sizeX $sizeY $front
    Fill-Rectangle $bitmap ($u + $sizeZ + $sizeX) ($v + $sizeZ) $sizeZ $sizeY $side
    Fill-Rectangle $bitmap ($u + (2 * $sizeZ) + $sizeX) ($v + $sizeZ) $sizeX $sizeY $back
}

function Paint-FrogFoot(
    [System.Drawing.Bitmap] $bitmap,
    [int] $x,
    [int] $y,
    [System.Drawing.Color] $color
) {
    Fill-Rectangle $bitmap ($x + 2) ($y + 3) 4 4 $color
    Fill-Rectangle $bitmap ($x + 3) $y 2 4 $color
    Fill-Rectangle $bitmap $x ($y + 1) 4 2 $color
    Fill-Rectangle $bitmap ($x + 4) ($y + 1) 4 2 $color
}

function Save-Texture([System.Drawing.Bitmap] $bitmap, [string] $name) {
    $path = Join-Path $entityTextureDirectory $name
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

$catShadow = New-Color '#17151C'
$catBase = New-Color '#27232D'
$catSide = New-Color '#302B37'
$catTop = New-Color '#3B3542'
$catMuzzle = New-Color '#B9B0A0'
$catMuzzleShadow = New-Color '#8D8579'
$catGold = New-Color '#C78B2C'
$catGoldLight = New-Color '#F0B94A'
$catEye = New-Color '#F5A623'
$catPupil = New-Color '#22150B'
$catEar = New-Color '#8E625D'

$cat = New-Texture 64 32
Paint-Cube $cat 0 0 5 4 5 $catTop $catShadow $catSide $catBase $catBase
Paint-Cube $cat 0 24 3 2 2 $catMuzzle $catMuzzleShadow $catMuzzleShadow $catMuzzle $catMuzzleShadow
Paint-Cube $cat 0 10 1 1 2 $catTop $catShadow $catSide $catEar $catBase
Paint-Cube $cat 6 10 1 1 2 $catTop $catShadow $catSide $catEar $catBase
Paint-Cube $cat 20 0 4 16 6 $catTop $catShadow $catSide $catBase $catBase
Paint-Cube $cat 0 15 1 8 1 $catTop $catShadow $catSide $catBase $catBase
Paint-Cube $cat 4 15 1 8 1 $catTop $catShadow $catSide $catBase $catBase
Paint-Cube $cat 8 13 2 6 2 $catTop $catShadow $catSide $catBase $catBase
Paint-Cube $cat 40 0 2 10 2 $catTop $catShadow $catSide $catBase $catBase

Fill-Rectangle $cat 6 6 1 1 $catEye
Fill-Rectangle $cat 8 6 1 1 $catEye
Fill-Rectangle $cat 6 7 1 1 $catPupil
Fill-Rectangle $cat 8 7 1 1 $catPupil
Fill-Rectangle $cat 3 26 1 1 $catPupil
Fill-Rectangle $cat 20 6 6 2 $catGold
Fill-Rectangle $cat 26 6 4 2 $catGoldLight
Fill-Rectangle $cat 30 6 6 2 $catGold
Fill-Rectangle $cat 36 6 4 2 $catGold
Fill-Rectangle $cat 40 10 8 2 $catMuzzle
Fill-Rectangle $cat 8 19 8 2 $catMuzzle
Save-Texture $cat 'familiar_cat.png'

$toadShadow = New-Color '#30371D'
$toadBase = New-Color '#4A572B'
$toadSide = New-Color '#596735'
$toadTop = New-Color '#6D7A40'
$toadBelly = New-Color '#C2B27A'
$toadBellyShadow = New-Color '#9B8C5E'
$toadEye = New-Color '#D98B28'
$toadEyeLight = New-Color '#F1B547'
$toadPupil = New-Color '#15150E'
$toadMouth = New-Color '#282816'

$toad = New-Texture 48 48
Paint-Cube $toad 3 1 7 3 9 $toadTop $toadBellyShadow $toadSide $toadBelly $toadBase
Paint-Cube $toad 0 13 7 3 9 $toadTop $toadBellyShadow $toadSide $toadBase $toadBase
Paint-Cube $toad 0 0 3 2 3 $toadTop $toadShadow $toadSide $toadEye $toadBase
Paint-Cube $toad 0 5 3 2 3 $toadTop $toadShadow $toadSide $toadEye $toadBase
Paint-Cube $toad 26 5 7 2 3 $toadBelly $toadBellyShadow $toadBellyShadow $toadBelly $toadBellyShadow
Paint-Cube $toad 0 32 2 3 3 $toadTop $toadShadow $toadSide $toadBase $toadBase
Paint-Cube $toad 0 38 2 3 3 $toadTop $toadShadow $toadSide $toadBase $toadBase
Paint-Cube $toad 14 25 3 3 4 $toadTop $toadShadow $toadSide $toadBase $toadBase
Paint-Cube $toad 0 25 3 3 4 $toadTop $toadShadow $toadSide $toadBase $toadBase

Fill-Rectangle $toad 3 3 3 2 $toadEye
Fill-Rectangle $toad 4 3 1 1 $toadEyeLight
Fill-Rectangle $toad 4 4 1 1 $toadPupil
Fill-Rectangle $toad 3 8 3 2 $toadEye
Fill-Rectangle $toad 4 8 1 1 $toadEyeLight
Fill-Rectangle $toad 4 9 1 1 $toadPupil
Fill-Rectangle $toad 9 23 7 1 $toadMouth
Fill-Rectangle $toad 9 24 7 1 $toadBelly
Paint-FrogFoot $toad 10 32 $toadSide
Paint-FrogFoot $toad 18 32 $toadSide
Paint-FrogFoot $toad 26 32 $toadSide
Paint-FrogFoot $toad 34 32 $toadSide
Paint-FrogFoot $toad 10 40 $toadSide
Paint-FrogFoot $toad 18 40 $toadSide
Paint-FrogFoot $toad 26 40 $toadSide
Paint-FrogFoot $toad 34 40 $toadSide
Save-Texture $toad 'toad.png'
