# Runs one command on the local dev server over RCON.
#
#   .\tools\rcon.ps1 "give Tester minecraft:stone 8"
#   .\tools\rcon.ps1 "list"
#
# The server must have enable-rcon=true and the same password in server.properties
# (tools/server-start.ps1 writes a properties file without rcon, so it is enabled by hand).
#
# RCON is a tiny binary protocol: every packet is
#   int32 length, int32 id, int32 type, payload, two zero bytes
# with little-endian integers. Type 3 is the login, 2 is a command, 0 is the answer.
#
# Byte arrays leave a PowerShell function as a list of numbers unless they are handed back
# through [ref], which is why the helpers below fill an output variable instead of returning.

param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Command,
    [string]$Server = '127.0.0.1',
    [int]$Port = 25575,
    [string]$Password = 'devrcon'
)

$ErrorActionPreference = 'Stop'

function New-RconPacket {
    param([int]$Id, [int]$Type, [string]$Body, [ref]$Out)

    $payload = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $buffer = New-Object System.IO.MemoryStream
    $writer = New-Object System.IO.BinaryWriter($buffer)
    $writer.Write([int](10 + $payload.Length))
    $writer.Write([int]$Id)
    $writer.Write([int]$Type)
    $writer.Write($payload, 0, $payload.Length)
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $writer.Flush()
    $Out.Value = $buffer.ToArray()
}

function Read-Exact {
    param([System.IO.Stream]$Stream, [int]$Count, [ref]$Out)

    $bytes = New-Object byte[] $Count
    $read = 0
    while ($read -lt $Count) {
        $n = $Stream.Read($bytes, $read, $Count - $read)
        if ($n -le 0) { throw 'RCON: сервер закрыл соединение' }
        $read += $n
    }
    $Out.Value = $bytes
}

function Read-RconPacket {
    param([System.IO.Stream]$Stream, [hashtable]$Out)

    $header = $null
    Read-Exact -Stream $Stream -Count 4 -Out ([ref]$header)
    $length = [System.BitConverter]::ToInt32($header, 0)

    $body = $null
    Read-Exact -Stream $Stream -Count $length -Out ([ref]$body)

    $Out.Id = [System.BitConverter]::ToInt32($body, 0)
    $payloadLength = $length - 10
    $Out.Text = if ($payloadLength -gt 0) {
        [System.Text.Encoding]::UTF8.GetString($body, 8, $payloadLength)
    } else { '' }
}

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Server, $Port)
$stream = $client.GetStream()

try {
    $login = $null
    New-RconPacket -Id 1 -Type 3 -Body $Password -Out ([ref]$login)
    $stream.Write($login, 0, $login.Length)
    $stream.Flush()

    $auth = @{}
    Read-RconPacket -Stream $stream -Out $auth
    if ($auth.Id -eq -1) { throw 'RCON: неверный пароль' }

    $packet = $null
    New-RconPacket -Id 2 -Type 2 -Body $Command -Out ([ref]$packet)
    $stream.Write($packet, 0, $packet.Length)
    $stream.Flush()

    $answer = @{}
    Read-RconPacket -Stream $stream -Out $answer
    $answer.Text
} finally {
    $client.Close()
}
