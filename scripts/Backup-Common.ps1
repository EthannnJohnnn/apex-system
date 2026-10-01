Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Find-ApexPgTool([string]$Name, [string]$PgBin) {
    if ($PgBin) { $path = Join-Path $PgBin ($Name + '.exe') }
    else {
        $command = Get-Command ($Name + '.exe') -ErrorAction SilentlyContinue
        if ($command) { $path = $command.Source }
        else { $path = Join-Path $env:ProgramFiles ('PostgreSQL\18\bin\' + $Name + '.exe') }
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Cannot find $Name. Install PostgreSQL 18 tools or supply -PgBin." }
    return $path
}

function Invoke-ApexPg([string]$Tool, [string[]]$Arguments) {
    # Windows PowerShell treats native stderr as ErrorRecord; inspect the actual exit code.
    $prior = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& $Tool @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $prior }
    if ($code -ne 0) { throw "PostgreSQL command failed ($code): $($lines -join [Environment]::NewLine)" }
    return ($lines -join [Environment]::NewLine)
}

function Use-ApexPassword {
    $password = $env:APEX_DB_PASSWORD
    if (-not $password) { $password = [Environment]::GetEnvironmentVariable('APEX_DB_PASSWORD', 'User') }
    if (-not $password) { throw 'Set APEX_DB_PASSWORD for the database role first. Never put a password in a command argument.' }
    $env:PGPASSWORD = $password
    $env:PGCONNECT_TIMEOUT = '10'
}

function Assert-ApexDatabaseName([string]$Name) {
    if ($Name -notmatch '^[a-z][a-z0-9_]{0,62}$') { throw 'Use a simple lowercase database name (letters, digits and underscores).' }
}

function Read-ApexBackup([string]$BackupFile) {
    $file = Get-Item -LiteralPath $BackupFile -ErrorAction Stop
    if ($file.PSIsContainer -or $file.Extension -ne '.dump') { throw 'Select an Apex .dump backup file.' }
    $manifestFile = $file.FullName + '.json'
    $manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
    if ($manifest.format -ne 'apex-backup-v1' -or $manifest.file -ne $file.Name) { throw 'Invalid or mismatched backup manifest.' }
    if ($manifest.sha256 -ne (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash) { throw 'Backup checksum does not match. Do not restore this file.' }
    if ([long]$manifest.bytes -ne $file.Length) { throw 'Backup size does not match the manifest.' }
    return @{ File = $file; Manifest = $manifest; ManifestFile = $manifestFile }
}
