param([switch] $GlyphsOnly)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $projectRoot 'src/main/resources/assets/warlockery'
$blockModelRoot = Join-Path $assetRoot 'models/block'
$blockStateRoot = Join-Path $assetRoot 'blockstates'
$itemModelRoot = Join-Path $assetRoot 'models/item'
$itemDefinitionRoot = Join-Path $assetRoot 'items'

function Write-Json {
    param([string] $Path, [object] $Value)
    $Value | ConvertTo-Json -Depth 30 | Set-Content -Encoding utf8 $Path
}

function Write-GeneratedItem {
    param([string] $Id, [string] $Texture = "warlockery:item/$Id")
    Write-Json (Join-Path $itemModelRoot "$Id.json") ([ordered]@{
        parent = 'minecraft:item/generated'
        textures = [ordered]@{ layer0 = $Texture }
    })
    Write-Json (Join-Path $itemDefinitionRoot "$Id.json") ([ordered]@{
        model = [ordered]@{ type = 'minecraft:model'; model = "warlockery:item/$Id" }
    })
}

function Cube-Element {
    param([double[]] $From, [double[]] $To, [string] $Texture)
    $faces = [ordered]@{}
    foreach ($face in @('down', 'up', 'north', 'south', 'west', 'east')) {
        $faces[$face] = [ordered]@{ texture = $Texture }
    }
    if ($From[1] -eq 0) { $faces.down.cullface = 'down' }
    if ($To[1] -eq 16) { $faces.up.cullface = 'up' }
    if ($From[2] -eq 0) { $faces.north.cullface = 'north' }
    if ($To[2] -eq 16) { $faces.south.cullface = 'south' }
    if ($From[0] -eq 0) { $faces.west.cullface = 'west' }
    if ($To[0] -eq 16) { $faces.east.cullface = 'east' }
    return [ordered]@{ from = $From; to = $To; faces = $faces }
}

function Rotated-Element {
    param(
        [double[]] $From,
        [double[]] $To,
        [string] $Texture,
        [string] $Axis,
        [double] $Angle,
        [double[]] $Origin
    )
    $element = Cube-Element $From $To $Texture
    foreach ($face in $element.faces.Values) {
        $face.Remove('cullface')
    }
    $element.rotation = [ordered]@{ origin = $Origin; axis = $Axis; angle = $Angle; rescale = $true }
    return $element
}

function Glyph-Element {
    param([double[]] $From, [double[]] $To, [double[]] $Uv)
    $element = Cube-Element $From $To '#glyph'
    $element.faces.up.uv = $Uv
    $element.faces.down.uv = $Uv
    return $element
}

function Write-ConnectedGlyphAssets {
    $parts = [ordered]@{
        center = Glyph-Element @(5, 0, 5) @(11, 0.5, 11) @(5, 5, 11, 11)
        north = Glyph-Element @(6, 0, 0) @(10, 0.5, 8) @(6, 0, 10, 8)
        east = Glyph-Element @(8, 0, 6) @(16, 0.5, 10) @(8, 6, 16, 10)
        south = Glyph-Element @(6, 0, 8) @(10, 0.5, 16) @(6, 8, 10, 16)
        west = Glyph-Element @(0, 0, 6) @(8, 0.5, 10) @(0, 6, 8, 10)
    }
    foreach ($part in $parts.GetEnumerator()) {
        Write-Json (Join-Path $blockModelRoot "chalk_glyph_$($part.Key).json") ([ordered]@{
            parent = 'minecraft:block/block'
            ambientocclusion = $false
            render_type = 'minecraft:cutout'
            textures = [ordered]@{ particle = '#glyph' }
            elements = @($part.Value)
        })
    }

    $glyphs = [ordered]@{
        circle = 'warlockery:block/circleglyph1.9'
        circleglyphritual = 'warlockery:block/circleglyphritual'
        circleglyphinfernal = 'warlockery:block/circleglyphinfernal'
        circleglyph_veil = 'warlockery:block/circleglyph_veil'
    }
    foreach ($glyph in $glyphs.GetEnumerator()) {
        foreach ($part in $parts.Keys) {
            $modelName = if ($part -eq 'center') { $glyph.Key } else { "$($glyph.Key)_$part" }
            Write-Json (Join-Path $blockModelRoot "$modelName.json") ([ordered]@{
                parent = "warlockery:block/chalk_glyph_$part"
                textures = [ordered]@{ glyph = $glyph.Value; particle = $glyph.Value }
            })
        }
        Write-Json (Join-Path $blockStateRoot "$($glyph.Key).json") ([ordered]@{
            multipart = @(
                [ordered]@{ apply = [ordered]@{ model = "warlockery:block/$($glyph.Key)" } },
                [ordered]@{ when = [ordered]@{ north = 'true' }; apply = [ordered]@{ model = "warlockery:block/$($glyph.Key)_north" } },
                [ordered]@{ when = [ordered]@{ east = 'true' }; apply = [ordered]@{ model = "warlockery:block/$($glyph.Key)_east" } },
                [ordered]@{ when = [ordered]@{ south = 'true' }; apply = [ordered]@{ model = "warlockery:block/$($glyph.Key)_south" } },
                [ordered]@{ when = [ordered]@{ west = 'true' }; apply = [ordered]@{ model = "warlockery:block/$($glyph.Key)_west" } }
            )
        })
    }
}

function Write-SculptedModel {
    param([string] $Id, [object] $Textures, [object[]] $Elements)
    Write-Json (Join-Path $blockModelRoot "$Id.json") ([ordered]@{
        parent = 'minecraft:block/block'
        ambientocclusion = $false
        textures = $Textures
        elements = $Elements
    })
}

Write-ConnectedGlyphAssets
if ($GlyphsOnly) {
    Write-Host 'Generated connected chalk glyph models and blockstates.'
    exit
}

Get-ChildItem -LiteralPath $blockModelRoot -File -Filter *.json | ForEach-Object {
    if ($_.BaseName -eq 'altar') {
        return
    }
    $json = Get-Content -Raw -LiteralPath $_.FullName
    if ($json.Contains('warlockery:block/altar')) {
        $updated = $json.Replace('warlockery:block/altar', "warlockery:block/$($_.BaseName)").TrimEnd() + "`n"
        [IO.File]::WriteAllText($_.FullName, $updated, [Text.UTF8Encoding]::new($false))
    }
}

Write-SculptedModel 'altar' ([ordered]@{
    particle = 'warlockery:block/altar_stone'
    stone = 'warlockery:block/altar_stone'
    rune = 'warlockery:block/altar_rune'
    wood = 'warlockery:block/altar_wood'
    wax = 'warlockery:block/altar_wax'
}) @(
    (Cube-Element @(0, 0, 0) @(16, 2, 16) '#stone'),
    (Cube-Element @(1, 2, 1) @(15, 4, 15) '#rune'),
    (Cube-Element @(2, 4, 2) @(14, 10, 14) '#stone'),
    (Cube-Element @(0, 10, 0) @(16, 13, 16) '#wood'),
    (Cube-Element @(1, 13, 1) @(15, 14, 15) '#rune'),
    (Cube-Element @(2, 6, 0) @(14, 9, 2) '#rune'),
    (Cube-Element @(2, 6, 14) @(14, 9, 16) '#rune'),
    (Cube-Element @(0, 6, 2) @(2, 9, 14) '#rune'),
    (Cube-Element @(14, 6, 2) @(16, 9, 14) '#rune')
)

Write-SculptedModel 'broken_hexes_statue' ([ordered]@{
    particle = 'warlockery:block/broken_hexes_statue'
    stone = 'warlockery:block/broken_hexes_statue'
    rune = 'warlockery:block/broken_hexes_rune'
}) @(
    (Cube-Element @(1, 0, 1) @(15, 2, 15) '#stone'),
    (Cube-Element @(3, 2, 3) @(13, 4, 13) '#rune'),
    (Cube-Element @(5, 4, 5) @(8, 9, 11) '#stone'),
    (Cube-Element @(9, 4, 5) @(12, 9, 11) '#stone'),
    (Cube-Element @(4, 9, 4) @(13, 14, 12) '#stone'),
    (Rotated-Element @(2, 9, 6) @(5, 14, 9) '#stone' 'z' -22.5 @(4, 11, 8)),
    (Rotated-Element @(12, 9, 6) @(15, 14, 9) '#stone' 'z' 22.5 @(13, 11, 8)),
    (Cube-Element @(6, 14, 5) @(11, 16, 11) '#stone'),
    (Cube-Element @(3, 6, 2) @(7, 13, 4) '#rune'),
    (Rotated-Element @(9, 7, 2) @(13, 12, 4) '#rune' 'z' 22.5 @(11, 9, 3))
)

Write-SculptedModel 'occluded_summons_statue' ([ordered]@{
    particle = 'warlockery:block/occluded_summons_statue'
    stone = 'warlockery:block/occluded_summons_statue'
    veil = 'warlockery:block/occluded_summons_veil'
}) @(
    (Cube-Element @(1, 0, 1) @(15, 2, 15) '#stone'),
    (Cube-Element @(3, 2, 3) @(13, 4, 13) '#veil'),
    (Cube-Element @(4, 4, 4) @(12, 12, 12) '#stone'),
    (Cube-Element @(2, 10, 4) @(14, 13, 12) '#veil'),
    (Cube-Element @(5, 12, 5) @(11, 16, 11) '#veil'),
    (Cube-Element @(6, 13, 4) @(10, 16, 6) '#stone'),
    (Rotated-Element @(2, 7, 5) @(5, 12, 8) '#veil' 'z' -22.5 @(4, 10, 7)),
    (Rotated-Element @(11, 7, 5) @(14, 12, 8) '#veil' 'z' 22.5 @(12, 10, 7)),
    (Cube-Element @(6, 7, 1) @(10, 11, 4) '#veil')
)

Write-SculptedModel 'statuegoddess' ([ordered]@{
    particle = 'warlockery:block/statuegoddess'
    stone = 'warlockery:block/statuegoddess'
    accent = 'warlockery:block/statuegoddess_accent'
}) @(
    (Cube-Element @(1, 0, 1) @(15, 2, 15) '#stone'),
    (Cube-Element @(3, 2, 3) @(13, 4, 13) '#accent'),
    (Cube-Element @(4, 4, 4) @(12, 11, 12) '#stone'),
    (Rotated-Element @(1, 7, 6) @(5, 13, 9) '#stone' 'z' -22.5 @(4, 10, 8)),
    (Rotated-Element @(11, 7, 6) @(15, 13, 9) '#stone' 'z' 22.5 @(12, 10, 8)),
    (Cube-Element @(6, 11, 5) @(10, 15, 11) '#stone'),
    (Cube-Element @(5, 15, 6) @(11, 16, 10) '#accent'),
    (Cube-Element @(3, 12, 7) @(6, 14, 9) '#accent'),
    (Cube-Element @(10, 12, 7) @(13, 14, 9) '#accent')
)

Write-SculptedModel 'statueofworship' ([ordered]@{
    particle = 'warlockery:block/statueofworship'
    stone = 'warlockery:block/statueofworship'
    metal = 'warlockery:block/statueofworship_metal'
}) @(
    (Cube-Element @(1, 0, 1) @(15, 2, 15) '#stone'),
    (Cube-Element @(2, 2, 2) @(14, 4, 14) '#metal'),
    (Cube-Element @(4, 4, 4) @(12, 11, 12) '#stone'),
    (Cube-Element @(2, 7, 5) @(5, 12, 11) '#stone'),
    (Cube-Element @(11, 7, 5) @(14, 12, 11) '#stone'),
    (Cube-Element @(5, 11, 5) @(11, 15, 11) '#stone'),
    (Cube-Element @(3, 12, 6) @(5, 14, 10) '#stone'),
    (Cube-Element @(11, 12, 6) @(13, 14, 10) '#stone'),
    (Cube-Element @(4, 15, 6) @(12, 16, 10) '#metal'),
    (Rotated-Element @(2, 3, 2) @(4, 14, 4) '#metal' 'z' -22.5 @(3, 8, 3)),
    (Cube-Element @(1, 12, 1) @(5, 14, 5) '#metal')
)

Write-Json (Join-Path $blockModelRoot 'pitgrass.json') ([ordered]@{
    parent = 'minecraft:block/cube_bottom_top'
    textures = [ordered]@{
        bottom = 'warlockery:block/pitdirt'
        side = 'warlockery:block/pitgrass_side'
        top = 'warlockery:block/pitgrass_top'
    }
})
Write-Json (Join-Path $blockModelRoot 'pitdirt.json') ([ordered]@{
    parent = 'minecraft:block/cube_all'
    textures = [ordered]@{ all = 'warlockery:block/pitdirt' }
})
Write-Json (Join-Path $blockModelRoot 'pentacle.json') ([ordered]@{
    parent = 'minecraft:block/cross'
    textures = [ordered]@{ cross = 'warlockery:block/pentacle' }
})
Write-GeneratedItem 'pentacle'

Write-SculptedModel 'alluringskull' ([ordered]@{
    particle = 'warlockery:block/alluring_skull'; bone = 'warlockery:block/alluring_skull'; eye = 'warlockery:block/alluring_skull_eye'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#bone'),
    (Cube-Element @(4, 2, 4) @(12, 10, 12) '#bone'),
    (Cube-Element @(5, 10, 5) @(11, 13, 11) '#bone'),
    (Cube-Element @(5, 5, 2) @(7, 8, 5) '#eye'),
    (Cube-Element @(9, 5, 2) @(11, 8, 5) '#eye'),
    (Cube-Element @(7, 3, 1) @(9, 5, 5) '#eye'),
    (Cube-Element @(6, 1, 1) @(10, 4, 5) '#bone')
)

Write-SculptedModel 'bloodcrucible' ([ordered]@{
    particle = 'warlockery:block/bloodcrucible'; metal = 'warlockery:block/bloodcrucible'; blood = 'warlockery:block/bloodcrucible_blood'
}) @(
    (Cube-Element @(2, 0, 2) @(14, 2, 14) '#metal'),
    (Cube-Element @(4, 2, 4) @(12, 5, 12) '#metal'),
    (Cube-Element @(1, 5, 1) @(15, 7, 15) '#metal'),
    (Cube-Element @(1, 7, 1) @(3, 13, 15) '#metal'),
    (Cube-Element @(13, 7, 1) @(15, 13, 15) '#metal'),
    (Cube-Element @(3, 7, 1) @(13, 13, 3) '#metal'),
    (Cube-Element @(3, 7, 13) @(13, 13, 15) '#metal'),
    (Cube-Element @(3, 7, 3) @(13, 8, 13) '#blood')
)

Write-SculptedModel 'brazier' ([ordered]@{
    particle = 'warlockery:block/brazier'; metal = 'warlockery:block/brazier'; ember = 'warlockery:block/brazier_ember'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#metal'),
    (Cube-Element @(7, 2, 7) @(9, 7, 9) '#metal'),
    (Cube-Element @(2, 7, 2) @(14, 9, 14) '#metal'),
    (Cube-Element @(3, 9, 3) @(13, 11, 13) '#ember'),
    (Cube-Element @(2, 9, 2) @(4, 14, 4) '#metal'),
    (Cube-Element @(12, 9, 2) @(14, 14, 4) '#metal'),
    (Cube-Element @(2, 9, 12) @(4, 14, 14) '#metal'),
    (Cube-Element @(12, 9, 12) @(14, 14, 14) '#metal')
)

Write-SculptedModel 'brazier_lit' ([ordered]@{
    particle = 'warlockery:block/brazier'; metal = 'warlockery:block/brazier'; ember = 'warlockery:block/brazier_ember'; flame = 'warlockery:block/brazier_flame'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#metal'),
    (Cube-Element @(7, 2, 7) @(9, 7, 9) '#metal'),
    (Cube-Element @(2, 7, 2) @(14, 9, 14) '#metal'),
    (Cube-Element @(3, 9, 3) @(13, 11, 13) '#ember'),
    (Cube-Element @(2, 9, 2) @(4, 14, 4) '#metal'),
    (Cube-Element @(12, 9, 2) @(14, 14, 4) '#metal'),
    (Cube-Element @(2, 9, 12) @(4, 14, 14) '#metal'),
    (Cube-Element @(12, 9, 12) @(14, 14, 14) '#metal'),
    (Cube-Element @(6, 10, 6) @(10, 16, 10) '#flame')
)
Write-Json (Join-Path $blockStateRoot 'brazier.json') ([ordered]@{
    variants = [ordered]@{
        'lit=false' = [ordered]@{ model = 'warlockery:block/brazier' }
        'lit=true' = [ordered]@{ model = 'warlockery:block/brazier_lit' }
    }
})

Write-SculptedModel 'candelabra' ([ordered]@{
    particle = 'warlockery:block/candelabra'; metal = 'warlockery:block/candelabra'; wax = 'warlockery:block/candelabra_wax'; flame = 'warlockery:block/candelabra_flame'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#metal'),
    (Cube-Element @(7, 2, 7) @(9, 13, 9) '#metal'),
    (Cube-Element @(3, 8, 7) @(13, 10, 9) '#metal'),
    (Cube-Element @(2, 10, 6) @(5, 15, 10) '#wax'),
    (Cube-Element @(7, 11, 6) @(10, 16, 10) '#wax'),
    (Cube-Element @(12, 10, 6) @(15, 15, 10) '#wax'),
    (Cube-Element @(3, 15, 7) @(4, 16, 9) '#flame'),
    (Cube-Element @(8, 15, 7) @(9, 16, 9) '#flame'),
    (Cube-Element @(13, 15, 7) @(14, 16, 9) '#flame')
)

Write-SculptedModel 'cauldron' ([ordered]@{
    particle = 'warlockery:block/cauldron'; metal = 'warlockery:block/cauldron'; rune = 'warlockery:block/cauldron_rune'
}) @(
    (Cube-Element @(2, 2, 2) @(14, 5, 14) '#metal'),
    (Cube-Element @(1, 5, 1) @(15, 7, 15) '#rune'),
    (Cube-Element @(1, 7, 1) @(3, 14, 15) '#metal'),
    (Cube-Element @(13, 7, 1) @(15, 14, 15) '#metal'),
    (Cube-Element @(3, 7, 1) @(13, 14, 3) '#metal'),
    (Cube-Element @(3, 7, 13) @(13, 14, 15) '#metal'),
    (Cube-Element @(2, 0, 2) @(5, 3, 5) '#metal'),
    (Cube-Element @(11, 0, 2) @(14, 3, 5) '#metal'),
    (Cube-Element @(2, 0, 11) @(5, 3, 14) '#metal'),
    (Cube-Element @(11, 0, 11) @(14, 3, 14) '#metal')
)

Write-SculptedModel 'chalice' ([ordered]@{
    particle = 'warlockery:block/chalice'; metal = 'warlockery:block/chalice'; cup = 'warlockery:block/chalice'
}) @(
    (Cube-Element @(4, 0, 4) @(12, 2, 12) '#metal'),
    (Cube-Element @(7, 2, 7) @(9, 7, 9) '#metal'),
    (Cube-Element @(4, 7, 4) @(12, 9, 12) '#cup'),
    (Cube-Element @(3, 9, 3) @(5, 14, 13) '#cup'),
    (Cube-Element @(11, 9, 3) @(13, 14, 13) '#cup'),
    (Cube-Element @(5, 9, 3) @(11, 14, 5) '#cup'),
    (Cube-Element @(5, 9, 11) @(11, 14, 13) '#cup')
)
Write-SculptedModel 'chalice_filled' ([ordered]@{
    particle = 'warlockery:block/chalice_filled'; metal = 'warlockery:block/chalice'; cup = 'warlockery:block/chalice'; liquid = 'warlockery:block/chalice_filled'
}) @(
    (Cube-Element @(4, 0, 4) @(12, 2, 12) '#metal'),
    (Cube-Element @(7, 2, 7) @(9, 7, 9) '#metal'),
    (Cube-Element @(4, 7, 4) @(12, 9, 12) '#cup'),
    (Cube-Element @(3, 9, 3) @(5, 14, 13) '#cup'),
    (Cube-Element @(11, 9, 3) @(13, 14, 13) '#cup'),
    (Cube-Element @(5, 9, 3) @(11, 14, 5) '#cup'),
    (Cube-Element @(5, 9, 11) @(11, 14, 13) '#cup'),
    (Cube-Element @(5, 11, 5) @(11, 12, 11) '#liquid')
)

Write-SculptedModel 'crystalball' ([ordered]@{
    particle = 'warlockery:block/crystalball'; base = 'warlockery:block/crystalball_base'; crystal = 'warlockery:block/crystalball'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#base'),
    (Cube-Element @(5, 2, 5) @(11, 5, 11) '#base'),
    (Cube-Element @(5, 5, 5) @(11, 13, 11) '#crystal'),
    (Cube-Element @(4, 7, 6) @(12, 11, 10) '#crystal'),
    (Cube-Element @(6, 7, 4) @(10, 11, 12) '#crystal'),
    (Cube-Element @(6, 4, 6) @(10, 14, 10) '#crystal')
)

Write-SculptedModel 'daylightcollector' ([ordered]@{
    particle = 'warlockery:block/daylightcollector'; frame = 'warlockery:block/daylightcollector'; lens = 'warlockery:block/daylightcollector_lens'
}) @(
    (Cube-Element @(1, 0, 1) @(15, 2, 15) '#frame'),
    (Cube-Element @(3, 2, 3) @(13, 4, 13) '#frame'),
    (Rotated-Element @(3, 4, 5) @(13, 6, 11) '#lens' 'z' -22.5 @(8, 5, 8)),
    (Cube-Element @(2, 4, 7) @(4, 13, 9) '#frame'),
    (Cube-Element @(12, 4, 7) @(14, 13, 9) '#frame'),
    (Cube-Element @(7, 4, 2) @(9, 13, 4) '#frame')
)

Write-SculptedModel 'demonheart' ([ordered]@{
    particle = 'warlockery:block/demon_heart'; base = 'warlockery:block/demon_heart_base'; heart = 'warlockery:block/demon_heart'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#base'),
    (Cube-Element @(6, 2, 6) @(10, 5, 10) '#base'),
    (Cube-Element @(4, 7, 4) @(8, 12, 10) '#heart'),
    (Cube-Element @(8, 7, 4) @(12, 12, 10) '#heart'),
    (Cube-Element @(6, 5, 5) @(10, 9, 11) '#heart'),
    (Rotated-Element @(7, 3, 6) @(10, 8, 10) '#heart' 'z' 45 @(8, 6, 8))
)

foreach ($distillery in @('distilleryidle', 'distilleryburning')) {
    Write-SculptedModel $distillery ([ordered]@{
        particle = "warlockery:block/$distillery"; metal = "warlockery:block/$distillery"; glass = "warlockery:block/${distillery}_glass"; pipe = 'warlockery:block/distillery_pipe'
    }) @(
        (Cube-Element @(1, 0, 1) @(15, 3, 15) '#metal'),
        (Cube-Element @(2, 3, 2) @(8, 13, 8) '#metal'),
        (Cube-Element @(3, 5, 3) @(7, 12, 7) '#glass'),
        (Cube-Element @(10, 3, 7) @(14, 11, 13) '#glass'),
        (Cube-Element @(8, 9, 7) @(12, 11, 9) '#pipe'),
        (Cube-Element @(7, 11, 3) @(12, 13, 5) '#pipe'),
        (Cube-Element @(10, 11, 3) @(12, 15, 9) '#pipe'),
        (Cube-Element @(9, 1, 2) @(14, 4, 6) '#metal')
    )
}

Write-SculptedModel 'doll_shelf' ([ordered]@{
    particle = 'warlockery:block/doll_shelf'; wood = 'warlockery:block/doll_shelf'; cloth = 'warlockery:block/doll_shelf_dolls'
}) @(
    (Cube-Element @(1, 0, 13) @(15, 16, 16) '#wood'),
    (Cube-Element @(0, 0, 0) @(2, 16, 16) '#wood'),
    (Cube-Element @(14, 0, 0) @(16, 16, 16) '#wood'),
    (Cube-Element @(0, 0, 0) @(16, 2, 16) '#wood'),
    (Cube-Element @(1, 7, 1) @(15, 9, 15) '#wood'),
    (Cube-Element @(3, 9, 8) @(6, 14, 12) '#cloth'),
    (Cube-Element @(10, 9, 8) @(13, 14, 12) '#cloth'),
    (Cube-Element @(6, 2, 7) @(10, 7, 12) '#cloth')
)

Write-SculptedModel 'dreamcatcher' ([ordered]@{
    particle = 'warlockery:block/dream_catcher'; frame = 'warlockery:block/dream_catcher'; web = 'warlockery:block/dream_catcher_web'
}) @(
    (Cube-Element @(2, 2, 7) @(14, 4, 9) '#frame'),
    (Cube-Element @(2, 12, 7) @(14, 14, 9) '#frame'),
    (Cube-Element @(2, 4, 7) @(4, 12, 9) '#frame'),
    (Cube-Element @(12, 4, 7) @(14, 12, 9) '#frame'),
    (Rotated-Element @(7, 3, 7) @(9, 13, 9) '#web' 'z' 45 @(8, 8, 8)),
    (Rotated-Element @(7, 3, 7) @(9, 13, 9) '#web' 'z' -45 @(8, 8, 8)),
    (Cube-Element @(4, 7, 7) @(12, 9, 9) '#web'),
    (Cube-Element @(4, 0, 7) @(6, 4, 9) '#web'),
    (Cube-Element @(10, 0, 7) @(12, 4, 9) '#web')
)

foreach ($funnel in @('fumefunnel', 'filteredfumefunnel')) {
    $filterTexture = if ($funnel -eq 'filteredfumefunnel') { '#filter' } else { '#metal' }
    Write-SculptedModel $funnel ([ordered]@{
        particle = "warlockery:block/$funnel"; metal = "warlockery:block/$funnel"; filter = 'warlockery:block/fumefunnel_filter'
    }) @(
        (Cube-Element @(6, 0, 6) @(10, 5, 10) '#metal'),
        (Cube-Element @(4, 5, 4) @(12, 8, 12) '#metal'),
        (Cube-Element @(2, 8, 2) @(14, 11, 14) '#metal'),
        (Cube-Element @(1, 11, 1) @(15, 13, 15) $filterTexture)
    )
}

Write-SculptedModel 'garlicgarland' ([ordered]@{
    particle = 'warlockery:block/garlicgarland'; cord = 'warlockery:block/garlicgarland_cord'; garlic = 'warlockery:block/garlicgarland'
}) @(
    (Cube-Element @(1, 13, 7) @(15, 15, 9) '#cord'),
    (Cube-Element @(2, 7, 6) @(6, 13, 10) '#garlic'),
    (Cube-Element @(6, 5, 6) @(10, 12, 10) '#garlic'),
    (Cube-Element @(10, 7, 6) @(14, 13, 10) '#garlic'),
    (Cube-Element @(3, 5, 7) @(5, 8, 9) '#cord'),
    (Cube-Element @(11, 5, 7) @(13, 8, 9) '#cord')
)

Write-SculptedModel 'glowglobe' ([ordered]@{
    particle = 'warlockery:block/glow_globe'; base = 'warlockery:block/glow_globe_base'; globe = 'warlockery:block/glow_globe'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#base'),
    (Cube-Element @(6, 2, 6) @(10, 5, 10) '#base'),
    (Cube-Element @(4, 5, 4) @(12, 13, 12) '#globe'),
    (Cube-Element @(5, 4, 5) @(11, 14, 11) '#globe'),
    (Cube-Element @(3, 7, 5) @(13, 11, 11) '#globe'),
    (Cube-Element @(6, 14, 6) @(10, 16, 10) '#base')
)

Write-SculptedModel 'kettle' ([ordered]@{
    particle = 'warlockery:block/kettle'; metal = 'warlockery:block/kettle'; rune = 'warlockery:block/kettle_rune'
}) @(
    (Cube-Element @(3, 1, 3) @(13, 4, 13) '#metal'),
    (Cube-Element @(2, 4, 2) @(14, 11, 14) '#metal'),
    (Cube-Element @(4, 11, 4) @(12, 13, 12) '#rune'),
    (Cube-Element @(3, 0, 3) @(5, 3, 5) '#metal'),
    (Cube-Element @(11, 0, 3) @(13, 3, 5) '#metal'),
    (Cube-Element @(3, 0, 11) @(5, 3, 13) '#metal'),
    (Cube-Element @(11, 0, 11) @(13, 3, 13) '#metal'),
    (Cube-Element @(1, 9, 7) @(3, 15, 9) '#metal'),
    (Cube-Element @(13, 9, 7) @(15, 15, 9) '#metal'),
    (Cube-Element @(2, 14, 7) @(14, 16, 9) '#metal')
)

Write-SculptedModel 'scarecrow' ([ordered]@{
    particle = 'warlockery:block/scarecrow'; wood = 'warlockery:block/scarecrow_wood'; cloth = 'warlockery:block/scarecrow'; straw = 'warlockery:block/scarecrow_straw'
}) @(
    (Cube-Element @(7, 0, 7) @(9, 14, 9) '#wood'),
    (Cube-Element @(1, 8, 7) @(15, 10, 9) '#wood'),
    (Cube-Element @(5, 4, 5) @(11, 11, 11) '#cloth'),
    (Cube-Element @(1, 7, 6) @(5, 11, 10) '#cloth'),
    (Cube-Element @(11, 7, 6) @(15, 11, 10) '#cloth'),
    (Cube-Element @(5, 11, 5) @(11, 15, 11) '#straw'),
    (Cube-Element @(3, 14, 4) @(13, 16, 12) '#cloth'),
    (Cube-Element @(5, 15, 6) @(11, 16, 10) '#cloth')
)

Write-SculptedModel 'silvervat' ([ordered]@{
    particle = 'warlockery:block/silvervat'; metal = 'warlockery:block/silvervat'; rune = 'warlockery:block/silvervat_rune'
}) @(
    (Cube-Element @(1, 0, 1) @(15, 3, 15) '#metal'),
    (Cube-Element @(2, 3, 2) @(14, 6, 14) '#rune'),
    (Cube-Element @(1, 6, 1) @(3, 15, 15) '#metal'),
    (Cube-Element @(13, 6, 1) @(15, 15, 15) '#metal'),
    (Cube-Element @(3, 6, 1) @(13, 15, 3) '#metal'),
    (Cube-Element @(3, 6, 13) @(13, 15, 15) '#metal'),
    (Cube-Element @(0, 14, 0) @(16, 16, 16) '#rune')
)

Write-SculptedModel 'spinningwheel' ([ordered]@{
    particle = 'warlockery:block/spinningwheel'; wood = 'warlockery:block/spinningwheel'; thread = 'warlockery:block/spinningwheel_thread'
}) @(
    (Cube-Element @(1, 0, 2) @(15, 2, 14) '#wood'),
    (Rotated-Element @(3, 1, 3) @(5, 13, 5) '#wood' 'z' 22.5 @(4, 7, 4)),
    (Rotated-Element @(11, 1, 11) @(13, 13, 13) '#wood' 'z' -22.5 @(12, 7, 12)),
    (Cube-Element @(2, 8, 6) @(14, 10, 8) '#wood'),
    (Cube-Element @(7, 4, 7) @(9, 16, 9) '#wood'),
    (Cube-Element @(3, 9, 7) @(13, 11, 9) '#wood'),
    (Rotated-Element @(7, 5, 7) @(9, 15, 9) '#thread' 'z' 45 @(8, 10, 8)),
    (Rotated-Element @(7, 5, 7) @(9, 15, 9) '#thread' 'z' -45 @(8, 10, 8))
)

foreach ($oven in @('alchemical_oven', 'alchemical_oven_lit')) {
    Write-SculptedModel $oven ([ordered]@{
        particle = "warlockery:block/$oven"; stone = "warlockery:block/$oven"; metal = 'warlockery:block/alchemical_oven_metal'; vent = 'warlockery:block/alchemical_oven_vent'
    }) @(
        (Cube-Element @(1, 0, 1) @(15, 3, 15) '#stone'),
        (Cube-Element @(2, 3, 2) @(14, 13, 14) '#stone'),
        (Cube-Element @(4, 4, 1) @(12, 10, 3) '#metal'),
        (Cube-Element @(6, 5, 0) @(10, 9, 2) '#vent'),
        (Cube-Element @(1, 12, 1) @(15, 15, 15) '#metal'),
        (Cube-Element @(3, 15, 3) @(6, 16, 6) '#vent'),
        (Cube-Element @(10, 15, 3) @(13, 16, 6) '#vent'),
        (Cube-Element @(3, 15, 10) @(6, 16, 13) '#vent'),
        (Cube-Element @(10, 15, 10) @(13, 16, 13) '#vent')
    )
}

foreach ($trapState in @('beartrap_disarmed', 'beartrap_armed', 'beartrap_sprung')) {
    $jawHeight = if ($trapState -eq 'beartrap_armed') { 7 } elseif ($trapState -eq 'beartrap_sprung') { 10 } else { 4 }
    Write-SculptedModel $trapState ([ordered]@{
        particle = 'warlockery:block/beartrap'; metal = 'warlockery:block/beartrap'; tooth = 'warlockery:block/beartrap_teeth'
    }) @(
        (Cube-Element @(1, 0, 1) @(15, 2, 15) '#metal'),
        (Cube-Element @(3, 2, 3) @(13, 3, 13) '#metal'),
        (Cube-Element @(1, 2, 1) @(3, $jawHeight, 15) '#metal'),
        (Cube-Element @(13, 2, 1) @(15, $jawHeight, 15) '#metal'),
        (Cube-Element @(3, 2, 1) @(5, ($jawHeight - 1), 3) '#tooth'),
        (Cube-Element @(7, 2, 1) @(9, ($jawHeight - 1), 3) '#tooth'),
        (Cube-Element @(11, 2, 1) @(13, ($jawHeight - 1), 3) '#tooth'),
        (Cube-Element @(3, 2, 13) @(5, ($jawHeight - 1), 15) '#tooth'),
        (Cube-Element @(7, 2, 13) @(9, ($jawHeight - 1), 15) '#tooth'),
        (Cube-Element @(11, 2, 13) @(13, ($jawHeight - 1), 15) '#tooth')
    )
}
Write-SculptedModel 'beartrap' ([ordered]@{
    particle = 'warlockery:block/beartrap'; metal = 'warlockery:block/beartrap'; tooth = 'warlockery:block/beartrap_teeth'
}) @(
    (Cube-Element @(1, 0, 1) @(15, 2, 15) '#metal'),
    (Cube-Element @(1, 2, 1) @(3, 7, 15) '#metal'),
    (Cube-Element @(13, 2, 1) @(15, 7, 15) '#metal'),
    (Cube-Element @(4, 2, 1) @(6, 6, 3) '#tooth'),
    (Cube-Element @(10, 2, 13) @(12, 6, 15) '#tooth')
)

Write-SculptedModel 'coffinblock' ([ordered]@{
    particle = 'warlockery:block/coffinblock'; wood = 'warlockery:block/coffinblock'; lining = 'warlockery:block/coffinblock_lining'
}) @(
    (Cube-Element @(3, 0, 0) @(13, 3, 16) '#wood'),
    (Cube-Element @(1, 3, 2) @(15, 6, 14) '#wood'),
    (Cube-Element @(3, 6, 0) @(13, 9, 16) '#wood'),
    (Cube-Element @(4, 3, 2) @(12, 7, 14) '#lining'),
    (Cube-Element @(6, 7, 5) @(10, 10, 11) '#wood')
)

foreach ($chest in @('leechchest', 'refillingchest')) {
    Write-SculptedModel $chest ([ordered]@{
        particle = "warlockery:block/$chest"; wood = "warlockery:block/$chest"; metal = "warlockery:block/${chest}_metal"
    }) @(
        (Cube-Element @(1, 0, 1) @(15, 10, 15) '#wood'),
        (Cube-Element @(0, 10, 0) @(16, 14, 16) '#wood'),
        (Cube-Element @(1, 14, 1) @(15, 16, 15) '#metal'),
        (Cube-Element @(7, 7, 0) @(10, 12, 2) '#metal'),
        (Cube-Element @(0, 1, 6) @(2, 15, 10) '#metal'),
        (Cube-Element @(14, 1, 6) @(16, 15, 10) '#metal')
    )
}

Write-SculptedModel 'trent' ([ordered]@{
    particle = 'warlockery:block/trent'; wood = 'warlockery:block/trent'; leaf = 'warlockery:block/trent_leaves'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#wood'),
    (Cube-Element @(6, 2, 6) @(10, 13, 10) '#wood'),
    (Rotated-Element @(1, 6, 6) @(7, 9, 9) '#wood' 'z' -22.5 @(6, 8, 8)),
    (Rotated-Element @(9, 6, 6) @(15, 9, 9) '#wood' 'z' 22.5 @(10, 8, 8)),
    (Cube-Element @(4, 11, 4) @(12, 16, 12) '#leaf'),
    (Cube-Element @(2, 13, 6) @(14, 16, 10) '#leaf'),
    (Cube-Element @(6, 12, 2) @(10, 16, 14) '#leaf')
)

Write-SculptedModel 'wolfaltar' ([ordered]@{
    particle = 'warlockery:block/wolfaltar'; stone = 'warlockery:block/wolfaltar'; fur = 'warlockery:block/wolfaltar_fur'; rune = 'warlockery:block/wolfaltar_rune'
}) @(
    (Cube-Element @(0, 0, 0) @(16, 2, 16) '#stone'),
    (Cube-Element @(1, 2, 1) @(15, 4, 15) '#rune'),
    (Cube-Element @(3, 4, 3) @(13, 9, 13) '#stone'),
    (Cube-Element @(1, 9, 1) @(15, 12, 15) '#stone'),
    (Cube-Element @(5, 12, 5) @(11, 16, 12) '#fur'),
    (Cube-Element @(3, 14, 6) @(6, 16, 10) '#fur'),
    (Cube-Element @(10, 14, 6) @(13, 16, 10) '#fur'),
    (Cube-Element @(6, 12, 2) @(10, 15, 6) '#fur')
)

Write-SculptedModel 'wolfhead' ([ordered]@{
    particle = 'warlockery:block/wolfhead'; fur = 'warlockery:block/wolfhead'; eye = 'warlockery:block/wolfhead_eye'
}) @(
    (Cube-Element @(4, 2, 6) @(12, 13, 15) '#fur'),
    (Cube-Element @(2, 10, 8) @(6, 16, 14) '#fur'),
    (Cube-Element @(10, 10, 8) @(14, 16, 14) '#fur'),
    (Cube-Element @(5, 1, 2) @(11, 8, 9) '#fur'),
    (Cube-Element @(4, 8, 4) @(7, 11, 8) '#eye'),
    (Cube-Element @(9, 8, 4) @(12, 11, 8) '#eye'),
    (Cube-Element @(7, 3, 0) @(9, 6, 4) '#fur')
)

Write-SculptedModel 'wolftrap' ([ordered]@{
    particle = 'warlockery:block/wolftrap'; metal = 'warlockery:block/wolftrap'; silver = 'warlockery:block/wolftrap_silver'; rune = 'warlockery:block/wolftrap_rune'
}) @(
    (Cube-Element @(0, 0, 0) @(16, 2, 16) '#metal'),
    (Cube-Element @(2, 2, 2) @(14, 3, 14) '#rune'),
    (Cube-Element @(0, 2, 0) @(2, 9, 16) '#silver'),
    (Cube-Element @(14, 2, 0) @(16, 9, 16) '#silver'),
    (Cube-Element @(2, 2, 0) @(14, 4, 2) '#silver'),
    (Cube-Element @(2, 2, 14) @(14, 4, 16) '#silver'),
    (Cube-Element @(3, 3, 1) @(5, 7, 3) '#silver'),
    (Cube-Element @(7, 3, 1) @(9, 7, 3) '#silver'),
    (Cube-Element @(11, 3, 1) @(13, 7, 3) '#silver'),
    (Cube-Element @(3, 3, 13) @(5, 7, 15) '#silver'),
    (Cube-Element @(7, 3, 13) @(9, 7, 15) '#silver'),
    (Cube-Element @(11, 3, 13) @(13, 7, 15) '#silver')
)

foreach ($mirror in @('mirrorblock', 'mirrorblock2', 'mirrorwall')) {
    $depth = if ($mirror -eq 'mirrorwall') { @(14, 16) } else { @(6, 10) }
    Write-SculptedModel $mirror ([ordered]@{
        particle = "warlockery:block/$mirror"; frame = "warlockery:block/$mirror"; glass = "warlockery:block/${mirror}_glass"
    }) @(
        (Cube-Element @(1, 1, $depth[0]) @(15, 3, $depth[1]) '#frame'),
        (Cube-Element @(1, 13, $depth[0]) @(15, 15, $depth[1]) '#frame'),
        (Cube-Element @(1, 3, $depth[0]) @(3, 13, $depth[1]) '#frame'),
        (Cube-Element @(13, 3, $depth[0]) @(15, 13, $depth[1]) '#frame'),
        (Cube-Element @(3, 3, ($depth[0] + 1)) @(13, 13, ($depth[1] - 1)) '#glass'),
        (Cube-Element @(6, 0, $depth[0]) @(10, 2, $depth[1]) '#frame')
    )
}

Write-SculptedModel 'paradox_egg' ([ordered]@{
    particle = 'warlockery:block/paradox_egg'; shell = 'warlockery:block/paradox_egg'; rune = 'warlockery:block/paradox_egg_rune'
}) @(
    (Cube-Element @(3, 0, 3) @(13, 2, 13) '#rune'),
    (Cube-Element @(5, 2, 5) @(11, 4, 11) '#shell'),
    (Cube-Element @(4, 4, 4) @(12, 10, 12) '#shell'),
    (Cube-Element @(5, 10, 5) @(11, 14, 11) '#shell'),
    (Cube-Element @(7, 14, 7) @(9, 16, 9) '#rune'),
    (Cube-Element @(3, 7, 6) @(13, 10, 10) '#rune')
)

Write-SculptedModel 'spiritportal' ([ordered]@{
    particle = 'warlockery:block/spiritportal'; frame = 'warlockery:block/spiritportal_frame'; portal = 'warlockery:block/spiritportal'
}) @(
    (Cube-Element @(1, 0, 5) @(15, 2, 11) '#frame'),
    (Cube-Element @(1, 2, 5) @(4, 15, 11) '#frame'),
    (Cube-Element @(12, 2, 5) @(15, 15, 11) '#frame'),
    (Cube-Element @(3, 14, 5) @(13, 16, 11) '#frame'),
    (Cube-Element @(4, 2, 7) @(12, 14, 9) '#portal'),
    (Cube-Element @(0, 0, 4) @(4, 3, 12) '#frame'),
    (Cube-Element @(12, 0, 4) @(16, 3, 12) '#frame')
)

Write-SculptedModel 'voidbramble' ([ordered]@{
    particle = 'warlockery:block/voidbramble'; thorn = 'warlockery:block/voidbramble'; glow = 'warlockery:block/voidbramble_glow'
}) @(
    (Rotated-Element @(7, 0, 7) @(9, 16, 9) '#thorn' 'z' 45 @(8, 8, 8)),
    (Rotated-Element @(7, 0, 7) @(9, 16, 9) '#thorn' 'z' -45 @(8, 8, 8)),
    (Rotated-Element @(7, 0, 7) @(9, 16, 9) '#thorn' 'x' 45 @(8, 8, 8)),
    (Rotated-Element @(7, 0, 7) @(9, 16, 9) '#thorn' 'x' -45 @(8, 8, 8)),
    (Cube-Element @(6, 6, 6) @(10, 10, 10) '#glow')
)

Write-SculptedModel 'web' ([ordered]@{
    particle = 'warlockery:block/web'; strand = 'warlockery:block/web'
}) @(
    (Cube-Element @(7, 0, 7) @(9, 16, 9) '#strand'),
    (Cube-Element @(0, 7, 7) @(16, 9, 9) '#strand'),
    (Rotated-Element @(7, 0, 7) @(9, 16, 9) '#strand' 'z' 45 @(8, 8, 8)),
    (Rotated-Element @(7, 0, 7) @(9, 16, 9) '#strand' 'z' -45 @(8, 8, 8))
)

Write-Json (Join-Path $blockModelRoot 'hex_sapling.json') ([ordered]@{
    parent = 'minecraft:block/cross'
    textures = [ordered]@{ cross = 'warlockery:block/hex_sapling' }
})

Write-GeneratedItem 'ingredient_brew_hitchcock'
Write-GeneratedItem 'brew_murderous_flock' 'warlockery:item/brew_splash_bottle'
Write-Json (Join-Path $itemDefinitionRoot 'brew_murderous_flock.json') ([ordered]@{
    model = [ordered]@{
        type = 'minecraft:model'
        model = 'warlockery:item/brew_murderous_flock'
        tints = @([ordered]@{ type = 'minecraft:potion'; default = -14412241 })
    }
})

Get-ChildItem -LiteralPath $itemDefinitionRoot -File -Filter '*.json' | ForEach-Object {
    if (Test-Path (Join-Path $blockStateRoot $_.Name)) {
        Write-GeneratedItem $_.BaseName
    }
}

Get-ChildItem -LiteralPath $itemModelRoot -File -Filter '*_spawn_egg.json' | ForEach-Object {
    Write-GeneratedItem $_.BaseName
}

Write-Host 'Generated sculpted block forms, separate block inventory icons, and palette-ready spawn egg models.'
