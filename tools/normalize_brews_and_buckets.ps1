param(
    [string]$SkillRoot = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.codex\skills\recolor-pixel-icons')
)

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$itemRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../src/main/resources/assets/warlockery/textures/item'))
$assetRoot = Split-Path -Parent (Split-Path -Parent $itemRoot)
$itemModelRoot = Join-Path $assetRoot 'models/item'
$itemDefinitionRoot = Join-Path $assetRoot 'items'
$recolor = Join-Path $SkillRoot 'scripts/recolor_pixel_icon.ps1'
$brewTemplate = Join-Path $itemRoot 'brew_splash_bottle.png'

function Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function Shade([System.Drawing.Color]$color, [double]$factor) {
    $red = [Math]::Clamp([int][Math]::Round($color.R * $factor), 0, 255)
    $green = [Math]::Clamp([int][Math]::Round($color.G * $factor), 0, 255)
    $blue = [Math]::Clamp([int][Math]::Round($color.B * $factor), 0, 255)
    return '#{0:X2}{1:X2}{2:X2}' -f $red, $green, $blue
}

function Palette([string]$hex) {
    $color = Color $hex
    $shades = @(
        Shade $color 0.42
        Shade $color 0.72
        Shade $color 1.0
        Shade $color 1.28
    )
    return $shades -join ','
}

function Write-Json([string]$path, [object]$value) {
    $json = $value | ConvertTo-Json -Depth 10
    [IO.File]::WriteAllText($path, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}

$brews = [ordered]@{
    ingredient_brew_bats = '#8f4fc4'
    ingredient_brew_congealed_spirit = '#8de0e2'
    ingredient_brew_depths = '#339da6'
    ingredient_brew_erosion = '#cf8a3d'
    ingredient_brew_flowing_spirit = '#35b8cf'
    ingredient_brew_frogs_tongue = '#75c933'
    ingredient_brew_grave = '#756f91'
    ingredient_brew_grotesque = '#9f7342'
    ingredient_brew_hexed_leaping = '#c8dd42'
    ingredient_brew_hitchcock = '#25212e'
    ingredient_brew_hollow_tears = '#bcecf1'
    ingredient_brew_ice = '#70bce8'
    ingredient_brew_infection = '#8c971f'
    ingredient_brew_ink = '#2e4d77'
    ingredient_brew_love = '#e56ba8'
    ingredient_brew_murder_of_crows = '#171923'
    ingredient_brew_raising = '#b457a6'
    ingredient_brew_revealing = '#e0b644'
    ingredient_brew_sleep = '#8177cf'
    ingredient_brew_soaring = '#4fc4d8'
    ingredient_brew_solid_dirt = '#79553a'
    ingredient_brew_solid_erosion = '#bd5636'
    ingredient_brew_solid_sand = '#d8c786'
    ingredient_brew_solid_sandstone = '#c9af70'
    ingredient_brew_solid_stone = '#8e9aa0'
    ingredient_brew_soul_anguish = '#e05667'
    ingredient_brew_soul_fear = '#4e3b75'
    ingredient_brew_soul_hunger = '#a8c633'
    ingredient_brew_soul_torment = '#8f345e'
    ingredient_brew_sprouting = '#59d96b'
    ingredient_brew_substitution = '#e5bb55'
    ingredient_brew_thorns = '#26733f'
    ingredient_brew_vines = '#3ba967'
    ingredient_brew_wasting = '#9a9b3c'
    ingredient_brew_web = '#d9e5eb'
}
$fluidArtworkOnly = @('ingredient_brew_flowing_spirit', 'ingredient_brew_hollow_tears')

foreach ($brew in $brews.GetEnumerator()) {
    & $recolor `
        -SourcePath $brewTemplate `
        -TargetPath (Join-Path $itemRoot ($brew.Key + '.png')) `
        -Palette (Palette $brew.Value) `
        -PreserveLowSaturation `
        -SaturationThreshold 0.12

    if ($brew.Key -notin $fluidArtworkOnly) {
        Write-Json (Join-Path $itemModelRoot ($brew.Key + '.json')) ([ordered]@{
            parent = 'minecraft:item/generated'
            textures = [ordered]@{ layer0 = 'warlockery:item/brew_splash_bottle' }
        })
        $rgb = [Convert]::ToInt32($brew.Value.Substring(1), 16)
        Write-Json (Join-Path $itemDefinitionRoot ($brew.Key + '.json')) ([ordered]@{
            model = [ordered]@{
                type = 'minecraft:model'
                model = "warlockery:item/$($brew.Key)"
                tints = @([ordered]@{
                    type = 'minecraft:constant'
                    value = $rgb
                })
            }
        })
    }
}

$clientJar = Get-ChildItem (Join-Path $PSScriptRoot '../.gradle/mavenizer/repo/net/minecraft/client-extra') -Recurse -Filter '*.jar' |
    Select-Object -First 1
if (!$clientJar) {
    throw 'Minecraft client-extra jar was not found. Run a Forge Gradle task first.'
}
$temporary = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../build/tmp/vanilla_bucket'))
[System.IO.Directory]::CreateDirectory($temporary) | Out-Null
$waterBucket = Join-Path $temporary 'water_bucket.png'
$archive = [System.IO.Compression.ZipFile]::OpenRead($clientJar.FullName)
$entry = $archive.GetEntry('assets/minecraft/textures/item/water_bucket.png')
if (!$entry) {
    $archive.Dispose()
    throw 'Minecraft water bucket texture was not found.'
}
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $waterBucket, $true)
$archive.Dispose()

$buckets = [ordered]@{
    bucket_brew = '#a34fc5'
    bucket_erosionbrew = '#a78668'
    bucket_spirit = '#44b7c9'
    bucket_hollowtears = '#d4edf1'
}
foreach ($bucket in $buckets.GetEnumerator()) {
    & $recolor `
        -SourcePath $waterBucket `
        -TargetPath (Join-Path $itemRoot ($bucket.Key + '.png')) `
        -Palette (Palette $bucket.Value) `
        -PreserveLowSaturation `
        -SaturationThreshold 0.18
}
