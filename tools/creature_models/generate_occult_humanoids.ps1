param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

. (Join-Path $PSScriptRoot 'common.ps1')

$entityTextureRoot = Join-Path $RepositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function Get-OccultColor {
    param([Parameter(Mandatory = $true)][string]$Hex)
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

# Circle Mage: restrained charcoal-plum study robes, drowned silver, and a teal circle focus.
$circleMage = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $circleMage -X 0 -Y 0 -Width 26 -Height 18 -Color (Get-OccultColor '#665968')
    Set-AtlasRectangle -Atlas $circleMage -X 2 -Y 2 -Width 22 -Height 5 -Color (Get-OccultColor '#292331')
    Set-AtlasRectangle -Atlas $circleMage -X 28 -Y 0 -Width 18 -Height 7 -Color (Get-OccultColor '#47777C')
    Set-AtlasRectangle -Atlas $circleMage -X 30 -Y 2 -Width 14 -Height 2 -Color (Get-OccultColor '#A9C7C5')
    Set-AtlasRectangle -Atlas $circleMage -X 48 -Y 0 -Width 12 -Height 6 -Color (Get-OccultColor '#86A8AA')
    Set-AtlasRectangle -Atlas $circleMage -X 0 -Y 16 -Width 55 -Height 26 -Color (Get-OccultColor '#30263A')
    Set-AtlasRectangle -Atlas $circleMage -X 24 -Y 16 -Width 30 -Height 14 -Color (Get-OccultColor '#493450')
    Set-AtlasRectangle -Atlas $circleMage -X 56 -Y 16 -Width 14 -Height 12 -Color (Get-OccultColor '#234E57')
    Set-AtlasRectangle -Atlas $circleMage -X 58 -Y 18 -Width 10 -Height 8 -Color (Get-OccultColor '#7FC9C2')
    Set-AtlasRectangle -Atlas $circleMage -X 61 -Y 20 -Width 4 -Height 4 -Color (Get-OccultColor '#D2E1D8')
    Set-AtlasRectangle -Atlas $circleMage -X 72 -Y 16 -Width 18 -Height 16 -Color (Get-OccultColor '#D1C5A0')
    for ($y = 19; $y -lt 30; $y += 3) {
        Set-AtlasRectangle -Atlas $circleMage -X 74 -Y $y -Width 12 -Height 1 -Color (Get-OccultColor '#65536B')
    }
    Set-AtlasRectangle -Atlas $circleMage -X 88 -Y 16 -Width 26 -Height 13 -Color (Get-OccultColor '#51314B')
    Set-AtlasRectangle -Atlas $circleMage -X 0 -Y 36 -Width 48 -Height 24 -Color (Get-OccultColor '#241D2C')
    for ($x = 2; $x -lt 46; $x += 8) {
        Set-AtlasRectangle -Atlas $circleMage -X $x -Y 38 -Width 2 -Height 18 -Color (Get-OccultColor '#3E3048')
    }
    Set-AtlasRectangle -Atlas $circleMage -X 16 -Y 36 -Width 16 -Height 12 -Color (Get-OccultColor '#6F6372')
    Save-PixelAtlas -Atlas $circleMage -Path (Join-Path $entityTextureRoot 'circle_mage.png')
}
finally {
    $circleMage.Dispose()
}

# Hedge Crone: bark, moss, root fiber, stone mortar, and amber ward knots.
$hedgeCrone = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $hedgeCrone -X 0 -Y 0 -Width 24 -Height 18 -Color (Get-OccultColor '#78634E')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 24 -Y 0 -Width 48 -Height 18 -Color (Get-OccultColor '#37402C')
    for ($x = 26; $x -lt 70; $x += 7) {
        Set-AtlasRectangle -Atlas $hedgeCrone -X $x -Y 2 -Width 2 -Height 14 -Color (Get-OccultColor '#596043')
    }
    Set-AtlasRectangle -Atlas $hedgeCrone -X 72 -Y 0 -Width 18 -Height 8 -Color (Get-OccultColor '#564231')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 75 -Y 2 -Width 3 -Height 2 -Color (Get-OccultColor '#C39243')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 0 -Y 20 -Width 58 -Height 28 -Color (Get-OccultColor '#51483A')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 28 -Y 20 -Width 30 -Height 20 -Color (Get-OccultColor '#3C4932')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 58 -Y 20 -Width 26 -Height 22 -Color (Get-OccultColor '#654B32')
    for ($y = 22; $y -lt 40; $y += 5) {
        Set-AtlasRectangle -Atlas $hedgeCrone -X 60 -Y $y -Width 20 -Height 1 -Color (Get-OccultColor '#8A6C46')
    }
    Set-AtlasRectangle -Atlas $hedgeCrone -X 0 -Y 42 -Width 46 -Height 14 -Color (Get-OccultColor '#6B7069')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 4 -Y 44 -Width 34 -Height 2 -Color (Get-OccultColor '#A2A398')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 48 -Y 42 -Width 24 -Height 16 -Color (Get-OccultColor '#7A5130')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 58 -Y 42 -Width 12 -Height 4 -Color (Get-OccultColor '#B18755')
    Set-AtlasRectangle -Atlas $hedgeCrone -X 72 -Y 42 -Width 28 -Height 20 -Color (Get-OccultColor '#8B6B2E')
    for ($x = 75; $x -lt 98; $x += 5) {
        Set-AtlasPixel -Atlas $hedgeCrone -X $x -Y 45 -Color (Get-OccultColor '#E0B75C')
    }
    Set-AtlasRectangle -Atlas $hedgeCrone -X 0 -Y 58 -Width 36 -Height 24 -Color (Get-OccultColor '#3B342B')
    Save-PixelAtlas -Atlas $hedgeCrone -Path (Join-Path $entityTextureRoot 'hedge_crone.png')
}
finally {
    $hedgeCrone.Dispose()
}

# Masculine Vampire: narrow abyssal tailoring, drowned silver, pearl closures, and coral rank marks.
$vampireMasculine = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $vampireMasculine -X 0 -Y 0 -Width 28 -Height 20 -Color (Get-OccultColor '#A9B9BA')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 1 -Y 1 -Width 26 -Height 6 -Color (Get-OccultColor '#071018')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 7 -Y 10 -Width 3 -Height 2 -Color (Get-OccultColor '#8EC6C5')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 18 -Y 10 -Width 3 -Height 2 -Color (Get-OccultColor '#8EC6C5')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 28 -Y 0 -Width 43 -Height 22 -Color (Get-OccultColor '#092330')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 47 -Y 0 -Width 3 -Height 21 -Color (Get-OccultColor '#CBD4CF')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 40 -Y 20 -Width 24 -Height 12 -Color (Get-OccultColor '#B95A4F')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 0 -Y 20 -Width 36 -Height 22 -Color (Get-OccultColor '#0A1B27')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 16 -Y 20 -Width 16 -Height 16 -Color (Get-OccultColor '#102E3C')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 32 -Y 20 -Width 7 -Height 12 -Color (Get-OccultColor '#E0DCC4')
    for ($y = 22; $y -lt 31; $y += 3) {
        Set-AtlasPixel -Atlas $vampireMasculine -X 35 -Y $y -Color (Get-OccultColor '#FFFFFF')
    }
    Set-AtlasRectangle -Atlas $vampireMasculine -X 0 -Y 40 -Width 32 -Height 18 -Color (Get-OccultColor '#050B12')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 32 -Y 40 -Width 32 -Height 18 -Color (Get-OccultColor '#0E3341')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 64 -Y 40 -Width 32 -Height 28 -Color (Get-OccultColor '#092638')
    for ($x = 66; $x -lt 94; $x += 6) {
        Set-AtlasRectangle -Atlas $vampireMasculine -X $x -Y 43 -Width 2 -Height 22 -Color (Get-OccultColor '#145064')
    }
    Set-AtlasRectangle -Atlas $vampireMasculine -X 96 -Y 40 -Width 28 -Height 24 -Color (Get-OccultColor '#061925')
    Set-AtlasRectangle -Atlas $vampireMasculine -X 98 -Y 42 -Width 24 -Height 2 -Color (Get-OccultColor '#687E83')
    # Vampire atlases are owned by generate_readable_vampires.ps1.
}
finally {
    $vampireMasculine.Dispose()
}

# Feminine Vampire: storm-teal manta shoulders, long kelp-current hair, pearl tide lines, and a coral train seal.
$vampireFeminine = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $vampireFeminine -X 0 -Y 0 -Width 28 -Height 20 -Color (Get-OccultColor '#B7C5C1')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 1 -Y 1 -Width 26 -Height 5 -Color (Get-OccultColor '#123E45')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 6 -Y 10 -Width 4 -Height 2 -Color (Get-OccultColor '#A7E2D8')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 18 -Y 10 -Width 4 -Height 2 -Color (Get-OccultColor '#A7E2D8')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 28 -Y 0 -Width 43 -Height 22 -Color (Get-OccultColor '#0C3540')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 44 -Y 0 -Width 4 -Height 20 -Color (Get-OccultColor '#E8E4D2')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 50 -Y 0 -Width 14 -Height 5 -Color (Get-OccultColor '#C96B5B')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 0 -Y 20 -Width 40 -Height 22 -Color (Get-OccultColor '#0A2631')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 16 -Y 20 -Width 20 -Height 14 -Color (Get-OccultColor '#15505B')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 32 -Y 20 -Width 8 -Height 14 -Color (Get-OccultColor '#D8D9C8')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 40 -Y 20 -Width 20 -Height 8 -Color (Get-OccultColor '#C96B5B')
    for ($y = 22; $y -lt 33; $y += 3) {
        Set-AtlasRectangle -Atlas $vampireFeminine -X 34 -Y $y -Width 3 -Height 1 -Color (Get-OccultColor '#FFFFFF')
    }
    Set-AtlasRectangle -Atlas $vampireFeminine -X 0 -Y 60 -Width 34 -Height 30 -Color (Get-OccultColor '#0E4248')
    for ($x = 2; $x -lt 32; $x += 6) {
        Set-AtlasRectangle -Atlas $vampireFeminine -X $x -Y 62 -Width 2 -Height 25 -Color (Get-OccultColor '#17616A')
    }
    Set-AtlasRectangle -Atlas $vampireFeminine -X 36 -Y 60 -Width 40 -Height 18 -Color (Get-OccultColor '#17606B')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 38 -Y 62 -Width 36 -Height 3 -Color (Get-OccultColor '#83C9C4')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 76 -Y 60 -Width 10 -Height 26 -Color (Get-OccultColor '#81AEB0')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 86 -Y 60 -Width 38 -Height 38 -Color (Get-OccultColor '#092E3B')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 90 -Y 64 -Width 30 -Height 3 -Color (Get-OccultColor '#C96B5B')
    Set-AtlasRectangle -Atlas $vampireFeminine -X 90 -Y 70 -Width 30 -Height 2 -Color (Get-OccultColor '#E6DCC4')
    # Vampire atlases are owned by generate_readable_vampires.ps1.
}
finally {
    $vampireFeminine.Dispose()
}

# Blood Thrall: lean drowned flesh constrained by shell ribs, pearl bars, and a coral obedience seal.
$bloodThrall = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $bloodThrall -X 0 -Y 0 -Width 34 -Height 16 -Color (Get-OccultColor '#708A82')
    Set-AtlasRectangle -Atlas $bloodThrall -X 5 -Y 7 -Width 4 -Height 2 -Color (Get-OccultColor '#9DCEC1')
    Set-AtlasRectangle -Atlas $bloodThrall -X 19 -Y 7 -Width 4 -Height 2 -Color (Get-OccultColor '#9DCEC1')
    Set-AtlasRectangle -Atlas $bloodThrall -X 0 -Y 14 -Width 24 -Height 24 -Color (Get-OccultColor '#405E5D')
    Set-AtlasRectangle -Atlas $bloodThrall -X 24 -Y 14 -Width 28 -Height 18 -Color (Get-OccultColor '#6E746B')
    for ($x = 26; $x -lt 50; $x += 5) {
        Set-AtlasRectangle -Atlas $bloodThrall -X $x -Y 16 -Width 2 -Height 14 -Color (Get-OccultColor '#9FA69A')
    }
    Set-AtlasRectangle -Atlas $bloodThrall -X 52 -Y 14 -Width 20 -Height 14 -Color (Get-OccultColor '#D8D5BD')
    for ($y = 16; $y -lt 27; $y += 4) {
        Set-AtlasRectangle -Atlas $bloodThrall -X 54 -Y $y -Width 16 -Height 1 -Color (Get-OccultColor '#F1EEE0')
    }
    Set-AtlasRectangle -Atlas $bloodThrall -X 72 -Y 14 -Width 24 -Height 15 -Color (Get-OccultColor '#A94D45')
    Set-AtlasRectangle -Atlas $bloodThrall -X 0 -Y 34 -Width 44 -Height 28 -Color (Get-OccultColor '#274247')
    for ($y = 36; $y -lt 60; $y += 6) {
        Set-AtlasRectangle -Atlas $bloodThrall -X 2 -Y $y -Width 38 -Height 2 -Color (Get-OccultColor '#365B5D')
    }
    Save-PixelAtlas -Atlas $bloodThrall -Path (Join-Path $entityTextureRoot 'blood_thrall.png')
}
finally {
    $bloodThrall.Dispose()
}

# Corpse: bruised earth flesh, pale reknit bone, black sutures, and uneven bindings.
$corpse = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $corpse -X 0 -Y 0 -Width 46 -Height 16 -Color (Get-OccultColor '#777267')
    Set-AtlasRectangle -Atlas $corpse -X 26 -Y 0 -Width 20 -Height 9 -Color (Get-OccultColor '#948C76')
    Set-AtlasRectangle -Atlas $corpse -X 0 -Y 16 -Width 26 -Height 28 -Color (Get-OccultColor '#655A55')
    Set-AtlasRectangle -Atlas $corpse -X 26 -Y 16 -Width 24 -Height 24 -Color (Get-OccultColor '#A19980')
    Set-AtlasRectangle -Atlas $corpse -X 50 -Y 16 -Width 24 -Height 22 -Color (Get-OccultColor '#554B50')
    Set-AtlasRectangle -Atlas $corpse -X 74 -Y 16 -Width 10 -Height 22 -Color (Get-OccultColor '#1E1D1B')
    for ($y = 18; $y -lt 36; $y += 4) {
        Set-AtlasRectangle -Atlas $corpse -X 76 -Y $y -Width 6 -Height 1 -Color (Get-OccultColor '#D0C5A8')
    }
    Set-AtlasRectangle -Atlas $corpse -X 0 -Y 34 -Width 34 -Height 28 -Color (Get-OccultColor '#4C4642')
    Set-AtlasRectangle -Atlas $corpse -X 18 -Y 34 -Width 18 -Height 22 -Color (Get-OccultColor '#827B70')
    Set-AtlasRectangle -Atlas $corpse -X 36 -Y 34 -Width 40 -Height 24 -Color (Get-OccultColor '#5C514C')
    Set-AtlasRectangle -Atlas $corpse -X 76 -Y 34 -Width 16 -Height 18 -Color (Get-OccultColor '#302C28')
    for ($x = 2; $x -lt 90; $x += 11) {
        Set-AtlasPixel -Atlas $corpse -X $x -Y 40 -Color (Get-OccultColor '#958A65')
        Set-AtlasPixel -Atlas $corpse -X ($x + 1) -Y 41 -Color (Get-OccultColor '#28231F')
    }
    Save-PixelAtlas -Atlas $corpse -Path (Join-Path $entityTextureRoot 'corpse.png')
}
finally {
    $corpse.Dispose()
}

# Warlock Hunter (registry werewolf_hunter): weathered field leather, split charcoal coat, silver bolts, warning red.
$werewolfHunter = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $werewolfHunter -X 0 -Y 0 -Width 30 -Height 20 -Color (Get-OccultColor '#6A5747')
    Set-AtlasRectangle -Atlas $werewolfHunter -X 30 -Y 0 -Width 72 -Height 20 -Color (Get-OccultColor '#302C2B')
    Set-AtlasRectangle -Atlas $werewolfHunter -X 32 -Y 2 -Width 66 -Height 3 -Color (Get-OccultColor '#524940')
    Set-AtlasRectangle -Atlas $werewolfHunter -X 0 -Y 20 -Width 30 -Height 30 -Color (Get-OccultColor '#413C38')
    Set-AtlasRectangle -Atlas $werewolfHunter -X 30 -Y 20 -Width 74 -Height 32 -Color (Get-OccultColor '#343337')
    Set-AtlasRectangle -Atlas $werewolfHunter -X 62 -Y 20 -Width 4 -Height 30 -Color (Get-OccultColor '#7B2F31')
    Set-AtlasRectangle -Atlas $werewolfHunter -X 0 -Y 42 -Width 30 -Height 24 -Color (Get-OccultColor '#879093')
    for ($y = 44; $y -lt 64; $y += 5) {
        Set-AtlasRectangle -Atlas $werewolfHunter -X 3 -Y $y -Width 24 -Height 2 -Color (Get-OccultColor '#C9D0CE')
    }
    Set-AtlasRectangle -Atlas $werewolfHunter -X 32 -Y 42 -Width 16 -Height 22 -Color (Get-OccultColor '#6A4A32')
    Set-AtlasRectangle -Atlas $werewolfHunter -X 48 -Y 42 -Width 40 -Height 26 -Color (Get-OccultColor '#4B3E35')
    Set-AtlasRectangle -Atlas $werewolfHunter -X 68 -Y 42 -Width 32 -Height 24 -Color (Get-OccultColor '#292A2C')
    Save-PixelAtlas -Atlas $werewolfHunter -Path (Join-Path $entityTextureRoot 'werewolf_hunter.png')
}
finally {
    $werewolfHunter.Dispose()
}

# Lycan Villager: authored wolf anatomy over an unmarked neutral underlayer; vanilla layers supply biome/profession/level dress.
$lycanVillager = New-PixelAtlas -Width 64 -Height 64
try {
    Set-AtlasRectangle -Atlas $lycanVillager -X 0 -Y 0 -Width 32 -Height 20 -Color (Get-OccultColor '#70665A')
    Set-AtlasRectangle -Atlas $lycanVillager -X 4 -Y 4 -Width 24 -Height 5 -Color (Get-OccultColor '#4C4742')
    Set-AtlasRectangle -Atlas $lycanVillager -X 24 -Y 0 -Width 16 -Height 10 -Color (Get-OccultColor '#8B8172')
    Set-AtlasRectangle -Atlas $lycanVillager -X 27 -Y 3 -Width 10 -Height 4 -Color (Get-OccultColor '#4A4039')
    Set-AtlasRectangle -Atlas $lycanVillager -X 32 -Y 0 -Width 32 -Height 18 -Color (Get-OccultColor '#554D45')
    Set-AtlasRectangle -Atlas $lycanVillager -X 56 -Y 0 -Width 8 -Height 8 -Color (Get-OccultColor '#3E3935')
    Set-AtlasRectangle -Atlas $lycanVillager -X 16 -Y 20 -Width 32 -Height 20 -Color (Get-OccultColor '#777066')
    Set-AtlasRectangle -Atlas $lycanVillager -X 0 -Y 22 -Width 16 -Height 18 -Color (Get-OccultColor '#686159')
    Set-AtlasRectangle -Atlas $lycanVillager -X 44 -Y 22 -Width 20 -Height 24 -Color (Get-OccultColor '#736B61')
    Set-AtlasRectangle -Atlas $lycanVillager -X 0 -Y 38 -Width 32 -Height 26 -Color (Get-OccultColor '#787168')
    Set-AtlasRectangle -Atlas $lycanVillager -X 40 -Y 38 -Width 24 -Height 8 -Color (Get-OccultColor '#736B61')
    Set-AtlasRectangle -Atlas $lycanVillager -X 30 -Y 47 -Width 34 -Height 17 -Color (Get-OccultColor '#504941')
    Set-AtlasRectangle -Atlas $lycanVillager -X 32 -Y 48 -Width 32 -Height 16 -Color (Get-OccultColor '#504941')
    Set-AtlasRectangle -Atlas $lycanVillager -X 40 -Y 48 -Width 18 -Height 9 -Color (Get-OccultColor '#3F3B38')
    for ($x = 2; $x -lt 62; $x += 7) {
        Set-AtlasPixel -Atlas $lycanVillager -X $x -Y (($x * 3) % 18) -Color (Get-OccultColor '#948A7A')
    }
    Save-PixelAtlas -Atlas $lycanVillager -Path (Join-Path $entityTextureRoot 'lycan_villager.png')
}
finally {
    $lycanVillager.Dispose()
}

Write-Output 'Generated seven independent occult-humanoid rigs across eight dedicated atlases.'
