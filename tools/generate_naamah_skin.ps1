$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$generator = Join-Path $PSScriptRoot 'GenerateOriginalAssets.java'

& java $generator --nami-naamah $projectRoot
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
