Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
$output = Join-Path $projectRoot 'src/main/resources/assets/warlockery/textures/gui/vampire_blood_pool.png'
$directory = Split-Path -Parent $output
[System.IO.Directory]::CreateDirectory($directory) | Out-Null

$bitmap = [System.Drawing.Bitmap]::new(81, 14, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$clear = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
for ($y = 0; $y -lt 14; $y++) {
    for ($x = 0; $x -lt 81; $x++) {
        $bitmap.SetPixel($x, $y, $clear)
    }
}

$black = [System.Drawing.Color]::FromArgb(255, 17, 13, 20)
$iron = [System.Drawing.Color]::FromArgb(255, 67, 60, 72)
$silver = [System.Drawing.Color]::FromArgb(255, 151, 139, 157)
$glass = [System.Drawing.Color]::FromArgb(80, 157, 130, 145)
$ruby = [System.Drawing.Color]::FromArgb(255, 151, 18, 52)

for ($x = 3; $x -le 77; $x++) {
    $bitmap.SetPixel($x, 1, $black)
    $bitmap.SetPixel($x, 12, $black)
    $bitmap.SetPixel($x, 2, $silver)
    $bitmap.SetPixel($x, 11, $iron)
}
for ($y = 3; $y -le 10; $y++) {
    $bitmap.SetPixel(1, $y, $black)
    $bitmap.SetPixel(2, $y, $silver)
    $bitmap.SetPixel(78, $y, $iron)
    $bitmap.SetPixel(79, $y, $black)
}
foreach ($point in @(@(2,2),@(78,2),@(2,11),@(78,11),@(0,6),@(80,6),@(40,0),@(40,13))) {
    $bitmap.SetPixel($point[0], $point[1], $black)
}
foreach ($point in @(@(1,5),@(1,7),@(79,5),@(79,7),@(39,1),@(41,1),@(39,12),@(41,12))) {
    $bitmap.SetPixel($point[0], $point[1], $ruby)
}
for ($x = 4; $x -le 76; $x++) {
    $bitmap.SetPixel($x, 3, $glass)
}

$bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
Write-Output $output
