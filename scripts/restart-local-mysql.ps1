param([string]$MySqlBin = "$env:ProgramFiles/MySQL/MySQL Server 8.0/bin")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$line = Get-Content -LiteralPath (Join-Path $projectRoot '.env') | Where-Object { $_ -match '^DB_URL=' } | Select-Object -First 1
if ($line -notmatch 'jdbc:mysql://127\.0\.0\.1:(\d+)/') { throw 'This helper only restarts the isolated loopback database.' }
$port = [int]$Matches[1]
if (Test-NetConnection 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue) { Write-Output "MySQL is already listening on $port."; exit 0 }
$dataPath = Join-Path $projectRoot '.local/mysql-data'
if (-not (Test-Path -LiteralPath (Join-Path $dataPath 'mysql'))) { throw 'Run init-local-mysql.ps1 first.' }
$server = Start-Process -FilePath "$MySqlBin/mysqld.exe" -ArgumentList @('--no-defaults', "--datadir=`"$dataPath`"", "--port=$port", '--bind-address=127.0.0.1', '--mysqlx=OFF', "--log-error=`"$projectRoot/.local/mysql.log`"") -WindowStyle Hidden -PassThru
$server.Id | Set-Content -LiteralPath (Join-Path $projectRoot '.local/mysql.pid')
Write-Output "Isolated MySQL starting on port $port."
