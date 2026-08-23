param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

. (Join-Path $PSScriptRoot 'common.ps1')

$entityTextureRoot = Join-Path $RepositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function Get-VampireColor {
    param([Parameter(Mandatory = $true)][string]$Hex)
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function Set-CuboidFaces {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$U,
        [Parameter(Mandatory = $true)][int]$V,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][int]$Depth,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Front,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Side,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Top,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Bottom,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Back
    )

    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth) -Y $V -Width $Width -Height $Depth -Color $Top
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth + $Width) -Y $V -Width $Width -Height $Depth -Color $Bottom
    Set-AtlasRectangle -Atlas $Atlas -X $U -Y ($V + $Depth) -Width $Depth -Height $Height -Color $Side
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth) -Y ($V + $Depth) -Width $Width -Height $Height -Color $Front
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth + $Width) -Y ($V + $Depth) -Width $Depth -Height $Height -Color $Side
    Set-AtlasRectangle -Atlas $Atlas -X ($U + (2 * $Depth) + $Width) -Y ($V + $Depth) -Width $Width -Height $Height -Color $Back
}

function Set-CuboidSideBand {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$U,
        [Parameter(Mandatory = $true)][int]$V,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Depth,
        [Parameter(Mandatory = $true)][int]$StartRow,
        [Parameter(Mandatory = $true)][int]$BandHeight,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Color
    )

    $row = $V + $Depth + $StartRow
    Set-AtlasRectangle -Atlas $Atlas -X $U -Y $row -Width $Depth -Height $BandHeight -Color $Color
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth) -Y $row -Width $Width -Height $BandHeight -Color $Color
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth + $Width) -Y $row -Width $Depth -Height $BandHeight -Color $Color
    Set-AtlasRectangle -Atlas $Atlas -X ($U + (2 * $Depth) + $Width) -Y $row -Width $Width -Height $BandHeight -Color $Color
}

$skin = Get-VampireColor '#E4DAD6'
$skinShade = Get-VampireColor '#B9A7A4'
$skinLight = Get-VampireColor '#F3EAE5'
$hair = Get-VampireColor '#10131A'
$hairSide = Get-VampireColor '#080B11'
$hairSheen = Get-VampireColor '#23343C'
$eyeRed = Get-VampireColor '#D13A4E'
$mouth = Get-VampireColor '#6C2534'
$pearl = Get-VampireColor '#E8E5D7'
$pearlShade = Get-VampireColor '#AEBDB8'
$coral = Get-VampireColor '#C85857'
$deepTeal = Get-VampireColor '#0B2C38'
$tideTeal = Get-VampireColor '#155164'
$abyss = Get-VampireColor '#07131C'
$black = Get-VampireColor '#090D14'
$wine = Get-VampireColor '#681C2E'
$wineShade = Get-VampireColor '#3D1220'
$boot = Get-VampireColor '#05090E'

function New-VampireBase {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Color]$BodyFront,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$BodySide
    )

    $atlas = New-PixelAtlas -Width 128 -Height 128
    Set-CuboidFaces -Atlas $atlas -U 0 -V 0 -Width 8 -Height 8 -Depth 8 `
        -Front $skin -Side $skinShade -Top $skinLight -Bottom $skinShade -Back $skinShade
    Set-CuboidFaces -Atlas $atlas -U 32 -V 0 -Width 8 -Height 12 -Depth 4 `
        -Front $BodyFront -Side $BodySide -Top $tideTeal -Bottom $abyss -Back $BodySide
    foreach ($u in @(0, 16)) {
        Set-CuboidFaces -Atlas $atlas -U $u -V 20 -Width 4 -Height 12 -Depth 4 `
            -Front $BodySide -Side $abyss -Top $tideTeal -Bottom $skinShade -Back $BodySide
        Set-CuboidSideBand -Atlas $atlas -U $u -V 20 -Width 4 -Depth 4 -StartRow 9 -BandHeight 3 -Color $skin
    }
    foreach ($u in @(32, 48)) {
        Set-CuboidFaces -Atlas $atlas -U $u -V 20 -Width 4 -Height 12 -Depth 4 `
            -Front $abyss -Side $black -Top $BodySide -Bottom $boot -Back $black
        Set-CuboidSideBand -Atlas $atlas -U $u -V 20 -Width 4 -Depth 4 -StartRow 8 -BandHeight 4 -Color $boot
    }
    Set-CuboidFaces -Atlas $atlas -U 92 -V 52 -Width 2 -Height 2 -Depth 1 `
        -Front $pearl -Side $pearlShade -Top $skinLight -Bottom $pearlShade -Back $pearlShade

    return $atlas
}

$masculine = New-VampireBase -BodyFront $deepTeal -BodySide $abyss
try {
    Set-CuboidFaces -Atlas $masculine -U 0 -V 40 -Width 8 -Height 3 -Depth 8 `
        -Front $hair -Side $hairSide -Top $hairSheen -Bottom $hairSide -Back $hairSide
    Set-CuboidFaces -Atlas $masculine -U 32 -V 40 -Width 9 -Height 3 -Depth 5 `
        -Front $deepTeal -Side $abyss -Top $tideTeal -Bottom $abyss -Back $abyss
    Set-CuboidFaces -Atlas $masculine -U 60 -V 40 -Width 8 -Height 10 -Depth 1 `
        -Front $deepTeal -Side $abyss -Top $tideTeal -Bottom $coral -Back $abyss

    Set-AtlasRectangle -Atlas $masculine -X 9 -Y 10 -Width 2 -Height 1 -Color $hair
    Set-AtlasRectangle -Atlas $masculine -X 13 -Y 10 -Width 2 -Height 1 -Color $hair
    Set-AtlasPixel -Atlas $masculine -X 10 -Y 11 -Color $eyeRed
    Set-AtlasPixel -Atlas $masculine -X 13 -Y 11 -Color $eyeRed
    Set-AtlasPixel -Atlas $masculine -X 11 -Y 13 -Color $skinShade
    Set-AtlasRectangle -Atlas $masculine -X 10 -Y 14 -Width 4 -Height 1 -Color $mouth
    Set-AtlasPixel -Atlas $masculine -X 10 -Y 14 -Color $pearl
    Set-AtlasPixel -Atlas $masculine -X 13 -Y 14 -Color $pearl
    Set-AtlasRectangle -Atlas $masculine -X 11 -Y 15 -Width 2 -Height 1 -Color $coral

    Set-AtlasRectangle -Atlas $masculine -X 38 -Y 5 -Width 4 -Height 10 -Color $wine
    Set-AtlasRectangle -Atlas $masculine -X 39 -Y 4 -Width 2 -Height 3 -Color $pearl
    Set-AtlasPixel -Atlas $masculine -X 37 -Y 6 -Color $tideTeal
    Set-AtlasPixel -Atlas $masculine -X 42 -Y 6 -Color $tideTeal
    Set-AtlasPixel -Atlas $masculine -X 37 -Y 7 -Color $tideTeal
    Set-AtlasPixel -Atlas $masculine -X 42 -Y 7 -Color $tideTeal
    foreach ($y in @(8, 11, 14)) {
        Set-AtlasPixel -Atlas $masculine -X 40 -Y $y -Color $pearl
    }
    Set-AtlasRectangle -Atlas $masculine -X 36 -Y 13 -Width 2 -Height 1 -Color $coral
    Set-AtlasRectangle -Atlas $masculine -X 42 -Y 13 -Width 2 -Height 1 -Color $coral
    Set-AtlasRectangle -Atlas $masculine -X 62 -Y 49 -Width 8 -Height 1 -Color $coral
    Save-PixelAtlas -Atlas $masculine -Path (Join-Path $entityTextureRoot 'vampire_masculine.png')
}
finally {
    $masculine.Dispose()
}

$feminine = New-VampireBase -BodyFront $wineShade -BodySide $black
try {
    Set-CuboidFaces -Atlas $feminine -U 0 -V 40 -Width 8 -Height 4 -Depth 8 `
        -Front $hair -Side $hairSide -Top $hairSheen -Bottom $hairSide -Back $hairSide
    Set-CuboidFaces -Atlas $feminine -U 32 -V 52 -Width 8 -Height 12 -Depth 1 `
        -Front $hair -Side $hairSide -Top $hairSheen -Bottom $hairSide -Back $hairSide
    foreach ($u in @(50, 100)) {
        Set-CuboidFaces -Atlas $feminine -U $u -V 52 -Width 2 -Height 11 -Depth 1 `
            -Front $hair -Side $hairSide -Top $hairSheen -Bottom $hairSide -Back $hairSide
    }
    Set-CuboidFaces -Atlas $feminine -U 60 -V 52 -Width 10 -Height 12 -Depth 5 `
        -Front $black -Side $abyss -Top $wine -Bottom $deepTeal -Back $abyss

    Set-AtlasRectangle -Atlas $feminine -X 9 -Y 10 -Width 2 -Height 1 -Color $hair
    Set-AtlasRectangle -Atlas $feminine -X 13 -Y 10 -Width 2 -Height 1 -Color $hair
    Set-AtlasPixel -Atlas $feminine -X 10 -Y 11 -Color $eyeRed
    Set-AtlasPixel -Atlas $feminine -X 13 -Y 11 -Color $eyeRed
    Set-AtlasPixel -Atlas $feminine -X 11 -Y 13 -Color $skinShade
    Set-AtlasRectangle -Atlas $feminine -X 11 -Y 14 -Width 2 -Height 1 -Color $coral
    Set-AtlasPixel -Atlas $feminine -X 10 -Y 14 -Color $pearl
    Set-AtlasPixel -Atlas $feminine -X 13 -Y 14 -Color $pearl

    Set-AtlasRectangle -Atlas $feminine -X 37 -Y 5 -Width 6 -Height 9 -Color $wine
    Set-AtlasRectangle -Atlas $feminine -X 39 -Y 4 -Width 2 -Height 2 -Color $skin
    Set-AtlasPixel -Atlas $feminine -X 38 -Y 5 -Color $pearl
    Set-AtlasPixel -Atlas $feminine -X 41 -Y 5 -Color $pearl
    Set-AtlasRectangle -Atlas $feminine -X 38 -Y 11 -Width 4 -Height 2 -Color $black
    Set-AtlasRectangle -Atlas $feminine -X 37 -Y 14 -Width 6 -Height 2 -Color $deepTeal
    Set-AtlasRectangle -Atlas $feminine -X 68 -Y 57 -Width 4 -Height 10 -Color $wineShade
    Set-AtlasRectangle -Atlas $feminine -X 66 -Y 66 -Width 8 -Height 1 -Color $coral
    Set-AtlasRectangle -Atlas $feminine -X 65 -Y 67 -Width 10 -Height 2 -Color $tideTeal
    Save-PixelAtlas -Atlas $feminine -Path (Join-Path $entityTextureRoot 'vampire_feminine.png')
}
finally {
    $feminine.Dispose()
}
