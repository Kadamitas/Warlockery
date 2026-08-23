param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

. (Join-Path $PSScriptRoot 'common.ps1')

function New-Color {
    param([Parameter(Mandatory = $true)][string]$Hex)
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function New-Material {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Front,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Side,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Top,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Bottom,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Back
    )
    return @($Front, $Side, $Top, $Bottom, $Back)
}

function Set-CuboidTexture {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$U,
        [Parameter(Mandatory = $true)][int]$V,
        [Parameter(Mandatory = $true)][double]$Width,
        [Parameter(Mandatory = $true)][double]$Height,
        [Parameter(Mandatory = $true)][double]$Depth,
        [Parameter(Mandatory = $true)][System.Drawing.Color[]]$Material
    )

    $widthPixels = [int][Math]::Ceiling($Width)
    $heightPixels = [int][Math]::Ceiling($Height)
    $depthPixels = [int][Math]::Ceiling($Depth)
    Set-AtlasRectangle -Atlas $Atlas -X $U -Y ($V + $depthPixels) -Width $depthPixels -Height $heightPixels -Color $Material[1]
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $depthPixels) -Y ($V + $depthPixels) -Width $widthPixels -Height $heightPixels -Color $Material[0]
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $depthPixels + $widthPixels) -Y ($V + $depthPixels) -Width $depthPixels -Height $heightPixels -Color $Material[1]
    Set-AtlasRectangle -Atlas $Atlas -X ($U + (2 * $depthPixels) + $widthPixels) -Y ($V + $depthPixels) -Width $widthPixels -Height $heightPixels -Color $Material[4]
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $depthPixels) -Y $V -Width $widthPixels -Height $depthPixels -Color $Material[2]
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $depthPixels + $widthPixels) -Y $V -Width $widthPixels -Height $depthPixels -Color $Material[3]
}

function Set-ClanBoxes {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][object[]]$Boxes,
        [Parameter(Mandatory = $true)][hashtable]$Materials
    )
    foreach ($box in $Boxes) {
        Set-CuboidTexture -Atlas $Atlas -U $box[0] -V $box[1] -Width $box[2] -Height $box[3] -Depth $box[4] `
            -Material $Materials[[string]$box[5]]
    }
}

function Save-ClanAtlas {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][string]$FileName
    )
    $path = Join-Path $RepositoryRoot "src\main\resources\assets\warlockery\textures\entity\$FileName"
    Save-PixelAtlas -Atlas $Atlas -Path $path
}

$black = New-Color '#11161A'
$blackSide = New-Color '#20282C'
$blackTop = New-Color '#303A3E'
$blackBottom = New-Color '#080B0D'
$cream = New-Color '#F2E7C9'
$creamShade = New-Color '#C9BA99'
$orange = New-Color '#E59428'
$orangeLight = New-Color '#F6B53C'
$orangeShade = New-Color '#8E4C18'
$eye = New-Color '#090B0C'
$yellow = New-Color '#E9B93F'
$yellowShade = New-Color '#8D6422'
$leather = New-Color '#6B4527'
$leatherLight = New-Color '#95653A'
$leatherShade = New-Color '#382619'
$iron = New-Color '#778389'
$ironLight = New-Color '#AAB6B9'
$ironShade = New-Color '#3D484D'
$amber = New-Color '#FFD35C'
$ore = New-Color '#4AAEB4'
$hood = New-Color '#6B2830'
$hoodLight = New-Color '#98414A'
$hoodShade = New-Color '#351A21'
$pack = New-Color '#765638'
$packLight = New-Color '#A0784C'
$packShade = New-Color '#3F2D20'
$slate = New-Color '#3C5362'
$slateLight = New-Color '#617987'
$slateShade = New-Color '#1F3039'
$amethyst = New-Color '#8560B5'
$amethystLight = New-Color '#B792E0'
$amethystShade = New-Color '#45325F'
$gold = New-Color '#D5A73D'
$goldLight = New-Color '#F2D06A'
$goldShade = New-Color '#77511D'
$ledger = New-Color '#65402F'
$ledgerLight = New-Color '#8E6547'
$ledgerShade = New-Color '#36231D'
$steel = New-Color '#4B5357'
$steelLight = New-Color '#828D91'
$steelShade = New-Color '#252B2E'
$ember = New-Color '#F06424'
$emberLight = New-Color '#FFD05A'
$emberShade = New-Color '#8F2719'

$plumageMaterial = New-Material $black $blackSide $blackTop $blackBottom $blackBottom
$bellyMaterial = New-Material $cream $creamShade $cream $creamShade $creamShade
$beakMaterial = New-Material $orangeLight $orange $orangeLight $orangeShade $orangeShade
$footMaterial = New-Material $orange $orangeShade $orangeLight $orangeShade $orangeShade

$goblinMaterials = @{
    plumage = $plumageMaterial
    belly = $bellyMaterial
    beak = $beakMaterial
    foot = $footMaterial
    crest = New-Material $orangeLight $orangeShade $orangeLight $orangeShade $orangeShade
    leather = New-Material $leather $leatherShade $leather $leatherShade $leatherShade
    iron = New-Material $iron $blackSide $ironLight $blackBottom $blackSide
    lamp = New-Material $amber $orangeShade $amber $orangeShade $orangeShade
    ore = New-Material $ore $blackSide $ironLight $blackBottom $blackSide
}
$goblinBoxes = @(
    @(0, 0, 7, 10, 7, 'plumage'), @(32, 0, 8, 6, 7, 'plumage'),
    @(64, 0, 4, 2, 4, 'beak'), @(88, 0, 4, 3, 2, 'crest'), @(100, 0, 4, 3, 2, 'crest'),
    @(0, 20, 6, 2, 5, 'leather'), @(24, 20, 2, 2, 2, 'lamp'),
    @(34, 20, 5, 8, 1, 'belly'), @(50, 20, 7, 2, 7, 'plumage'),
    @(0, 34, 5, 3, 4, 'plumage'), @(22, 34, 4, 5, 3, 'leather'),
    @(38, 34, 3, 3, 3, 'ore'), @(54, 34, 2, 7, 2, 'leather'), @(64, 34, 7, 2, 2, 'iron'),
    @(82, 34, 2, 7, 4, 'plumage'), @(96, 34, 2, 5, 3, 'plumage'),
    @(0, 50, 2, 7, 4, 'plumage'), @(14, 50, 2, 5, 3, 'plumage'),
    @(28, 50, 3, 4, 3, 'plumage'), @(42, 50, 4, 2, 5, 'foot'),
    @(62, 50, 3, 4, 3, 'plumage'), @(76, 50, 4, 2, 5, 'foot')
)
$goblin = New-PixelAtlas -Width 128 -Height 128
try {
    Set-ClanBoxes -Atlas $goblin -Boxes $goblinBoxes -Materials $goblinMaterials
    Set-AtlasRectangle -Atlas $goblin -X 39 -Y 9 -Width 2 -Height 3 -Color $cream
    Set-AtlasRectangle -Atlas $goblin -X 45 -Y 9 -Width 2 -Height 3 -Color $cream
    Set-AtlasPixel -Atlas $goblin -X 40 -Y 9 -Color $eye
    Set-AtlasPixel -Atlas $goblin -X 45 -Y 9 -Color $eye
    Save-ClanAtlas -Atlas $goblin -FileName 'goblin.png'
}
finally {
    $goblin.Dispose()
}

$hobgoblinMaterials = @{
    plumage = $plumageMaterial
    belly = $bellyMaterial
    beak = $beakMaterial
    foot = $footMaterial
    hood = New-Material $hood $blackSide $hoodLight $blackBottom $blackSide
    pack = New-Material $pack $blackSide $packLight $blackBottom $blackSide
    leather = New-Material $pack $blackSide $packLight $blackBottom $blackSide
    metal = New-Material $iron $blackSide $ironLight $blackBottom $blackSide
    lamp = New-Material $amber $orangeShade $amber $orangeShade $orangeShade
}
$hobgoblinBoxes = @(
    @(0, 0, 9, 11, 8, 'plumage'), @(36, 0, 9, 7, 7, 'plumage'), @(68, 0, 5, 2, 4, 'beak'),
    @(92, 0, 10, 4, 8, 'hood'), @(130, 0, 3, 3, 2, 'metal'), @(144, 0, 7, 10, 1, 'belly'),
    @(0, 22, 10, 9, 2, 'hood'), @(26, 22, 6, 4, 5, 'plumage'), @(48, 22, 8, 9, 4, 'pack'),
    @(74, 22, 9, 3, 3, 'hood'), @(100, 22, 10, 7, 1, 'metal'), @(124, 22, 4, 5, 4, 'pack'),
    @(142, 22, 3, 4, 3, 'lamp'), @(158, 22, 3, 6, 2, 'leather'), @(172, 22, 2, 8, 2, 'metal'),
    @(0, 42, 3, 9, 3, 'plumage'), @(18, 42, 2, 5, 3, 'plumage'),
    @(36, 42, 3, 9, 3, 'plumage'), @(54, 42, 2, 5, 3, 'plumage'),
    @(72, 42, 3.5, 5, 3.5, 'plumage'), @(88, 42, 5.5, 2, 6, 'foot'),
    @(114, 42, 3.5, 5, 3.5, 'plumage'), @(130, 42, 5.5, 2, 6, 'foot')
)
$hobgoblin = New-PixelAtlas -Width 192 -Height 128
try {
    Set-ClanBoxes -Atlas $hobgoblin -Boxes $hobgoblinBoxes -Materials $hobgoblinMaterials
    Set-AtlasRectangle -Atlas $hobgoblin -X 43 -Y 9 -Width 3 -Height 4 -Color $cream
    Set-AtlasRectangle -Atlas $hobgoblin -X 49 -Y 9 -Width 3 -Height 4 -Color $cream
    Set-AtlasPixel -Atlas $hobgoblin -X 45 -Y 9 -Color $eye
    Set-AtlasPixel -Atlas $hobgoblin -X 49 -Y 9 -Color $eye
    Save-ClanAtlas -Atlas $hobgoblin -FileName 'hobgoblin.png'
}
finally {
    $hobgoblin.Dispose()
}

$stonebrokerMaterials = @{
    plumage = $plumageMaterial
    belly = $bellyMaterial
    beak = $beakMaterial
    foot = $footMaterial
    mantle = New-Material $slate $blackSide $slateLight $blackBottom $blackSide
    crystal = New-Material $amethyst $blackSide $amethystLight $blackBottom $blackSide
    gold = New-Material $orange $orangeShade $orangeLight $orangeShade $orangeShade
    ledger = New-Material $ledger $blackSide $ledgerLight $blackBottom $blackSide
    metal = New-Material $slate $blackSide $slateLight $blackBottom $blackSide
}
$stonebrokerBoxes = @(
    @(0, 0, 14, 19, 10, 'plumage'), @(50, 0, 12, 9, 9, 'plumage'), @(94, 0, 7, 3, 5, 'beak'),
    @(124, 0, 2, 3, 1, 'gold'), @(132, 0, 1, 6, 1, 'gold'), @(138, 0, 10, 16, 1, 'belly'),
    @(0, 24, 15, 3, 11, 'mantle'), @(54, 24, 7, 5, 7, 'plumage'), @(84, 24, 15, 3, 11, 'mantle'),
    @(138, 24, 4, 7, 4, 'crystal'), @(158, 24, 4, 6, 4, 'crystal'), @(0, 42, 5, 8, 5, 'crystal'),
    @(22, 42, 3, 10, 8, 'ledger'), @(46, 42, 1, 11, 9, 'ledger'), @(68, 42, 4, 2, 3, 'gold'),
    @(84, 42, 5, 10, 4, 'ledger'), @(104, 42, 5, 8, 3, 'metal'),
    @(124, 42, 3, 11, 4, 'plumage'), @(146, 42, 4, 6, 3, 'plumage'),
    @(0, 62, 3, 11, 4, 'plumage'), @(22, 62, 4, 6, 3, 'plumage'),
    @(44, 62, 5, 6, 5, 'plumage'), @(66, 62, 7, 2, 7, 'foot'),
    @(100, 62, 5, 6, 5, 'plumage'), @(122, 62, 7, 2, 7, 'foot')
)
$stonebroker = New-PixelAtlas -Width 192 -Height 160
try {
    Set-ClanBoxes -Atlas $stonebroker -Boxes $stonebrokerBoxes -Materials $stonebrokerMaterials
    Set-AtlasRectangle -Atlas $stonebroker -X 59 -Y 11 -Width 4 -Height 5 -Color $cream
    Set-AtlasRectangle -Atlas $stonebroker -X 67 -Y 11 -Width 4 -Height 5 -Color $cream
    Set-AtlasPixel -Atlas $stonebroker -X 61 -Y 11 -Color $eye
    Set-AtlasPixel -Atlas $stonebroker -X 68 -Y 11 -Color $eye
    Save-ClanAtlas -Atlas $stonebroker -FileName 'stonebroker.png'
}
finally {
    $stonebroker.Dispose()
}

$forgewardenMaterials = @{
    plumage = $plumageMaterial
    belly = $bellyMaterial
    beak = $beakMaterial
    foot = $footMaterial
    armor = New-Material $steel $blackSide $steelLight $blackBottom $blackSide
    iron = New-Material $steel $blackSide $steelLight $blackBottom $blackSide
    ember = New-Material $ember $orangeShade $orangeLight $blackBottom $orangeShade
    leather = New-Material $leather $blackSide $leatherLight $blackBottom $blackSide
}
$forgewardenBoxes = @(
    @(0, 0, 15, 21, 11, 'plumage'), @(54, 0, 12, 9, 9, 'plumage'), @(98, 0, 7, 3, 5, 'beak'),
    @(130, 0, 13, 4, 10, 'armor'), @(0, 24, 11, 18, 2, 'belly'), @(30, 24, 6, 7, 2, 'ember'),
    @(50, 24, 7, 6, 8, 'plumage'), @(82, 24, 17, 4, 12, 'armor'),
    @(142, 24, 6, 5, 7, 'armor'), @(0, 46, 6, 5, 7, 'armor'),
    @(28, 46, 12, 10, 4, 'leather'), @(62, 46, 10, 4, 3, 'armor'),
    @(90, 46, 3, 7, 3, 'armor'), @(104, 46, 3, 6, 3, 'armor'), @(118, 46, 16, 3, 11, 'leather'),
    @(0, 68, 2, 9, 2, 'iron'), @(12, 68, 4, 3, 2, 'ember'), @(28, 68, 4, 12, 4, 'plumage'),
    @(52, 68, 5, 6, 5, 'armor'), @(80, 68, 10, 6, 7, 'iron'), @(116, 68, 4, 12, 4, 'plumage'),
    @(140, 68, 4, 8, 5, 'armor'), @(0, 90, 5.5, 7, 5.5, 'plumage'), @(24, 90, 7, 2, 8, 'foot'),
    @(62, 90, 5.5, 7, 5.5, 'plumage'), @(86, 90, 7, 2, 8, 'foot')
)
$forgewarden = New-PixelAtlas -Width 192 -Height 160
try {
    Set-ClanBoxes -Atlas $forgewarden -Boxes $forgewardenBoxes -Materials $forgewardenMaterials
    Set-AtlasRectangle -Atlas $forgewarden -X 63 -Y 11 -Width 4 -Height 5 -Color $cream
    Set-AtlasRectangle -Atlas $forgewarden -X 71 -Y 11 -Width 4 -Height 5 -Color $cream
    Set-AtlasPixel -Atlas $forgewarden -X 65 -Y 11 -Color $eye
    Set-AtlasPixel -Atlas $forgewarden -X 72 -Y 11 -Color $eye
    Save-ClanAtlas -Atlas $forgewarden -FileName 'forgewarden.png'
}
finally {
    $forgewarden.Dispose()
}

Write-Output 'Generated four readable penguin-clan atlases with direct UV material maps.'
