param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$backupDirectory=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
$containerId=(& docker compose -f (Join-Path $project 'compose.staging.yaml') ps -q mysql).Trim()
if($LASTEXITCODE -ne 0 -or !$containerId) { throw 'Staging MySQL container is not running' }
$dumpName='utbridora-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'.sql'
# The credential is read inside the container and is never expanded into the host command line.
& docker exec $containerId sh -c 'MYSQL_PWD=$MYSQL_PASSWORD exec mysqldump -u$MYSQL_USER --single-transaction --no-tablespaces --set-gtid-purged=OFF --result-file=/tmp/core-backup.sql $MYSQL_DATABASE'
if($LASTEXITCODE -ne 0) { throw 'mysqldump failed' }
& docker cp "${containerId}:/tmp/core-backup.sql" (Join-Path $backupDirectory $dumpName)
if($LASTEXITCODE -ne 0) { throw 'Copy backup failed' }
Write-Output "Backup created: $(Join-Path $backupDirectory $dumpName)"
