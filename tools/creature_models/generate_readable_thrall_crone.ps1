param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

. (Join-Path $PSScriptRoot 'common.ps1')

$entityTextureRoot = Join-Path $RepositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function Get-ReadableColor {
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

$thrallSkin = Get-ReadableColor '#B9B9AE'
$thrallSkinShade = Get-ReadableColor '#858D87'
$thrallSkinLight = Get-ReadableColor '#D8D5C8'
$thrallHair = Get-ReadableColor '#293033'
$thrallEye = Get-ReadableColor '#52222A'
$thrallMouth = Get-ReadableColor '#654846'
$thrallTunic = Get-ReadableColor '#26383E'
$thrallTunicSide = Get-ReadableColor '#18292F'
$thrallTrouser = Get-ReadableColor '#1D282C'
$thrallBoot = Get-ReadableColor '#101619'
$thrallBinding = Get-ReadableColor '#66767B'
$thrallPearl = Get-ReadableColor '#DEDCCB'
$thrallCoral = Get-ReadableColor '#AF514F'

$thrall = New-PixelAtlas -Width 128 -Height 128
try {
    Set-CuboidFaces -Atlas $thrall -U 0 -V 0 -Width 7 -Height 7 -Depth 7 `
        -Front $thrallSkin -Side $thrallSkinShade -Top $thrallHair -Bottom $thrallSkinShade -Back $thrallHair
    Set-CuboidFaces -Atlas $thrall -U 28 -V 0 -Width 6 -Height 12 -Depth 4 `
        -Front $thrallTunic -Side $thrallTunicSide -Top $thrallBinding -Bottom $thrallTrouser -Back $thrallTunicSide
    Set-CuboidFaces -Atlas $thrall -U 48 -V 0 -Width 8 -Height 2 -Depth 5 `
        -Front $thrallBinding -Side $thrallTunicSide -Top $thrallPearl -Bottom $thrallBinding -Back $thrallTunicSide
    Set-CuboidFaces -Atlas $thrall -U 76 -V 0 -Width 6 -Height 1 -Depth 1 `
        -Front $thrallPearl -Side $thrallBinding -Top $thrallSkinLight -Bottom $thrallBinding -Back $thrallBinding
    Set-CuboidFaces -Atlas $thrall -U 90 -V 0 -Width 2 -Height 2 -Depth 1 `
        -Front $thrallCoral -Side $thrallTunicSide -Top $thrallCoral -Bottom $thrallTunicSide -Back $thrallTunicSide
    foreach ($u in @(0, 12)) {
        Set-CuboidFaces -Atlas $thrall -U $u -V 20 -Width 3 -Height 12 -Depth 3 `
            -Front $thrallTunicSide -Side $thrallTrouser -Top $thrallBinding -Bottom $thrallSkinShade -Back $thrallTunicSide
        Set-CuboidSideBand -Atlas $thrall -U $u -V 20 -Width 3 -Depth 3 -StartRow 9 -BandHeight 3 -Color $thrallSkin
    }
    foreach ($u in @(24, 36)) {
        Set-CuboidFaces -Atlas $thrall -U $u -V 20 -Width 3 -Height 12 -Depth 3 `
            -Front $thrallTrouser -Side $thrallTunicSide -Top $thrallTrouser -Bottom $thrallBoot -Back $thrallTunicSide
        Set-CuboidSideBand -Atlas $thrall -U $u -V 20 -Width 3 -Depth 3 -StartRow 8 -BandHeight 4 -Color $thrallBoot
    }

    Set-AtlasRectangle -Atlas $thrall -X 7 -Y 7 -Width 7 -Height 1 -Color $thrallHair
    Set-AtlasPixel -Atlas $thrall -X 8 -Y 9 -Color $thrallHair
    Set-AtlasPixel -Atlas $thrall -X 12 -Y 9 -Color $thrallHair
    Set-AtlasPixel -Atlas $thrall -X 8 -Y 10 -Color $thrallEye
    Set-AtlasPixel -Atlas $thrall -X 12 -Y 10 -Color $thrallEye
    Set-AtlasPixel -Atlas $thrall -X 10 -Y 11 -Color $thrallSkinShade
    Set-AtlasRectangle -Atlas $thrall -X 9 -Y 12 -Width 3 -Height 1 -Color $thrallMouth
    Set-AtlasRectangle -Atlas $thrall -X 32 -Y 8 -Width 6 -Height 1 -Color $thrallPearl
    Set-AtlasRectangle -Atlas $thrall -X 32 -Y 13 -Width 1 -Height 3 -Color $thrallCoral
    Save-PixelAtlas -Atlas $thrall -Path (Join-Path $entityTextureRoot 'blood_thrall.png')
}
finally {
    $thrall.Dispose()
}

$croneSkin = Get-ReadableColor '#B89B7C'
$croneSkinShade = Get-ReadableColor '#816A57'
$croneSkinLight = Get-ReadableColor '#D1B696'
$croneHair = Get-ReadableColor '#C9C7BD'
$croneHairShade = Get-ReadableColor '#77786F'
$croneEye = Get-ReadableColor '#273128'
$croneMouth = Get-ReadableColor '#70434D'
$croneDress = Get-ReadableColor '#30263B'
$croneDressShade = Get-ReadableColor '#1C1825'
$croneApron = Get-ReadableColor '#665644'
$croneShawl = Get-ReadableColor '#48533A'
$croneShawlLight = Get-ReadableColor '#687359'
$croneBoot = Get-ReadableColor '#211C1B'
$croneWood = Get-ReadableColor '#6E5433'
$croneWoodLight = Get-ReadableColor '#9A7847'
$croneStone = Get-ReadableColor '#777873'
$croneStoneLight = Get-ReadableColor '#A6A79F'
$croneHerb = Get-ReadableColor '#6F7A3E'
$croneWard = Get-ReadableColor '#D4A84B'

$crone = New-PixelAtlas -Width 128 -Height 128
try {
    Set-CuboidFaces -Atlas $crone -U 0 -V 0 -Width 7 -Height 7 -Depth 7 `
        -Front $croneSkin -Side $croneSkinShade -Top $croneHair -Bottom $croneSkinShade -Back $croneHairShade
    Set-CuboidFaces -Atlas $crone -U 0 -V 20 -Width 7 -Height 3 -Depth 7 `
        -Front $croneHair -Side $croneHairShade -Top $croneHair -Bottom $croneHairShade -Back $croneHairShade
    Set-CuboidFaces -Atlas $crone -U 28 -V 20 -Width 7 -Height 7 -Depth 1 `
        -Front $croneHair -Side $croneHairShade -Top $croneHair -Bottom $croneHairShade -Back $croneHairShade
    foreach ($u in @(44, 50)) {
        Set-CuboidFaces -Atlas $crone -U $u -V 20 -Width 2 -Height 6 -Depth 1 `
            -Front $croneHair -Side $croneHairShade -Top $croneHair -Bottom $croneHairShade -Back $croneHairShade
    }
    Set-CuboidFaces -Atlas $crone -U 56 -V 20 -Width 2 -Height 2 -Depth 2 `
        -Front $croneSkinShade -Side $croneSkinShade -Top $croneSkinLight -Bottom $croneSkinShade -Back $croneSkin
    Set-CuboidFaces -Atlas $crone -U 0 -V 40 -Width 10 -Height 11 -Depth 6 `
        -Front $croneDress -Side $croneDressShade -Top $croneApron -Bottom $croneDressShade -Back $croneDressShade
    Set-CuboidFaces -Atlas $crone -U 32 -V 40 -Width 8 -Height 11 -Depth 5 `
        -Front $croneDress -Side $croneDressShade -Top $croneShawl -Bottom $croneDressShade -Back $croneDressShade
    Set-CuboidFaces -Atlas $crone -U 64 -V 20 -Width 9 -Height 5 -Depth 6 `
        -Front $croneShawl -Side $croneDressShade -Top $croneShawlLight -Bottom $croneShawl -Back $croneDressShade
    foreach ($u in @(0, 12)) {
        Set-CuboidFaces -Atlas $crone -U $u -V 60 -Width 3 -Height 12 -Depth 3 `
            -Front $croneDress -Side $croneDressShade -Top $croneShawl -Bottom $croneSkinShade -Back $croneDressShade
        Set-CuboidSideBand -Atlas $crone -U $u -V 60 -Width 3 -Depth 3 -StartRow 9 -BandHeight 3 -Color $croneSkin
    }
    foreach ($u in @(24, 36)) {
        Set-CuboidFaces -Atlas $crone -U $u -V 60 -Width 3 -Height 4 -Depth 3 `
            -Front $croneBoot -Side $croneDressShade -Top $croneDress -Bottom $croneBoot -Back $croneDressShade
    }
    Set-CuboidFaces -Atlas $crone -U 48 -V 60 -Width 1 -Height 7 -Depth 1 `
        -Front $croneWood -Side $croneDressShade -Top $croneWoodLight -Bottom $croneWood -Back $croneDressShade
    Set-CuboidFaces -Atlas $crone -U 100 -V 60 -Width 4 -Height 1 -Depth 1 `
        -Front $croneWood -Side $croneDressShade -Top $croneWoodLight -Bottom $croneWood -Back $croneDressShade
    Set-CuboidFaces -Atlas $crone -U 56 -V 60 -Width 4 -Height 2 -Depth 3 `
        -Front $croneStone -Side $croneDressShade -Top $croneStoneLight -Bottom $croneStone -Back $croneDressShade
    Set-CuboidFaces -Atlas $crone -U 72 -V 60 -Width 1 -Height 6 -Depth 1 `
        -Front $croneWood -Side $croneDressShade -Top $croneWoodLight -Bottom $croneWood -Back $croneDressShade
    Set-CuboidFaces -Atlas $crone -U 76 -V 60 -Width 2 -Height 5 -Depth 2 `
        -Front $croneHerb -Side $croneDressShade -Top $croneWard -Bottom $croneHerb -Back $croneDressShade

    Set-AtlasRectangle -Atlas $crone -X 8 -Y 9 -Width 1 -Height 1 -Color $croneHairShade
    Set-AtlasRectangle -Atlas $crone -X 12 -Y 9 -Width 1 -Height 1 -Color $croneHairShade
    Set-AtlasPixel -Atlas $crone -X 8 -Y 10 -Color $croneEye
    Set-AtlasPixel -Atlas $crone -X 12 -Y 10 -Color $croneEye
    Set-AtlasPixel -Atlas $crone -X 10 -Y 11 -Color $croneSkinShade
    Set-AtlasRectangle -Atlas $crone -X 9 -Y 13 -Width 3 -Height 1 -Color $croneMouth
    Set-AtlasPixel -Atlas $crone -X 7 -Y 11 -Color $croneSkinShade
    Set-AtlasPixel -Atlas $crone -X 13 -Y 11 -Color $croneSkinShade
    Set-AtlasRectangle -Atlas $crone -X 6 -Y 48 -Width 10 -Height 1 -Color $croneApron
    Set-AtlasRectangle -Atlas $crone -X 8 -Y 49 -Width 6 -Height 7 -Color $croneApron
    Set-AtlasRectangle -Atlas $crone -X 70 -Y 27 -Width 7 -Height 1 -Color $croneShawlLight
    Set-AtlasPixel -Atlas $crone -X 78 -Y 64 -Color $croneWard
    Save-PixelAtlas -Atlas $crone -Path (Join-Path $entityTextureRoot 'hedge_crone.png')
}
finally {
    $crone.Dispose()
}
