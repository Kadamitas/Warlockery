$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$path = Join-Path $root 'src\main\resources\assets\warlockery\lang\en_us.json'
$lang = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable

$translations = [ordered]@{
    'itemGroup.warlockery.main'='Warlockery'
    'item.warlockery.ritual_knife'='Ritual Knife'
    'item.warlockery.arcane_focus'='Arcane Focus'
    'item.warlockery.sympathetic_vial'='Sympathetic Vial'
    'item.warlockery.silver_repeater'='Silver Repeater'
    'item.warlockery.thorn_spear'='Thorned Pursuer Spear'
    'item.warlockery.hedge_crones_hat'="Hedge Crone's Hat"
    'item.warlockery.beast_speech_charm'='Beast-Speech Charm'
    'item.warlockery.silver_tongue_charm'='Silver-Tongue Charm'
    'item.warlockery.archfiends_urn'="Archfiend's Urn"
    'item.warlockery.stonebrokers_quiver'="Stonebroker's Quiver"
    'item.warlockery.forgewardens_girdle'="Forgewarden's Girdle"
    'item.warlockery.replication_staff'='Replication Staff'
    'item.warlockery.replication_charge'='Replication Charge'
    'item.warlockery.emberstep_slippers'='Emberstep Slippers'
    'item.warlockery.ingredient_heartwood_splinter'='Heartwood Splinter'
    'item.warlockery.ingredient_fool_skull'="Fool's Skull"
    'item.warlockery.ingredient_matriarchs_blood'="Matriarch's Blood"
    'item.warlockery.ingredient_bramble_colossus_seed'='Bramble Colossus Seed'
    'item.warlockery.ingredient_spirit_of_the_veil'='Spirit of the Veil'
    'item.warlockery.ingredient_verdant_catalyst'='Verdant Catalyst'
    'item.warlockery.ingredient_verdant_catalyst_prime'='Prime Verdant Catalyst'
    'block.warlockery.alchemical_oven'='Alchemical Oven'
    'block.warlockery.alchemical_oven_lit'='Alchemical Oven'
    'item.warlockery.alchemical_oven'='Alchemical Oven'
    'item.warlockery.alchemical_oven_lit'='Alchemical Oven'
    'block.warlockery.hexwood'='Hexwood'
    'block.warlockery.hex_log'='Hexwood Log'
    'block.warlockery.hex_leaves'='Hexwood Leaves'
    'block.warlockery.hex_sapling'='Hexwood Sapling'
    'block.warlockery.hex_ladder'='Hex Ladder'
    'block.warlockery.circleglyph_veil'='Veil Glyph'
    'item.warlockery.chalk_veil'='Veil Chalk'
    'block.warlockery.abyssal_portal'='Abyssal Portal'
    'block.warlockery.abyssal_stone'='Abyssal Stone'
    'block.warlockery.paradox_egg'='Paradox Egg'
    'overlay.warlockery.altar.title'='Runed Altar'
    'screen.warlockery.ritual.requirement.center'='Use the Arcane Focus on the circle center'
    'screen.warlockery.ritual.requirement.coven'='Participants in the circle'
    'message.warlockery.sympathetic_vial.bound'='The sympathetic vial is now bound to %s'
    'tooltip.warlockery.sympathetic_vial.bound'='Bound to %s'
    'tooltip.warlockery.sympathetic_vial.empty'='Use on a creature to collect a sympathetic imprint'
    'subtitle.warlockery.chalk'='Chalk rings softly'
    'subtitle.warlockery.ritual_pulse'='The ritual pulses'
    'subtitle.warlockery.ritual_chime'='Arcane notes resonate'
}
foreach ($entry in $translations.GetEnumerator()) { $lang[$entry.Key] = $entry.Value }

foreach ($key in @($lang.Keys)) {
    $value = [string]$lang[$key]
    $lang[$key] = $value.Trim()
}

$json = $lang | ConvertTo-Json -Depth 20
[IO.File]::WriteAllText($path, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Host "Finalized $($lang.Count) Warlockery translations."
