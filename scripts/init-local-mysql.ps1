param([string]$MySqlBin = "$env:ProgramFiles/MySQL/MySQL Server 8.0/bin", [int]$Port = 3307)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$dataPath = Join-Path $projectRoot '.local/mysql-data'
$envPath = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envPath) { throw '.env already exists. This helper does not overwrite configuration.' }
New-Item -ItemType Directory -Force -Path $dataPath | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $dataPath 'mysql'))) {
    & "$MySqlBin/mysqld.exe" --no-defaults --initialize-insecure "--basedir=$(Split-Path -Parent $MySqlBin)" "--datadir=$dataPath" --console
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed.' }
}
$connection = Test-NetConnection 127.0.0.1 -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
if ($connection) { throw "Port $Port is in use. Choose another port or stop the local test server." }
$server = Start-Process -FilePath "$MySqlBin/mysqld.exe" -ArgumentList @('--no-defaults', "--datadir=`"$dataPath`"", "--port=$Port", '--bind-address=127.0.0.1', '--mysqlx=OFF', "--log-error=`"$projectRoot/.local/mysql.log`"") -WindowStyle Hidden -PassThru
for ($attempt=0; $attempt -lt 30; $attempt++) {
    & "$MySqlBin/mysqladmin.exe" --no-defaults -h 127.0.0.1 -P $Port -u root ping --silent 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Milliseconds 500
}
$appPassword = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$rootPassword = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$sql = "CREATE DATABASE astral_exchange CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE astral_exchange_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER 'astral'@'localhost' IDENTIFIED BY '$appPassword'; GRANT ALL PRIVILEGES ON astral_exchange.* TO 'astral'@'localhost'; GRANT ALL PRIVILEGES ON astral_exchange_test.* TO 'astral'@'localhost'; ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';"
$sql | & "$MySqlBin/mysql.exe" --no-defaults -h 127.0.0.1 -P $Port -u root
if ($LASTEXITCODE -ne 0) { Stop-Process -Id $server.Id; throw 'MySQL provisioning failed.' }
@"
DB_URL=jdbc:mysql://127.0.0.1:$Port/astral_exchange?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
DB_USER=astral
DB_PASSWORD=$appPassword
PORT=8080
COOKIE_SECURE=false
DEV_MYSQL_ROOT_PASSWORD=$rootPassword
"@ | Set-Content -LiteralPath $envPath -Encoding utf8
$server.Id | Set-Content -LiteralPath (Join-Path $projectRoot '.local/mysql.pid')
Write-Output "Isolated MySQL started on port $Port. Credentials saved to ignored .env; existing databases were not changed."
