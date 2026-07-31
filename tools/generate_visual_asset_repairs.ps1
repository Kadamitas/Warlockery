param(
    [string] $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$itemRoot = Join-Path $ProjectRoot 'src/main/resources/assets/warlockery/textures/item'
$blockRoot = Join-Path $ProjectRoot 'src/main/resources/assets/warlockery/textures/block'

function Color([string] $Hex) {
    return [Drawing.Color]::FromArgb(
        255,
        [Convert]::ToInt32($Hex.Substring(0, 2), 16),
        [Convert]::ToInt32($Hex.Substring(2, 2), 16),
        [Convert]::ToInt32($Hex.Substring(4, 2), 16)
    )
}

function New-TransparentBitmap {
    return [Drawing.Bitmap]::new(16, 16, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Paint-Pixels([Drawing.Bitmap] $Bitmap, [string] $Hex, [string[]] $Coordinates) {
    $color = Color $Hex
    foreach ($coordinate in $Coordinates) {
        $parts = $coordinate.Split(',')
        $Bitmap.SetPixel([int] $parts[0], [int] $parts[1], $color)
    }
}

function Write-CritterSnare {
    $bitmap = New-TransparentBitmap
    try {
        Paint-Pixels $bitmap '15201B' @(
            '5,2', '6,2', '7,2', '8,2', '9,2', '10,2',
            '3,3', '4,3', '11,3', '12,3',
            '2,4', '3,4', '12,4', '13,4',
            '2,5', '13,5', '2,6', '13,6', '2,7', '13,7', '2,8', '13,8',
            '3,9', '4,9', '11,9', '12,9', '5,10', '10,10',
            '6,6', '7,6', '8,6', '9,6', '5,7', '6,7', '7,7', '8,7', '9,7', '10,7',
            '6,8', '7,8', '8,8', '9,8', '7,9', '8,9',
            '7,10', '8,10', '7,11', '8,11', '7,12', '8,12', '7,13', '8,13', '7,14', '8,14',
            '3,11', '4,11', '5,11', '10,11', '11,11', '12,11',
            '2,12', '3,12', '4,12', '5,12', '10,12', '11,12', '12,12', '13,12',
            '3,13', '4,13', '11,13', '12,13'
        )
        Paint-Pixels $bitmap '35683F' @(
            '5,3', '6,3', '7,3', '8,3', '9,3', '10,3',
            '4,4', '11,4', '3,5', '12,5', '3,6', '12,6', '3,7', '12,7', '3,8', '12,8',
            '4,8', '11,8', '5,9', '10,9',
            '7,11', '8,11', '7,12', '8,12', '7,13', '8,13',
            '3,12', '4,12', '5,12', '10,12', '11,12', '12,12', '4,13', '11,13'
        )
        Paint-Pixels $bitmap '5EA64D' @(
            '6,3', '7,3', '4,4', '3,5', '3,6', '4,8', '5,9',
            '7,11', '7,12', '4,12', '5,12', '10,12', '11,12'
        )
        Paint-Pixels $bitmap '91D65B' @('7,3', '3,5', '4,12', '10,12')
        Paint-Pixels $bitmap '2A2433' @(
            '6,7', '7,7', '8,7', '9,7', '5,8', '6,8', '7,8', '8,8', '9,8', '10,8', '7,9', '8,9'
        )
        Paint-Pixels $bitmap '66507A' @('5,7', '10,7', '6,8', '9,8')
        Paint-Pixels $bitmap 'E9E4A8' @('7,7', '8,7')
        $bitmap.Save((Join-Path $itemRoot 'crittersnare.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

function Write-WispyCotton {
    $bitmap = New-TransparentBitmap
    try {
        Paint-Pixels $bitmap '1B3034' @(
            '5,1', '6,1', '7,1', '8,1', '9,1', '10,1',
            '3,2', '4,2', '5,2', '10,2', '11,2', '12,2',
            '2,3', '3,3', '12,3', '13,3', '2,4', '13,4',
            '1,5', '2,5', '13,5', '14,5', '1,6', '14,6',
            '2,7', '13,7', '3,8', '4,8', '11,8', '12,8',
            '5,9', '6,9', '7,9', '8,9', '9,9', '10,9',
            '7,10', '8,10', '7,11', '8,11', '7,12', '8,12', '7,13', '8,13', '7,14', '8,14',
            '4,11', '5,11', '6,11', '9,11', '10,11', '11,11',
            '3,12', '4,12', '5,12', '10,12', '11,12', '12,12'
        )
        Paint-Pixels $bitmap 'A8DDE0' @(
            '5,2', '6,2', '7,2', '8,2', '9,2', '10,2',
            '4,3', '5,3', '6,3', '7,3', '8,3', '9,3', '10,3', '11,3',
            '3,4', '4,4', '5,4', '6,4', '7,4', '8,4', '9,4', '10,4', '11,4', '12,4',
            '2,5', '3,5', '4,5', '5,5', '6,5', '7,5', '8,5', '9,5', '10,5', '11,5', '12,5', '13,5',
            '2,6', '3,6', '4,6', '5,6', '6,6', '7,6', '8,6', '9,6', '10,6', '11,6', '12,6', '13,6',
            '3,7', '4,7', '5,7', '6,7', '7,7', '8,7', '9,7', '10,7', '11,7', '12,7',
            '5,8', '6,8', '7,8', '8,8', '9,8', '10,8'
        )
        Paint-Pixels $bitmap 'E7F5EF' @(
            '5,2', '6,2', '9,2', '10,2', '4,3', '5,3', '8,3', '9,3', '10,3', '11,3',
            '3,4', '4,4', '7,4', '8,4', '11,4', '12,4',
            '3,5', '4,5', '7,5', '8,5', '11,5', '12,5',
            '4,6', '5,6', '8,6', '9,6', '12,6', '5,7', '6,7', '9,7', '10,7'
        )
        Paint-Pixels $bitmap 'FFFFFF' @('5,3', '9,3', '4,4', '8,4', '11,4', '7,5', '12,5')
        Paint-Pixels $bitmap '3D7465' @(
            '7,10', '8,10', '7,11', '8,11', '7,12', '8,12', '7,13', '8,13',
            '4,11', '5,11', '6,11', '9,11', '10,11', '11,11',
            '4,12', '5,12', '10,12', '11,12'
        )
        Paint-Pixels $bitmap '70AD79' @('7,10', '7,11', '5,11', '10,11', '4,12', '11,12')
        Paint-Pixels $bitmap 'B5F4EE' @('1,2', '2,2', '1,3', '14,7', '14,8', '15,8')
        $bitmap.Save((Join-Path $itemRoot 'somniancotton.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

function New-FilledBitmap([string] $Hex) {
    $bitmap = New-TransparentBitmap
    $color = Color $Hex
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $bitmap.SetPixel($x, $y, $color)
        }
    }
    return $bitmap
}

function Write-FumeFunnelMetal {
    $bitmap = New-FilledBitmap '465159'
    try {
        Paint-Pixels $bitmap '2A3035' @(
            '0,0', '1,0', '2,0', '3,0', '4,0', '5,0', '6,0', '7,0', '8,0', '9,0', '10,0', '11,0', '12,0', '13,0', '14,0', '15,0',
            '0,15', '1,15', '2,15', '3,15', '4,15', '5,15', '6,15', '7,15', '8,15', '9,15', '10,15', '11,15', '12,15', '13,15', '14,15', '15,15'
        )
        Paint-Pixels $bitmap '65737C' @(
            '3,2', '3,3', '3,12', '3,13',
            '10,2', '10,3', '10,12', '10,13'
        )
        $bitmap.Save((Join-Path $blockRoot 'fumefunnel.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

function Write-FilteredFumeFunnelMetal {
    $bitmap = New-FilledBitmap '38545E'
    try {
        Paint-Pixels $bitmap '20333A' @(
            '0,0', '1,0', '2,0', '3,0', '4,0', '5,0', '6,0', '7,0', '8,0', '9,0', '10,0', '11,0', '12,0', '13,0', '14,0', '15,0',
            '0,8', '1,8', '2,8', '3,8', '4,8', '5,8', '6,8', '7,8', '8,8', '9,8', '10,8', '11,8', '12,8', '13,8', '14,8', '15,8'
        )
        Paint-Pixels $bitmap '5E7C84' @(
            '4,1', '4,2', '4,3', '4,4', '4,5', '4,6', '4,7',
            '11,9', '11,10', '11,11', '11,12', '11,13', '11,14', '11,15'
        )
        Paint-Pixels $bitmap '91AFB2' @('2,3', '13,3', '2,12', '13,12')
        $bitmap.Save((Join-Path $blockRoot 'filteredfumefunnel.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

function Write-FumeFilterMesh {
    $bitmap = New-FilledBitmap 'A78950'
    try {
        Paint-Pixels $bitmap '5A472E' @(
            '0,0', '1,0', '2,0', '3,0', '4,0', '5,0', '6,0', '7,0', '8,0', '9,0', '10,0', '11,0', '12,0', '13,0', '14,0', '15,0',
            '0,15', '1,15', '2,15', '3,15', '4,15', '5,15', '6,15', '7,15', '8,15', '9,15', '10,15', '11,15', '12,15', '13,15', '14,15', '15,15',
            '3,4', '7,4', '11,4', '5,7', '9,7', '3,10', '7,10', '11,10'
        )
        Paint-Pixels $bitmap 'D4BA78' @('4,4', '8,4', '6,7', '10,7', '4,10', '8,10', '12,10')
        $bitmap.Save((Join-Path $blockRoot 'fumefunnel_filter.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

function Write-WolfTrapMetal {
    $bitmap = New-FilledBitmap '30353A'
    try {
        Paint-Pixels $bitmap '1D2024' @(
            '0,0', '1,0', '2,0', '3,0', '4,0', '5,0', '6,0', '7,0', '8,0', '9,0', '10,0', '11,0', '12,0', '13,0', '14,0', '15,0',
            '0,8', '1,8', '2,8', '3,8', '4,8', '5,8', '6,8', '7,8', '8,8', '9,8', '10,8', '11,8', '12,8', '13,8', '14,8', '15,8'
        )
        Paint-Pixels $bitmap '4D555C' @(
            '4,1', '4,2', '4,3', '4,4', '4,5', '4,6', '4,7',
            '11,9', '11,10', '11,11', '11,12', '11,13', '11,14', '11,15'
        )
        Paint-Pixels $bitmap '68727A' @('2,3', '13,3', '2,11', '13,11')
        $bitmap.Save((Join-Path $blockRoot 'wolftrap.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

function Write-WolfTrapSilver {
    $bitmap = New-FilledBitmap '91A2AD'
    try {
        Paint-Pixels $bitmap '5B6973' @(
            '0,0', '1,0', '2,0', '3,0', '4,0', '5,0', '6,0', '7,0', '8,0', '9,0', '10,0', '11,0', '12,0', '13,0', '14,0', '15,0',
            '0,15', '1,15', '2,15', '3,15', '4,15', '5,15', '6,15', '7,15', '8,15', '9,15', '10,15', '11,15', '12,15', '13,15', '14,15', '15,15'
        )
        Paint-Pixels $bitmap 'C7D3D9' @(
            '3,2', '3,3', '3,12', '3,13',
            '10,2', '10,3', '10,12', '10,13'
        )
        $bitmap.Save((Join-Path $blockRoot 'wolftrap_silver.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
}

Write-CritterSnare
Write-WispyCotton
Write-FumeFunnelMetal
Write-FilteredFumeFunnelMetal
Write-FumeFilterMesh
Write-WolfTrapMetal
Write-WolfTrapSilver

Write-Host 'Generated critter-snare, wispy-cotton, fume-funnel, and wolf-trap visual repairs.'
