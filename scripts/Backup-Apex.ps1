[CmdletBinding()]
param(
    [ValidateSet('localhost','127.0.0.1')][string]$DbHost = '127.0.0.1',
    [ValidateRange(1,65535)][int]$Port = 5432,
    [string]$Database = 'apex_db',
    [string]$Username = 'apex_app',
    [string]$BackupDirectory = (Join-Path $env:LOCALAPPDATA 'Apex\backups'),
    [ValidateRange(1,365)][int]$KeepRecent = 14,
    [ValidateSet('routine','pre-update','recovery-test')][string]$Reason = 'routine',
    [string]$PgBin
)
. (Join-Path $PSScriptRoot 'Backup-Common.ps1')
Assert-ApexDatabaseName $Database
$dumpTool = Find-ApexPgTool 'pg_dump' $PgBin
$restoreTool = Find-ApexPgTool 'pg_restore' $PgBin
$psql = Find-ApexPgTool 'psql' $PgBin
$savedPassword = $env:PGPASSWORD
$savedTimeout = $env:PGCONNECT_TIMEOUT
$lock = $null
try {
    Use-ApexPassword
    $directory = [IO.Path]::GetFullPath($BackupDirectory)
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    # Exclusive lock prevents overlapping retention/copy operations in the same backup directory.
    $lock = [IO.File]::Open((Join-Path $directory '.backup.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $connection = @('--host', $DbHost, '--port', "$Port", '--username', $Username, '--no-password', '--dbname', $Database)
    $schemaVersion = Invoke-ApexPg $psql ($connection + @('-X','-A','-t','-v','ON_ERROR_STOP=1','-c',
        "SELECT version FROM public.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"))
    if (-not $schemaVersion.Trim()) { throw 'This database does not contain a migrated Apex schema.' }
    $name = 'apex-' + $Database + '-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8) + '.dump'
    $file = Join-Path $directory $name
    $partial = $file + '.partial'
    # Apex uses public only; unrelated scratch/test schemas are intentionally excluded.
    Invoke-ApexPg $dumpTool ($connection + @('--format=custom','--no-owner','--no-acl','--schema=public','--file', $partial)) | Out-Null
    Invoke-ApexPg $restoreTool @('--list', $partial) | Out-Null
    $hash = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash
    $manifest = [ordered]@{ format='apex-backup-v1'; file=$name; createdUtc=[DateTime]::UtcNow.ToString('o');
        database=$Database; schema='public'; schemaVersion=$schemaVersion.Trim(); reason=$Reason;
        bytes=(Get-Item -LiteralPath $partial).Length; sha256=$hash;
        toolVersion=(Invoke-ApexPg $dumpTool @('--version')).Trim() }
    # A backup is usable only when both the final dump and its manifest are present.
    [IO.File]::WriteAllText(($file + '.json.partial'), ($manifest | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    [IO.File]::Move($partial, $file)
    [IO.File]::Move(($file + '.json.partial'), ($file + '.json'))
    Read-ApexBackup $file | Out-Null
    # Keep recent backups handy; archive older valid pairs without permanently deleting them.
    $older = @(Get-ChildItem -LiteralPath $directory -File -Filter ('apex-' + $Database + '-*.dump') |
        Sort-Object Name -Descending | Select-Object -Skip $KeepRecent)
    if ($older.Count -gt 0) {
        $archive = Join-Path $directory 'archive'
        [IO.Directory]::CreateDirectory($archive) | Out-Null
        foreach ($old in $older) {
            Read-ApexBackup $old.FullName | Out-Null
            [IO.File]::Move($old.FullName, (Join-Path $archive $old.Name))
            [IO.File]::Move(($old.FullName + '.json'), (Join-Path $archive ($old.Name + '.json')))
        }
    }
    Write-Host "Backup verified: $file"
    Write-Host 'Contains confidential records and password hashes. Keep it private; copy BOTH .dump and .dump.json to a protected external drive.'
    Write-Output $file
} finally {
    if ($lock) { $lock.Dispose() }
    $env:PGPASSWORD = $savedPassword
    $env:PGCONNECT_TIMEOUT = $savedTimeout
}
