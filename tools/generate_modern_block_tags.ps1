$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$dataRoot = Join-Path $projectRoot 'src/main/resources/data'

function Add-TagValues {
    param([string] $Namespace, [string] $Registry, [string] $Path, [string[]] $Values)
    $target = Join-Path $dataRoot "$Namespace/tags/$Registry/$Path.json"
    New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
    $existing = if (Test-Path $target) { @(Get-Content -Raw $target | ConvertFrom-Json | Select-Object -ExpandProperty values) } else { @() }
    [ordered]@{
        replace = $false
        values = @($existing + $Values | Select-Object -Unique)
    } | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 $target
}

$doors = @('warlockery:alderwooddoor', 'warlockery:rowanwooddoor', 'warlockery:cwoodendoor', 'warlockery:icedoor')
$woodenDoors = @('warlockery:alderwooddoor', 'warlockery:rowanwooddoor', 'warlockery:cwoodendoor')
$buttons = @('warlockery:cbuttonstone', 'warlockery:cbuttonwood')
$fences = @('warlockery:icefence', 'warlockery:stockade', 'warlockery:icestockade')
$plates = @('warlockery:cstonepressureplate', 'warlockery:cwoodpressureplate', 'warlockery:icepressureplate', 'warlockery:snowpressureplate')
$slabs = @('warlockery:iceslab', 'warlockery:icedoubleslab', 'warlockery:snowslab', 'warlockery:snowdoubleslab', 'warlockery:hexwoodslab', 'warlockery:hexwooddoubleslab')
$woodenSlabs = @('warlockery:hexwoodslab', 'warlockery:hexwooddoubleslab')
$stairs = @('warlockery:icestairs', 'warlockery:snowstairs', 'warlockery:stairswoodalder', 'warlockery:stairswoodhawthorn', 'warlockery:stairswoodrowan')
$woodenStairs = @('warlockery:stairswoodalder', 'warlockery:stairswoodhawthorn', 'warlockery:stairswoodrowan')
$woodenMineables = @($woodenDoors + 'warlockery:cbuttonwood' + 'warlockery:cwoodpressureplate' + 'warlockery:stockade' + $woodenSlabs + $woodenStairs + 'warlockery:hex_ladder')
$stoneMineables = @('warlockery:cbuttonstone', 'warlockery:cstonepressureplate', 'warlockery:icedoor', 'warlockery:icepressureplate', 'warlockery:icefence', 'warlockery:icefencegate', 'warlockery:icestockade', 'warlockery:iceslab', 'warlockery:icedoubleslab', 'warlockery:icestairs')
$snowMineables = @('warlockery:snowpressureplate', 'warlockery:snowslab', 'warlockery:snowdoubleslab', 'warlockery:snowstairs')

foreach ($registry in @('block', 'item')) {
    Add-TagValues minecraft $registry doors $doors
    Add-TagValues minecraft $registry wooden_doors $woodenDoors
    Add-TagValues minecraft $registry buttons $buttons
    Add-TagValues minecraft $registry stone_buttons @('warlockery:cbuttonstone')
    Add-TagValues minecraft $registry wooden_buttons @('warlockery:cbuttonwood')
    Add-TagValues minecraft $registry fences $fences
    Add-TagValues minecraft $registry wooden_fences @('warlockery:stockade')
    Add-TagValues minecraft $registry fence_gates @('warlockery:icefencegate')
    Add-TagValues minecraft $registry slabs $slabs
    Add-TagValues minecraft $registry wooden_slabs $woodenSlabs
    Add-TagValues minecraft $registry stairs $stairs
    Add-TagValues minecraft $registry wooden_stairs $woodenStairs
}

Add-TagValues minecraft block pressure_plates $plates
Add-TagValues minecraft block stone_pressure_plates @('warlockery:cstonepressureplate', 'warlockery:icepressureplate', 'warlockery:snowpressureplate')
Add-TagValues minecraft block wooden_pressure_plates @('warlockery:cwoodpressureplate')
Add-TagValues minecraft item wooden_pressure_plates @('warlockery:cwoodpressureplate')
Add-TagValues minecraft block climbable @('warlockery:hex_ladder')
Add-TagValues minecraft block 'mineable/axe' $woodenMineables
Add-TagValues minecraft block 'mineable/pickaxe' $stoneMineables
Add-TagValues minecraft block 'mineable/shovel' $snowMineables

Write-Host 'Published modern vanilla family and mining tags for the shaped Warlockery blocks.'
