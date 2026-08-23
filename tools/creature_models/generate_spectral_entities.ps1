param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

. (Join-Path $PSScriptRoot 'common.ps1')

$entityTextureRoot = Join-Path $RepositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

function Convert-HexColor {
    param([Parameter(Mandatory = $true)][string]$Hex)
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function New-SpeciesAtlas {
    param(
        [Parameter(Mandatory = $true)][string]$FileName,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][string]$Base,
        [Parameter(Mandatory = $true)][string]$Shade,
        [Parameter(Mandatory = $true)][string]$Glow,
        [Parameter(Mandatory = $true)][object[]]$Regions
    )
    $atlas = New-PixelAtlas -Width $Width -Height $Height
    try {
        $baseColor = Convert-HexColor $Base
        $shadeColor = Convert-HexColor $Shade
        $glowColor = Convert-HexColor $Glow
        $regionIndex = 0
        foreach ($region in $Regions) {
            $color = if ($region.Count -ge 5) {
                Convert-HexColor $region[4]
            } elseif (($regionIndex % 3) -eq 0) {
                $baseColor
            } elseif (($regionIndex % 3) -eq 1) {
                $shadeColor
            } else {
                $glowColor
            }
            Set-AtlasRectangle -Atlas $atlas -X $region[0] -Y $region[1] -Width $region[2] -Height $region[3] -Color $color
            if ($region[2] -ge 4 -and $region[3] -ge 4) {
                Set-AtlasRectangle -Atlas $atlas -X ($region[0] + 1) -Y ($region[1] + 1) -Width ($region[2] - 2) -Height 1 -Color $glowColor
                Set-AtlasPixel -Atlas $atlas -X ($region[0] + 1) -Y ($region[1] + $region[3] - 2) -Color $shadeColor
            }
            $regionIndex++
        }
        Save-PixelAtlas -Atlas $atlas -Path (Join-Path $entityTextureRoot $FileName)
    }
    finally {
        $atlas.Dispose()
    }
}

# Banshee is owned by generate_readable_banshee.ps1.
New-SpeciesAtlas -FileName 'eldritch_watcher.png' -Width 128 -Height 64 -Base '#172A35' -Shade '#385360' -Glow '#F4A52E' -Regions @(
    @(0,0,44,12), @(0,12,30,15), @(44,0,16,8), @(60,0,12,4), @(60,4,12,4), @(72,0,20,12),
    @(0,30,38,7), @(38,30,30,8), @(64,30,8,12), @(72,30,8,11), @(80,30,8,13), @(88,30,8,10)
)
New-SpeciesAtlas -FileName 'poltergeist.png' -Width 128 -Height 128 -Base '#75C83F' -Shade '#174E35' -Glow '#CBFF72' -Regions @(
    @(0,0,96,16,'#A7E64C'), @(0,18,68,12,'#5DAF38'), @(0,30,92,14,'#75C83F'),
    @(0,46,92,16,'#8FD5B3'), @(0,62,44,14,'#3D9657'), @(0,76,96,18,'#77664D'),
    @(64,0,28,8,'#173A27')
)
New-SpeciesAtlas -FileName 'spectre.png' -Width 128 -Height 128 -Base '#28243F' -Shade '#514A75' -Glow '#A9E8F2' -Regions @(
    @(0,0,96,18,'#24233D'), @(0,20,120,14,'#4B456F'), @(0,34,118,16,'#31304E'),
    @(0,50,96,18,'#86C8DD'), @(64,0,28,10,'#07131F'), @(0,34,30,16,'#6D7DA0')
)
New-SpeciesAtlas -FileName 'spirit.png' -Width 128 -Height 128 -Base '#E8E1C9' -Shade '#45AEB9' -Glow '#FFC536' -Regions @(
    @(0,0,112,14,'#E8E1C9'), @(48,0,48,20,'#3EA7B3'), @(72,10,24,10,'#3EA7B3'),
    @(0,16,120,16,'#EFE8D2'), @(48,16,70,16,'#FFC536'), @(0,34,122,18,'#4AB8C1'),
    @(76,34,48,18,'#58C8D0')
)
New-SpeciesAtlas -FileName 'lost_soul.png' -Width 64 -Height 64 -Base '#68728F' -Shade '#343B5C' -Glow '#F4D6D0' -Regions @(
    @(0,0,44,14,'#707990'), @(0,16,64,18,'#505A78'), @(0,30,44,14,'#403C68'),
    @(22,16,12,8,'#FFC7B1'), @(34,16,8,8,'#D98A9B'), @(40,16,8,8,'#C48A9D'),
    @(48,16,8,8,'#91A879'), @(56,16,8,8,'#7AA9CE')
)
New-SpeciesAtlas -FileName 'echo_shade.png' -Width 128 -Height 128 -Base '#142537' -Shade '#763D4A' -Glow '#71BAC3' -Regions @(
    @(0,0,24,12), @(24,0,8,6), @(0,14,26,16), @(26,14,32,9), @(54,0,14,15), @(68,0,14,15),
    @(82,0,16,14), @(98,0,16,14), @(0,30,32,5), @(0,34,32,5), @(34,30,12,12), @(46,30,12,11),
    @(58,30,16,19), @(74,30,32,6), @(58,50,14,17), @(72,50,28,6)
)

Write-Output 'Generated seven revised spectral-entity atlases; frozen original-only atlases were not touched.'
