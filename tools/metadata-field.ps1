# Печатает номера полей метаданных класса сущности (tools/metadata-field.java).
#
#   .\tools\metadata-field.ps1 net.minecraft.world.entity.animal.sheep.Sheep
#   .\tools\metadata-field.ps1 net.minecraft.world.entity.monster.Slime net.minecraft.world.entity.animal.sheep.Sheep
#
# Номера нужны, чтобы читать поля из `Set Entity Data`: порядок в javap не совпадает с
# номерами в сети, потому что номер — это порядок `defineId` при инициализации класса плюс
# число полей родителей. Нужен JDK 25 (классы 26.3 — class file version 69).

param(
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)][string[]]$Classes
)

$ErrorActionPreference = 'Stop'

# Сервер 26.3 лежит рядом: он не обфусцирован, поэтому имена полей настоящие.
$server = Join-Path $PSScriptRoot 'mcd\server-26.3.jar'
if (-not (Test-Path $server)) { throw "Нет сервера 26.3: $server" }

$libraries = Join-Path $env:APPDATA '.minecraft\libraries'
$classpath = @($server) + @(
    Get-ChildItem $libraries -Recurse -Filter *.jar -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch 'natives' } |
        Select-Object -ExpandProperty FullName
)

$java = $null
foreach ($root in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java')) {
    if (-not (Test-Path $root)) { continue }
    foreach ($dir in Get-ChildItem $root -Directory -ErrorAction SilentlyContinue) {
        if ($dir.Name -match '^jdk-(\d+)' -and [int]$Matches[1] -ge 25) {
            $candidate = Join-Path $dir.FullName 'bin\java.exe'
            if (Test-Path $candidate) { $java = $candidate; break }
        }
    }
    if ($java) { break }
}
if (-not $java) { throw 'Не найден JDK 25+ (java.exe)' }

# java пишет предупреждение JVM в stderr; с `Stop` PowerShell принял бы его за ошибку
# и оборвал вывод таблицы, поэтому здесь предупреждения не считаются ошибкой.
$ErrorActionPreference = 'Continue'
& $java '-Duser.language=en' '-Duser.country=US' -cp ($classpath -join ';') `
    (Join-Path $PSScriptRoot 'metadata-field.java') @Classes
