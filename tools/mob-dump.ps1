# Печатает модель моба из клиентского jar (tools/mob-dump.java).
#
#   .\tools\mob-dump.ps1 net.minecraft.client.model.animal.squid.SquidModel
#   .\tools\mob-dump.ps1 net.minecraft.client.model.animal.golem.IronGolemModel
#
# Модели мобов в игре — код, а не данные, поэтому числа берутся у самой игры. Нужен JDK 25
# (классы 26.3 собраны под class file version 69) и установленный клиент 26.3.

param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Model,
    [string]$Method = 'createBodyLayer'
)

$ErrorActionPreference = 'Stop'

$jar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $jar)) { throw "Нет клиента 26.3: $jar" }

$libraries = Join-Path $env:APPDATA '.minecraft\libraries'
$classpath = @($jar) + @(
    Get-ChildItem $libraries -Recurse -Filter *.jar |
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

& $java '-Duser.language=en' '-Duser.country=US' -cp ($classpath -join ';') `
    (Join-Path $PSScriptRoot 'mob-dump.java') $Model $Method
