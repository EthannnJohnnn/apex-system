[CmdletBinding()]
param([string]$PgBin)
. (Join-Path $PSScriptRoot 'Backup-Common.ps1')
$repo = Split-Path $PSScriptRoot -Parent
$work = Join-Path $repo ('tmp\backup-test-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($work) | Out-Null
$data = Join-Path $work 'cluster'
$pwfile = Join-Path $work 'init-password.txt'
$rolefile = Join-Path $work 'init-role.sql'
$init = Find-ApexPgTool 'initdb' $PgBin
$ctl = Find-ApexPgTool 'pg_ctl' $PgBin
$createdb = Find-ApexPgTool 'createdb' $PgBin
$psql = Find-ApexPgTool 'psql' $PgBin
$saved = @{}
foreach ($key in @('APEX_DB_PASSWORD','PGPASSWORD','PGCONNECT_TIMEOUT','APEX_BACKUP_TEST_URL','APEX_BACKUP_TEST_PASSWORD')) {
    $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
$started = $false
function Control-TestCluster([string]$Action) {
    $arguments = @('-D', ('"' + $data + '"'), '-w', '-t', '30')
    if ($Action -eq 'start') {
        $arguments += @('-l', ('"' + (Join-Path $work 'postgres.log') + '"'), '-o', ('"-h 127.0.0.1 -p ' + $port + '"'), 'start')
    } else { $arguments += @('-m','fast','stop') }
    # Do not pipe pg_ctl: a background Windows server can inherit pipe handles and keep the reader waiting.
    $process = Start-Process -FilePath $ctl -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $work ($Action + '.stdout')) -RedirectStandardError (Join-Path $work ($Action + '.stderr'))
    try {
        $null = $process.Handle
        if (-not $process.WaitForExit(40000)) { throw "Temporary PostgreSQL $Action timed out. See $work." }
        if ($process.ExitCode -ne 0) { throw "Temporary PostgreSQL $Action failed. See $work\$Action.stderr." }
    } finally { $process.Dispose() }
}
function Sql([string]$Database, [string]$Query) {
    return (Invoke-ApexPg $psql @('-X','-A','-t','-h','127.0.0.1','-p',"$port",'-U','apex_app','-w','-d',$Database,'-v','ON_ERROR_STOP=1','-c',$Query)).Trim()
}
function Expect-Rejection([scriptblock]$Action, [string]$Expected) {
    $rejected = $false
    try { & $Action | Out-Null }
    catch { if ($_.Exception.Message -notlike ('*' + $Expected + '*')) { throw }; $rejected = $true }
    if (-not $rejected) { throw "Expected rejection: $Expected" }
    Write-Host "PASS rejection: $Expected"
}
function Run-Fixture([string]$Database) {
    $env:APEX_BACKUP_TEST_URL = "jdbc:postgresql://127.0.0.1:$port/$Database"
    Push-Location (Join-Path $repo 'backend')
    try {
        & .\mvnw.cmd test-compile spring-boot:test-run '-Dspring-boot.run.main-class=ph.edu.slsu.psim.apex.system.Stage17Fixture' *> (Join-Path $work ($Database + '.log'))
        if ($LASTEXITCODE -ne 0) { throw "Fixture failed; inspect $work\$Database.log" }
    } finally { Pop-Location }
}
try {
    $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $env:APEX_DB_PASSWORD = $password
    $env:PGPASSWORD = $password
    $env:APEX_BACKUP_TEST_PASSWORD = $password
    $env:PGCONNECT_TIMEOUT = '10'
    [IO.File]::WriteAllText($pwfile, $password, [Text.UTF8Encoding]::new($false))
    # No changes to the installed Windows PostgreSQL service or its real databases.
    Invoke-ApexPg $init @('-D',$data,'-U','apex_test_admin','--auth=scram-sha-256','--encoding=UTF8','--locale=C','--pwfile',$pwfile) | Out-Null
    Remove-Item -LiteralPath $pwfile
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start(); $port = $listener.LocalEndpoint.Port; $listener.Stop()
    if ($port -eq 5432) { throw 'Refusing normal PostgreSQL port.' }
    $started = $true
    Control-TestCluster 'start'
    # The bootstrap admin is test-only. The application role has the real client's limited privileges.
    [IO.File]::WriteAllText($rolefile, "CREATE ROLE apex_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$password';", [Text.UTF8Encoding]::new($false))
    Invoke-ApexPg $psql @('-X','-h','127.0.0.1','-p',"$port",'-U','apex_test_admin','-w','-d','postgres','-v','ON_ERROR_STOP=1','-f',$rolefile) | Out-Null
    Remove-Item -LiteralPath $rolefile
    foreach ($db in @('apex_backup_fixture','apex_restore_test','apex_restore_occupied')) {
        Invoke-ApexPg $createdb @('-h','127.0.0.1','-p',"$port",'-U','apex_test_admin','-w','-O','apex_app',$db) | Out-Null
    }
    Run-Fixture 'apex_backup_fixture'
    $backupDir = Join-Path $work 'backups'
    $options = @{ Port=$port; Database='apex_backup_fixture'; BackupDirectory=$backupDir; KeepRecent=2; Reason='recovery-test'; PgBin=$PgBin }
    $one = & (Join-Path $PSScriptRoot 'Backup-Apex.ps1') @options
    $two = & (Join-Path $PSScriptRoot 'Backup-Apex.ps1') @options
    $three = & (Join-Path $PSScriptRoot 'Backup-Apex.ps1') @options
    if (@(Get-ChildItem -LiteralPath $backupDir -Filter '*.dump').Count -ne 2 -or
        @(Get-ChildItem -LiteralPath (Join-Path $backupDir 'archive') -Filter '*.dump').Count -ne 1) { throw 'Retention did not keep 2 recent / 1 archived backup.' }
    Write-Host 'PASS retention: 2 recent + 1 recoverable archive'
    $copyDir = Join-Path $work 'external-copy-simulation'
    & (Join-Path $PSScriptRoot 'Copy-ApexBackup.ps1') -BackupFile $three -DestinationDirectory $copyDir
    $copy = Join-Path $copyDir ([IO.Path]::GetFileName($three))
    Expect-Rejection { & (Join-Path $PSScriptRoot 'Copy-ApexBackup.ps1') -BackupFile $three -DestinationDirectory $copyDir } 'already exists'
    Expect-Rejection { & (Join-Path $PSScriptRoot 'Restore-Apex.ps1') -BackupFile $copy -TargetDatabase 'apex_db' -Port $port -PgBin $PgBin } 'NEW recovery database'
    Sql 'apex_restore_occupied' 'CREATE TABLE public.keep_me (id integer)' | Out-Null
    Expect-Rejection { & (Join-Path $PSScriptRoot 'Restore-Apex.ps1') -BackupFile $copy -TargetDatabase 'apex_restore_occupied' -Port $port -PgBin $PgBin } 'not empty'
    if ((Sql 'apex_restore_occupied' "SELECT to_regclass('public.keep_me')") -ne 'keep_me') { throw 'Occupied target changed.' }
    & (Join-Path $PSScriptRoot 'Restore-Apex.ps1') -BackupFile $copy -TargetDatabase 'apex_restore_test' -Port $port -PgBin $PgBin
    $tables = (Sql 'apex_backup_fixture' "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename") -split '\r?\n'
    foreach ($table in $tables) {
        if ($table -notmatch '^[a-z_][a-z0-9_]*$') { throw 'Unexpected table identifier.' }
        $query = "SELECT count(*) || ':' || md5(COALESCE(string_agg(row_to_json(t)::text,E'\n' ORDER BY row_to_json(t)::text),'')) FROM public.$table t"
        $source = Sql 'apex_backup_fixture' $query
        $restored = Sql 'apex_restore_test' $query
        if ($source -ne $restored) { throw "Restored contents differ: $table" }
        Write-Host "PASS exact restored rows: $table ($($source.Split(':')[0]))"
    }
    Expect-Rejection { Sql 'apex_restore_test' 'UPDATE point_entry SET amount=999' } 'append-only'
    Run-Fixture 'apex_restore_test'
    Write-Host 'PASS restored application: login hash, summaries, attendance, warnings, identity sequence'
    # An incomplete/corrupted copied file must never be accepted as a backup.
    $bytes = [IO.File]::ReadAllBytes($copy); $bytes[0] = $bytes[0] -bxor 1; [IO.File]::WriteAllBytes($copy, $bytes)
    Expect-Rejection { Read-ApexBackup $copy } 'checksum'
    $env:APEX_DB_PASSWORD = 'deliberately-wrong-test-password'
    Expect-Rejection { & (Join-Path $PSScriptRoot 'Backup-Apex.ps1') @options } 'PostgreSQL command failed'
    if (@(Get-ChildItem -LiteralPath $backupDir -Filter '*.dump').Count -ne 2) { throw 'Failed backup changed retained backup count.' }
    Write-Host "ALL BACKUP/RESTORE CHECKS PASSED. Test artifacts: $work"
} finally {
    try { if ($started) { Control-TestCluster 'stop' } }
    finally {
        if (Test-Path -LiteralPath $pwfile) { Remove-Item -LiteralPath $pwfile }
        if (Test-Path -LiteralPath $rolefile) { Remove-Item -LiteralPath $rolefile }
        foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
    }
}
