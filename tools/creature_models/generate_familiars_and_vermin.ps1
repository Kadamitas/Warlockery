$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'common.ps1')

function New-Color {
    param([int]$Red, [int]$Green, [int]$Blue)
    return [System.Drawing.Color]::FromArgb(255, $Red, $Green, $Blue)
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

function Paint-CatAtlas {
    $atlas = New-PixelAtlas -Width 128 -Height 128
    $soot = New-Color 49 38 36
    $brown = New-Color 68 47 39
    $warm = New-Color 91 57 39
    $cream = New-Color 211 187 132
    $copper = New-Color 174 91 43
    $dark = New-Color 30 27 30
    $amber = New-Color 239 180 55

    Paint-CubeUv $atlas 0 0 8 7 7 $warm $soot $brown $dark
    Paint-CubeUv $atlas 30 0 5 3 3 $cream $cream $cream $brown
    Paint-CubeUv $atlas 46 0 2 4 2 $brown $dark $warm $dark
    Paint-CubeUv $atlas 54 0 2 4 2 $brown $dark $warm $dark
    Paint-CubeUv $atlas 62 0 2 3 2 $warm $brown $warm $dark
    Paint-CubeUv $atlas 70 0 2 3 2 $warm $brown $warm $dark
    Paint-CubeUv $atlas 0 16 10 7 15 $warm $soot $brown $dark
    Paint-CubeUv $atlas 52 16 8 7 5 $brown $soot $warm $dark
    Paint-CubeUv $atlas 80 16 9 1 10 $copper $copper $copper $dark
    Paint-CubeUv $atlas 118 16 2 3 1 $copper $copper $copper $dark
    foreach ($u in 0, 12, 24, 36) {
        Paint-CubeUv $atlas $u 40 3 7 3 $brown $soot $warm $dark
    }
    foreach ($u in 48, 66, 84, 102) {
        Paint-CubeUv $atlas $u 40 4 2 5 $cream $warm $cream $brown
    }
    Paint-CubeUv $atlas 0 52 3 9 3 $warm $brown $warm $dark
    Paint-CubeUv $atlas 14 52 3 8 3 $warm $brown $warm $dark
    Paint-CubeUv $atlas 28 52 4 5 4 $dark $dark $soot $dark

    Set-AtlasRectangle $atlas 8 7 2 2 $amber
    Set-AtlasRectangle $atlas 18 7 2 2 $amber
    Set-AtlasRectangle $atlas 13 9 2 1 $dark
    Set-AtlasRectangle $atlas 9 4 10 2 $copper
    Set-AtlasRectangle $atlas 65 20 2 5 $copper
    # Familiar Cat is owned by generate_readable_familiar_textures.ps1.
    $atlas.Dispose()
}

function Paint-OwlAtlas {
    $atlas = New-PixelAtlas -Width 128 -Height 128
    $umber = New-Color 79 58 44
    $dark = New-Color 42 39 38
    $cream = New-Color 213 201 169
    $parchment = New-Color 179 158 119
    $copper = New-Color 166 102 59
    $moss = New-Color 91 141 67
    $beak = New-Color 181 142 67

    Paint-CubeUv $atlas 0 0 10 12 8 $umber $dark $umber $dark
    Paint-CubeUv $atlas 38 0 8 10 3 $cream $parchment $cream $umber
    Paint-CubeUv $atlas 62 0 10 9 8 $umber $dark $cream $dark
    Paint-CubeUv $atlas 0 22 11 8 2 $cream $parchment $cream $umber
    Paint-CubeUv $atlas 28 22 2 3 3 $beak $beak $beak $dark
    Paint-CubeUv $atlas 40 22 3 4 2 $umber $dark $umber $dark
    Paint-CubeUv $atlas 50 22 3 4 2 $umber $dark $umber $dark
    Paint-CubeUv $atlas 0 34 4 11 8 $umber $dark $copper $dark
    Paint-CubeUv $atlas 26 34 4 11 8 $umber $dark $copper $dark
    Paint-CubeUv $atlas 52 34 3 10 2 $dark $umber $copper $dark
    Paint-CubeUv $atlas 62 34 3 10 2 $dark $umber $copper $dark
    Paint-CubeUv $atlas 72 34 3 11 2 $dark $umber $parchment $dark
    Paint-CubeUv $atlas 82 34 3 11 2 $dark $umber $parchment $dark
    Paint-CubeUv $atlas 92 34 3 4 3 $umber $dark $umber $dark
    Paint-CubeUv $atlas 104 34 3 4 3 $umber $dark $umber $dark
    Paint-CubeUv $atlas 0 56 4 2 5 $beak $umber $beak $dark
    Paint-CubeUv $atlas 18 56 4 2 5 $beak $umber $beak $dark
    Paint-CubeUv $atlas 36 56 4 3 8 $umber $dark $copper $dark
    Paint-CubeUv $atlas 60 56 4 3 8 $umber $dark $copper $dark
    Paint-CubeUv $atlas 84 56 4 3 8 $umber $dark $parchment $dark

    Set-AtlasRectangle $atlas 10 24 3 3 $moss
    Set-AtlasRectangle $atlas 20 24 3 3 $moss
    Set-AtlasRectangle $atlas 20 43 3 7 $copper
    Set-AtlasRectangle $atlas 46 43 3 7 $copper
    Save-PixelAtlas -Atlas $atlas -Path 'src/main/resources/assets/warlockery/textures/entity/owl.png'
    $atlas.Dispose()
}

function Paint-ToadAtlas {
    $atlas = New-PixelAtlas -Width 128 -Height 128
    $moss = New-Color 91 98 45
    $olive = New-Color 120 116 54
    $peat = New-Color 69 57 38
    $lichen = New-Color 148 139 67
    $ochre = New-Color 185 156 86
    $amber = New-Color 222 171 54
    $dark = New-Color 38 35 24

    Paint-CubeUv $atlas 0 0 14 6 10 $olive $moss $lichen $peat
    Paint-CubeUv $atlas 48 0 13 5 7 $moss $peat $olive $dark
    Paint-CubeUv $atlas 0 18 4 4 4 $lichen $moss $amber $peat
    Paint-CubeUv $atlas 16 18 4 4 4 $lichen $moss $amber $peat
    Paint-CubeUv $atlas 32 18 9 3 3 $ochre $moss $ochre $peat
    Paint-CubeUv $atlas 56 18 3 6 3 $olive $peat $lichen $dark
    Paint-CubeUv $atlas 68 18 3 6 3 $olive $peat $lichen $dark
    Paint-CubeUv $atlas 80 18 5 2 6 $moss $peat $lichen $dark
    Paint-CubeUv $atlas 102 18 5 2 6 $moss $peat $lichen $dark
    Paint-CubeUv $atlas 0 32 5 5 7 $olive $peat $moss $dark
    Paint-CubeUv $atlas 24 32 5 5 7 $olive $peat $moss $dark
    Paint-CubeUv $atlas 48 32 7 3 8 $moss $peat $lichen $dark
    Paint-CubeUv $atlas 78 32 7 3 8 $moss $peat $lichen $dark
    Paint-CubeUv $atlas 0 50 8 2 6 $ochre $peat $ochre $dark
    Paint-CubeUv $atlas 28 50 3 2 3 $peat $peat $lichen $dark
    Paint-CubeUv $atlas 40 50 3 2 3 $peat $peat $lichen $dark
    Paint-CubeUv $atlas 52 50 3 2 3 $peat $peat $lichen $dark

    Set-AtlasRectangle $atlas 10 12 4 2 $peat
    Set-AtlasRectangle $atlas 22 12 4 2 $peat
    Set-AtlasRectangle $atlas 12 4 5 2 $lichen
    Set-AtlasRectangle $atlas 26 4 5 2 $lichen
    Set-AtlasRectangle $atlas 60 4 2 2 $amber
    Set-AtlasRectangle $atlas 72 4 2 2 $amber
    # Toad is owned by generate_readable_familiar_textures.ps1.
    $atlas.Dispose()
}

function Paint-HexBatAtlas {
    $atlas = New-PixelAtlas -Width 128 -Height 128
    $charcoal = New-Color 35 34 45
    $black = New-Color 23 24 32
    $mulberry = New-Color 86 52 78
    $violet = New-Color 123 77 126
    $lilac = New-Color 168 117 172
    $teal = New-Color 58 189 177

    Paint-CubeUv $atlas 0 0 9 6 10 $charcoal $black $mulberry $black
    Paint-CubeUv $atlas 38 0 10 5 7 $charcoal $black $mulberry $black
    Paint-CubeUv $atlas 72 0 4 2 3 $mulberry $black $mulberry $black
    Paint-CubeUv $atlas 86 0 2 7 3 $charcoal $black $violet $black
    Paint-CubeUv $atlas 96 0 2 7 3 $charcoal $black $violet $black
    Paint-CubeUv $atlas 0 18 7 2 6 $charcoal $black $mulberry $black
    Paint-CubeUv $atlas 26 18 10 2 5 $charcoal $black $violet $black
    Paint-CubeUv $atlas 56 18 12 1 7 $charcoal $black $mulberry $black
    Paint-CubeUv $atlas 0 30 7 2 6 $charcoal $black $mulberry $black
    Paint-CubeUv $atlas 26 30 10 2 5 $charcoal $black $violet $black
    Paint-CubeUv $atlas 56 30 12 1 7 $charcoal $black $mulberry $black
    Paint-CubeUv $atlas 0 42 5 1 8 $mulberry $black $violet $black
    Paint-CubeUv $atlas 26 42 5 1 8 $mulberry $black $violet $black
    Paint-CubeUv $atlas 52 42 5 1 10 $mulberry $black $lilac $black
    Paint-CubeUv $atlas 84 42 5 1 10 $mulberry $black $lilac $black
    Paint-CubeUv $atlas 0 56 2 4 2 $charcoal $black $mulberry $black
    Paint-CubeUv $atlas 8 56 2 4 2 $charcoal $black $mulberry $black
    Paint-CubeUv $atlas 16 56 3 2 4 $violet $black $lilac $black
    Paint-CubeUv $atlas 30 56 3 2 4 $violet $black $lilac $black
    Paint-CubeUv $atlas 44 56 5 1 5 $mulberry $black $violet $black

    Set-AtlasRectangle $atlas 47 7 2 2 $teal
    Set-AtlasRectangle $atlas 57 7 2 2 $teal
    Set-AtlasRectangle $atlas 63 24 3 2 $lilac
    Set-AtlasRectangle $atlas 68 24 3 2 $lilac
    Set-AtlasRectangle $atlas 63 36 3 2 $violet
    Set-AtlasRectangle $atlas 68 36 3 2 $violet
    Save-PixelAtlas -Atlas $atlas -Path 'src/main/resources/assets/warlockery/textures/entity/hex_bat.png'
    $atlas.Dispose()
}

function Paint-LouseAtlas {
    $atlas = New-PixelAtlas -Width 128 -Height 128
    $umber = New-Color 72 48 47
    $mauve = New-Color 104 70 75
    $dark = New-Color 42 31 35
    $bone = New-Color 181 157 133
    $wine = New-Color 126 38 54
    $lilac = New-Color 164 113 185

    Paint-CubeUv $atlas 0 0 7 3 6 $umber $dark $mauve $dark
    Paint-CubeUv $atlas 26 0 9 4 8 $mauve $umber $mauve $dark
    Paint-CubeUv $atlas 60 0 11 4 10 $umber $dark $mauve $dark
    Paint-CubeUv $atlas 0 16 10 4 9 $mauve $dark $umber $dark
    Paint-CubeUv $atlas 38 16 9 3 8 $umber $dark $mauve $dark
    Paint-CubeUv $atlas 72 16 3 2 3 $wine $dark $wine $dark
    Paint-CubeUv $atlas 84 16 2 1 4 $bone $dark $wine $dark
    Paint-CubeUv $atlas 96 16 2 1 4 $bone $dark $wine $dark
    foreach ($entry in @(
        @(0, 30), @(16, 30), @(32, 30), @(48, 30), @(64, 30), @(80, 30)
    )) {
        Paint-CubeUv $atlas $entry[0] $entry[1] 5 2 3 $bone $umber $mauve $dark
    }
    foreach ($entry in @(
        @(0, 40), @(12, 40), @(24, 40), @(36, 40), @(48, 40), @(60, 40)
    )) {
        Paint-CubeUv $atlas $entry[0] $entry[1] 2 2 3 $bone $dark $wine $dark
    }

    Set-AtlasRectangle $atlas 70 10 2 2 $lilac
    Set-AtlasRectangle $atlas 76 10 2 2 $lilac
    Set-AtlasRectangle $atlas 81 19 3 2 $wine
    Set-AtlasRectangle $atlas 48 8 3 3 $wine
    Save-PixelAtlas -Atlas $atlas -Path 'src/main/resources/assets/warlockery/textures/entity/parasytic_louse.png'
    $atlas.Dispose()
}

Paint-CatAtlas
Paint-OwlAtlas
Paint-ToadAtlas
Paint-HexBatAtlas
Paint-LouseAtlas
