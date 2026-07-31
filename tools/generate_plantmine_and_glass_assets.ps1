Add-Type -AssemblyName System.Drawing

$assetRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\warlockery\textures'

function New-Canvas([int]$width, [int]$height) {
    return [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Set-PixelArt($bitmap, [string]$color, [object[]]$points) {
    $paint = [System.Drawing.ColorTranslator]::FromHtml($color)
    foreach ($point in $points) {
        $bitmap.SetPixel([int]$point[0], [int]$point[1], $paint)
    }
}

function Save-Png($bitmap, [string]$path) {
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

$plant = New-Canvas 16 16
Set-PixelArt $plant '#18271d' @(
    @(7,1),@(8,1),@(6,2),@(7,2),@(8,2),@(9,2),@(5,3),@(6,3),@(7,3),@(8,3),@(9,3),@(10,3),
    @(4,4),@(5,4),@(6,4),@(7,4),@(8,4),@(9,4),@(10,4),@(11,4),@(3,5),@(4,5),@(5,5),@(6,5),
    @(7,5),@(8,5),@(9,5),@(10,5),@(11,5),@(12,5),@(3,6),@(4,6),@(5,6),@(6,6),@(7,6),@(8,6),
    @(9,6),@(10,6),@(11,6),@(12,6),@(4,7),@(5,7),@(6,7),@(7,7),@(8,7),@(9,7),@(10,7),@(11,7),
    @(5,8),@(6,8),@(7,8),@(8,8),@(9,8),@(10,8),@(6,9),@(7,9),@(8,9),@(9,9),@(6,10),@(7,10),
    @(8,10),@(9,10),@(5,11),@(6,11),@(7,11),@(8,11),@(9,11),@(10,11),@(4,12),@(5,12),@(6,12),
    @(7,12),@(8,12),@(9,12),@(10,12),@(11,12),@(4,13),@(5,13),@(6,13),@(7,13),@(8,13),@(9,13),
    @(10,13),@(11,13),@(5,14),@(6,14),@(7,14),@(8,14),@(9,14),@(10,14)
)
Set-PixelArt $plant '#477b35' @(
    @(7,2),@(8,2),@(6,3),@(7,3),@(8,3),@(9,3),@(5,4),@(6,4),@(7,4),@(8,4),@(9,4),@(10,4),
    @(4,5),@(5,5),@(6,5),@(10,5),@(11,5),@(4,6),@(5,6),@(11,6),@(5,7),@(10,7),@(6,8),@(9,8)
)
Set-PixelArt $plant '#79ad45' @(@(7,3),@(8,3),@(6,4),@(7,4),@(8,4),@(9,4),@(5,5),@(6,5),@(10,5),@(5,6),@(11,6))
Set-PixelArt $plant '#cbd65a' @(@(7,4),@(8,4),@(6,5),@(9,5),@(6,6),@(9,6))
Set-PixelArt $plant '#59422e' @(@(7,6),@(8,6),@(7,7),@(8,7),@(7,8),@(8,8),@(7,9),@(8,9))
Set-PixelArt $plant '#9a7047' @(@(8,7),@(8,8))
Set-PixelArt $plant '#4f4438' @(@(6,10),@(7,10),@(8,10),@(9,10),@(5,11),@(6,11),@(7,11),@(8,11),@(9,11),@(10,11),@(4,12),@(5,12),@(6,12),@(7,12),@(8,12),@(9,12),@(10,12),@(11,12),@(4,13),@(5,13),@(6,13),@(7,13),@(8,13),@(9,13),@(10,13),@(11,13),@(5,14),@(6,14),@(7,14),@(8,14),@(9,14),@(10,14))
Set-PixelArt $plant '#786459' @(@(6,11),@(7,11),@(8,11),@(9,11),@(5,12),@(6,12),@(9,12),@(10,12),@(5,13),@(10,13))
Set-PixelArt $plant '#c07a45' @(@(7,12),@(8,12),@(7,13),@(8,13))
Set-PixelArt $plant '#f0b85f' @(@(8,12))
Save-Png $plant (Join-Path $assetRoot 'item\plantmine.png')

$glassFiles = Get-ChildItem (Join-Path $assetRoot 'block') -Filter 'shadedglass*.png'
foreach ($file in $glassFiles) {
    $source = [System.Drawing.Bitmap]::new($file.FullName)
    $center = $source.GetPixel(8, 8)
    $source.Dispose()
    $glass = New-Canvas 16 16
    $baseAlpha = if ($file.BaseName -like '*off*') { 45 } else { 74 }
    $edgeAlpha = if ($file.BaseName -like '*off*') { 105 } else { 145 }
    $base = [System.Drawing.Color]::FromArgb($baseAlpha, $center.R, $center.G, $center.B)
    $edge = [System.Drawing.Color]::FromArgb($edgeAlpha, [Math]::Min(255, $center.R + 35), [Math]::Min(255, $center.G + 35), [Math]::Min(255, $center.B + 35))
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $glass.SetPixel($x, $y, $base)
        }
    }
    foreach ($point in @(@(0,0),@(1,0),@(0,1),@(15,15),@(14,15),@(15,14),@(3,3),@(4,3),@(3,4),@(11,11),@(12,11),@(12,12))) {
        $glass.SetPixel([int]$point[0], [int]$point[1], $edge)
    }
    Save-Png $glass $file.FullName
}
