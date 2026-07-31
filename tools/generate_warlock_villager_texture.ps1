Add-Type -AssemblyName System.Drawing

$targets = @(
    (Join-Path $PSScriptRoot '..\src\main\resources\assets\warlockery\textures\entity\villager\profession\warlock.png'),
    (Join-Path $PSScriptRoot '..\src\main\resources\assets\warlockery\textures\entity\zombie_villager\profession\warlock.png')
)
$transparent = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
$ink = [System.Drawing.Color]::FromArgb(255, 31, 20, 39)
$plum = [System.Drawing.Color]::FromArgb(255, 82, 41, 100)
$violet = [System.Drawing.Color]::FromArgb(255, 126, 67, 142)
$green = [System.Drawing.Color]::FromArgb(255, 111, 160, 74)
$gold = [System.Drawing.Color]::FromArgb(255, 224, 174, 72)

foreach ($target in $targets) {
    $folder = Split-Path $target
    [IO.Directory]::CreateDirectory($folder) | Out-Null
    $bitmap = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 64; $y++) {
        for ($x = 0; $x -lt 64; $x++) {
            $bitmap.SetPixel($x, $y, $transparent)
        }
    }
    for ($y = 18; $y -lt 36; $y++) {
        for ($x = 16; $x -lt 48; $x++) {
            $bitmap.SetPixel($x, $y, $(if (($x + $y) % 5 -eq 0) { $violet } else { $plum }))
        }
    }
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 32; $x -lt 64; $x++) {
            if ($y -ge [Math]::Abs($x - 48) / 2) {
                $bitmap.SetPixel($x, $y, $(if ($y -gt 11) { $ink } else { $plum }))
            }
        }
    }
    for ($x = 20; $x -lt 44; $x++) {
        $bitmap.SetPixel($x, 24, $gold)
    }
    for ($y = 26; $y -lt 31; $y++) {
        $bitmap.SetPixel(30, $y, $green)
        $bitmap.SetPixel(31, $y, $green)
        $bitmap.SetPixel(32, $y, $green)
        $bitmap.SetPixel(33, $y, $green)
    }
    $bitmap.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}
