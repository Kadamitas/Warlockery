param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

. (Join-Path $PSScriptRoot 'common.ps1')

function New-Color {
    param([Parameter(Mandatory = $true)][string]$Hex)
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function Set-CuboidTexture {
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

    Set-AtlasRectangle -Atlas $Atlas -X $U -Y ($V + $Depth) -Width $Depth -Height $Height -Color $Side
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth) -Y ($V + $Depth) -Width $Width -Height $Height -Color $Front
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth + $Width) -Y ($V + $Depth) -Width $Depth -Height $Height -Color $Side
    Set-AtlasRectangle -Atlas $Atlas -X ($U + (2 * $Depth) + $Width) -Y ($V + $Depth) -Width $Width -Height $Height -Color $Back
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth) -Y $V -Width $Width -Height $Depth -Color $Top
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth + $Width) -Y $V -Width $Width -Height $Depth -Color $Bottom
}

$skin = New-Color '#DCEFF2'
$skinShade = New-Color '#B8D4DC'
$hair = New-Color '#EEF9FA'
$hairLight = New-Color '#FFFFFF'
$hairShade = New-Color '#A9C8D2'
$hairDeep = New-Color '#7194A4'
$dressLight = New-Color '#B8F1F3'
$dress = New-Color '#69CDD6'
$dressShade = New-Color '#318C9D'
$deepBlue = New-Color '#193750'
$eye = New-Color '#15243B'
$iris = New-Color '#52E3EC'
$lip = New-Color '#704B70'
$mouth = New-Color '#171527'
$pearl = New-Color '#F5FFFF'

$atlas = New-PixelAtlas -Width 128 -Height 128
try {
    Set-CuboidTexture -Atlas $atlas -U 0 -V 0 -Width 7 -Height 8 -Depth 7 `
        -Front $skin -Side $skinShade -Top $hair -Bottom $skinShade -Back $hairShade
    Set-AtlasRectangle -Atlas $atlas -X 7 -Y 7 -Width 7 -Height 2 -Color $hair
    Set-AtlasPixel -Atlas $atlas -X 8 -Y 10 -Color $hairShade
    Set-AtlasPixel -Atlas $atlas -X 12 -Y 10 -Color $hairShade
    Set-AtlasPixel -Atlas $atlas -X 8 -Y 11 -Color $eye
    Set-AtlasPixel -Atlas $atlas -X 12 -Y 11 -Color $eye
    Set-AtlasPixel -Atlas $atlas -X 9 -Y 11 -Color $iris
    Set-AtlasPixel -Atlas $atlas -X 11 -Y 11 -Color $iris
    Set-AtlasPixel -Atlas $atlas -X 10 -Y 13 -Color $skinShade
    Set-AtlasRectangle -Atlas $atlas -X 9 -Y 14 -Width 3 -Height 1 -Color $lip
    Set-AtlasPixel -Atlas $atlas -X 10 -Y 14 -Color $mouth

    Set-CuboidTexture -Atlas $atlas -U 32 -V 0 -Width 3 -Height 2 -Depth 1 `
        -Front $mouth -Side $lip -Top $pearl -Bottom $mouth -Back $lip
    Set-AtlasRectangle -Atlas $atlas -X 33 -Y 1 -Width 3 -Height 1 -Color $pearl

    Set-CuboidTexture -Atlas $atlas -U 42 -V 0 -Width 8 -Height 3 -Depth 8 `
        -Front $hair -Side $hairShade -Top $hairLight -Bottom $hairDeep -Back $hairShade
    Set-AtlasRectangle -Atlas $atlas -X 50 -Y 8 -Width 8 -Height 1 -Color $hairLight
    Set-CuboidTexture -Atlas $atlas -U 0 -V 20 -Width 8 -Height 14 -Depth 2 `
        -Front $hairShade -Side $hairDeep -Top $hair -Bottom $hairDeep -Back $hair
    Set-AtlasRectangle -Atlas $atlas -X 2 -Y 22 -Width 3 -Height 14 -Color $hair
    Set-AtlasRectangle -Atlas $atlas -X 7 -Y 22 -Width 3 -Height 14 -Color $hairLight
    Set-CuboidTexture -Atlas $atlas -U 24 -V 20 -Width 2 -Height 13 -Depth 3 `
        -Front $hair -Side $hairShade -Top $hairLight -Bottom $hairDeep -Back $hairShade
    Set-AtlasRectangle -Atlas $atlas -X 27 -Y 24 -Width 1 -Height 11 -Color $hairLight
    Set-CuboidTexture -Atlas $atlas -U 36 -V 20 -Width 1 -Height 9 -Depth 1 `
        -Front $hairLight -Side $hairShade -Top $hairLight -Bottom $hairDeep -Back $hairShade

    Set-CuboidTexture -Atlas $atlas -U 0 -V 40 -Width 10 -Height 2 -Depth 4 `
        -Front $dressLight -Side $dressShade -Top $pearl -Bottom $dressShade -Back $dress
    Set-AtlasRectangle -Atlas $atlas -X 7 -Y 44 -Width 4 -Height 2 -Color $pearl
    Set-CuboidTexture -Atlas $atlas -U 80 -V 0 -Width 3 -Height 2 -Depth 3 `
        -Front $skin -Side $skinShade -Top $skin -Bottom $skinShade -Back $skinShade
    Set-CuboidTexture -Atlas $atlas -U 30 -V 40 -Width 8 -Height 7 -Depth 4 `
        -Front $dress -Side $dressShade -Top $dressLight -Bottom $dressShade -Back $dressShade
    Set-AtlasRectangle -Atlas $atlas -X 36 -Y 44 -Width 4 -Height 1 -Color $pearl
    Set-AtlasRectangle -Atlas $atlas -X 37 -Y 45 -Width 2 -Height 2 -Color $pearl
    Set-AtlasRectangle -Atlas $atlas -X 37 -Y 47 -Width 2 -Height 4 -Color $dressLight
    Set-CuboidTexture -Atlas $atlas -U 56 -V 40 -Width 6 -Height 2 -Depth 4 `
        -Front $deepBlue -Side $dressShade -Top $dress -Bottom $deepBlue -Back $deepBlue
    Set-AtlasRectangle -Atlas $atlas -X 62 -Y 44 -Width 2 -Height 2 -Color $dressLight

    Set-CuboidTexture -Atlas $atlas -U 0 -V 54 -Width 3 -Height 4 -Depth 3 `
        -Front $dressLight -Side $dressShade -Top $pearl -Bottom $dress -Back $dress
    Set-CuboidTexture -Atlas $atlas -U 14 -V 54 -Width 2 -Height 6 -Depth 2 `
        -Front $skin -Side $skinShade -Top $skin -Bottom $skinShade -Back $skinShade
    Set-CuboidTexture -Atlas $atlas -U 24 -V 54 -Width 2 -Height 3 -Depth 2 `
        -Front $skin -Side $skinShade -Top $skin -Bottom $skinShade -Back $skinShade

    Set-CuboidTexture -Atlas $atlas -U 0 -V 68 -Width 6 -Height 2 -Depth 4 `
        -Front $deepBlue -Side $dressShade -Top $dress -Bottom $deepBlue -Back $deepBlue
    Set-AtlasRectangle -Atlas $atlas -X 6 -Y 72 -Width 2 -Height 2 -Color $dressLight
    Set-CuboidTexture -Atlas $atlas -U 22 -V 68 -Width 8 -Height 6 -Depth 5 `
        -Front $dress -Side $dressShade -Top $dressLight -Bottom $dressShade -Back $dressShade
    Set-AtlasRectangle -Atlas $atlas -X 30 -Y 73 -Width 2 -Height 6 -Color $dressLight
    Set-CuboidTexture -Atlas $atlas -U 50 -V 68 -Width 10 -Height 5 -Depth 6 `
        -Front $dressShade -Side $deepBlue -Top $dress -Bottom $deepBlue -Back $deepBlue
    Set-AtlasRectangle -Atlas $atlas -X 59 -Y 74 -Width 4 -Height 5 -Color $dress
    Set-AtlasRectangle -Atlas $atlas -X 60 -Y 74 -Width 2 -Height 5 -Color $dressLight

    Set-CuboidTexture -Atlas $atlas -U 0 -V 82 -Width 1 -Height 10 -Depth 2 `
        -Front $hairLight -Side $hairShade -Top $pearl -Bottom $dressShade -Back $dressLight
    Set-AtlasRectangle -Atlas $atlas -X 2 -Y 85 -Width 1 -Height 8 -Color $dressLight
    Set-CuboidTexture -Atlas $atlas -U 8 -V 82 -Width 6 -Height 5 -Depth 4 `
        -Front $dressLight -Side $dressShade -Top $dress -Bottom $deepBlue -Back $dressShade
    Set-AtlasRectangle -Atlas $atlas -X 14 -Y 86 -Width 2 -Height 5 -Color $pearl
    Set-CuboidTexture -Atlas $atlas -U 30 -V 82 -Width 2 -Height 4 -Depth 3 `
        -Front $dress -Side $dressShade -Top $dressLight -Bottom $deepBlue -Back $dressShade

    $output = Join-Path $RepositoryRoot 'src\main\resources\assets\warlockery\textures\entity\banshee.png'
    Save-PixelAtlas -Atlas $atlas -Path $output
}
finally {
    $atlas.Dispose()
}

Write-Output 'Generated the readable Banshee atlas with a deliberate silver, pale-skin, and cyan palette.'
