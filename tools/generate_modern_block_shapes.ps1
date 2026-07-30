$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $projectRoot 'src/main/resources/assets/warlockery'
$dataRoot = Join-Path $projectRoot 'src/main/resources/data/warlockery'
$blockstateRoot = Join-Path $assetRoot 'blockstates'
$blockModelRoot = Join-Path $assetRoot 'models/block'
$itemModelRoot = Join-Path $assetRoot 'models/item'
$itemDefinitionRoot = Join-Path $assetRoot 'items'
$lootRoot = Join-Path $dataRoot 'loot_table/blocks'

function Write-Json {
    param([string] $Path, [object] $Value)
    $Value | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 $Path
}

function Write-Model {
    param([string] $Id, [string] $Suffix, [string] $Parent, [hashtable] $Textures)
    Write-Json (Join-Path $blockModelRoot "$Id$Suffix.json") ([ordered]@{
        parent = $Parent
        textures = $Textures
    })
}

function Write-ItemModel {
    param([string] $Id, [string] $Model)
    Write-Json (Join-Path $itemModelRoot "$Id.json") ([ordered]@{ parent = $Model })
    Write-Json (Join-Path $itemDefinitionRoot "$Id.json") ([ordered]@{
        model = [ordered]@{
            type = 'minecraft:model'
            model = $Model
        }
    })
}

function Write-SelfLoot {
    param([string] $Id)
    Write-Json (Join-Path $lootRoot "$Id.json") ([ordered]@{
        type = 'minecraft:block'
        pools = @([ordered]@{
            bonus_rolls = 0.0
            entries = @([ordered]@{ type = 'minecraft:item'; name = "warlockery:$Id" })
            rolls = 1.0
        })
        random_sequence = "warlockery:blocks/$Id"
    })
}

function Write-Door {
    param([string] $Id, [string] $BottomTexture, [string] $TopTexture, [string] $ItemTexture)
    $variants = [ordered]@{}
    $baseRotation = [ordered]@{ east = 0; north = 270; south = 90; west = 180 }
    foreach ($facing in $baseRotation.Keys) {
        foreach ($half in @('lower', 'upper')) {
            foreach ($hinge in @('left', 'right')) {
                foreach ($open in @($false, $true)) {
                    $vertical = if ($half -eq 'lower') { 'bottom' } else { 'top' }
                    $suffix = "_${vertical}_${hinge}"
                    $rotation = $baseRotation[$facing]
                    if ($open) {
                        $suffix += '_open'
                        $rotation = ($rotation + $(if ($hinge -eq 'left') { 90 } else { 270 })) % 360
                    }
                    $entry = [ordered]@{ model = "warlockery:block/$Id$suffix" }
                    if ($rotation -ne 0) { $entry.y = $rotation }
                    $key = "facing=$facing,half=$half,hinge=$hinge,open=$($open.ToString().ToLowerInvariant())"
                    $variants[$key] = $entry
                }
            }
        }
    }
    Write-Json (Join-Path $blockstateRoot "$Id.json") ([ordered]@{ variants = $variants })
    foreach ($vertical in @('bottom', 'top')) {
        foreach ($hinge in @('left', 'right')) {
            foreach ($open in @('', '_open')) {
                $suffix = "_${vertical}_${hinge}$open"
                Write-Model $Id $suffix "minecraft:block/door_${vertical}_${hinge}$open" ([ordered]@{
                    bottom = $BottomTexture
                    top = $TopTexture
                })
            }
        }
    }
    Write-Model $Id '' 'minecraft:block/door_bottom_left' ([ordered]@{ bottom = $BottomTexture; top = $TopTexture })
    Write-Json (Join-Path $itemModelRoot "$Id.json") ([ordered]@{
        parent = 'minecraft:item/generated'
        textures = [ordered]@{ layer0 = $ItemTexture }
    })
    Write-Json (Join-Path $itemDefinitionRoot "$Id.json") ([ordered]@{
        model = [ordered]@{ type = 'minecraft:model'; model = "warlockery:item/$Id" }
    })
    $condition = [ordered]@{
        condition = 'minecraft:block_state_property'
        block = "warlockery:$Id"
        properties = [ordered]@{ half = 'lower' }
    }
    Write-Json (Join-Path $lootRoot "$Id.json") ([ordered]@{
        type = 'minecraft:block'
        pools = @([ordered]@{
            bonus_rolls = 0.0
            conditions = @($condition)
            entries = @([ordered]@{ type = 'minecraft:item'; name = "warlockery:$Id" })
            rolls = 1.0
        })
        random_sequence = "warlockery:blocks/$Id"
    })
}

function Write-Button {
    param([string] $Id, [string] $Texture)
    $variants = [ordered]@{}
    $floorRotation = [ordered]@{ north = 0; east = 90; south = 180; west = 270 }
    foreach ($face in @('floor', 'wall', 'ceiling')) {
        foreach ($facing in $floorRotation.Keys) {
            foreach ($powered in @($false, $true)) {
                $rotation = $floorRotation[$facing]
                $entry = [ordered]@{
                    model = "warlockery:block/$Id$(if ($powered) { '_pressed' } else { '' })"
                }
                if ($face -eq 'wall') { $entry.x = 90; $entry.uvlock = $true }
                if ($face -eq 'ceiling') { $entry.x = 180; $rotation = ($rotation + 180) % 360 }
                if ($rotation -ne 0) { $entry.y = $rotation }
                $key = "face=$face,facing=$facing,powered=$($powered.ToString().ToLowerInvariant())"
                $variants[$key] = $entry
            }
        }
    }
    Write-Json (Join-Path $blockstateRoot "$Id.json") ([ordered]@{ variants = $variants })
    Write-Model $Id '' 'minecraft:block/button' ([ordered]@{ texture = $Texture })
    Write-Model $Id '_pressed' 'minecraft:block/button_pressed' ([ordered]@{ texture = $Texture })
    Write-Model $Id '_inventory' 'minecraft:block/button_inventory' ([ordered]@{ texture = $Texture })
    Write-ItemModel $Id "warlockery:block/${Id}_inventory"
    Write-SelfLoot $Id
}

function Write-PressurePlate {
    param([string] $Id, [string] $Texture)
    Write-Json (Join-Path $blockstateRoot "$Id.json") ([ordered]@{
        variants = [ordered]@{
            'powered=false' = [ordered]@{ model = "warlockery:block/$Id" }
            'powered=true' = [ordered]@{ model = "warlockery:block/${Id}_down" }
        }
    })
    Write-Model $Id '' 'minecraft:block/pressure_plate_up' ([ordered]@{ texture = $Texture })
    Write-Model $Id '_down' 'minecraft:block/pressure_plate_down' ([ordered]@{ texture = $Texture })
    Write-ItemModel $Id "warlockery:block/$Id"
    Write-SelfLoot $Id
}

function Write-Fence {
    param([string] $Id, [string] $Texture)
    $multipart = @([ordered]@{ apply = [ordered]@{ model = "warlockery:block/${Id}_post" } })
    $rotations = [ordered]@{ north = 0; east = 90; south = 180; west = 270 }
    foreach ($direction in $rotations.Keys) {
        $apply = [ordered]@{ model = "warlockery:block/${Id}_side"; uvlock = $true }
        if ($rotations[$direction] -ne 0) { $apply.y = $rotations[$direction] }
        $multipart += [ordered]@{ apply = $apply; when = [ordered]@{ $direction = 'true' } }
    }
    Write-Json (Join-Path $blockstateRoot "$Id.json") ([ordered]@{ multipart = $multipart })
    Write-Model $Id '_post' 'minecraft:block/fence_post' ([ordered]@{ texture = $Texture })
    Write-Model $Id '_side' 'minecraft:block/fence_side' ([ordered]@{ texture = $Texture })
    Write-Model $Id '_inventory' 'minecraft:block/fence_inventory' ([ordered]@{ texture = $Texture })
    Write-ItemModel $Id "warlockery:block/${Id}_inventory"
    Write-SelfLoot $Id
}

function Write-FenceGate {
    param([string] $Id, [string] $Texture)
    $variants = [ordered]@{}
    $rotations = [ordered]@{ south = 0; west = 90; north = 180; east = 270 }
    foreach ($facing in $rotations.Keys) {
        foreach ($inWall in @($false, $true)) {
            foreach ($open in @($false, $true)) {
                $suffix = $(if ($inWall) { '_wall' } else { '' }) + $(if ($open) { '_open' } else { '' })
                $entry = [ordered]@{ model = "warlockery:block/$Id$suffix"; uvlock = $true }
                if ($rotations[$facing] -ne 0) { $entry.y = $rotations[$facing] }
                $key = "facing=$facing,in_wall=$($inWall.ToString().ToLowerInvariant()),open=$($open.ToString().ToLowerInvariant())"
                $variants[$key] = $entry
            }
        }
    }
    Write-Json (Join-Path $blockstateRoot "$Id.json") ([ordered]@{ variants = $variants })
    Write-Model $Id '' 'minecraft:block/template_fence_gate' ([ordered]@{ texture = $Texture })
    Write-Model $Id '_open' 'minecraft:block/template_fence_gate_open' ([ordered]@{ texture = $Texture })
    Write-Model $Id '_wall' 'minecraft:block/template_fence_gate_wall' ([ordered]@{ texture = $Texture })
    Write-Model $Id '_wall_open' 'minecraft:block/template_fence_gate_wall_open' ([ordered]@{ texture = $Texture })
    Write-ItemModel $Id "warlockery:block/$Id"
    Write-SelfLoot $Id
}

function Write-Slab {
    param([string] $Id, [string] $Texture)
    Write-Json (Join-Path $blockstateRoot "$Id.json") ([ordered]@{
        variants = [ordered]@{
            'type=bottom' = [ordered]@{ model = "warlockery:block/$Id" }
            'type=double' = [ordered]@{ model = "warlockery:block/${Id}_double" }
            'type=top' = [ordered]@{ model = "warlockery:block/${Id}_top" }
        }
    })
    $textures = [ordered]@{ bottom = $Texture; side = $Texture; top = $Texture }
    Write-Model $Id '' 'minecraft:block/slab' $textures
    Write-Model $Id '_top' 'minecraft:block/slab_top' $textures
    Write-Model $Id '_double' 'minecraft:block/cube_all' ([ordered]@{ all = $Texture })
    Write-ItemModel $Id "warlockery:block/$Id"
    $countFunction = [ordered]@{
        function = 'minecraft:set_count'
        conditions = @([ordered]@{
            condition = 'minecraft:block_state_property'
            block = "warlockery:$Id"
            properties = [ordered]@{ type = 'double' }
        })
        count = 2.0
        add = $false
    }
    Write-Json (Join-Path $lootRoot "$Id.json") ([ordered]@{
        type = 'minecraft:block'
        pools = @([ordered]@{
            bonus_rolls = 0.0
            entries = @([ordered]@{
                type = 'minecraft:item'
                functions = @($countFunction, [ordered]@{ function = 'minecraft:explosion_decay' })
                name = "warlockery:$Id"
            })
            rolls = 1.0
        })
        random_sequence = "warlockery:blocks/$Id"
    })
}

function Write-Stairs {
    param([string] $Id, [string] $Texture)
    $variants = [ordered]@{}
    $baseRotation = [ordered]@{ east = 0; north = 270; south = 90; west = 180 }
    foreach ($facing in $baseRotation.Keys) {
        foreach ($half in @('bottom', 'top')) {
            foreach ($shape in @('inner_left', 'inner_right', 'outer_left', 'outer_right', 'straight')) {
                $modelSuffix = if ($shape.StartsWith('inner')) { '_inner' } elseif ($shape.StartsWith('outer')) { '_outer' } else { '' }
                $rotation = $baseRotation[$facing]
                if ($shape.EndsWith('left')) {
                    $rotation = ($rotation + $(if ($half -eq 'bottom') { 270 } else { 0 })) % 360
                } elseif ($shape.EndsWith('right') -and $half -eq 'top') {
                    $rotation = ($rotation + 90) % 360
                }
                $entry = [ordered]@{ model = "warlockery:block/$Id$modelSuffix"; uvlock = $true }
                if ($half -eq 'top') { $entry.x = 180 }
                if ($rotation -ne 0) { $entry.y = $rotation }
                $variants["facing=$facing,half=$half,shape=$shape"] = $entry
            }
        }
    }
    Write-Json (Join-Path $blockstateRoot "$Id.json") ([ordered]@{ variants = $variants })
    $textures = [ordered]@{ bottom = $Texture; side = $Texture; top = $Texture }
    Write-Model $Id '' 'minecraft:block/stairs' $textures
    Write-Model $Id '_inner' 'minecraft:block/inner_stairs' $textures
    Write-Model $Id '_outer' 'minecraft:block/outer_stairs' $textures
    Write-ItemModel $Id "warlockery:block/$Id"
    Write-SelfLoot $Id
}

function Write-Ladder {
    param([string] $Id, [string] $Texture)
    $variants = [ordered]@{}
    $rotations = [ordered]@{ north = 0; east = 90; south = 180; west = 270 }
    foreach ($facing in $rotations.Keys) {
        $entry = [ordered]@{ model = "warlockery:block/$Id" }
        if ($rotations[$facing] -ne 0) { $entry.y = $rotations[$facing] }
        $variants["facing=$facing"] = $entry
    }
    Write-Json (Join-Path $blockstateRoot "$Id.json") ([ordered]@{ variants = $variants })
    Write-Model $Id '' 'minecraft:block/ladder' ([ordered]@{ particle = $Texture; texture = $Texture })
    Write-ItemModel $Id "warlockery:block/$Id"
    Write-SelfLoot $Id
}

$doors = [ordered]@{
    alderwooddoor = @('warlockery:block/alderwooddoor_bottom', 'warlockery:block/alderwooddoor_top', 'warlockery:item/alderwooddoor')
    rowanwooddoor = @('warlockery:block/rowanwooddoor_bottom', 'warlockery:block/rowanwooddoor_top', 'warlockery:item/rowanwooddoor')
    icedoor = @('warlockery:block/icedoor_bottom', 'warlockery:block/icedoor_top', 'warlockery:item/icedoor')
}
$plates = [ordered]@{
    icepressureplate = 'minecraft:block/packed_ice'
    snowpressureplate = 'minecraft:block/snow'
}
$fences = [ordered]@{
    icefence = 'minecraft:block/packed_ice'
    stockade = 'warlockery:block/alder_planks'
    icestockade = 'minecraft:block/packed_ice'
}
$slabs = [ordered]@{
    iceslab = 'minecraft:block/packed_ice'
    icedoubleslab = 'minecraft:block/packed_ice'
    snowslab = 'minecraft:block/snow'
    snowdoubleslab = 'minecraft:block/snow'
    hexwoodslab = 'warlockery:block/hawthorn_planks'
    hexwooddoubleslab = 'warlockery:block/hawthorn_planks'
}
$stairs = [ordered]@{
    icestairs = 'minecraft:block/packed_ice'
    snowstairs = 'minecraft:block/snow'
    stairswoodalder = 'warlockery:block/alder_planks'
    stairswoodhawthorn = 'warlockery:block/hawthorn_planks'
    stairswoodrowan = 'warlockery:block/rowan_planks'
}

$doors.GetEnumerator() | ForEach-Object { Write-Door $_.Key $_.Value[0] $_.Value[1] $_.Value[2] }
$plates.GetEnumerator() | ForEach-Object { Write-PressurePlate $_.Key $_.Value }
$fences.GetEnumerator() | ForEach-Object { Write-Fence $_.Key $_.Value }
Write-FenceGate 'icefencegate' 'minecraft:block/packed_ice'
$slabs.GetEnumerator() | ForEach-Object { Write-Slab $_.Key $_.Value }
$stairs.GetEnumerator() | ForEach-Object { Write-Stairs $_.Key $_.Value }
Write-Ladder 'hex_ladder' 'minecraft:block/ladder'

Write-Host 'Generated modern block states, models, item views, and loot for shaped blocks.'
