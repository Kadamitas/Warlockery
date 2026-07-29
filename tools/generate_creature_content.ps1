$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$resources = Join-Path $root 'src\main\resources'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Write-Json([string]$path, $value) {
    [IO.Directory]::CreateDirectory((Split-Path $path -Parent)) | Out-Null
    [IO.File]::WriteAllText($path, (($value | ConvertTo-Json -Depth 30) + [Environment]::NewLine), $utf8)
}

$entities = [ordered]@{
    ent='Ent'; werewolf='Werewolf'; werewolf_hunter='Werewolf Hunter'; hobgoblin='Hobgoblin'; goblin='Goblin'; stonebroker='Stonebroker'; forgewarden='Forgewarden'
    hex_bat='Hex Bat'; hedge_crone='Hedge Crone'; banshee='Banshee'; familiar_cat='Familiar Cat'; corpse='Body'
    circle_mage='Circle Mage'; umbral_sigil='Umbral Sigil'; death='Death'; pale_steed="Pale Steed"; demon='Demon'
    eldritch_watcher='Eldritch Watcher'; spectral_familiar='Spectral Familiar'; blood_thrall='Blood Thrall'; hellhound='Hellhound'
    thorned_pursuer='Thorned Pursuer'; illusion_creeper='Illusion Creeper'; illusion_spider='Illusion Spider'
    illusion_zombie='Illusion Zombie'; imp='Flame Imp'; emberhorn_archfiend='Emberhorn Archfiend'; crimson_matriarch='Crimson Matriarch'
    abyssal_regent='Abyssal Regent'; lost_soul='Lost Soul'; parasytic_louse='Parasytic Louse'; mandrake='Mandrake'
    dreamroot='Dreamroot'; glass_doppelganger='Glass Doppelganger'; nightmare='Nightmare'; owl='Owl'; poltergeist='Poltergeist'
    echo_shade='Echo Shade'; spectre='Spectre'; spirit='Spirit'; toad='Toad'; bramble_colossus='Bramble Colossus'; vampire='Vampire'
    ironbound_sentinel='Ironbound Sentinel'; lycan_villager='Lycan Villager'; storm_simian='Storm Simian'; feral_lycan='Feral Lycan'
}

$drops = @{
    ent='warlockery:ingredient_heartwood_splinter'; hobgoblin='warlockery:raw_delvealloy'; stonebroker='warlockery:ingredient_delvealloynugget'
    forgewarden='warlockery:ingredient_delvealloyingot'; demon='warlockery:ingredient_infernal_blood'; imp='minecraft:blaze_powder'
    hellhound='warlockery:ingredient_dog_tongue'; werewolf='warlockery:ingredient_wolfsbane'; feral_lycan='warlockery:ingredient_wolfsbane'
    lycan_villager='warlockery:ingredient_wolfsbane'; spirit='warlockery:ingredient_spectral_dust'; spectre='warlockery:ingredient_spectral_dust'
    banshee='warlockery:ingredient_spectral_dust'; parasytic_louse='warlockery:louse'
}

foreach ($entry in $entities.GetEnumerator()) {
    $id = $entry.Key
    $eggTexture = if ($id -eq 'ent') { 'minecraft:item/iron_golem_spawn_egg' }
        elseif ($id -in @('hobgoblin','goblin','stonebroker','forgewarden')) { 'minecraft:item/villager_spawn_egg' }
        elseif ($id -eq 'werewolf_hunter') { 'minecraft:item/pillager_spawn_egg' }
        elseif ($id -in @('werewolf','feral_lycan','lycan_villager','hellhound')) { 'minecraft:item/wolf_spawn_egg' }
        elseif ($id -in @('hex_bat','banshee','umbral_sigil','eldritch_watcher','spectral_familiar','imp','poltergeist','spectre','spirit','storm_simian')) { 'minecraft:item/vex_spawn_egg' }
        else { 'minecraft:item/zombie_spawn_egg' }
    Write-Json (Join-Path $resources "assets\warlockery\items\${id}_spawn_egg.json") ([ordered]@{
        model=[ordered]@{ type='minecraft:model'; model="warlockery:item/${id}_spawn_egg" }
    })
    Write-Json (Join-Path $resources "assets\warlockery\models\item\${id}_spawn_egg.json") ([ordered]@{
        parent='minecraft:item/generated'; textures=[ordered]@{ layer0=$eggTexture }
    })
    $pools = @()
    if ($drops.ContainsKey($id)) {
        $pools += [ordered]@{
            rolls=1; bonus_rolls=0
            entries=@([ordered]@{ type='minecraft:item'; name=$drops[$id] })
            conditions=@([ordered]@{ condition='minecraft:random_chance'; chance=0.35 })
        }
    }
    Write-Json (Join-Path $resources "data\warlockery\loot_table\entities\${id}.json") ([ordered]@{
        type='minecraft:entity'; pools=$pools; random_sequence="warlockery:entities/${id}"
    })
}

$blocks = [ordered]@{
    silver_ore='minecraft:block/iron_ore'; deepslate_silver_ore='minecraft:block/deepslate_iron_ore'
    raw_silver_block='minecraft:block/raw_iron_block'; silver_block='minecraft:block/iron_block'
    delvealloy_ore='minecraft:block/copper_ore'; deepslate_delvealloy_ore='minecraft:block/deepslate_copper_ore'
    raw_delvealloy_block='minecraft:block/raw_copper_block'; delvealloy_block='minecraft:block/exposed_copper'
}
foreach ($entry in $blocks.GetEnumerator()) {
    $id = $entry.Key
    $blockDrop = switch ($id) {
        'silver_ore' { 'warlockery:raw_silver' }
        'deepslate_silver_ore' { 'warlockery:raw_silver' }
        'delvealloy_ore' { 'warlockery:raw_delvealloy' }
        'deepslate_delvealloy_ore' { 'warlockery:raw_delvealloy' }
        default { "warlockery:${id}" }
    }
    Write-Json (Join-Path $resources "assets\warlockery\blockstates\${id}.json") ([ordered]@{
        variants=[ordered]@{ ''=[ordered]@{ model="warlockery:block/${id}" } }
    })
    Write-Json (Join-Path $resources "assets\warlockery\models\block\${id}.json") ([ordered]@{
        parent='minecraft:block/cube_all'; textures=[ordered]@{ all=$entry.Value }
    })
    Write-Json (Join-Path $resources "assets\warlockery\models\item\${id}.json") ([ordered]@{ parent="warlockery:block/${id}" })
    Write-Json (Join-Path $resources "assets\warlockery\items\${id}.json") ([ordered]@{
        model=[ordered]@{ type='minecraft:model'; model="warlockery:item/${id}" }
    })
    Write-Json (Join-Path $resources "data\warlockery\loot_table\blocks\${id}.json") ([ordered]@{
        type='minecraft:block'; pools=@([ordered]@{ rolls=1; bonus_rolls=0; entries=@([ordered]@{ type='minecraft:item'; name=$blockDrop }); conditions=@([ordered]@{ condition='minecraft:survives_explosion' }) }); random_sequence="warlockery:blocks/${id}"
    })
}

$items = [ordered]@{
    raw_silver='warlockery:item/ingredient_silverdust'; silver_ingot='warlockery:item/ingredient_silverdust'
    raw_delvealloy='warlockery:item/ingredient_delvealloyingot'
}
foreach ($entry in $items.GetEnumerator()) {
    Write-Json (Join-Path $resources "assets\warlockery\models\item\$($entry.Key).json") ([ordered]@{ parent='minecraft:item/generated'; textures=[ordered]@{ layer0=$entry.Value } })
    Write-Json (Join-Path $resources "assets\warlockery\items\$($entry.Key).json") ([ordered]@{ model=[ordered]@{ type='minecraft:model'; model="warlockery:item/$($entry.Key)" } })
}

$langPath = Join-Path $resources 'assets\warlockery\lang\en_us.json'
$lang = Get-Content -LiteralPath $langPath -Raw | ConvertFrom-Json -AsHashtable
foreach ($entry in $entities.GetEnumerator()) {
    $lang["entity.warlockery.$($entry.Key)"] = $entry.Value
    $lang["item.warlockery.$($entry.Key)_spawn_egg"] = "$($entry.Value) Spawn Egg"
}
$lang['item.warlockery.raw_silver']='Raw Silver'; $lang['item.warlockery.silver_ingot']='Silver Ingot'; $lang['item.warlockery.raw_delvealloy']='Raw Delvealloy'
$lang['block.warlockery.silver_ore']='Silver Ore'; $lang['block.warlockery.deepslate_silver_ore']='Deepslate Silver Ore'
$lang['block.warlockery.raw_silver_block']='Block of Raw Silver'; $lang['block.warlockery.silver_block']='Block of Silver'
$lang['block.warlockery.delvealloy_ore']='Delvealloy Ore'; $lang['block.warlockery.deepslate_delvealloy_ore']='Deepslate Delvealloy Ore'
$lang['block.warlockery.raw_delvealloy_block']='Block of Raw Delvealloy'; $lang['block.warlockery.delvealloy_block']='Block of Delvealloy'
foreach ($role in @('miner','smith','shaman','prospector')) { $lang["entity.warlockery.hobgoblin.profession.$role"] = "Hobgoblin $([cultureinfo]::InvariantCulture.TextInfo.ToTitleCase($role))" }
foreach ($tree in @('oak','birch','spruce','jungle','dark_oak','acacia','mangrove','cherry','pale_oak')) { $lang["entity.warlockery.ent.variant.$tree"] = "$([cultureinfo]::InvariantCulture.TextInfo.ToTitleCase($tree.Replace('_',' '))) Ent" }
Write-Json $langPath $lang

Write-Host "Generated client definitions, loot tables and language for $($entities.Count) creatures and $($blocks.Count) material blocks."
