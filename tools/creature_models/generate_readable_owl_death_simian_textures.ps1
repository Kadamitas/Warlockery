Add-Type -AssemblyName System.Drawing

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$entityTextureDirectory = Join-Path $repositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function New-Color([string] $hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function New-Texture {
    return [System.Drawing.Bitmap]::new(128, 128, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
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
    if ($x -lt 0 -or $y -lt 0 -or $x + $width -gt $bitmap.Width -or $y + $height -gt $bitmap.Height) {
        throw "Texture rectangle exceeds atlas: $x,$y $width x $height"
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

function Save-Texture([System.Drawing.Bitmap] $bitmap, [string] $name) {
    $path = Join-Path $entityTextureDirectory $name
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

$brownShadow = New-Color '#2C211B'
$brownBase = New-Color '#4A3527'
$brownSide = New-Color '#5B4331'
$brownTop = New-Color '#71553B'
$cream = New-Color '#C6B489'
$creamShade = New-Color '#9C8963'
$gold = New-Color '#E1A837'
$black = New-Color '#17130F'
$beak = New-Color '#B87324'

$owl = New-Texture
Paint-Cube $owl 0 0 8 9 6 $brownTop $brownShadow $brownSide $brownBase $brownBase
Paint-Cube $owl 28 0 6 7 1 $cream $creamShade $creamShade $cream $creamShade
Paint-Cube $owl 48 0 8 7 7 $brownTop $brownShadow $brownSide $brownBase $brownBase
Paint-Cube $owl 78 0 8 6 1 $cream $creamShade $creamShade $cream $creamShade
Paint-Cube $owl 96 0 2 2 1 $gold $black $black $gold $black
Paint-Cube $owl 104 0 2 2 1 $gold $black $black $gold $black
Paint-Cube $owl 112 0 2 3 2 $beak $brownShadow $beak $beak $brownShadow
Paint-Cube $owl 0 28 2 8 5 $brownTop $brownShadow $brownSide $brownBase $brownShadow
Paint-Cube $owl 28 28 2 8 5 $brownTop $brownShadow $brownSide $brownBase $brownShadow
Paint-Cube $owl 54 28 2 3 2 $beak $brownShadow $beak $beak $brownShadow
Paint-Cube $owl 62 28 2 3 2 $beak $brownShadow $beak $beak $brownShadow
Paint-Cube $owl 70 28 3 1 4 $beak $brownShadow $beak $beak $brownShadow
Paint-Cube $owl 88 28 5 2 6 $brownTop $brownShadow $brownSide $brownBase $brownShadow
Fill-Rectangle $owl 98 2 1 1 $black
Fill-Rectangle $owl 106 2 1 1 $black
Save-Texture $owl 'owl.png'

$robeBlack = New-Color '#15121B'
$robeBase = New-Color '#241D30'
$robeSide = New-Color '#302640'
$robeTop = New-Color '#42344F'
$robeEdge = New-Color '#554266'
$bone = New-Color '#C8C0A5'
$boneShade = New-Color '#8F8875'
$eyeBlue = New-Color '#50D9F4'
$wood = New-Color '#55402D'
$steel = New-Color '#A6ADB5'
$steelShade = New-Color '#68717B'

$death = New-Texture
Paint-Cube $death 0 0 8 10 5 $robeTop $robeBlack $robeSide $robeBase $robeBlack
Paint-Cube $death 28 0 10 3 6 $robeTop $robeBlack $robeSide $robeEdge $robeBase
Paint-Cube $death 60 0 9 8 7 $robeTop $robeBlack $robeSide $robeBase $robeBlack
Paint-Cube $death 92 0 6 5 1 $bone $boneShade $boneShade $bone $boneShade
Paint-Cube $death 110 0 2 1 1 $eyeBlue $robeBlack $eyeBlue $eyeBlue $robeBlack
Paint-Cube $death 116 0 2 1 1 $eyeBlue $robeBlack $eyeBlue $eyeBlue $robeBlack
Paint-Cube $death 116 8 2 3 2 $steel $steelShade $steelShade $steel $steelShade
Paint-Cube $death 0 24 5 3 5 $robeTop $robeBlack $robeSide $robeEdge $robeBase
Paint-Cube $death 24 24 4 11 4 $robeTop $robeBlack $robeSide $robeBase $robeBlack
Paint-Cube $death 48 24 1 25 1 $wood $wood $wood $wood $wood
Paint-Cube $death 56 24 8 2 1 $steel $steelShade $steelShade $steel $steelShade
Paint-Cube $death 76 24 5 2 1 $steel $steelShade $steelShade $steel $steelShade
Paint-Cube $death 80 24 3 5 3 $robeTop $robeBlack $robeSide $eyeBlue $robeBlack
Paint-Cube $death 90 24 10 6 7 $robeTop $robeBlack $robeSide $robeBase $robeBlack
Paint-Cube $death 0 50 6 10 6 $robeTop $robeBlack $robeSide $robeBase $robeBlack
Paint-Cube $death 30 50 6 10 6 $robeTop $robeBlack $robeSide $robeBase $robeBlack
Paint-Cube $death 60 50 11 7 7 $robeTop $robeBlack $robeSide $robeEdge $robeBase
Paint-Cube $death 116 50 1 7 1 $steel $steelShade $steelShade $steel $steelShade
Fill-Rectangle $death 94 3 2 2 $robeBlack
Fill-Rectangle $death 97 3 2 2 $robeBlack
Fill-Rectangle $death 96 5 1 1 $boneShade
Save-Texture $death 'death.png'

$furShadow = New-Color '#211A18'
$furBase = New-Color '#3B2C27'
$furSide = New-Color '#4C3931'
$furTop = New-Color '#60483A'
$skin = New-Color '#A47D5A'
$skinShade = New-Color '#76543E'
$eye = New-Color '#15110F'
$storm = New-Color '#4FD6E8'
$stormShade = New-Color '#257D98'

$simian = New-Texture
Paint-Cube $simian 0 0 6 5 5 $furTop $furShadow $furSide $furBase $furShadow
Paint-Cube $simian 22 0 4 3 3 $skin $skinShade $skinShade $skin $skinShade
Paint-Cube $simian 38 0 2 3 1 $skin $skinShade $skinShade $skin $skinShade
Paint-Cube $simian 48 0 2 3 1 $skin $skinShade $skinShade $skin $skinShade
Paint-Cube $simian 58 0 4 1 3 $storm $stormShade $stormShade $storm $stormShade
Paint-Cube $simian 0 18 6 7 4 $furTop $furShadow $furSide $furBase $furShadow
Paint-Cube $simian 26 18 7 2 5 $storm $stormShade $stormShade $storm $stormShade
Paint-Cube $simian 48 18 3 6 3 $furTop $furShadow $furSide $furBase $furShadow
Paint-Cube $simian 66 18 3 6 3 $furTop $furShadow $furSide $furBase $furShadow
Paint-Cube $simian 84 18 4 2 4 $skin $skinShade $skinShade $skin $skinShade
Paint-Cube $simian 0 38 3 4 3 $furTop $furShadow $furSide $furBase $furShadow
Paint-Cube $simian 18 38 2 3 2 $furTop $furShadow $furSide $furBase $furShadow
Paint-Cube $simian 30 38 3 2 5 $skin $skinShade $skinShade $skin $skinShade
Paint-Cube $simian 50 38 2 5 2 $furTop $furShadow $furSide $furBase $furShadow
Paint-Cube $simian 62 38 2 5 2 $furTop $furShadow $furSide $furBase $furShadow
Paint-Cube $simian 74 38 2 4 2 $furTop $furShadow $furSide $furBase $furShadow
Fill-Rectangle $simian 6 6 1 1 $eye
Fill-Rectangle $simian 9 6 1 1 $eye
Fill-Rectangle $simian 25 5 2 1 $furShadow
Save-Texture $simian 'storm_simian.png'
