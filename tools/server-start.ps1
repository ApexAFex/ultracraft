# Starts the local vanilla Minecraft server used as a protocol oracle.
#
#   .\tools\server-start.ps1                 # start and wait until ready
#   .\tools\server-start.ps1 -Restart        # stop a running instance first
#   .\tools\server-start.ps1 -Restart -DebugBlocks
#                                            # log the server's verdict on every
#                                            # block-break packet ("Server ACK ...")
#
# Requires JDK 25+ (Minecraft 26.3 refuses older runtimes).

param(
    [string]$ServerDir = (Join-Path $PSScriptRoot 'server'),
    [string]$Memory = '1536M',
    [switch]$Restart,
    [switch]$DebugBlocks
)

$ErrorActionPreference = 'Stop'

function Find-Jdk {
    param([int]$MinimumMajor)

    $roots = @(
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java',
        'C:\Program Files\Microsoft'
    )
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        foreach ($dir in Get-ChildItem $root -Directory -ErrorAction SilentlyContinue) {
            $exe = Join-Path $dir.FullName 'bin\java.exe'
            if (-not (Test-Path $exe)) { continue }
            if ($dir.Name -match '(\d+)(\.\d+)*') {
                $major = [int]$Matches[1]
                if ($major -ge $MinimumMajor) { return $exe }
            }
        }
    }
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw "Не найден JDK $MinimumMajor+. Установи: winget install --id EclipseAdoptium.Temurin.25.JDK -e"
}

if (-not (Test-Path (Join-Path $ServerDir 'server.jar'))) {
    throw "Нет $(Join-Path $ServerDir 'server.jar'). Скачай server.jar для 26.3 с minecraft.net в $ServerDir."
}

if ($Restart) {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -like "*server.jar*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
}

# eula.txt is the licence acknowledgement the vanilla server requires before it starts.
$eula = Join-Path $ServerDir 'eula.txt'
if (-not (Test-Path $eula)) { 'eula=true' | Out-File -FilePath $eula -Encoding ascii }

$properties = Join-Path $ServerDir 'server.properties'
if (-not (Test-Path $properties)) {
    @'
online-mode=false
white-list=false
enforce-whitelist=false
level-type=minecraft\:flat
spawn-protection=0
max-players=10
view-distance=4
simulation-distance=4
network-compression-threshold=256
generate-structures=false
sync-chunk-writes=false
max-tick-time=-1
motd=mcplus dev
'@ | Out-File -FilePath $properties -Encoding ascii
}

$java = Find-Jdk -MinimumMajor 25
$log = Join-Path $ServerDir 'server.log'
$err = Join-Path $ServerDir 'server.err'

# The debug flags are read by SharedConstants from system properties; the prefix is MC_DEBUG_.
$javaArgs = @("-Xmx$Memory")
if ($DebugBlocks) {
    $config = Join-Path $PSScriptRoot 'server-debug-log4j2.xml'
    $javaArgs += '-DMC_DEBUG_ENABLED=true', '-DMC_DEBUG_BLOCK_BREAK=true'
    $javaArgs += "-Dlog4j.configurationFile=$config"
    Write-Host 'отладка разрушения блоков включена: в лог пойдут строки «Server ACK …»'
}
$javaArgs += '-jar', 'server.jar', 'nogui'

Write-Host "java: $java"
Write-Host "каталог: $ServerDir"

$process = Start-Process -FilePath $java `
    -ArgumentList $javaArgs `
    -WorkingDirectory $ServerDir `
    -RedirectStandardOutput $log -RedirectStandardError $err `
    -NoNewWindow -PassThru

$deadline = (Get-Date).AddSeconds(180)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if ($process.HasExited) {
        Write-Host "Сервер завершился с кодом $($process.ExitCode)"
        if (Test-Path $err) { Get-Content $err -Tail 15 }
        exit 1
    }
    if ((Test-Path $log) -and ((Get-Content $log -Raw) -match 'Done \(')) {
        Write-Host "Сервер готов: 127.0.0.1:25565 (pid $($process.Id))"
        Write-Host "Проверка: .\gradlew.bat :app:run --args=`"join`""
        exit 0
    }
}

Write-Host "Сервер не ответил за 180 секунд, смотри $log"
exit 1
