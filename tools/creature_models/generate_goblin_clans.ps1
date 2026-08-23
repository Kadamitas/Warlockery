param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'common.ps1')

$entityTextureRoot = Join-Path $RepositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function Convert-ClanColor {
    param([Parameter(Mandatory = $true)][string]$Hex)
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function Paint-CubeUv {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$U,
        [Parameter(Mandatory = $true)][int]$V,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][int]$Depth,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Top,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Side,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Front,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Bottom
    )

    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth) -Y $V `
        -Width $Width -Height $Depth -Color $Top
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth + $Width) -Y $V `
        -Width $Width -Height $Depth -Color $Bottom
    Set-AtlasRectangle -Atlas $Atlas -X $U -Y ($V + $Depth) `
        -Width $Depth -Height $Height -Color $Side
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth) -Y ($V + $Depth) `
        -Width $Width -Height $Height -Color $Front
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth + $Width) -Y ($V + $Depth) `
        -Width $Depth -Height $Height -Color $Side
    Set-AtlasRectangle -Atlas $Atlas -X ($U + $Depth + $Width + $Depth) -Y ($V + $Depth) `
        -Width $Width -Height $Height -Color $Side
}

function Add-Speckle {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$X,
        [Parameter(Mandatory = $true)][int]$Y,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Color,
        [Parameter(Mandatory = $true)][int]$Phase
    )
    for ($row = 1; $row -lt $Height - 1; $row += 3) {
        $column = 1 + (($row * 5 + $Phase) % [Math]::Max(2, $Width - 2))
        if ($column -lt $Width - 1) {
            Set-AtlasPixel -Atlas $Atlas -X ($X + $column) -Y ($Y + $row) -Color $Color
        }
    }
}

function Paint-GoblinAtlas {
    $atlas = New-PixelAtlas -Width 128 -Height 128
    try {
        $ink = Convert-ClanColor '#18252A'
        $feather = Convert-ClanColor '#29403C'
        $moss = Convert-ClanColor '#42634E'
        $ruff = Convert-ClanColor '#A9A37A'
        $belly = Convert-ClanColor '#D9CBA4'
        $ochre = Convert-ClanColor '#D69A3E'
        $beak = Convert-ClanColor '#C8782F'
        $leather = Convert-ClanColor '#765039'
        $darkLeather = Convert-ClanColor '#402F29'
        $iron = Convert-ClanColor '#728080'
        $ore = Convert-ClanColor '#69A6AA'
        $lamp = Convert-ClanColor '#F5CC55'

        Paint-CubeUv $atlas 0 0 8 10 7 $moss $ink $feather $ink
        Paint-CubeUv $atlas 32 0 8 6 7 $feather $ink $feather $ink
        Paint-CubeUv $atlas 64 0 6 3 5 $ochre $beak $beak $darkLeather
        Paint-CubeUv $atlas 88 0 3 4 2 $moss $ink $ruff $ink
        Paint-CubeUv $atlas 100 0 3 4 2 $moss $ink $ruff $ink
        Paint-CubeUv $atlas 0 20 6 2 5 $leather $darkLeather $moss $ink
        Paint-CubeUv $atlas 24 20 2 2 2 $lamp $ochre $lamp $darkLeather
        Paint-CubeUv $atlas 34 20 6 8 1 $belly $ruff $belly $darkLeather
        Paint-CubeUv $atlas 50 20 9 2 8 $ruff $feather $ruff $ink
        Paint-CubeUv $atlas 0 34 5 3 4 $feather $ink $moss $ink
        Paint-CubeUv $atlas 22 34 4 5 3 $leather $darkLeather $leather $ink
        Paint-CubeUv $atlas 38 34 3 3 3 $ore $iron $ore $darkLeather
        Paint-CubeUv $atlas 54 34 2 7 2 $leather $darkLeather $leather $ink
        Paint-CubeUv $atlas 64 34 7 2 2 $iron $ink $iron $darkLeather
        Paint-CubeUv $atlas 82 34 2 7 4 $feather $ink $moss $ink
        Paint-CubeUv $atlas 96 34 2 5 3 $moss $ink $feather $ink
        Paint-CubeUv $atlas 0 50 2 7 4 $feather $ink $moss $ink
        Paint-CubeUv $atlas 14 50 2 5 3 $moss $ink $feather $ink
        Paint-CubeUv $atlas 28 50 3 4 3 $ochre $darkLeather $beak $ink
        Paint-CubeUv $atlas 42 50 4 2 5 $ochre $darkLeather $beak $ink
        Paint-CubeUv $atlas 62 50 3 4 3 $ochre $darkLeather $beak $ink
        Paint-CubeUv $atlas 76 50 4 2 5 $ochre $darkLeather $beak $ink

        Set-AtlasRectangle $atlas 39 24 4 5 $belly
        Set-AtlasPixel $atlas 42 8 $lamp
        Set-AtlasPixel $atlas 52 8 $lamp
        Set-AtlasRectangle $atlas 42 39 2 2 $ore
        Set-AtlasRectangle $atlas 45 39 2 3 $iron
        Add-Speckle $atlas 7 7 8 10 $moss 2
        Add-Speckle $atlas 25 7 8 10 $ink 5
        Save-PixelAtlas -Atlas $atlas -Path (Join-Path $entityTextureRoot 'goblin.png')
    }
    finally { $atlas.Dispose() }
}

function Paint-HobgoblinAtlas {
    $atlas = New-PixelAtlas -Width 192 -Height 128
    try {
        $charcoal = Convert-ClanColor '#252B2D'
        $slate = Convert-ClanColor '#3D4748'
        $gentoo = Convert-ClanColor '#EEE2C2'
        $russet = Convert-ClanColor '#7D4932'
        $umber = Convert-ClanColor '#4A3329'
        $canvas = Convert-ClanColor '#9B865F'
        $sage = Convert-ClanColor '#65755D'
        $brass = Convert-ClanColor '#B38A43'
        $amber = Convert-ClanColor '#E0A447'
        $beak = Convert-ClanColor '#C56C31'
        $iron = Convert-ClanColor '#657071'
        $blue = Convert-ClanColor '#5E8E99'

        Paint-CubeUv $atlas 0 0 9 11 8 $slate $charcoal $slate $charcoal
        Paint-CubeUv $atlas 36 0 9 7 7 $slate $charcoal $charcoal $charcoal
        Paint-CubeUv $atlas 68 0 6 3 5 $beak $russet $beak $umber
        Paint-CubeUv $atlas 92 0 10 4 8 $sage $umber $sage $charcoal
        Paint-CubeUv $atlas 130 0 3 3 2 $brass $umber $brass $charcoal
        Paint-CubeUv $atlas 144 0 7 10 1 $gentoo $canvas $gentoo $umber
        Paint-CubeUv $atlas 0 22 10 9 2 $sage $umber $canvas $charcoal
        Paint-CubeUv $atlas 26 22 6 4 5 $slate $charcoal $sage $charcoal
        Paint-CubeUv $atlas 48 22 8 9 4 $canvas $umber $canvas $charcoal
        Paint-CubeUv $atlas 74 22 9 3 3 $russet $umber $canvas $charcoal
        Paint-CubeUv $atlas 100 22 10 7 1 $iron $umber $canvas $charcoal
        Paint-CubeUv $atlas 124 22 4 5 4 $russet $umber $canvas $charcoal
        Paint-CubeUv $atlas 142 22 3 4 3 $amber $brass $amber $umber
        Paint-CubeUv $atlas 158 22 3 6 2 $umber $charcoal $russet $charcoal
        Paint-CubeUv $atlas 172 22 2 8 2 $iron $charcoal $blue $umber
        Paint-CubeUv $atlas 0 42 3 9 5 $slate $charcoal $slate $charcoal
        Paint-CubeUv $atlas 18 42 3 6 4 $sage $charcoal $slate $charcoal
        Paint-CubeUv $atlas 36 42 3 9 5 $slate $charcoal $slate $charcoal
        Paint-CubeUv $atlas 54 42 3 6 4 $sage $charcoal $slate $charcoal
        Paint-CubeUv $atlas 72 42 4 5 4 $beak $umber $russet $charcoal
        Paint-CubeUv $atlas 88 42 6 2 6 $beak $umber $beak $charcoal
        Paint-CubeUv $atlas 114 42 4 5 4 $beak $umber $russet $charcoal
        Paint-CubeUv $atlas 130 42 6 2 6 $beak $umber $beak $charcoal

        # Half-pixel leg dimensions touch the preceding texel after UV interpolation.
        Set-AtlasRectangle $atlas 72 42 18 14 $beak
        Set-AtlasRectangle $atlas 114 42 18 14 $beak

        Set-AtlasRectangle $atlas 99 9 9 2 $canvas
        Set-AtlasRectangle $atlas 55 28 9 2 $russet
        Set-AtlasRectangle $atlas 151 29 2 4 $amber
        Set-AtlasRectangle $atlas 107 26 3 10 $iron
        Set-AtlasPixel $atlas 47 8 $blue
        Set-AtlasPixel $atlas 57 8 $blue
        Add-Speckle $atlas 52 26 8 9 $russet 7
        Add-Speckle $atlas 104 25 10 7 $iron 4
        Save-PixelAtlas -Atlas $atlas -Path (Join-Path $entityTextureRoot 'hobgoblin.png')
    }
    finally { $atlas.Dispose() }
}

function Paint-StonebrokerAtlas {
    $atlas = New-PixelAtlas -Width 192 -Height 160
    try {
        $obsidian = Convert-ClanColor '#20282D'
        $blueBlack = Convert-ClanColor '#34434A'
        $cream = Convert-ClanColor '#E3D1A5'
        $gold = Convert-ClanColor '#C69A46'
        $darkGold = Convert-ClanColor '#79582D'
        $beak = Convert-ClanColor '#CA7133'
        $geode = Convert-ClanColor '#596878'
        $amethyst = Convert-ClanColor '#8C63B3'
        $crystal = Convert-ClanColor '#C9A9ED'
        $leather = Convert-ClanColor '#644735'
        $parchment = Convert-ClanColor '#C6B487'
        $teal = Convert-ClanColor '#5C9293'
        $iron = Convert-ClanColor '#7B8586'

        Paint-CubeUv $atlas 0 0 14 19 10 $blueBlack $obsidian $blueBlack $obsidian
        Paint-CubeUv $atlas 50 0 12 9 9 $blueBlack $obsidian $blueBlack $obsidian
        Paint-CubeUv $atlas 94 0 8 4 6 $gold $beak $beak $darkGold
        Paint-CubeUv $atlas 124 0 2 3 1 $crystal $darkGold $teal $obsidian
        Paint-CubeUv $atlas 132 0 1 6 1 $gold $darkGold $gold $obsidian
        Paint-CubeUv $atlas 138 0 10 16 1 $cream $gold $cream $darkGold
        Paint-CubeUv $atlas 0 24 15 3 11 $gold $darkGold $geode $obsidian
        Paint-CubeUv $atlas 54 24 7 5 7 $blueBlack $obsidian $geode $obsidian
        Paint-CubeUv $atlas 84 24 15 3 11 $geode $obsidian $amethyst $obsidian
        Paint-CubeUv $atlas 138 24 4 7 4 $crystal $amethyst $crystal $geode
        Paint-CubeUv $atlas 158 24 4 6 4 $amethyst $geode $crystal $obsidian
        Paint-CubeUv $atlas 0 42 5 8 5 $crystal $amethyst $crystal $geode
        Paint-CubeUv $atlas 22 42 3 10 8 $leather $darkGold $parchment $obsidian
        Paint-CubeUv $atlas 46 42 1 11 9 $leather $darkGold $parchment $obsidian
        Paint-CubeUv $atlas 68 42 4 2 3 $gold $darkGold $gold $obsidian
        Paint-CubeUv $atlas 84 42 5 10 4 $leather $obsidian $geode $obsidian
        Paint-CubeUv $atlas 104 42 5 8 3 $iron $obsidian $teal $obsidian
        Paint-CubeUv $atlas 124 42 4 12 6 $blueBlack $obsidian $geode $obsidian
        Paint-CubeUv $atlas 146 42 5 7 5 $geode $obsidian $amethyst $obsidian
        Paint-CubeUv $atlas 0 62 4 12 6 $blueBlack $obsidian $geode $obsidian
        Paint-CubeUv $atlas 22 62 5 7 5 $geode $obsidian $amethyst $obsidian
        Paint-CubeUv $atlas 44 62 5 6 5 $gold $darkGold $beak $obsidian
        Paint-CubeUv $atlas 66 62 8 3 8 $gold $darkGold $beak $obsidian
        Paint-CubeUv $atlas 100 62 5 6 5 $gold $darkGold $beak $obsidian
        Paint-CubeUv $atlas 122 62 8 3 8 $gold $darkGold $beak $obsidian

        Set-AtlasRectangle $atlas 150 6 5 13 $cream
        Set-AtlasRectangle $atlas 142 31 3 5 $crystal
        Set-AtlasRectangle $atlas 163 31 3 4 $amethyst
        Set-AtlasRectangle $atlas 6 49 4 7 $crystal
        Set-AtlasRectangle $atlas 28 51 2 9 $parchment
        Set-AtlasRectangle $atlas 90 48 3 9 $teal
        Set-AtlasPixel $atlas 70 9 $teal
        Set-AtlasPixel $atlas 82 9 $gold
        Add-Speckle $atlas 94 35 15 3 $crystal 3
        Add-Speckle $atlas 60 10 12 9 $blueBlack 9
        Save-PixelAtlas -Atlas $atlas -Path (Join-Path $entityTextureRoot 'stonebroker.png')
    }
    finally { $atlas.Dispose() }
}

function Paint-ForgewardenAtlas {
    $atlas = New-PixelAtlas -Width 192 -Height 160
    try {
        $soot = Convert-ClanColor '#1C2023'
        $iron = Convert-ClanColor '#41494D'
        $steel = Convert-ClanColor '#697176'
        $brightSteel = Convert-ClanColor '#9A9D98'
        $cream = Convert-ClanColor '#D6C6A0'
        $brass = Convert-ClanColor '#A87835'
        $copper = Convert-ClanColor '#9D5130'
        $ember = Convert-ClanColor '#E95A24'
        $yellow = Convert-ClanColor '#FFB83D'
        $beak = Convert-ClanColor '#C96C2F'
        $leather = Convert-ClanColor '#5A382B'
        $ward = Convert-ClanColor '#6E4C83'
        $ash = Convert-ClanColor '#77706A'

        Paint-CubeUv $atlas 0 0 15 21 11 $iron $soot $steel $soot
        Paint-CubeUv $atlas 54 0 12 9 9 $iron $soot $steel $soot
        Paint-CubeUv $atlas 98 0 8 4 7 $brass $beak $beak $leather
        Paint-CubeUv $atlas 130 0 13 4 10 $steel $soot $iron $soot
        Paint-CubeUv $atlas 0 24 11 18 2 $brightSteel $iron $cream $soot
        Paint-CubeUv $atlas 30 24 6 7 2 $yellow $copper $ember $soot
        Paint-CubeUv $atlas 50 24 7 6 8 $iron $soot $steel $soot
        Paint-CubeUv $atlas 82 24 17 4 12 $steel $soot $brass $soot
        Paint-CubeUv $atlas 142 24 6 5 7 $steel $soot $iron $soot
        Paint-CubeUv $atlas 0 46 6 5 7 $steel $soot $iron $soot
        Paint-CubeUv $atlas 28 46 12 10 4 $leather $soot $ash $soot
        Paint-CubeUv $atlas 62 46 10 4 3 $iron $soot $copper $soot
        Paint-CubeUv $atlas 90 46 3 7 3 $steel $soot $ash $soot
        Paint-CubeUv $atlas 104 46 3 6 3 $steel $soot $ash $soot
        Paint-CubeUv $atlas 118 46 16 3 11 $leather $soot $brass $soot
        Paint-CubeUv $atlas 0 68 2 9 2 $steel $soot $brightSteel $soot
        Paint-CubeUv $atlas 12 68 4 3 2 $brass $soot $brass $soot
        Paint-CubeUv $atlas 28 68 5 13 6 $iron $soot $steel $soot
        Paint-CubeUv $atlas 52 68 6 7 7 $steel $soot $brass $soot
        Paint-CubeUv $atlas 80 68 10 6 7 $brightSteel $soot $iron $soot
        Paint-CubeUv $atlas 116 68 5 13 6 $iron $soot $steel $soot
        Paint-CubeUv $atlas 140 68 5 10 8 $ward $soot $brightSteel $soot
        Paint-CubeUv $atlas 0 90 6 7 6 $beak $leather $copper $soot
        Paint-CubeUv $atlas 24 90 9 3 9 $beak $leather $copper $soot
        Paint-CubeUv $atlas 62 90 6 7 6 $beak $leather $copper $soot
        Paint-CubeUv $atlas 86 90 9 3 9 $beak $leather $copper $soot

        # Half-pixel leg dimensions touch the preceding texel after UV interpolation.
        Set-AtlasRectangle $atlas 0 90 24 20 $copper
        Set-AtlasRectangle $atlas 62 90 26 20 $copper

        Set-AtlasRectangle $atlas 4 37 10 4 $cream
        Set-AtlasRectangle $atlas 35 28 4 5 $yellow
        Set-AtlasRectangle $atlas 36 33 4 4 $ember
        Set-AtlasRectangle $atlas 91 31 12 2 $brass
        Set-AtlasRectangle $atlas 35 52 8 5 $ash
        Set-AtlasRectangle $atlas 145 75 5 8 $ward
        Set-AtlasRectangle $atlas 89 75 8 5 $brightSteel
        Set-AtlasPixel $atlas 76 9 $yellow
        Set-AtlasPixel $atlas 87 9 $ember
        Add-Speckle $atlas 11 11 15 21 $ash 5
        Add-Speckle $atlas 39 52 12 10 $copper 8
        Save-PixelAtlas -Atlas $atlas -Path (Join-Path $entityTextureRoot 'forgewarden.png')
    }
    finally { $atlas.Dispose() }
}

Paint-GoblinAtlas
Paint-HobgoblinAtlas
Paint-StonebrokerAtlas
Paint-ForgewardenAtlas

Write-Output 'Generated four transparent, independently mapped penguin-clan atlases.'
