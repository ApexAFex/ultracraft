# Печатает цвета красителей из клиента 26.3 (tools/dye-colors.java).
#
#   .\tools\dye-colors.ps1
#
# Цвет нужен там, где ваниль красит текстуру: шерсть овцы, кожаная броня, флаги.
# Числа берутся у самой игры, а не из вики: белый краситель — 0xF9FFFE, а не 0xFFFFFF.
# Нужен JDK 25 (классы 26.3 — class file version 69) и установленный клиент 26.3.

$ErrorActionPreference = 'Stop'

$jar = Join-Path $env:APPDATA '.minecraft\versions\26.3\26.3.jar'
if (-not (Test-Path $jar)) { throw "Нет клиента 26.3: $jar" }

$libraries = Join-Path $env:APPDATA '.minecraft\libraries'
$classpath = @($jar) + @(
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
    (Join-Path $PSScriptRoot 'dye-colors.java')
