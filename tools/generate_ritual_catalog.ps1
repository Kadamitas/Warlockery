$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$ritualRoot = Join-Path $projectRoot 'src/main/resources/data/warlockery/ritual'
$readmePath = Join-Path $projectRoot 'README.md'

$entries = Get-ChildItem $ritualRoot -Filter '*.json' |
    ForEach-Object {
        $definition = Get-Content -Raw $_.FullName | ConvertFrom-Json
        [pscustomobject]@{
            Title = [string]$definition.title
            Description = [string]$definition.description
        }
    } |
    Sort-Object Title

$lines = $entries | ForEach-Object { "- **$($_.Title)**: $($_.Description)" }
$catalog = "## Ritual catalog`r`n`r`n$($lines -join "`r`n")"
$readme = Get-Content -Raw $readmePath
$pattern = '(?s)## Ritual catalog\r?\n.*?(?=\r?\n## Doll catalog)'

if ($readme -notmatch $pattern) {
    throw 'README ritual catalog section was not found.'
}

$updated = [regex]::Replace($readme, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{
    param($match)
    $catalog
})

Set-Content -Encoding utf8 $readmePath $updated
Write-Host "Updated the README with $($entries.Count) live ritual definitions."
