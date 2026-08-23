$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'common.ps1')

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$entityTextureRoot = Join-Path $repositoryRoot 'src\main\resources\assets\warlockery\textures\entity'

$fuseVoid = [System.Drawing.Color]::FromArgb(255, 7, 15, 13)
$fuseBottle = [System.Drawing.Color]::FromArgb(255, 24, 45, 31)
$fuseMoss = [System.Drawing.Color]::FromArgb(255, 55, 75, 38)
$fuseLichen = [System.Drawing.Color]::FromArgb(255, 111, 126, 47)
$fuseCold = [System.Drawing.Color]::FromArgb(255, 42, 202, 195)
$fuseBruise = [System.Drawing.Color]::FromArgb(255, 92, 54, 135)

$fuseAtlas = New-PixelAtlas -Width 64 -Height 64
try {
    Set-AtlasRectangle -Atlas $fuseAtlas -X 0 -Y 0 -Width 26 -Height 7 -Color $fuseBottle
    Set-AtlasRectangle -Atlas $fuseAtlas -X 6 -Y 1 -Width 13 -Height 2 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 12 -Y 4 -Width 6 -Height 2 -Color $fuseLichen
    Set-AtlasPixel -Atlas $fuseAtlas -X 3 -Y 5 -Color $fuseBruise
    Set-AtlasRectangle -Atlas $fuseAtlas -X 32 -Y 0 -Width 16 -Height 7 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 38 -Y 1 -Width 7 -Height 2 -Color $fuseLichen
    Set-AtlasRectangle -Atlas $fuseAtlas -X 48 -Y 0 -Width 16 -Height 7 -Color $fuseBottle
    Set-AtlasRectangle -Atlas $fuseAtlas -X 53 -Y 3 -Width 8 -Height 2 -Color $fuseMoss
    Set-AtlasPixel -Atlas $fuseAtlas -X 60 -Y 1 -Color $fuseCold
    Set-AtlasRectangle -Atlas $fuseAtlas -X 0 -Y 8 -Width 14 -Height 11 -Color $fuseBottle
    Set-AtlasRectangle -Atlas $fuseAtlas -X 3 -Y 10 -Width 6 -Height 7 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 16 -Y 8 -Width 14 -Height 11 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 20 -Y 9 -Width 3 -Height 8 -Color $fuseBottle
    Set-AtlasPixel -Atlas $fuseAtlas -X 27 -Y 16 -Color $fuseBruise
    Set-AtlasRectangle -Atlas $fuseAtlas -X 32 -Y 8 -Width 18 -Height 7 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 38 -Y 10 -Width 7 -Height 4 -Color $fuseLichen
    Set-AtlasRectangle -Atlas $fuseAtlas -X 0 -Y 21 -Width 4 -Height 5 -Color $fuseVoid
    Set-AtlasPixel -Atlas $fuseAtlas -X 1 -Y 22 -Color $fuseCold
    Set-AtlasRectangle -Atlas $fuseAtlas -X 8 -Y 21 -Width 18 -Height 6 -Color $fuseBottle
    Set-AtlasRectangle -Atlas $fuseAtlas -X 13 -Y 22 -Width 8 -Height 3 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 28 -Y 21 -Width 14 -Height 5 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 31 -Y 22 -Width 7 -Height 2 -Color $fuseLichen
    Set-AtlasRectangle -Atlas $fuseAtlas -X 44 -Y 21 -Width 12 -Height 5 -Color $fuseBottle
    Set-AtlasPixel -Atlas $fuseAtlas -X 52 -Y 22 -Color $fuseBruise
    Set-AtlasRectangle -Atlas $fuseAtlas -X 56 -Y 21 -Width 8 -Height 4 -Color $fuseLichen
    Set-AtlasRectangle -Atlas $fuseAtlas -X 58 -Y 22 -Width 4 -Height 2 -Color $fuseCold
    Set-AtlasRectangle -Atlas $fuseAtlas -X 0 -Y 28 -Width 28 -Height 11 -Color $fuseBottle
    Set-AtlasRectangle -Atlas $fuseAtlas -X 6 -Y 30 -Width 13 -Height 7 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 12 -Y 31 -Width 3 -Height 5 -Color $fuseLichen
    Set-AtlasPixel -Atlas $fuseAtlas -X 24 -Y 37 -Color $fuseBruise
    Set-AtlasRectangle -Atlas $fuseAtlas -X 30 -Y 28 -Width 24 -Height 9 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 37 -Y 29 -Width 9 -Height 6 -Color $fuseBottle
    Set-AtlasPixel -Atlas $fuseAtlas -X 51 -Y 30 -Color $fuseCold
    Set-AtlasRectangle -Atlas $fuseAtlas -X 0 -Y 40 -Width 20 -Height 8 -Color $fuseBottle
    Set-AtlasRectangle -Atlas $fuseAtlas -X 5 -Y 41 -Width 10 -Height 5 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 22 -Y 40 -Width 4 -Height 12 -Color $fuseLichen
    Set-AtlasRectangle -Atlas $fuseAtlas -X 23 -Y 41 -Width 2 -Height 9 -Color $fuseCold
    Set-AtlasPixel -Atlas $fuseAtlas -X 24 -Y 47 -Color $fuseBruise
    Set-AtlasRectangle -Atlas $fuseAtlas -X 28 -Y 40 -Width 8 -Height 10 -Color $fuseBottle
    Set-AtlasRectangle -Atlas $fuseAtlas -X 30 -Y 42 -Width 4 -Height 6 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 38 -Y 40 -Width 16 -Height 10 -Color $fuseMoss
    Set-AtlasRectangle -Atlas $fuseAtlas -X 41 -Y 42 -Width 8 -Height 6 -Color $fuseBottle
    Set-AtlasPixel -Atlas $fuseAtlas -X 51 -Y 48 -Color $fuseBruise
    Save-PixelAtlas -Atlas $fuseAtlas -Path (Join-Path $entityTextureRoot 'illusion_creeper.png')
}
finally {
    $fuseAtlas.Dispose()
}

$weaverBlack = [System.Drawing.Color]::FromArgb(255, 12, 13, 16)
$weaverCharcoal = [System.Drawing.Color]::FromArgb(255, 29, 31, 37)
$weaverIron = [System.Drawing.Color]::FromArgb(255, 61, 64, 72)
$weaverEdge = [System.Drawing.Color]::FromArgb(255, 102, 105, 113)
$weaverOxblood = [System.Drawing.Color]::FromArgb(255, 86, 23, 27)
$weaverRed = [System.Drawing.Color]::FromArgb(255, 208, 39, 40)
$weaverCold = [System.Drawing.Color]::FromArgb(255, 37, 190, 192)
$weaverBruise = [System.Drawing.Color]::FromArgb(255, 86, 48, 128)

$weaverAtlas = New-PixelAtlas -Width 64 -Height 64
try {
    Set-AtlasRectangle -Atlas $weaverAtlas -X 0 -Y 0 -Width 36 -Height 12 -Color $weaverCharcoal
    Set-AtlasRectangle -Atlas $weaverAtlas -X 5 -Y 1 -Width 25 -Height 3 -Color $weaverIron
    Set-AtlasRectangle -Atlas $weaverAtlas -X 13 -Y 5 -Width 12 -Height 5 -Color $weaverBlack
    Set-AtlasPixel -Atlas $weaverAtlas -X 3 -Y 9 -Color $weaverBruise
    Set-AtlasRectangle -Atlas $weaverAtlas -X 36 -Y 0 -Width 26 -Height 5 -Color $weaverIron
    Set-AtlasRectangle -Atlas $weaverAtlas -X 43 -Y 1 -Width 13 -Height 2 -Color $weaverEdge
    Set-AtlasPixel -Atlas $weaverAtlas -X 59 -Y 3 -Color $weaverCold
    Set-AtlasRectangle -Atlas $weaverAtlas -X 0 -Y 13 -Width 12 -Height 6 -Color $weaverBlack
    Set-AtlasRectangle -Atlas $weaverAtlas -X 3 -Y 14 -Width 5 -Height 4 -Color $weaverOxblood
    Set-AtlasRectangle -Atlas $weaverAtlas -X 14 -Y 13 -Width 14 -Height 2 -Color $weaverOxblood
    Set-AtlasPixel -Atlas $weaverAtlas -X 16 -Y 13 -Color $weaverRed
    Set-AtlasPixel -Atlas $weaverAtlas -X 20 -Y 14 -Color $weaverRed
    Set-AtlasPixel -Atlas $weaverAtlas -X 24 -Y 13 -Color $weaverRed
    Set-AtlasRectangle -Atlas $weaverAtlas -X 0 -Y 16 -Width 22 -Height 8 -Color $weaverCharcoal
    Set-AtlasRectangle -Atlas $weaverAtlas -X 5 -Y 18 -Width 12 -Height 4 -Color $weaverIron
    Set-AtlasRectangle -Atlas $weaverAtlas -X 24 -Y 16 -Width 26 -Height 12 -Color $weaverCharcoal
    Set-AtlasRectangle -Atlas $weaverAtlas -X 29 -Y 17 -Width 16 -Height 4 -Color $weaverIron
    Set-AtlasRectangle -Atlas $weaverAtlas -X 34 -Y 23 -Width 9 -Height 3 -Color $weaverBlack
    Set-AtlasPixel -Atlas $weaverAtlas -X 47 -Y 25 -Color $weaverBruise
    Set-AtlasRectangle -Atlas $weaverAtlas -X 0 -Y 30 -Width 18 -Height 4 -Color $weaverIron
    Set-AtlasRectangle -Atlas $weaverAtlas -X 4 -Y 31 -Width 11 -Height 2 -Color $weaverCharcoal
    Set-AtlasPixel -Atlas $weaverAtlas -X 16 -Y 30 -Color $weaverOxblood
    Set-AtlasRectangle -Atlas $weaverAtlas -X 20 -Y 30 -Width 18 -Height 4 -Color $weaverCharcoal
    Set-AtlasRectangle -Atlas $weaverAtlas -X 24 -Y 31 -Width 11 -Height 2 -Color $weaverBlack
    Set-AtlasPixel -Atlas $weaverAtlas -X 36 -Y 32 -Color $weaverRed
    Set-AtlasRectangle -Atlas $weaverAtlas -X 40 -Y 30 -Width 8 -Height 8 -Color $weaverBlack
    Set-AtlasRectangle -Atlas $weaverAtlas -X 42 -Y 31 -Width 4 -Height 5 -Color $weaverOxblood
    Set-AtlasPixel -Atlas $weaverAtlas -X 46 -Y 36 -Color $weaverCold
    Set-AtlasRectangle -Atlas $weaverAtlas -X 0 -Y 40 -Width 30 -Height 2 -Color $weaverOxblood
    Set-AtlasRectangle -Atlas $weaverAtlas -X 4 -Y 40 -Width 10 -Height 1 -Color $weaverCold
    Set-AtlasRectangle -Atlas $weaverAtlas -X 17 -Y 41 -Width 9 -Height 1 -Color $weaverBruise
    Save-PixelAtlas -Atlas $weaverAtlas -Path (Join-Path $entityTextureRoot 'illusion_spider.png')
}
finally {
    $weaverAtlas.Dispose()
}

$decoyVoid = [System.Drawing.Color]::FromArgb(255, 10, 15, 17)
$decoyOlive = [System.Drawing.Color]::FromArgb(255, 49, 59, 36)
$decoyMoss = [System.Drawing.Color]::FromArgb(255, 82, 91, 49)
$decoyTeal = [System.Drawing.Color]::FromArgb(255, 38, 75, 73)
$decoyOxide = [System.Drawing.Color]::FromArgb(255, 68, 112, 103)
$decoyViolet = [System.Drawing.Color]::FromArgb(255, 59, 43, 89)
$decoyVioletLight = [System.Drawing.Color]::FromArgb(255, 93, 61, 127)
$decoyCold = [System.Drawing.Color]::FromArgb(255, 47, 192, 187)

$decoyAtlas = New-PixelAtlas -Width 64 -Height 64
try {
    Set-AtlasRectangle -Atlas $decoyAtlas -X 0 -Y 0 -Width 24 -Height 13 -Color $decoyOlive
    Set-AtlasRectangle -Atlas $decoyAtlas -X 5 -Y 2 -Width 14 -Height 4 -Color $decoyMoss
    Set-AtlasRectangle -Atlas $decoyAtlas -X 8 -Y 7 -Width 8 -Height 4 -Color $decoyVoid
    Set-AtlasPixel -Atlas $decoyAtlas -X 3 -Y 10 -Color $decoyCold
    Set-AtlasRectangle -Atlas $decoyAtlas -X 26 -Y 0 -Width 8 -Height 7 -Color $decoyOlive
    Set-AtlasRectangle -Atlas $decoyAtlas -X 28 -Y 1 -Width 4 -Height 5 -Color $decoyVoid
    Set-AtlasRectangle -Atlas $decoyAtlas -X 36 -Y 0 -Width 18 -Height 7 -Color $decoyMoss
    Set-AtlasRectangle -Atlas $decoyAtlas -X 42 -Y 1 -Width 8 -Height 3 -Color $decoyOlive
    Set-AtlasPixel -Atlas $decoyAtlas -X 52 -Y 5 -Color $decoyVioletLight
    Set-AtlasRectangle -Atlas $decoyAtlas -X 26 -Y 14 -Width 16 -Height 13 -Color $decoyTeal
    Set-AtlasRectangle -Atlas $decoyAtlas -X 29 -Y 16 -Width 10 -Height 8 -Color $decoyOxide
    Set-AtlasPixel -Atlas $decoyAtlas -X 40 -Y 25 -Color $decoyVioletLight
    Set-AtlasRectangle -Atlas $decoyAtlas -X 44 -Y 14 -Width 12 -Height 7 -Color $decoyViolet
    Set-AtlasRectangle -Atlas $decoyAtlas -X 47 -Y 15 -Width 6 -Height 5 -Color $decoyVioletLight
    Set-AtlasRectangle -Atlas $decoyAtlas -X 0 -Y 30 -Width 20 -Height 9 -Color $decoyOlive
    Set-AtlasRectangle -Atlas $decoyAtlas -X 5 -Y 31 -Width 10 -Height 6 -Color $decoyMoss
    Set-AtlasPixel -Atlas $decoyAtlas -X 17 -Y 37 -Color $decoyCold
    Set-AtlasRectangle -Atlas $decoyAtlas -X 22 -Y 30 -Width 12 -Height 9 -Color $decoyOlive
    Set-AtlasRectangle -Atlas $decoyAtlas -X 25 -Y 31 -Width 6 -Height 6 -Color $decoyMoss
    Set-AtlasRectangle -Atlas $decoyAtlas -X 36 -Y 30 -Width 16 -Height 11 -Color $decoyOlive
    Set-AtlasRectangle -Atlas $decoyAtlas -X 40 -Y 32 -Width 8 -Height 7 -Color $decoyMoss
    Set-AtlasPixel -Atlas $decoyAtlas -X 50 -Y 39 -Color $decoyVioletLight
    Set-AtlasRectangle -Atlas $decoyAtlas -X 0 -Y 44 -Width 16 -Height 10 -Color $decoyViolet
    Set-AtlasRectangle -Atlas $decoyAtlas -X 4 -Y 46 -Width 8 -Height 6 -Color $decoyVioletLight
    Set-AtlasRectangle -Atlas $decoyAtlas -X 18 -Y 44 -Width 22 -Height 9 -Color $decoyOlive
    Set-AtlasRectangle -Atlas $decoyAtlas -X 23 -Y 45 -Width 12 -Height 6 -Color $decoyMoss
    Set-AtlasPixel -Atlas $decoyAtlas -X 38 -Y 51 -Color $decoyCold
    Set-AtlasRectangle -Atlas $decoyAtlas -X 42 -Y 44 -Width 12 -Height 6 -Color $decoyVoid
    Set-AtlasRectangle -Atlas $decoyAtlas -X 45 -Y 45 -Width 6 -Height 4 -Color $decoyCold
    Save-PixelAtlas -Atlas $decoyAtlas -Path (Join-Path $entityTextureRoot 'illusion_zombie.png')
}
finally {
    $decoyAtlas.Dispose()
}
