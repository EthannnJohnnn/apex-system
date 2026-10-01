[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$BackupFile,
    [Parameter(Mandatory=$true)][string]$DestinationDirectory
)
. (Join-Path $PSScriptRoot 'Backup-Common.ps1')
$backup = Read-ApexBackup $BackupFile
$destination = [IO.Path]::GetFullPath($DestinationDirectory)
if ($destination.TrimEnd('\') -eq $backup.File.DirectoryName.TrimEnd('\')) { throw 'Choose a different destination, preferably an external drive.' }
[IO.Directory]::CreateDirectory($destination) | Out-Null
$target = Join-Path $destination $backup.File.Name
if ((Test-Path -LiteralPath $target) -or (Test-Path -LiteralPath ($target + '.json'))) { throw 'Destination already exists; nothing was overwritten.' }
$partial = $target + '.' + [Guid]::NewGuid().ToString('N') + '.partial'
[IO.File]::Copy($backup.File.FullName, $partial, $false)
if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash -ne $backup.Manifest.sha256) { throw 'Copied file failed checksum verification.' }
[IO.File]::Copy($backup.ManifestFile, ($partial + '.json'), $false)
[IO.File]::Move($partial, $target)
[IO.File]::Move(($partial + '.json'), ($target + '.json'))
Read-ApexBackup $target | Out-Null
Write-Host "External copy verified: $target"
