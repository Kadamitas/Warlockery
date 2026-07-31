param([switch] $InventoryOnly)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $projectRoot 'src/main/resources/assets/warlockery'
$blockModelRoot = Join-Path $assetRoot 'models/block'
$itemModelRoot = Join-Path $assetRoot 'models/item'
$itemDefinitionRoot = Join-Path $assetRoot 'items'
$textureRoot = Join-Path $assetRoot 'textures/block'
$flatInventoryModels = [Collections.Generic.HashSet[string]]::new([string[]]@('demonheart'))

function Read-Json {
    param([string] $Path)
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Write-Json {
    param([string] $Path, [object] $Value)
    $json = $Value | ConvertTo-Json -Depth 30
    [IO.File]::WriteAllText($Path, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}

function Color {
    param([string] $Hex, [int] $Alpha = 255)
    return [Drawing.Color]::FromArgb(
        $Alpha,
        [Convert]::ToInt32($Hex.Substring(0, 2), 16),
        [Convert]::ToInt32($Hex.Substring(2, 2), 16),
        [Convert]::ToInt32($Hex.Substring(4, 2), 16)
    )
}

function Material {
    param([string] $Texture)
    $id = $Texture.ToLowerInvariant()
    if ($id -match 'flame|ember|_lit$') { return @('7B2418', 'A83B1D', 'D96825', 'F2A43A') }
    if ($id -match 'statue|occluded|broken_hexes') { return @('54534F', '6C6A64', '817E76', '969288') }
    if ($id -match 'glow_globe_base|crystalball_base|demon_heart_base') { return @('353A3E', '4B5156', '626A70', '818A90') }
    if ($id -match 'blood|heart') { return @('4A1018', '711725', '98243A', 'C44552') }
    if ($id -match 'spiritportal_frame') { return @('54534F', '6C6A64', '817E76', '969288') }
    if ($id -match 'portal|glow|crystal|glass|lens|veil') { return @('344C55', '466D78', '65A0AA', '92C7C8') }
    if ($id -match 'web|thread') { return @('C2C4C4', 'D6D8D8', 'E7E9E9', 'F5F7F7') }
    if ($id -match 'wax') { return @('B28948', 'D0AA62', 'E4C27A', 'F2D99E') }
    if ($id -match 'garlic|straw') { return @('9A824E', 'BBA164', 'D4BE82', 'E7D6A6') }
    if ($id -match 'fur') { return @('514238', '6A5748', '826C58', '9C856D') }
    if ($id -match 'leaf|leav|bramble') { return @('304A30', '41633D', '587B4C', '759665') }
    if ($id -match '^scarecrow$|doll_shelf_dolls|lining') { return @('49343E', '624650', '795965', '94717D') }
    if ($id -match 'skull|bone') { return @('8F8B79', 'B0AA93', 'CCC4AA', 'E0D9BD') }
    if ($id -match 'silver|_metal|pipe|vent|funnel|brazier|cauldron|kettle|candelabra|chalice|distillery|collector|trap') {
        return @('353A3E', '4B5156', '626A70', '818A90')
    }
    if ($id -match 'wood|coffin|shelf|chest|spinningwheel|scarecrow|trent|dream_catcher|refilling|leech') {
        return @('49301E', '634329', '7C5736', '987047')
    }
    if ($id -match 'altar|wolfaltar|paradox_egg') {
        return @('54534F', '6C6A64', '817E76', '969288')
    }
    if ($id -match 'metal') {
        return @('353A3E', '4B5156', '626A70', '818A90')
    }
    return @('4E4D49', '62605B', '75726B', '89857C')
}

function Alpha {
    param([string] $Texture)
    if ($Texture -match 'statue|occluded|broken_hexes|_base|_frame') {
        return 255
    }
    if ($Texture -match 'glass|lens|portal|glow|crystal|veil|web') {
        return 190
    }
    return 255
}

function Write-PlainTexture {
    param([string] $Texture)
    $palette = Material $Texture
    $alpha = Alpha $Texture
    $bitmap = [Drawing.Bitmap]::new(16, 16, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $base = Color $palette[1] $alpha
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                $bitmap.SetPixel($x, $y, $base)
            }
        }
        $dark = Color $palette[0] $alpha
        $light = Color $palette[2] $alpha
        $highlight = Color $palette[3] $alpha
        foreach ($point in @(@(1, 3), @(4, 12), @(7, 5), @(10, 14), @(13, 7), @(15, 1))) {
            $bitmap.SetPixel($point[0], $point[1], $dark)
        }
        foreach ($point in @(@(2, 9), @(5, 2), @(8, 11), @(11, 4), @(14, 13))) {
            $bitmap.SetPixel($point[0], $point[1], $light)
        }
        foreach ($point in @(@(3, 6), @(12, 2))) {
            $bitmap.SetPixel($point[0], $point[1], $highlight)
        }
        $path = Join-Path $textureRoot "$Texture.png"
        $bitmap.Save($path, [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

$sculptedModels = Get-ChildItem -LiteralPath $blockModelRoot -File -Filter '*.json' |
    Where-Object { (Get-Content -Raw -LiteralPath $_.FullName).Contains('"elements"') }

$textureIds = [Collections.Generic.HashSet[string]]::new()
foreach ($modelFile in $sculptedModels) {
    $id = $modelFile.BaseName
    $itemModel = Join-Path $itemModelRoot "$id.json"
    $itemDefinition = Join-Path $itemDefinitionRoot "$id.json"
    if ((Test-Path -LiteralPath $itemModel) -and (Test-Path -LiteralPath $itemDefinition) -and !$flatInventoryModels.Contains($id)) {
        Write-Json $itemModel ([ordered]@{ parent = "warlockery:block/$id" })
    }
    if (!$InventoryOnly) {
        $model = Read-Json $modelFile.FullName
        if ($null -ne $model.textures) {
            foreach ($property in $model.textures.PSObject.Properties) {
                $reference = [string]$property.Value
                if ($reference.StartsWith('warlockery:block/')) {
                    [void]$textureIds.Add($reference.Substring('warlockery:block/'.Length))
                }
            }
        }
    }
}

if (!$InventoryOnly) {
    foreach ($textureId in $textureIds) {
        $path = Join-Path $textureRoot "$textureId.png"
        if (Test-Path -LiteralPath $path) {
            Write-PlainTexture $textureId
        }
    }
    & (Join-Path $PSScriptRoot 'generate_focus_and_wolf_assets.ps1') -ProjectRoot $projectRoot
    & (Join-Path $PSScriptRoot 'generate_visual_asset_repairs.ps1') -ProjectRoot $projectRoot
}

Write-Host "Updated $($sculptedModels.Count) sculpted models and $($textureIds.Count) plain material textures."
