# Печатает модель жителя из клиентского jar (tools/villager-dump.java).
#
#   .\tools\villager-dump.ps1                            # в консоль
#   .\tools\villager-dump.ps1 tools\villager-model.tsv   # в файл для build-villager-model.js
#
# Модели мобов в игре — код, а не данные, поэтому числа берутся у самой игры. Нужен JDK 25
# (классы 26.3 собраны под class file version 69) и установленный клиент 26.3.

param(
    [string]$Output
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

# Кавычки обязательны: без них PowerShell съедает -D… как свой параметр.
$result = & $java '-Duser.language=en' '-Duser.country=US' -cp ($classpath -join ';') `
    (Join-Path $PSScriptRoot 'villager-dump.java')
if ($LASTEXITCODE -ne 0) { throw "Модель не напечаталась (код $LASTEXITCODE)" }

if ($Output) {
    $result | Set-Content -Path $Output -Encoding utf8
    Write-Host "Модель записана в $Output (строк: $($result.Count))"
} else {
    $result
}
