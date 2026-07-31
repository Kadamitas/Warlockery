param(
    [string]$TextureDirectory = "src/main/resources/assets/warlockery/textures/item"
)

Add-Type -AssemblyName System.Drawing

function New-Sprite {
    param(
        [string]$Name,
        [hashtable]$Pixels
    )

    $image = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    foreach ($entry in $Pixels.GetEnumerator()) {
        $parts = $entry.Key.Split(',')
        $color = [System.Drawing.ColorTranslator]::FromHtml($entry.Value)
        $image.SetPixel([int]$parts[0], [int]$parts[1], $color)
    }
    $target = Join-Path $TextureDirectory "$Name.png"
    $image.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    $image.Dispose()
}

$cane = @{}
foreach ($pixel in @(
    '10,1','11,1','12,1','9,2','13,2','9,3','13,3','10,4','11,4','12,4',
    '10,5','9,6','9,7','8,8','8,9','7,10','7,11','6,12','6,13','5,14','5,15'
)) { $cane[$pixel] = '#2A1715' }
foreach ($pixel in @('10,2','11,2','12,2','10,3','11,3','12,3')) { $cane[$pixel] = '#D6A453' }
foreach ($pixel in @('10,5','9,6','9,7','8,8','8,9','7,10','7,11','6,12','6,13','5,14')) { $cane[$pixel] = '#794626' }
foreach ($pixel in @('11,2','12,2','10,3')) { $cane[$pixel] = '#F2D083' }
foreach ($pixel in @('9,6','8,8','7,10','6,12')) { $cane[$pixel] = '#A96932' }

$sword = @{}
foreach ($pixel in @(
    '7,0','6,1','7,1','8,1','6,2','7,2','8,2','6,3','7,3','8,3','6,4','7,4','8,4',
    '6,5','7,5','8,5','6,6','7,6','8,6','6,7','7,7','8,7','6,8','7,8','8,8','6,9','7,9','8,9',
    '4,10','5,10','6,10','7,10','8,10','9,10','10,10','4,11','5,11','6,11','7,11','8,11','9,11','10,11',
    '7,12','8,12','7,13','8,13','7,14','8,14','6,15','7,15','8,15','9,15'
)) { $sword[$pixel] = '#1E2028' }
foreach ($pixel in @('7,1','7,2','7,3','7,4','7,5','7,6','7,7','7,8','7,9')) { $sword[$pixel] = '#DCEAF1' }
foreach ($pixel in @('8,2','8,3','8,4','8,5','8,6','8,7','8,8','8,9')) { $sword[$pixel] = '#8EA9B8' }
foreach ($pixel in @('5,10','6,10','7,10','8,10','9,10','5,11','6,11','9,11')) { $sword[$pixel] = '#D6A453' }
foreach ($pixel in @('6,10','7,10','8,10')) { $sword[$pixel] = '#F2D083' }
foreach ($pixel in @('7,12','8,12','7,13','8,13','7,14','8,14')) { $sword[$pixel] = '#6F3D27' }
foreach ($pixel in @('7,12','7,13','7,14')) { $sword[$pixel] = '#A66735' }
foreach ($pixel in @('7,15','8,15')) { $sword[$pixel] = '#D6A453' }

New-Sprite -Name 'canesword' -Pixels $cane
New-Sprite -Name 'canesword_drawn' -Pixels $sword
