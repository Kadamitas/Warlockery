param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot,

    [Parameter(Mandatory = $true)]
    [string]$TargetRoot,

    [string]$ManifestPath = (Join-Path $PSScriptRoot 'readable_roster_palettes.json'),

    [string]$RecolorScript = (Join-Path $env:USERPROFILE '.codex\skills\recolor-pixel-icons\scripts\recolor_pixel_icon.ps1')
)

$ErrorActionPreference = 'Stop'

$resolvedSource = (Resolve-Path -LiteralPath $SourceRoot).Path
$resolvedTarget = (Resolve-Path -LiteralPath $TargetRoot).Path
$resolvedManifest = (Resolve-Path -LiteralPath $ManifestPath).Path
$resolvedRecolor = (Resolve-Path -LiteralPath $RecolorScript).Path

if ($resolvedSource -eq $resolvedTarget) {
    throw 'SourceRoot and TargetRoot must differ so generation always uses an immutable source atlas set.'
}

$manifest = Get-Content -LiteralPath $resolvedManifest -Raw | ConvertFrom-Json
foreach ($entry in $manifest.entries) {
    $fileName = $entry.id + '.png'
    $sourcePath = Join-Path $resolvedSource $fileName
    $targetPath = Join-Path $resolvedTarget $fileName
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Missing source atlas: $sourcePath"
    }

    $palette = $entry.palette -join ','
    & $resolvedRecolor -SourcePath $sourcePath -TargetPath $targetPath -Palette $palette
}

Write-Output ("Generated {0} roster atlases from {1}" -f $manifest.entries.Count, $resolvedSource)
