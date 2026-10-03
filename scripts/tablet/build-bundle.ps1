# Builds everything the tablet needs into <repo>\tablet-bundle (git-ignored, it contains your .env secrets):
#   app.jar   Spring Boot backend      web\   static storefront/admin (Vite build)
#   .env      copy of your .env with the database settings switched to the tablet's local MariaDB
#   *.sh      scripts to run in Termux (line endings forced to LF)
# Usage (PowerShell, from anywhere):  powershell -ExecutionPolicy Bypass -File scripts\tablet\build-bundle.ps1
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $root 'tablet-bundle'

function Write-Lf([string]$path, [string]$text) {
    [IO.File]::WriteAllText($path, ($text -replace "`r`n", "`n"), (New-Object Text.UTF8Encoding($false)))
}

Write-Host '== 1/5 Build backend jar (tests skipped, they already run on H2)'
Push-Location $root
try {
    & mvn -q clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' }
} finally { Pop-Location }
$jar = Get-ChildItem (Join-Path $root 'target') -Filter '*.jar' | Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw 'No jar found in target\' }

Write-Host '== 2/5 Build frontend (VITE_API_BASE=/identity so it talks to the same origin)'
Push-Location (Join-Path $root 'shop-ui')
try {
    $env:VITE_API_BASE = '/identity'
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw 'npm run build failed' }
} finally { Remove-Item Env:\VITE_API_BASE -ErrorAction SilentlyContinue; Pop-Location }

Write-Host '== 3/5 Assemble tablet-bundle\'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out | Out-Null
Copy-Item $jar.FullName (Join-Path $out 'app.jar')
Copy-Item (Join-Path $root 'shop-ui\dist') (Join-Path $out 'web') -Recurse
Get-ChildItem (Join-Path $PSScriptRoot '*.sh') | ForEach-Object {
    Write-Lf (Join-Path $out $_.Name) ([IO.File]::ReadAllText($_.FullName))
}

Write-Host '== 4/5 Write tablet .env (database -> local MariaDB, new random password)'
$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { throw '.env not found in the repo root' }
$chars = (48..57) + (65..90) + (97..122)
$dbPassword = -join ($chars | Get-Random -Count 24 | ForEach-Object { [char]$_ })
$override = [ordered]@{
    DB_URL      = 'jdbc:mysql://127.0.0.1:3306/identity_service?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8'
    DB_USERNAME = 'shop'
    DB_PASSWORD = $dbPassword
    SERVER_PORT = '8081'
}
$lines = [IO.File]::ReadAllLines($envFile)
$seen = @{}
$result = foreach ($line in $lines) {
    $m = [regex]::Match($line, '^\s*([A-Za-z0-9_]+)\s*=')
    if ($m.Success -and $override.Contains($m.Groups[1].Value)) {
        $key = $m.Groups[1].Value; $seen[$key] = $true; "$key=$($override[$key])"
    } else { $line }
}
foreach ($key in $override.Keys) { if (-not $seen[$key]) { $result += "$key=$($override[$key])" } }
Write-Lf (Join-Path $out '.env') (($result -join "`n") + "`n")

Write-Host '== 5/5 Done'
$size = [math]::Round(((Get-ChildItem $out -Recurse | Measure-Object Length -Sum).Sum) / 1MB, 1)
Write-Host "Bundle: $out ($size MB)"
Write-Host 'Next: copy it to the tablet, see docs\tablet-server.md (step 4).'
