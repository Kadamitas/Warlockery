$generator = Join-Path $PSScriptRoot 'creature_models\generate_naamah.ps1'

& $generator
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
