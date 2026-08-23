$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'common.ps1')

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$entityTextureRoot = Join-Path $repositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

$impSoot = [System.Drawing.Color]::FromArgb(255, 25, 19, 23)
$impCoal = [System.Drawing.Color]::FromArgb(255, 62, 28, 31)
$impRed = [System.Drawing.Color]::FromArgb(255, 126, 43, 38)
$impEmber = [System.Drawing.Color]::FromArgb(255, 239, 91, 27)
$impGlow = [System.Drawing.Color]::FromArgb(255, 255, 180, 55)
$impPlum = [System.Drawing.Color]::FromArgb(255, 83, 38, 69)
$impPlumLight = [System.Drawing.Color]::FromArgb(255, 126, 58, 91)
$impHorn = [System.Drawing.Color]::FromArgb(255, 213, 187, 155)
$impOldGold = [System.Drawing.Color]::FromArgb(255, 202, 145, 52)

$impAtlas = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $impAtlas -X 0 -Y 0 -Width 30 -Height 14 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 7 -Y 4 -Width 16 -Height 5 -Color $impRed
    Set-AtlasRectangle -Atlas $impAtlas -X 10 -Y 6 -Width 3 -Height 2 -Color $impGlow
    Set-AtlasRectangle -Atlas $impAtlas -X 18 -Y 6 -Width 3 -Height 2 -Color $impGlow
    Set-AtlasRectangle -Atlas $impAtlas -X 32 -Y 0 -Width 18 -Height 6 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 37 -Y 2 -Width 8 -Height 3 -Color $impRed
    Set-AtlasPixel -Atlas $impAtlas -X 39 -Y 3 -Color $impHorn
    Set-AtlasPixel -Atlas $impAtlas -X 43 -Y 3 -Color $impHorn
    Set-AtlasRectangle -Atlas $impAtlas -X 52 -Y 0 -Width 18 -Height 7 -Color $impHorn
    Set-AtlasRectangle -Atlas $impAtlas -X 52 -Y 5 -Width 18 -Height 2 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 72 -Y 0 -Width 22 -Height 7 -Color $impPlumLight
    Set-AtlasRectangle -Atlas $impAtlas -X 76 -Y 1 -Width 14 -Height 2 -Color $impRed
    Set-AtlasRectangle -Atlas $impAtlas -X 0 -Y 16 -Width 24 -Height 14 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 8 -Y 17 -Width 6 -Height 11 -Color $impRed
    Set-AtlasRectangle -Atlas $impAtlas -X 10 -Y 19 -Width 2 -Height 7 -Color $impEmber
    Set-AtlasRectangle -Atlas $impAtlas -X 26 -Y 16 -Width 46 -Height 19 -Color $impPlum
    Set-AtlasRectangle -Atlas $impAtlas -X 26 -Y 16 -Width 46 -Height 2 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 31 -Y 21 -Width 4 -Height 11 -Color $impPlumLight
    Set-AtlasRectangle -Atlas $impAtlas -X 60 -Y 21 -Width 4 -Height 11 -Color $impPlumLight
    Set-AtlasRectangle -Atlas $impAtlas -X 74 -Y 16 -Width 38 -Height 16 -Color $impPlum
    Set-AtlasRectangle -Atlas $impAtlas -X 74 -Y 16 -Width 38 -Height 2 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 82 -Y 22 -Width 3 -Height 8 -Color $impPlumLight
    Set-AtlasRectangle -Atlas $impAtlas -X 102 -Y 22 -Width 3 -Height 8 -Color $impPlumLight
    Set-AtlasRectangle -Atlas $impAtlas -X 0 -Y 36 -Width 58 -Height 10 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 14 -Y 36 -Width 14 -Height 7 -Color $impRed
    Set-AtlasRectangle -Atlas $impAtlas -X 15 -Y 37 -Width 3 -Height 3 -Color $impOldGold
    Set-AtlasRectangle -Atlas $impAtlas -X 44 -Y 36 -Width 14 -Height 7 -Color $impRed
    Set-AtlasRectangle -Atlas $impAtlas -X 51 -Y 39 -Width 2 -Height 2 -Color $impGlow
    Set-AtlasRectangle -Atlas $impAtlas -X 0 -Y 50 -Width 62 -Height 10 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 14 -Y 50 -Width 16 -Height 7 -Color $impRed
    Set-AtlasRectangle -Atlas $impAtlas -X 46 -Y 50 -Width 16 -Height 7 -Color $impRed
    Set-AtlasRectangle -Atlas $impAtlas -X 0 -Y 66 -Width 37 -Height 10 -Color $impCoal
    Set-AtlasRectangle -Atlas $impAtlas -X 20 -Y 66 -Width 17 -Height 6 -Color $impEmber
    Set-AtlasRectangle -Atlas $impAtlas -X 24 -Y 67 -Width 9 -Height 3 -Color $impGlow
    Save-PixelAtlas -Atlas $impAtlas -Path (Join-Path $entityTextureRoot 'imp.png')
}
finally {
    $impAtlas.Dispose()
}

$stormRain = [System.Drawing.Color]::FromArgb(255, 29, 48, 69)
$stormDeep = [System.Drawing.Color]::FromArgb(255, 18, 31, 48)
$stormSlate = [System.Drawing.Color]::FromArgb(255, 58, 73, 88)
$stormCloud = [System.Drawing.Color]::FromArgb(255, 174, 190, 201)
$stormCloudLight = [System.Drawing.Color]::FromArgb(255, 215, 225, 229)
$stormCyan = [System.Drawing.Color]::FromArgb(255, 52, 210, 224)
$stormGold = [System.Drawing.Color]::FromArgb(255, 226, 170, 66)
$stormCopper = [System.Drawing.Color]::FromArgb(255, 126, 82, 52)
$stormWing = [System.Drawing.Color]::FromArgb(255, 20, 34, 51)
$stormWingSlate = [System.Drawing.Color]::FromArgb(255, 45, 60, 75)

$stormAtlas = New-PixelAtlas -Width 128 -Height 128
try {
    Set-AtlasRectangle -Atlas $stormAtlas -X 0 -Y 0 -Width 30 -Height 14 -Color $stormSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 6 -Y 3 -Width 18 -Height 5 -Color $stormDeep
    Set-AtlasRectangle -Atlas $stormAtlas -X 10 -Y 6 -Width 3 -Height 2 -Color $stormCyan
    Set-AtlasRectangle -Atlas $stormAtlas -X 18 -Y 6 -Width 3 -Height 2 -Color $stormCyan
    Set-AtlasRectangle -Atlas $stormAtlas -X 32 -Y 0 -Width 20 -Height 8 -Color $stormSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 37 -Y 3 -Width 10 -Height 4 -Color $stormCloud
    Set-AtlasRectangle -Atlas $stormAtlas -X 54 -Y 0 -Width 28 -Height 6 -Color $stormDeep
    Set-AtlasRectangle -Atlas $stormAtlas -X 57 -Y 1 -Width 8 -Height 2 -Color $stormCloudLight
    Set-AtlasRectangle -Atlas $stormAtlas -X 71 -Y 1 -Width 8 -Height 2 -Color $stormCloudLight
    Set-AtlasRectangle -Atlas $stormAtlas -X 82 -Y 0 -Width 20 -Height 6 -Color $stormCopper
    Set-AtlasRectangle -Atlas $stormAtlas -X 0 -Y 16 -Width 70 -Height 20 -Color $stormDeep
    Set-AtlasRectangle -Atlas $stormAtlas -X 8 -Y 19 -Width 24 -Height 13 -Color $stormRain
    Set-AtlasRectangle -Atlas $stormAtlas -X 15 -Y 19 -Width 3 -Height 10 -Color $stormGold
    Set-AtlasRectangle -Atlas $stormAtlas -X 40 -Y 16 -Width 30 -Height 16 -Color $stormRain
    Set-AtlasRectangle -Atlas $stormAtlas -X 72 -Y 16 -Width 46 -Height 13 -Color $stormCloud
    Set-AtlasRectangle -Atlas $stormAtlas -X 82 -Y 18 -Width 24 -Height 4 -Color $stormCloudLight
    Set-AtlasRectangle -Atlas $stormAtlas -X 0 -Y 36 -Width 72 -Height 13 -Color $stormCloud
    Set-AtlasRectangle -Atlas $stormAtlas -X 7 -Y 37 -Width 18 -Height 4 -Color $stormCloudLight
    Set-AtlasRectangle -Atlas $stormAtlas -X 30 -Y 36 -Width 42 -Height 12 -Color $stormSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 74 -Y 36 -Width 50 -Height 17 -Color $stormRain
    Set-AtlasRectangle -Atlas $stormAtlas -X 83 -Y 39 -Width 3 -Height 10 -Color $stormGold
    Set-AtlasRectangle -Atlas $stormAtlas -X 106 -Y 39 -Width 3 -Height 10 -Color $stormGold
    Set-AtlasRectangle -Atlas $stormAtlas -X 0 -Y 52 -Width 100 -Height 18 -Color $stormSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 8 -Y 55 -Width 14 -Height 11 -Color $stormCloud
    Set-AtlasRectangle -Atlas $stormAtlas -X 80 -Y 55 -Width 14 -Height 11 -Color $stormCloud
    Set-AtlasRectangle -Atlas $stormAtlas -X 104 -Y 52 -Width 24 -Height 10 -Color $stormRain
    Set-AtlasRectangle -Atlas $stormAtlas -X 0 -Y 68 -Width 110 -Height 12 -Color $stormDeep
    Set-AtlasRectangle -Atlas $stormAtlas -X 18 -Y 68 -Width 24 -Height 11 -Color $stormSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 84 -Y 68 -Width 26 -Height 11 -Color $stormSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 0 -Y 82 -Width 24 -Height 13 -Color $stormRain
    Set-AtlasRectangle -Atlas $stormAtlas -X 24 -Y 82 -Width 8 -Height 9 -Color $stormCyan
    Set-AtlasRectangle -Atlas $stormAtlas -X 26 -Y 84 -Width 4 -Height 5 -Color $stormCloudLight
    Set-AtlasRectangle -Atlas $stormAtlas -X 0 -Y 98 -Width 10 -Height 8 -Color $stormWingSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 10 -Y 98 -Width 8 -Height 12 -Color $stormWingSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 18 -Y 98 -Width 20 -Height 18 -Color $stormWing
    Set-AtlasRectangle -Atlas $stormAtlas -X 38 -Y 98 -Width 18 -Height 16 -Color $stormWing
    Set-AtlasRectangle -Atlas $stormAtlas -X 56 -Y 98 -Width 10 -Height 8 -Color $stormWingSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 66 -Y 98 -Width 8 -Height 12 -Color $stormWingSlate
    Set-AtlasRectangle -Atlas $stormAtlas -X 74 -Y 98 -Width 20 -Height 18 -Color $stormWing
    Set-AtlasRectangle -Atlas $stormAtlas -X 94 -Y 98 -Width 18 -Height 16 -Color $stormWing
    Set-AtlasRectangle -Atlas $stormAtlas -X 26 -Y 104 -Width 4 -Height 2 -Color $stormCyan
    Set-AtlasRectangle -Atlas $stormAtlas -X 82 -Y 104 -Width 4 -Height 2 -Color $stormCyan
    Set-AtlasPixel -Atlas $stormAtlas -X 27 -Y 107 -Color $stormCloudLight
    Set-AtlasPixel -Atlas $stormAtlas -X 83 -Y 107 -Color $stormCloudLight
    Save-PixelAtlas -Atlas $stormAtlas -Path (Join-Path $entityTextureRoot 'storm_simian.png')
}
finally {
    $stormAtlas.Dispose()
}
