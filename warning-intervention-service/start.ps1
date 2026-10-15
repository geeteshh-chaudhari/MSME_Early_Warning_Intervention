param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string]$Jar = (Join-Path $PSScriptRoot 'target\warning-intervention-service-1.jar')
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $EnvFile)) { throw 'Environment file not found; supply -EnvFile.' }
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    $trimmed = $line.Trim()
    if (!$trimmed -or $trimmed.StartsWith('#')) { continue }
    if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { throw 'Invalid environment file line.' }
    $key = $Matches[1]
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1, $value.Length - 2) }
    [Environment]::SetEnvironmentVariable($key, $value, 'Process')
}
foreach ($key in @('database_url', 'database_user3', 'database_password3', 'JWT_SECRET')) {
    if (![Environment]::GetEnvironmentVariable($key, 'Process')) { throw "Missing required setting: $key" }
}
if (!(Test-Path -LiteralPath $Jar)) { throw 'Build the service before launching.' }
& java -jar $Jar
exit $LASTEXITCODE
