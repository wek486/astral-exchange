$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env')) {
    if ($line -match '^(DB_URL|DB_USER|DB_PASSWORD)=(.*)$') {
        [Environment]::SetEnvironmentVariable("TEST_$($Matches[1])", $Matches[2], 'Process')
    }
}
# This script never runs tests against the application's database.
if ($env:TEST_DB_URL -notmatch '/astral_exchange\?') { throw 'Expected database name astral_exchange; configure TEST_DB_URL manually for custom names.' }
$env:TEST_DB_URL = $env:TEST_DB_URL.Replace('/astral_exchange?', '/astral_exchange_test?')
$env:TEST_DB_DRIVER = 'com.mysql.cj.jdbc.Driver'
Push-Location (Join-Path $projectRoot 'backend')
try {
    & mvn.cmd "-Dmaven.repo.local=$projectRoot/.local/m2" test
    if ($LASTEXITCODE -ne 0) { throw 'MySQL integration tests failed.' }
} finally { Pop-Location }
