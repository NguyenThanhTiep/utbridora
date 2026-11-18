param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$backupDirectory=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
$containerId=(& docker compose -f (Join-Path $project 'compose.staging.yaml') ps -q mysql).Trim()
if($LASTEXITCODE -ne 0 -or !$containerId) { throw 'Staging MySQL container is not running' }
if($containerId -notmatch '^[a-f0-9]{12,64}$') { throw 'Expected exactly one staging MySQL container' }
$backupId=[Guid]::NewGuid().ToString('N')
$dumpName='utbridora-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+$backupId+'.sql'
$remoteDump='/tmp/utbridora-backup-'+$backupId+'.sql'
$destination=Join-Path $backupDirectory $dumpName
$partial=$destination+'.partial'
try {
    # Unique path prevents concurrent backups from copying each other's dump. Password stays in container memory.
    & docker exec $containerId sh -c 'umask 077; export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump -u"$MYSQL_USER" --single-transaction --hex-blob --no-tablespaces --set-gtid-purged=OFF --result-file="$1" "$MYSQL_DATABASE"' sh $remoteDump
    if($LASTEXITCODE -ne 0) { throw 'mysqldump failed; no verified backup was created' }
    $remoteDigest=(& docker exec $containerId sha256sum $remoteDump)
    if($LASTEXITCODE -ne 0 -or $remoteDigest -notmatch '^([a-f0-9]{64})\s') { throw 'Cannot checksum container dump' }
    $expectedDigest=$Matches[1]
    & docker cp "${containerId}:$remoteDump" $partial
    if($LASTEXITCODE -ne 0) { throw 'Copy backup failed' }
    $actualDigest=(Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant()
    if($actualDigest -ne $expectedDigest) { throw 'Backup copy checksum mismatch; retain partial file for investigation' }
    Move-Item -LiteralPath $partial -Destination $destination
    $image=(& docker inspect --format '{{.Image}}' $containerId)
    if($LASTEXITCODE -ne 0) { throw 'Cannot identify database image; backup exists but manifest is incomplete' }
    [ordered]@{createdAt=[DateTime]::UtcNow.ToString('o');dump=$dumpName;sha256=$actualDigest;
        bytes=(Get-Item -LiteralPath $destination).Length;mysqlContainer=$containerId;mysqlImage=$image;
        restoreVerified=$false;privateFileStorageIncluded=$false} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath ($destination+'.manifest.json')
    Write-Output "Backup and checksum manifest created: $destination"
} finally {
    # Generated fixed /tmp path only; never remove a directory or a caller-supplied path.
    & docker exec $containerId rm -f -- $remoteDump
    if($LASTEXITCODE -ne 0) { Write-Warning 'Could not remove temporary dump from the database container' }
}
