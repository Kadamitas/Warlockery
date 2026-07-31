Add-Type -AssemblyName System.Drawing

$sourcePath = Join-Path $PSScriptRoot '..\src\main\resources\assets\warlockery\textures\block\spinningwheel_thread.png'
$outputPath = Join-Path $PSScriptRoot '..\src\main\resources\assets\warlockery\textures\block\spinningwheel_thread_active.png'
$source = [System.Drawing.Bitmap]::new($sourcePath)
$size = $source.Width
$sheet = [System.Drawing.Bitmap]::new($size, $size * 4, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($sheet)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

for ($frame = 0; $frame -lt 4; $frame++) {
    $graphics.ResetTransform()
    $graphics.TranslateTransform($size / 2, $frame * $size + $size / 2)
    $graphics.RotateTransform($frame * 90)
    $graphics.TranslateTransform(-$size / 2, -$size / 2)
    $graphics.DrawImage($source, 0, 0, $size, $size)
}

$graphics.Dispose()
$source.Dispose()
$sheet.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
$sheet.Dispose()
