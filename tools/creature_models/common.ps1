Add-Type -AssemblyName System.Drawing

function New-PixelAtlas {
    param(
        [Parameter(Mandatory = $true)][ValidateRange(1, 16384)][int]$Width,
        [Parameter(Mandatory = $true)][ValidateRange(1, 16384)][int]$Height,
        [System.Drawing.Color]$ClearColor = [System.Drawing.Color]::Transparent
    )

    $bitmap = [System.Drawing.Bitmap]::new(
        $Width,
        $Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear($ClearColor)
    }
    finally {
        $graphics.Dispose()
    }
    return $bitmap
}

function Test-AtlasRectangle {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$X,
        [Parameter(Mandatory = $true)][int]$Y,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height
    )

    return $X -ge 0 -and $Y -ge 0 -and $Width -ge 0 -and $Height -ge 0 `
        -and $X + $Width -le $Atlas.Width -and $Y + $Height -le $Atlas.Height
}

function Set-AtlasPixel {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$X,
        [Parameter(Mandatory = $true)][int]$Y,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Color
    )

    if (-not (Test-AtlasRectangle -Atlas $Atlas -X $X -Y $Y -Width 1 -Height 1)) {
        throw "Pixel coordinate is outside the atlas: $X,$Y"
    }
    $Atlas.SetPixel($X, $Y, $Color)
}

function Set-AtlasRectangle {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$X,
        [Parameter(Mandatory = $true)][int]$Y,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][System.Drawing.Color]$Color
    )

    if (-not (Test-AtlasRectangle -Atlas $Atlas -X $X -Y $Y -Width $Width -Height $Height)) {
        throw "Rectangle is outside the atlas: $X,$Y $Width x $Height"
    }
    for ($row = $Y; $row -lt $Y + $Height; $row++) {
        for ($column = $X; $column -lt $X + $Width; $column++) {
            $Atlas.SetPixel($column, $row, $Color)
        }
    }
}

function Copy-AtlasRectangle {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][int]$SourceX,
        [Parameter(Mandatory = $true)][int]$SourceY,
        [Parameter(Mandatory = $true)][int]$Width,
        [Parameter(Mandatory = $true)][int]$Height,
        [Parameter(Mandatory = $true)][int]$DestinationX,
        [Parameter(Mandatory = $true)][int]$DestinationY
    )

    if (-not (Test-AtlasRectangle -Atlas $Atlas -X $SourceX -Y $SourceY -Width $Width -Height $Height)) {
        throw "Source rectangle is outside the atlas"
    }
    if (-not (Test-AtlasRectangle -Atlas $Atlas -X $DestinationX -Y $DestinationY -Width $Width -Height $Height)) {
        throw "Destination rectangle is outside the atlas"
    }
    $pixels = [System.Drawing.Color[,]]::new($Width, $Height)
    for ($row = 0; $row -lt $Height; $row++) {
        for ($column = 0; $column -lt $Width; $column++) {
            $pixels[$column, $row] = $Atlas.GetPixel($SourceX + $column, $SourceY + $row)
        }
    }
    for ($row = 0; $row -lt $Height; $row++) {
        for ($column = 0; $column -lt $Width; $column++) {
            $Atlas.SetPixel($DestinationX + $column, $DestinationY + $row, $pixels[$column, $row])
        }
    }
}

function Save-PixelAtlas {
    param(
        [Parameter(Mandatory = $true)][System.Drawing.Bitmap]$Atlas,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $resolved = [System.IO.Path]::GetFullPath($Path)
    $directory = [System.IO.Path]::GetDirectoryName($resolved)
    if (-not [string]::IsNullOrEmpty($directory)) {
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    }
    $Atlas.Save($resolved, [System.Drawing.Imaging.ImageFormat]::Png)
}
