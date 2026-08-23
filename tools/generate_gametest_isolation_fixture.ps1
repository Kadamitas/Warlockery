[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\src\main\resources\data\warlockery\structure\empty32x32x32.nbt")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-UnsignedShortBigEndian {
    param(
        [System.IO.Stream]$Stream,
        [int]$Value
    )

    $Stream.WriteByte([byte](($Value -shr 8) -band 0xFF))
    $Stream.WriteByte([byte]($Value -band 0xFF))
}

function Write-IntBigEndian {
    param(
        [System.IO.Stream]$Stream,
        [int]$Value
    )

    $Stream.WriteByte([byte](($Value -shr 24) -band 0xFF))
    $Stream.WriteByte([byte](($Value -shr 16) -band 0xFF))
    $Stream.WriteByte([byte](($Value -shr 8) -band 0xFF))
    $Stream.WriteByte([byte]($Value -band 0xFF))
}

function Write-NbtString {
    param(
        [System.IO.Stream]$Stream,
        [string]$Value
    )

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    Write-UnsignedShortBigEndian -Stream $Stream -Value $bytes.Length
    $Stream.Write($bytes, 0, $bytes.Length)
}

function Write-NbtListHeader {
    param(
        [System.IO.Stream]$Stream,
        [string]$Name,
        [byte]$ElementType,
        [int]$Length
    )

    $Stream.WriteByte(9)
    Write-NbtString -Stream $Stream -Value $Name
    $Stream.WriteByte($ElementType)
    Write-IntBigEndian -Stream $Stream -Value $Length
}

$output = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($output)) | Out-Null

$payload = [System.IO.MemoryStream]::new()
try {
    $payload.WriteByte(10)
    Write-NbtString -Stream $payload -Value ''

    $payload.WriteByte(3)
    Write-NbtString -Stream $payload -Value 'DataVersion'
    Write-IntBigEndian -Stream $payload -Value 4903

    Write-NbtListHeader -Stream $payload -Name 'size' -ElementType 3 -Length 3
    1..3 | ForEach-Object { Write-IntBigEndian -Stream $payload -Value 32 }

    Write-NbtListHeader -Stream $payload -Name 'blocks' -ElementType 10 -Length 0
    Write-NbtListHeader -Stream $payload -Name 'palette' -ElementType 10 -Length 0
    Write-NbtListHeader -Stream $payload -Name 'entities' -ElementType 10 -Length 0
    $payload.WriteByte(0)

    $destination = [System.IO.File]::Create($output)
    try {
        $gzip = [System.IO.Compression.GZipStream]::new(
            $destination,
            [System.IO.Compression.CompressionMode]::Compress,
            $false
        )
        try {
            $payload.WriteTo($gzip)
        } finally {
            $gzip.Dispose()
        }
    } finally {
        $destination.Dispose()
    }
} finally {
    $payload.Dispose()
}
