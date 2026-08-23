param()

. (Join-Path $PSScriptRoot 'common.ps1')

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$entityTextureRoot = Join-Path $repositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function Paint-CreatureIsland {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$X,
        [Parameter(Mandatory = $true)][int]$Y,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Base,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Shade,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Highlight,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Accent,
        [int]$Cadence = 5
    )

    Set-AtlasRectangle -Atlas $Atlas -X $X -Y $Y -Width $Width -Height $Height -Color $Base
    Set-AtlasRectangle -Atlas $Atlas -X $X -Y $Y -Width $Width -Height 1 -Color $Highlight
    Set-AtlasRectangle -Atlas $Atlas -X $X -Y ($Y + $Height - 1) -Width $Width -Height 1 -Color $Shade
    Set-AtlasRectangle -Atlas $Atlas -X $X -Y $Y -Width 1 -Height $Height -Color $Highlight
    Set-AtlasRectangle -Atlas $Atlas -X ($X + $Width - 1) -Y $Y -Width 1 -Height $Height -Color $Shade
    for ($row = 2; $row -lt ($Height - 1); $row += [Math]::Max(3, $Cadence)) {
        for ($column = 2 + (($row + $X + $Y) % 4); $column -lt ($Width - 1); $column += [Math]::Max(5, $Cadence + 2)) {
            Set-AtlasPixel -Atlas $Atlas -X ($X + $column) -Y ($Y + $row) -Color $Accent
            if (($column + 1) -lt ($Width - 1)) {
                Set-AtlasPixel -Atlas $Atlas -X ($X + $column + 1) -Y ($Y + $row) -Color $Shade
            }
        }
    }
}

function Paint-Bands {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][object[]]$Bands,
        [Parameter(Mandatory = $true)][System.Drawing.Color[]]$Palette
    )
    for ($index = 0; $index -lt $Bands.Count; $index++) {
        $band = $Bands[$index]
        Paint-CreatureIsland -Atlas $Atlas -X $band[0] -Y $band[1] -Width $band[2] -Height $band[3] `
            -Base $Palette[0] -Shade $Palette[1] -Highlight $Palette[2] -Accent $Palette[3] -Cadence (4 + ($index % 3))
    }
}

# Demon: soot-black plate fields over blood-red infernal skin, with ember cracks and muted horn gold.
$demon = New-PixelAtlas -Width 128 -Height 128
try {
    $demonPalette = @(
        [System.Drawing.Color]::FromArgb(255, 91, 29, 24),
        [System.Drawing.Color]::FromArgb(255, 20, 17, 20),
        [System.Drawing.Color]::FromArgb(255, 154, 58, 34),
        [System.Drawing.Color]::FromArgb(255, 246, 91, 22)
    )
    Paint-Bands -Atlas $demon -Palette $demonPalette -Bands @(
        @(0, 0, 82, 16), @(0, 18, 96, 14), @(0, 34, 66, 16), @(0, 52, 86, 20)
    )
    Set-AtlasRectangle -Atlas $demon -X 42 -Y 18 -Width 12 -Height 3 -Color ([System.Drawing.Color]::FromArgb(255, 246, 177, 54))
    Set-AtlasRectangle -Atlas $demon -X 66 -Y 18 -Width 5 -Height 5 -Color ([System.Drawing.Color]::FromArgb(255, 184, 112, 42))
    Save-PixelAtlas -Atlas $demon -Path (Join-Path $entityTextureRoot 'demon.png')
}
finally { $demon.Dispose() }

# Emberhorn Archfiend: obsidian anatomy, guarded crimson heart, magma horns and fists.
$archfiend = New-PixelAtlas -Width 192 -Height 128
try {
    $archfiendPalette = @(
        [System.Drawing.Color]::FromArgb(255, 47, 28, 27),
        [System.Drawing.Color]::FromArgb(255, 13, 14, 17),
        [System.Drawing.Color]::FromArgb(255, 100, 54, 39),
        [System.Drawing.Color]::FromArgb(255, 255, 91, 20)
    )
    Paint-Bands -Atlas $archfiend -Palette $archfiendPalette -Bands @(
        @(0, 0, 172, 18), @(0, 18, 132, 20), @(0, 42, 126, 22), @(0, 66, 160, 20)
    )
    Set-AtlasRectangle -Atlas $archfiend -X 98 -Y 0 -Width 22 -Height 12 -Color ([System.Drawing.Color]::FromArgb(255, 155, 19, 28))
    Set-AtlasRectangle -Atlas $archfiend -X 120 -Y 0 -Width 11 -Height 8 -Color ([System.Drawing.Color]::FromArgb(255, 255, 157, 45))
    Set-AtlasRectangle -Atlas $archfiend -X 132 -Y 0 -Width 28 -Height 9 -Color ([System.Drawing.Color]::FromArgb(255, 194, 42, 30))
    Save-PixelAtlas -Atlas $archfiend -Path (Join-Path $entityTextureRoot 'emberhorn_archfiend.png')
}
finally { $archfiend.Dispose() }

# Abyssal Regent: blackened torment shell, six maroon-violet wing fields, fire core, cold void erosion.
$regent = New-PixelAtlas -Width 256 -Height 128
try {
    $regentPalette = @(
        [System.Drawing.Color]::FromArgb(255, 39, 21, 39),
        [System.Drawing.Color]::FromArgb(255, 8, 10, 18),
        [System.Drawing.Color]::FromArgb(255, 87, 39, 82),
        [System.Drawing.Color]::FromArgb(255, 100, 86, 200)
    )
    Paint-Bands -Atlas $regent -Palette $regentPalette -Bands @(
        @(0, 0, 214, 20), @(0, 24, 224, 16), @(0, 50, 166, 20)
    )
    Set-AtlasRectangle -Atlas $regent -X 98 -Y 0 -Width 28 -Height 12 -Color ([System.Drawing.Color]::FromArgb(255, 184, 42, 27))
    Set-AtlasRectangle -Atlas $regent -X 104 -Y 2 -Width 16 -Height 8 -Color ([System.Drawing.Color]::FromArgb(255, 255, 119, 28))
    for ($x = 8; $x -lt 218; $x += 17) {
        Set-AtlasPixel -Atlas $regent -X $x -Y 28 -Color ([System.Drawing.Color]::FromArgb(255, 68, 213, 226))
        Set-AtlasPixel -Atlas $regent -X ($x + 1) -Y 29 -Color ([System.Drawing.Color]::FromArgb(255, 131, 61, 195))
    }
    Save-PixelAtlas -Atlas $regent -Path (Join-Path $entityTextureRoot 'abyssal_regent.png')
}
finally { $regent.Dispose() }

# Death: charcoal appointment shroud, aged iron scythe, electric-blue eyes and cold lantern glass.
$death = New-PixelAtlas -Width 128 -Height 128
try {
    $deathPalette = @(
        [System.Drawing.Color]::FromArgb(255, 39, 42, 45),
        [System.Drawing.Color]::FromArgb(255, 11, 13, 17),
        [System.Drawing.Color]::FromArgb(255, 91, 88, 80),
        [System.Drawing.Color]::FromArgb(255, 104, 132, 128)
    )
    Paint-Bands -Atlas $death -Palette $deathPalette -Bands @(
        @(0, 0, 72, 18), @(0, 20, 128, 18), @(0, 40, 108, 30), @(0, 58, 110, 20), @(0, 78, 26, 16)
    )
    Set-AtlasRectangle -Atlas $death -X 64 -Y 20 -Width 8 -Height 4 -Color ([System.Drawing.Color]::FromArgb(255, 57, 205, 255))
    Set-AtlasRectangle -Atlas $death -X 0 -Y 58 -Width 20 -Height 12 -Color ([System.Drawing.Color]::FromArgb(255, 48, 132, 145))
    Set-AtlasRectangle -Atlas $death -X 56 -Y 40 -Width 48 -Height 4 -Color ([System.Drawing.Color]::FromArgb(255, 112, 111, 103))
    Save-PixelAtlas -Atlas $death -Path (Join-Path $entityTextureRoot 'death.png')
}
finally { $death.Dispose() }

# Ironbound Sentinel: weathered iron and masonry, brown leather harness, deep-blue tabard, brass bell and moss.
$sentinel = New-PixelAtlas -Width 192 -Height 128
try {
    $sentinelPalette = @(
        [System.Drawing.Color]::FromArgb(255, 83, 82, 76),
        [System.Drawing.Color]::FromArgb(255, 31, 34, 36),
        [System.Drawing.Color]::FromArgb(255, 137, 132, 116),
        [System.Drawing.Color]::FromArgb(255, 75, 94, 72)
    )
    Paint-Bands -Atlas $sentinel -Palette $sentinelPalette -Bands @(
        @(0, 0, 192, 22), @(0, 22, 192, 18), @(0, 42, 188, 22), @(0, 66, 100, 20)
    )
    Set-AtlasRectangle -Atlas $sentinel -X 124 -Y 0 -Width 64 -Height 12 -Color ([System.Drawing.Color]::FromArgb(255, 92, 56, 35))
    Set-AtlasRectangle -Atlas $sentinel -X 20 -Y 22 -Width 44 -Height 14 -Color ([System.Drawing.Color]::FromArgb(255, 105, 63, 38))
    Set-AtlasRectangle -Atlas $sentinel -X 66 -Y 22 -Width 36 -Height 14 -Color ([System.Drawing.Color]::FromArgb(255, 35, 57, 101))
    Set-AtlasRectangle -Atlas $sentinel -X 104 -Y 22 -Width 54 -Height 12 -Color ([System.Drawing.Color]::FromArgb(255, 181, 128, 45))
    Save-PixelAtlas -Atlas $sentinel -Path (Join-Path $entityTextureRoot 'ironbound_sentinel.png')
}
finally { $sentinel.Dispose() }
