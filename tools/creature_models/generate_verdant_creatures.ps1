param()

. (Join-Path $PSScriptRoot 'common.ps1')

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$entityTextureRoot = Join-Path $repositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function New-VerdantPalette {
    param(
        [Parameter(Mandatory = $true)][int[]]$Base,
        [Parameter(Mandatory = $true)][int[]]$Shade,
        [Parameter(Mandatory = $true)][int[]]$Highlight,
        [Parameter(Mandatory = $true)][int[]]$Accent
    )
    return @(
        [System.Drawing.Color]::FromArgb(255, $Base[0], $Base[1], $Base[2]),
        [System.Drawing.Color]::FromArgb(255, $Shade[0], $Shade[1], $Shade[2]),
        [System.Drawing.Color]::FromArgb(255, $Highlight[0], $Highlight[1], $Highlight[2]),
        [System.Drawing.Color]::FromArgb(255, $Accent[0], $Accent[1], $Accent[2])
    )
}

function Paint-VerdantFace {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$X,
        [Parameter(Mandatory = $true)][int]$Y,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][System.Drawing.Color[]]$Palette,
        [Parameter(Mandatory = $true)][int]$Seed
    )
    Set-AtlasRectangle -Atlas $Atlas -X $X -Y $Y -Width $Width -Height $Height -Color $Palette[0]
    Set-AtlasRectangle -Atlas $Atlas -X $X -Y $Y -Width $Width -Height 1 -Color $Palette[2]
    Set-AtlasRectangle -Atlas $Atlas -X $X -Y ($Y + $Height - 1) -Width $Width -Height 1 -Color $Palette[1]
    Set-AtlasRectangle -Atlas $Atlas -X $X -Y $Y -Width 1 -Height $Height -Color $Palette[2]
    Set-AtlasRectangle -Atlas $Atlas -X ($X + $Width - 1) -Y $Y -Width 1 -Height $Height -Color $Palette[1]
    if ($Width -gt 3 -and $Height -gt 3) {
        for ($row = 2; $row -lt $Height - 1; $row += 3 + ($Seed % 2)) {
            for ($column = 2 + (($row + $Seed) % 2); $column -lt $Width - 1; $column += 4 + ($Seed % 3)) {
                Set-AtlasPixel -Atlas $Atlas -X ($X + $column) -Y ($Y + $row) -Color $Palette[3]
            }
        }
    }
}

function Paint-VerdantBoxUv {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][object[]]$Box,
        [Parameter(Mandatory = $true)][hashtable]$Palettes,
        [Parameter(Mandatory = $true)][int]$Seed
    )
    $u = [int]$Box[0]
    $v = [int]$Box[1]
    $width = [int]$Box[2]
    $height = [int]$Box[3]
    $depth = [int]$Box[4]
    $palette = [System.Drawing.Color[]]$Palettes[[string]$Box[5]]
    Paint-VerdantFace -Atlas $Atlas -X ($u + $depth) -Y $v -Width $width -Height $depth -Palette $palette -Seed ($Seed + 1)
    Paint-VerdantFace -Atlas $Atlas -X ($u + $depth + $width) -Y $v -Width $width -Height $depth -Palette $palette -Seed ($Seed + 2)
    Paint-VerdantFace -Atlas $Atlas -X $u -Y ($v + $depth) -Width $depth -Height $height -Palette $palette -Seed ($Seed + 3)
    Paint-VerdantFace -Atlas $Atlas -X ($u + $depth) -Y ($v + $depth) -Width $width -Height $height -Palette $palette -Seed ($Seed + 4)
    Paint-VerdantFace -Atlas $Atlas -X ($u + $depth + $width) -Y ($v + $depth) -Width $depth -Height $height -Palette $palette -Seed ($Seed + 5)
    Paint-VerdantFace -Atlas $Atlas -X ($u + (2 * $depth) + $width) -Y ($v + $depth) -Width $width -Height $height -Palette $palette -Seed ($Seed + 6)
}

function Write-VerdantAtlas {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][object[]]$Boxes,
        [Parameter(Mandatory = $true)][hashtable]$Palettes
    )
    $atlas = New-PixelAtlas -Width $Width -Height $Height
    try {
        for ($index = 0; $index -lt $Boxes.Count; $index++) {
            Paint-VerdantBoxUv -Atlas $atlas -Box $Boxes[$index] -Palettes $Palettes -Seed $index
        }
        Save-PixelAtlas -Atlas $atlas -Path (Join-Path $entityTextureRoot "$Name.png")
    }
    finally {
        $atlas.Dispose()
    }
}

$entPalettes = @{
    bark = New-VerdantPalette @(74, 55, 38) @(37, 31, 25) @(121, 94, 58) @(91, 110, 67)
    lichen = New-VerdantPalette @(87, 104, 61) @(44, 58, 39) @(137, 145, 92) @(166, 150, 76)
    hollow = New-VerdantPalette @(151, 91, 30) @(68, 38, 18) @(224, 148, 47) @(247, 188, 65)
}
$entBoxes = @(
    @(0,0,16,11,10,'bark'), @(54,0,14,18,10,'bark'), @(104,0,8,8,2,'hollow'), @(126,0,3,3,2,'hollow'),
    @(140,0,8,10,2,'bark'), @(164,0,10,6,2,'bark'), @(190,0,8,8,9,'bark'), @(226,0,5,15,5,'bark'),
    @(0,26,4,16,4,'bark'), @(18,26,7,7,4,'bark'), @(42,26,2,9,2,'bark'), @(52,26,2,8,2,'bark'),
    @(62,26,8,7,8,'bark'), @(96,26,6,13,6,'bark'), @(122,26,4,17,4,'bark'), @(140,26,7,8,4,'bark'),
    @(164,26,2,10,2,'bark'), @(174,26,2,7,2,'bark'), @(184,26,10,8,8,'bark'), @(222,26,3,13,3,'bark'),
    @(236,26,2,9,2,'bark'), @(0,50,3,11,3,'bark'), @(14,50,2,8,2,'bark'), @(24,50,3,12,3,'bark'),
    @(38,50,7,12,7,'bark'), @(68,50,10,4,15,'bark'), @(120,50,3,3,9,'bark'), @(146,50,8,12,8,'bark'),
    @(180,50,12,4,13,'bark'), @(0,76,3,3,9,'bark'), @(28,92,14,7,12,'lichen'), @(0,112,10,5,9,'lichen'),
    @(84,92,13,6,11,'lichen'), @(42,112,8,4,8,'lichen'), @(136,92,12,6,10,'lichen'), @(78,112,7,4,7,'lichen')
)
Write-VerdantAtlas -Name 'ent' -Width 256 -Height 128 -Boxes $entBoxes -Palettes $entPalettes

$bramblePalettes = @{
    briar = New-VerdantPalette @(58, 43, 40) @(27, 24, 25) @(102, 77, 59) @(92, 108, 68)
    vine = New-VerdantPalette @(85, 100, 62) @(42, 54, 39) @(129, 139, 84) @(161, 139, 74)
    sap = New-VerdantPalette @(188, 169, 116) @(89, 73, 49) @(231, 218, 159) @(212, 116, 66)
    bloom = New-VerdantPalette @(114, 42, 54) @(48, 25, 35) @(167, 68, 76) @(204, 101, 72)
}
$brambleBoxes = @(
    @(0,0,20,10,24,'briar'), @(90,0,24,7,26,'briar'), @(192,0,4,8,4,'briar'), @(210,0,3,7,3,'briar'),
    @(224,0,3,6,3,'briar'), @(0,36,12,8,14,'briar'), @(54,36,4,6,2,'sap'), @(70,36,3,4,14,'briar'),
    @(106,36,3,9,3,'briar'), @(120,36,3,4,14,'briar'), @(156,36,3,8,3,'briar'), @(170,36,4,4,20,'vine'),
    @(152,84,4,4,20,'vine'), @(0,62,7,9,8,'briar'), @(32,62,6,4,12,'briar'), @(70,62,3,5,3,'bloom'),
    @(84,62,7,9,8,'briar'), @(116,62,6,4,12,'briar'), @(154,62,3,5,3,'bloom'), @(168,62,4,7,5,'vine'),
    @(188,62,4,3,7,'vine'), @(212,62,4,7,5,'vine'), @(232,62,4,3,7,'vine'), @(0,84,8,8,9,'briar'),
    @(36,84,7,3,10,'briar'), @(72,84,8,8,9,'briar'), @(108,84,7,3,10,'briar'), @(202,84,8,10,8,'bloom'),
    @(0,108,6,7,6,'bloom')
)
Write-VerdantAtlas -Name 'bramble_colossus' -Width 256 -Height 128 -Boxes $brambleBoxes -Palettes $bramblePalettes

$pursuerPalettes = @{
    briar = New-VerdantPalette @(63, 42, 55) @(29, 25, 34) @(104, 73, 78) @(127, 85, 64)
    horn = New-VerdantPalette @(148, 107, 51) @(70, 49, 31) @(204, 157, 76) @(107, 124, 79)
    binding = New-VerdantPalette @(54, 100, 96) @(31, 55, 59) @(91, 144, 132) @(164, 132, 68)
    eye = New-VerdantPalette @(177, 43, 31) @(76, 23, 29) @(245, 91, 44) @(255, 164, 61)
}
$pursuerBoxes = @(
    @(0,0,8,12,6,'briar'), @(30,0,10,6,5,'briar'), @(62,0,10,2,7,'binding'), @(98,0,7,7,9,'briar'),
    @(132,0,4,2,1,'eye'), @(144,0,2,3,10,'horn'), @(170,0,2,2,8,'horn'), @(0,20,2,3,10,'horn'),
    @(26,20,2,2,8,'horn'), @(48,20,16,3,5,'horn'), @(92,20,3,9,3,'horn'), @(106,20,3,8,3,'horn'),
    @(120,20,4,12,4,'briar'), @(138,20,3,13,3,'horn'), @(152,20,4,12,4,'briar'), @(170,20,3,13,3,'horn'),
    @(0,40,5,8,5,'briar'), @(22,40,4,8,4,'briar'), @(40,40,6,3,10,'horn'), @(74,40,5,8,5,'briar'),
    @(96,40,4,8,4,'briar'), @(114,40,6,3,10,'horn'), @(148,40,5,4,5,'binding'), @(170,40,2,15,2,'briar'),
    @(180,40,2,13,2,'horn'), @(0,60,2,14,2,'briar'), @(10,60,2,12,2,'horn'), @(20,60,3,8,3,'horn'),
    @(34,60,3,7,3,'horn'), @(48,60,2,12,2,'briar'), @(58,60,2,12,2,'briar')
)
Write-VerdantAtlas -Name 'thorned_pursuer' -Width 192 -Height 128 -Boxes $pursuerBoxes -Palettes $pursuerPalettes
