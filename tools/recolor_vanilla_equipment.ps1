param(
    [string]$SkillRoot = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.codex\skills\recolor-pixel-icons')
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

$clientJar = Get-ChildItem (Join-Path $PSScriptRoot '..\.gradle\mavenizer\repo\net\minecraft\client-extra') -Recurse -Filter '*.jar' |
    Select-Object -First 1
if (!$clientJar) {
    throw 'Minecraft client-extra jar was not found. Run a Forge Gradle task first.'
}

$temporary = Join-Path $PSScriptRoot '..\build\tmp\vanilla_equipment_icons'
$target = Join-Path $PSScriptRoot '..\src\main\resources\assets\warlockery\textures\item'
[System.IO.Directory]::CreateDirectory($temporary) | Out-Null
$archive = [System.IO.Compression.ZipFile]::OpenRead($clientJar.FullName)

function Extract-Icon([string]$name) {
    $destination = Join-Path $temporary "$name.png"
    $entry = $archive.GetEntry("assets/minecraft/textures/item/$name.png")
    if (!$entry) {
        throw "Missing vanilla icon: $name"
    }
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
    return $destination
}

function Recolor([string]$source, [string]$destination, [string]$palette, [switch]$MetalOnly) {
    $arguments = @{
        SourcePath = $source
        TargetPath = (Join-Path $target "$destination.png")
        Palette = $palette
    }
    if ($MetalOnly) {
        $arguments.PreserveHighSaturation = $true
        $arguments.SaturationThreshold = 0.24
    }
    & (Join-Path $SkillRoot 'scripts\recolor_pixel_icon.ps1') @arguments
}

$tools = @{
    sword = 'silversword'
    pickaxe = 'silverpickaxe'
    axe = 'silveraxe'
    hoe = 'silverhoe'
    shovel = 'silvershovel'
    helmet = 'silverhelm'
    chestplate = 'silverchestplate'
    leggings = 'silverleggings'
    boots = 'silverboots'
}
foreach ($part in $tools.Keys) {
    Recolor (Extract-Icon "iron_$part") $tools[$part] '#48606a,#78909b,#b8cbd2,#e9f2f3,#ffffff' -MetalOnly
}

$goblinite = @{
    sword = 'delvealloysword'
    pickaxe = 'delvealloypickaxe'
    axe = 'delvealloyaxe'
    hoe = 'delvealloyhoe'
    shovel = 'delvealloyshovel'
    helmet = 'delvealloyhelm'
    chestplate = 'delvealloychestplate'
    leggings = 'delvealloyleggings'
    boots = 'delvealloyboots'
}
foreach ($part in $goblinite.Keys) {
    Recolor (Extract-Icon "netherite_$part") $goblinite[$part] '#071a12,#123c27,#24643b,#4a9451,#83d05f'
}

Recolor (Extract-Icon 'iron_spear') 'thorn_spear' '#142415,#315a29,#5d8c3c,#9fc75b,#d6ec82' -MetalOnly
$archive.Dispose()
