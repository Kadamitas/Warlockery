param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

. (Join-Path $PSScriptRoot 'common.ps1')

$entityTextureRoot = Join-Path $RepositoryRoot 'src\main\resources\assets\warlockery\textures\entity'
$atlas = New-PixelAtlas -Width 192 -Height 128

function Get-NaamahColor {
    param([Parameter(Mandatory = $true)][string]$Hex)
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function Set-NaamahBand {
    param(
        [Parameter(Mandatory = $true)][int]$Y,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][string]$Base,
        [Parameter(Mandatory = $true)][string]$Shadow,
        [Parameter(Mandatory = $true)][string]$Highlight,
        [Parameter(Mandatory = $true)][string]$Accent,
        [int]$Cadence = 8
    )
    $baseColor = Get-NaamahColor $Base
    $shadowColor = Get-NaamahColor $Shadow
    $highlightColor = Get-NaamahColor $Highlight
    $accentColor = Get-NaamahColor $Accent
    Set-AtlasRectangle -Atlas $atlas -X 0 -Y $Y -Width 192 -Height $Height -Color $baseColor
    Set-AtlasRectangle -Atlas $atlas -X 0 -Y $Y -Width 192 -Height 1 -Color $highlightColor
    Set-AtlasRectangle -Atlas $atlas -X 0 -Y ($Y + $Height - 1) -Width 192 -Height 1 -Color $shadowColor
    for ($x = 3; $x -lt 190; $x += $Cadence) {
        for ($row = 2; $row -lt ($Height - 1); $row += 5) {
            $tone = if ((($x + $row + $Y) % 3) -eq 0) { $accentColor } else { $shadowColor }
            Set-AtlasPixel -Atlas $atlas -X $x -Y ($Y + $row) -Color $tone
            if (($x + 1) -lt 192) {
                Set-AtlasPixel -Atlas $atlas -X ($x + 1) -Y ($Y + $row) -Color $highlightColor
            }
        }
    }
}

try {
    # Architectural near-black hair with deep-crimson underlayers.
    Set-NaamahBand -Y 0 -Height 31 -Base '#100A12' -Shadow '#09060B' -Highlight '#2A1223' -Accent '#681527' -Cadence 9
    for ($x = 8; $x -lt 188; $x += 18) {
        Set-AtlasRectangle -Atlas $atlas -X $x -Y 4 -Width 2 -Height 23 -Color (Get-NaamahColor '#3B1022')
        Set-AtlasPixel -Atlas $atlas -X ($x + 1) -Y 25 -Color (Get-NaamahColor '#D23A27')
    }

    # Warm pale face, ruby gaze, burgundy lips, and gold earrings.
    Set-NaamahBand -Y 31 -Height 17 -Base '#E2B0A2' -Shadow '#B86F70' -Highlight '#F3C8B8' -Accent '#D89183' -Cadence 13
    Set-AtlasRectangle -Atlas $atlas -X 44 -Y 32 -Width 8 -Height 4 -Color (Get-NaamahColor '#26070C')
    Set-AtlasRectangle -Atlas $atlas -X 46 -Y 33 -Width 4 -Height 2 -Color (Get-NaamahColor '#DD382F')
    Set-AtlasPixel -Atlas $atlas -X 48 -Y 33 -Color (Get-NaamahColor '#FFD071')
    Set-AtlasRectangle -Atlas $atlas -X 52 -Y 32 -Width 11 -Height 4 -Color (Get-NaamahColor '#84213E')
    Set-AtlasRectangle -Atlas $atlas -X 64 -Y 32 -Width 9 -Height 5 -Color (Get-NaamahColor '#F1C05B')

    # Antique-gold and sea-glass crown, jewels, and wave-crested shoulders.
    Set-NaamahBand -Y 48 -Height 15 -Base '#C88B3A' -Shadow '#71401F' -Highlight '#F1C05B' -Accent '#176E77' -Cadence 7
    for ($x = 39; $x -lt 139; $x += 11) {
        Set-AtlasRectangle -Atlas $atlas -X $x -Y 49 -Width 3 -Height 10 -Color (Get-NaamahColor '#0F4B57')
        Set-AtlasPixel -Atlas $atlas -X ($x + 1) -Y 49 -Color (Get-NaamahColor '#64D5D2')
        Set-AtlasPixel -Atlas $atlas -X ($x + 1) -Y 58 -Color (Get-NaamahColor '#F1C05B')
    }
    Set-AtlasRectangle -Atlas $atlas -X 74 -Y 48 -Width 34 -Height 5 -Color (Get-NaamahColor '#AA2A3C')
    Set-AtlasRectangle -Atlas $atlas -X 80 -Y 49 -Width 22 -Height 2 -Color (Get-NaamahColor '#FFD071')

    # Black-and-oxblood tide-rib bodice and articulated bell sleeves.
    Set-NaamahBand -Y 63 -Height 31 -Base '#17131B' -Shadow '#09080C' -Highlight '#2B222C' -Accent '#741629' -Cadence 10
    for ($x = 5; $x -lt 188; $x += 16) {
        Set-AtlasRectangle -Atlas $atlas -X $x -Y 65 -Width 2 -Height 22 -Color (Get-NaamahColor '#741629')
        Set-AtlasPixel -Atlas $atlas -X ($x + 1) -Y 66 -Color (Get-NaamahColor '#C88B3A')
    }
    Set-AtlasRectangle -Atlas $atlas -X 44 -Y 76 -Width 81 -Height 5 -Color (Get-NaamahColor '#681527')
    Set-AtlasRectangle -Atlas $atlas -X 44 -Y 82 -Width 81 -Height 4 -Color (Get-NaamahColor '#F06427')
    Set-AtlasRectangle -Atlas $atlas -X 44 -Y 87 -Width 81 -Height 3 -Color (Get-NaamahColor '#176E77')
    Set-AtlasRectangle -Atlas $atlas -X 44 -Y 90 -Width 81 -Height 3 -Color (Get-NaamahColor '#64D5D2')

    # Layered split gown and deep-ocean rear tidal mantle.
    Set-NaamahBand -Y 94 -Height 14 -Base '#092A35' -Shadow '#07151C' -Highlight '#176E77' -Accent '#741629' -Cadence 9
    for ($x = 8; $x -lt 188; $x += 20) {
        Set-AtlasRectangle -Atlas $atlas -X $x -Y 95 -Width 3 -Height 11 -Color (Get-NaamahColor '#681527')
        Set-AtlasPixel -Atlas $atlas -X ($x + 1) -Y 95 -Color (Get-NaamahColor '#C88B3A')
        Set-AtlasPixel -Atlas $atlas -X ($x + 1) -Y 105 -Color (Get-NaamahColor '#64D5D2')
    }

    # Rear wave fins and thigh-high boots, still leaving a transparent atlas margin.
    Set-NaamahBand -Y 108 -Height 18 -Base '#17131B' -Shadow '#08080C' -Highlight '#2B222C' -Accent '#0F4B57' -Cadence 11
    Set-AtlasRectangle -Atlas $atlas -X 0 -Y 108 -Width 88 -Height 8 -Color (Get-NaamahColor '#0F4B57')
    Set-AtlasRectangle -Atlas $atlas -X 0 -Y 108 -Width 88 -Height 2 -Color (Get-NaamahColor '#64D5D2')
    Set-AtlasRectangle -Atlas $atlas -X 90 -Y 108 -Width 66 -Height 3 -Color (Get-NaamahColor '#C88B3A')
    Set-AtlasRectangle -Atlas $atlas -X 111 -Y 112 -Width 46 -Height 2 -Color (Get-NaamahColor '#741629')

    Save-PixelAtlas -Atlas $atlas -Path (Join-Path $entityTextureRoot 'naamah.png')
}
finally {
    $atlas.Dispose()
}

Write-Output 'Generated the dedicated transparent 192x128 Naamah ocean-matriarch atlas.'
