[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$BackupFile,
    [Parameter(Mandatory=$true)][string]$TargetDatabase,
    [ValidateSet('localhost','127.0.0.1')][string]$DbHost = '127.0.0.1',
    [ValidateRange(1,65535)][int]$Port = 5432,
    [string]$Username = 'apex_app',
    [string]$PgBin
)
. (Join-Path $PSScriptRoot 'Backup-Common.ps1')
Assert-ApexDatabaseName $TargetDatabase
if ($TargetDatabase -notmatch '^apex_restore_[a-z0-9_]+$') { throw 'Restore only accepts a NEW recovery database named apex_restore_...; never apex_db.' }
$backup = Read-ApexBackup $BackupFile
if ($TargetDatabase -eq $backup.Manifest.database) { throw 'The restore target must differ from the backup source database.' }
$restoreTool = Find-ApexPgTool 'pg_restore' $PgBin
$psql = Find-ApexPgTool 'psql' $PgBin
$savedPassword = $env:PGPASSWORD
$savedTimeout = $env:PGCONNECT_TIMEOUT
try {
    Use-ApexPassword
    $connection = @('--host',$DbHost,'--port',"$Port",'--username',$Username,'--no-password','--dbname',$TargetDatabase)
    $query = "SELECT (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname<>'information_schema') + (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname<>'information_schema') + (SELECT count(*) FROM pg_namespace WHERE nspname NOT LIKE 'pg_%' AND nspname NOT IN ('public','information_schema'))"
    $objects = Invoke-ApexPg $psql ($connection + @('-X','-A','-t','-v','ON_ERROR_STOP=1','-c',$query))
    if ($objects.Trim() -ne '0') { throw 'Recovery database is not empty. Refusing to overwrite any existing objects.' }
    Invoke-ApexPg $restoreTool @('--list', $backup.File.FullName) | Out-Null
    # Target is an explicitly named EMPTY recovery database, not the live database.
    # --clean handles its pre-existing empty public schema; the entire restore is atomic.
    Invoke-ApexPg $restoreTool ($connection + @('--no-owner','--no-acl','--clean','--if-exists','--exit-on-error','--single-transaction', $backup.File.FullName)) | Out-Null
    $version = Invoke-ApexPg $psql ($connection + @('-X','-A','-t','-v','ON_ERROR_STOP=1','-c',
        "SELECT version FROM public.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"))
    if ($version.Trim() -ne $backup.Manifest.schemaVersion) { throw 'Restored schema version differs from the backup manifest. Do not switch Apex to this database.' }
    Write-Host "Restore completed into $TargetDatabase. Live Apex configuration was NOT changed. Verify records before switching."
} finally {
    $env:PGPASSWORD = $savedPassword
    $env:PGCONNECT_TIMEOUT = $savedTimeout
}
