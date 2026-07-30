param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [switch]$TintsOnly
)

$brews = @(
    [ordered]@{ id = 'heal'; name = 'Brew of Heal'; color = 0xF82423; catalyst = '#c:foods' }
    [ordered]@{ id = 'harm'; name = 'Brew of Harm'; color = 0x430A09; catalyst = '#c:bones' }
    [ordered]@{ id = 'absorption'; name = 'Brew of Absorption'; color = 0x2552A5; catalyst = '#c:dusts/glowstone' }
    [ordered]@{ id = 'health_boost'; name = 'Brew of Health Boost'; color = 0xF87D23; catalyst = '#c:foods/cooked_meat' }
    [ordered]@{ id = 'regeneration'; name = 'Brew of Regeneration'; color = 0xCD5CAB; catalyst = '#c:crops' }
    [ordered]@{ id = 'damage_boost'; name = 'Brew of Damage Boost'; color = 0x932423; catalyst = '#minecraft:swords' }
    [ordered]@{ id = 'fast_movement'; name = 'Brew of Fast Movement'; color = 0x7CAFC6; catalyst = '#minecraft:foot_armor' }
    [ordered]@{ id = 'slow_movement'; name = 'Brew of Slow Movement'; color = 0x5A6C81; catalyst = '#c:crops' }
    [ordered]@{ id = 'jump'; name = 'Brew of Jump'; color = 0x786297; catalyst = '#minecraft:foot_armor' }
    [ordered]@{ id = 'floating'; name = 'Brew of Floating'; color = 0xCEFFFF; catalyst = '#minecraft:wool' }
    [ordered]@{ id = 'slow_fall'; name = 'Brew of Slow Fall'; color = 0xF3CFB9; catalyst = '#minecraft:leaves' }
    [ordered]@{ id = 'blindness'; name = 'Brew of Blindness'; color = 0x1F1F23; catalyst = '#c:dyes/black' }
    [ordered]@{ id = 'invisible'; name = 'Brew of Invisible'; color = 0x7F8392; catalyst = '#c:dusts/glowstone' }
    [ordered]@{ id = 'night_vision'; name = 'Brew of Night Vision'; color = 0x1F1FA1; catalyst = '#c:foods' }
    [ordered]@{ id = 'water_breathing'; name = 'Brew of Water Breathing'; color = 0x2E5299; catalyst = '#c:foods/raw_meat' }
    [ordered]@{ id = 'swim_speed'; name = 'Brew of Swim Speed'; color = 0x88A3BE; catalyst = '#minecraft:foot_armor' }
    [ordered]@{ id = 'fire_resistance'; name = 'Brew of Resist Fire'; color = 0xE49A3A; catalyst = '#minecraft:coals' }
    [ordered]@{ id = 'poison'; name = 'Brew of Poison'; color = 0x4E9331; catalyst = '#c:crops' }
    [ordered]@{ id = 'wither'; name = 'Brew of Wither'; color = 0x352A27; catalyst = '#c:bones' }
    [ordered]@{ id = 'weakness'; name = 'Brew of Weakness'; color = 0x484D48; catalyst = '#c:foods/raw_meat' }
    [ordered]@{ id = 'fullness'; name = 'Brew of Fullness'; color = 0xE0A23A; catalyst = '#c:foods' }
    [ordered]@{ id = 'paralysis'; name = 'Brew of Paralysis'; color = 0x232F3D; catalyst = '#c:crops' }
    [ordered]@{ id = 'air_hike'; name = 'Brew of Air Hike'; color = 0xA8E6E6; catalyst = '#minecraft:wool' }
    [ordered]@{ id = 'fertilize'; name = 'Brew of Fertilize'; color = 0x66A33D; catalyst = '#c:bones' }
    [ordered]@{ id = 'grow_flowers'; name = 'Brew of Grow Flowers'; color = 0xDA70D6; catalyst = '#c:seeds' }
    [ordered]@{ id = 'grow_sapling'; name = 'Brew of Grow Sapling'; color = 0x4F8B3A; catalyst = '#minecraft:saplings' }
    [ordered]@{ id = 'extinguish_fires'; name = 'Brew of Extinguish Fires'; color = 0x3B7FD1; catalyst = '#minecraft:wool' }
    [ordered]@{ id = 'freeze'; name = 'Brew of Freeze'; color = 0x9AD6DF; catalyst = '#minecraft:foot_armor' }
    [ordered]@{ id = 'webs'; name = 'Brew of Webs'; color = 0xD8D8D8; catalyst = '#minecraft:swords' }
    [ordered]@{ id = 'flames'; name = 'Brew of Flames'; color = 0xE65A24; catalyst = '#minecraft:coals' }
    [ordered]@{ id = 'blast'; name = 'Brew of Blast'; color = 0x3D3D3D; catalyst = '#c:dusts/redstone' }
    [ordered]@{ id = 'push'; name = 'Brew of Push Away'; color = 0x62C5E8; catalyst = '#minecraft:arrows' }
    [ordered]@{ id = 'pull'; name = 'Brew of Pull'; color = 0x674EA7; catalyst = '#minecraft:arrows' }
    [ordered]@{ id = 'animal_attraction'; name = 'Brew of Animal Attraction'; color = 0xE98AA5; catalyst = '#c:foods' }
    [ordered]@{ id = 'animal_repulsion'; name = 'Brew of Animal Repulsion'; color = 0x776655; catalyst = '#c:bones' }
    [ordered]@{ id = 'fell_tree'; name = 'Brew of Fell Tree'; color = 0x75502B; catalyst = '#minecraft:axes' }
    [ordered]@{ id = 'prune_leaves'; name = 'Brew of Prune Leaves'; color = 0x3F7F38; catalyst = '#minecraft:hoes' }
    [ordered]@{ id = 'harvest'; name = 'Brew of Harvest'; color = 0xD7A83E; catalyst = '#c:crops' }
    [ordered]@{ id = 'till_land'; name = 'Brew of Till Land'; color = 0x805B3A; catalyst = '#minecraft:hoes' }
    [ordered]@{ id = 'revealing'; name = 'Brew of Revealing'; color = 0xF1E36B; catalyst = '#c:dusts/glowstone' }
    [ordered]@{ id = 'remove_buffs'; name = 'Brew of Remove Buffs'; color = 0x8A6D9E; catalyst = '#c:foods' }
    [ordered]@{ id = 'remove_debuffs'; name = 'Brew of Remove Debuffs'; color = 0x79B5A3; catalyst = '#c:foods' }
    [ordered]@{ id = 'stout_belly'; name = 'Brew of Stout Belly'; color = 0xC8984A; catalyst = '#c:foods' }
    [ordered]@{ id = 'harm_werewolves'; name = 'Brew of Harm Werewolves'; color = 0xC6CED6; catalyst = '#c:ingots/silver' }
    [ordered]@{ id = 'weaken_vampires'; name = 'Brew of Weaken Vampires'; color = 0x7B1723; catalyst = '#c:crops' }
    [ordered]@{ id = 'demonbane'; name = 'Brew of Demonbane'; color = 0xD9BE64; catalyst = '#c:ingots/silver' }
)

function Write-Json([string]$Path, [object]$Value) {
    New-Item -ItemType Directory -Force -Path (Split-Path $Path) | Out-Null
    $json = $Value | ConvertTo-Json -Depth 20
    [IO.File]::WriteAllText($Path, $json + "`n", [Text.UTF8Encoding]::new($false))
}

$assets = Join-Path $ProjectRoot 'src/main/resources/assets/warlockery'
$data = Join-Path $ProjectRoot 'src/main/resources/data'

function Sync-BrewTints {
    $brewKindSource = Get-Content -LiteralPath (Join-Path $ProjectRoot 'src/main/java/com/kadamitas/warlockery/brew/BrewKind.java') -Raw
    $brewKindPattern = '(?s)public static final BrewKind\s+\w+\s*=\s*(?:effect|effects|world|hybrid)\(\s*"([^"]+)"\s*,\s*0x([0-9A-Fa-f]{6})'
    $untintedBrews = @('combustion', 'endless_water')
    [regex]::Matches($brewKindSource, $brewKindPattern) | ForEach-Object {
        $brewId = $_.Groups[1].Value
        $definitionPath = Join-Path $assets "items/brew_$brewId.json"
        if ((Test-Path -LiteralPath $definitionPath) -and $brewId -notin $untintedBrews) {
            $rgb = [Convert]::ToInt64($_.Groups[2].Value, 16)
            $argb = [long]4278190080 + $rgb
            if ($argb -gt [int]::MaxValue) {
                $argb -= [long]4294967296
            }
            $definition = Get-Content -LiteralPath $definitionPath -Raw | ConvertFrom-Json -AsHashtable
            $currentTint = $definition.model.tints | Select-Object -First 1
            if ($null -eq $currentTint -or $currentTint.type -ne 'minecraft:potion' -or $currentTint.default -ne [int]$argb) {
                $definition.model.tints = @([ordered]@{ type = 'minecraft:potion'; default = [int]$argb })
                Write-Json $definitionPath $definition
            }
        }
    }
}

if ($TintsOnly) {
    Sync-BrewTints
    exit
}

foreach ($brew in $brews) {
    $itemId = "brew_$($brew.id)"
    $signedColor = [long]4278190080 + [long]$brew.color
    if ($signedColor -gt [int]::MaxValue) {
        $signedColor -= [long]4294967296
    }
    $definition = [ordered]@{
        model = [ordered]@{
            type = 'minecraft:model'
            model = "warlockery:item/$itemId"
            tints = @([ordered]@{ type = 'minecraft:potion'; default = [int]$signedColor })
        }
    }
    $model = [ordered]@{
        parent = 'minecraft:item/generated'
        textures = [ordered]@{ layer0 = 'warlockery:item/brew_splash_bottle' }
    }
    $recipe = [ordered]@{
        machine = 'kettle'
        fluid = [ordered]@{ ingredient = '#minecraft:water'; amount = 250 }
        inputs = @(
            [ordered]@{ ingredient = 'warlockery:ingredient_infusion_base' }
            [ordered]@{ ingredient = $brew.catalyst }
        )
        outputs = @([ordered]@{ item = "warlockery:$itemId"; count = 1 })
        processing_time = 200
    }
    Write-Json (Join-Path $assets "items/$itemId.json") $definition
    Write-Json (Join-Path $assets "models/item/$itemId.json") $model
    Write-Json (Join-Path $data "warlockery/warlockery_machine/kettle_$itemId.json") $recipe
}

Sync-BrewTints

$languagePath = Join-Path $assets 'lang/en_us.json'
$language = Get-Content -LiteralPath $languagePath -Raw | ConvertFrom-Json -AsHashtable
$brews | ForEach-Object { $language["item.warlockery.brew_$($_.id)"] = $_.name }
Write-Json $languagePath $language

$brewTagPath = Join-Path $data 'warlockery/tags/item/brews.json'
$brewTag = Get-Content -LiteralPath $brewTagPath -Raw | ConvertFrom-Json -AsHashtable
$values = [System.Collections.Generic.List[string]]::new()
$brewTag.values | ForEach-Object { $values.Add([string]$_) }
$brews | ForEach-Object {
    $value = "warlockery:brew_$($_.id)"
    if (-not $values.Contains($value)) {
        $values.Add($value)
    }
}
$brewTag.values = $values
Write-Json $brewTagPath $brewTag

Write-Json (Join-Path $data 'c/tags/item/potions/splash.json') ([ordered]@{
    replace = $false
    values = @('#warlockery:brews')
})
