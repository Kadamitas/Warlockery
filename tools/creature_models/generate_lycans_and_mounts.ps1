param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'common.ps1')

$entityTextureRoot = Join-Path $RepositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function Convert-LycanMountColor {
    param([Parameter(Mandatory = $true)][string]$Hex)

    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function Paint-LycanMountIsland {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][object[]]$Region,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Base,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Shade,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Highlight,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Accent,
        [Parameter(Mandatory = $true)][int]$Phase
    )

    $x = [int]$Region[0]
    $y = [int]$Region[1]
    $width = [int]$Region[2]
    $height = [int]$Region[3]
    Set-AtlasRectangle -Atlas $Atlas -X $x -Y $y -Width $width -Height $height -Color $Base
    Set-AtlasRectangle -Atlas $Atlas -X $x -Y $y -Width $width -Height 1 -Color $Highlight
    Set-AtlasRectangle -Atlas $Atlas -X $x -Y ($y + $height - 1) -Width $width -Height 1 -Color $Shade
    Set-AtlasRectangle -Atlas $Atlas -X $x -Y $y -Width 1 -Height $height -Color $Highlight
    Set-AtlasRectangle -Atlas $Atlas -X ($x + $width - 1) -Y $y -Width 1 -Height $height -Color $Shade
    for ($row = 2; $row -lt $height - 1; $row += 4) {
        $column = 2 + (($row + $Phase) % [Math]::Max(2, $width - 4))
        if ($column -lt $width - 1) {
            Set-AtlasPixel -Atlas $Atlas -X ($x + $column) -Y ($y + $row) -Color $Accent
        }
    }
}

function New-LycanMountAtlas {
    param(
        [Parameter(Mandatory = $true)][string]$FileName,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][string[]]$Palette,
        [Parameter(Mandatory = $true)][object[]]$Regions,
        [Parameter(Mandatory = $true)][scriptblock]$Details
    )

    $atlas = New-PixelAtlas -Width $Width -Height $Height
    try {
        $colors = @($Palette | ForEach-Object { Convert-LycanMountColor $_ })
        for ($index = 0; $index -lt $Regions.Count; $index++) {
            Paint-LycanMountIsland -Atlas $atlas -Region $Regions[$index] `
                -Base $colors[0] -Shade $colors[1] -Highlight $colors[2] -Accent $colors[3] `
                -Phase ($index * 3 + 1)
        }
        & $Details $atlas $colors
        Save-PixelAtlas -Atlas $atlas -Path (Join-Path $entityTextureRoot $FileName)
    }
    finally {
        $atlas.Dispose()
    }
}

$werewolfRegions = @(
    @(0,0,30,15), @(32,0,22,10), @(56,0,8,7), @(66,0,8,7), @(76,0,24,8),
    @(0,18,38,18), @(40,18,26,13), @(68,18,32,13),
    @(0,40,16,12), @(18,40,20,15), @(40,40,22,10),
    @(64,40,16,12), @(82,40,20,15), @(104,40,22,10),
    @(0,58,20,13), @(22,58,16,12), @(40,58,26,12),
    @(68,58,20,13), @(90,58,16,12), @(108,58,26,12),
    @(0,74,12,11), @(14,74,10,11), @(26,74,8,8), @(36,74,16,9), @(54,74,16,9)
)
New-LycanMountAtlas -FileName 'werewolf.png' -Width 192 -Height 192 `
    -Palette @('#58616A', '#252B32', '#87929B', '#A25D4B') -Regions $werewolfRegions -Details {
        param($atlas, $colors)
        Set-AtlasRectangle -Atlas $atlas -X 10 -Y 23 -Width 2 -Height 11 -Color $colors[3]
        Set-AtlasRectangle -Atlas $atlas -X 12 -Y 25 -Width 2 -Height 9 -Color $colors[3]
        Set-AtlasPixel -Atlas $atlas -X 9 -Y 6 -Color (Convert-LycanMountColor '#D9B45A')
        Set-AtlasPixel -Atlas $atlas -X 20 -Y 6 -Color (Convert-LycanMountColor '#D9B45A')
    }

$feralRegions = @(
    @(0,0,28,14), @(30,0,24,11), @(56,0,8,9), @(66,0,8,9), @(76,0,18,10),
    @(96,0,14,9), @(112,0,14,9),
    @(0,18,28,18), @(30,18,22,13), @(54,18,24,12),
    @(80,18,14,10), @(96,18,12,11), @(110,18,10,9),
    @(0,40,12,14), @(14,40,16,15), @(32,40,18,11),
    @(52,40,12,14), @(66,40,16,15), @(84,40,18,11),
    @(0,58,16,14), @(18,58,12,13), @(32,58,24,11),
    @(58,58,16,14), @(76,58,12,13), @(90,58,24,11),
    @(0,76,12,11), @(14,76,10,10), @(26,76,8,9), @(36,76,12,8), @(50,76,10,9)
)
New-LycanMountAtlas -FileName 'feral_lycan.png' -Width 192 -Height 160 `
    -Palette @('#756E63', '#242526', '#B8AE97', '#3B2723') -Regions $feralRegions -Details {
        param($atlas, $colors)
        Set-AtlasRectangle -Atlas $atlas -X 4 -Y 23 -Width 8 -Height 3 -Color $colors[1]
        Set-AtlasRectangle -Atlas $atlas -X 18 -Y 28 -Width 7 -Height 3 -Color $colors[2]
        Set-AtlasRectangle -Atlas $atlas -X 34 -Y 43 -Width 3 -Height 8 -Color $colors[3]
        Set-AtlasPixel -Atlas $atlas -X 8 -Y 6 -Color (Convert-LycanMountColor '#E7D26A')
        Set-AtlasPixel -Atlas $atlas -X 19 -Y 6 -Color (Convert-LycanMountColor '#E7D26A')
    }

$hellhoundRegions = @(
    @(0,0,34,16), @(36,0,28,11), @(66,0,24,9), @(92,0,12,10), @(106,0,12,10),
    @(120,0,10,9), @(132,0,10,9),
    @(0,20,36,19), @(38,20,54,19), @(94,20,34,16), @(130,20,24,11),
    @(0,44,18,14), @(20,44,16,14), @(38,44,24,11),
    @(64,44,18,14), @(84,44,16,14), @(102,44,24,11),
    @(0,62,20,14), @(22,62,16,14), @(40,62,24,11),
    @(66,62,20,14), @(88,62,16,14), @(106,62,24,11),
    @(0,80,12,12), @(14,80,10,11), @(26,80,10,10), @(38,80,10,9),
    @(50,80,10,9), @(62,80,10,9), @(74,80,12,9), @(88,80,12,9)
)
New-LycanMountAtlas -FileName 'hellhound.png' -Width 256 -Height 160 `
    -Palette @('#342F31', '#161517', '#625255', '#D34825') -Regions $hellhoundRegions -Details {
        param($atlas, $colors)
        $ember = Convert-LycanMountColor '#FF8A2C'
        Set-AtlasRectangle -Atlas $atlas -X 42 -Y 4 -Width 16 -Height 3 -Color $ember
        Set-AtlasRectangle -Atlas $atlas -X 8 -Y 26 -Width 2 -Height 9 -Color $colors[3]
        Set-AtlasRectangle -Atlas $atlas -X 54 -Y 24 -Width 2 -Height 12 -Color $colors[3]
        Set-AtlasRectangle -Atlas $atlas -X 110 -Y 24 -Width 2 -Height 9 -Color $colors[3]
        Set-AtlasPixel -Atlas $atlas -X 10 -Y 7 -Color $ember
        Set-AtlasPixel -Atlas $atlas -X 23 -Y 7 -Color $ember
    }

$paleSteedRegions = @(
    @(0,0,30,17), @(32,0,22,13), @(56,0,8,14), @(66,0,8,14), @(76,0,18,13),
    @(96,0,20,14), @(118,0,22,15),
    @(0,22,38,20), @(40,22,50,19), @(92,22,36,18), @(130,22,18,11),
    @(0,46,16,18), @(18,46,12,18), @(32,46,22,11),
    @(56,46,16,18), @(74,46,12,18), @(88,46,22,11),
    @(0,68,20,17), @(22,68,12,17), @(36,68,22,11),
    @(60,68,20,17), @(82,68,12,17), @(96,68,22,11),
    @(0,90,12,10), @(14,90,12,10), @(28,90,12,10), @(42,90,10,12),
    @(54,90,10,12), @(66,90,10,11), @(78,90,10,10), @(90,90,10,9),
    @(102,90,8,9), @(112,90,8,9)
)
New-LycanMountAtlas -FileName 'pale_steed.png' -Width 256 -Height 192 `
    -Palette @('#C9C7BC', '#5B676C', '#F0EDDD', '#65C5CB') -Regions $paleSteedRegions -Details {
        param($atlas, $colors)
        Set-AtlasRectangle -Atlas $atlas -X 8 -Y 27 -Width 2 -Height 12 -Color $colors[3]
        Set-AtlasRectangle -Atlas $atlas -X 54 -Y 26 -Width 2 -Height 12 -Color $colors[3]
        Set-AtlasRectangle -Atlas $atlas -X 104 -Y 27 -Width 2 -Height 10 -Color $colors[3]
        Set-AtlasPixel -Atlas $atlas -X 9 -Y 7 -Color (Convert-LycanMountColor '#D8FFFF')
        Set-AtlasPixel -Atlas $atlas -X 20 -Y 7 -Color (Convert-LycanMountColor '#D8FFFF')
    }

$nightmareRegions = @(
    @(0,0,38,18), @(40,0,28,12), @(70,0,26,10), @(98,0,12,13), @(112,0,12,13),
    @(126,0,12,11), @(140,0,12,11),
    @(0,22,42,20), @(44,22,56,21), @(102,22,40,19), @(144,22,28,13),
    @(0,48,20,17), @(22,48,16,16), @(40,48,22,11),
    @(64,48,20,17), @(86,48,16,16), @(104,48,22,11),
    @(0,70,24,18), @(26,70,14,17), @(42,70,24,11),
    @(68,70,24,18), @(94,70,14,17), @(110,70,24,11),
    @(0,94,14,11), @(16,94,12,10), @(30,94,10,10), @(42,94,10,10),
    @(54,94,10,9), @(66,94,10,9), @(78,94,12,9), @(92,94,12,9),
    @(106,94,12,9), @(120,94,12,9), @(134,94,12,9)
)
New-LycanMountAtlas -FileName 'nightmare.png' -Width 256 -Height 224 `
    -Palette @('#28232F', '#0E0E13', '#51445D', '#B52F63') -Regions $nightmareRegions -Details {
        param($atlas, $colors)
        $ember = Convert-LycanMountColor '#F05A38'
        Set-AtlasRectangle -Atlas $atlas -X 7 -Y 27 -Width 3 -Height 12 -Color $colors[3]
        Set-AtlasRectangle -Atlas $atlas -X 58 -Y 27 -Width 3 -Height 13 -Color $ember
        Set-AtlasRectangle -Atlas $atlas -X 118 -Y 28 -Width 2 -Height 11 -Color $colors[3]
        Set-AtlasRectangle -Atlas $atlas -X 46 -Y 73 -Width 2 -Height 12 -Color $ember
        Set-AtlasPixel -Atlas $atlas -X 11 -Y 8 -Color $ember
        Set-AtlasPixel -Atlas $atlas -X 26 -Y 8 -Color $ember
    }

Write-Output 'Generated five transparent, independently mapped lycan, canid, and occult-mount atlases.'
