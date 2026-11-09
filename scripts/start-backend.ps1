$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^(DB_URL|DB_USER|DB_PASSWORD|PORT|COOKIE_SECURE)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
Push-Location (Join-Path $projectRoot 'backend')
try {
    & mvn.cmd "-Dmaven.repo.local=$projectRoot/.local/m2" spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. Check Java, MySQL and .env.' }
} finally { Pop-Location }
