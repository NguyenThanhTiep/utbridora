param(
    [ValidateSet('start','stop','logs','seed-demo')][string]$Action='start',
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')][string]$ProjectName='utbridora-local',
    [ValidatePattern('^\.env\.[A-Za-z0-9][A-Za-z0-9._-]*$')][string]$EnvironmentFileName='.env.local'
)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$configuration=Join-Path $project 'compose.local.yaml'
$environmentFile=Join-Path $project $EnvironmentFileName
if (($ProjectName -ne 'utbridora-local') -xor ($EnvironmentFileName -ne '.env.local')) {
    throw 'ProjectName rieng phai di kem EnvironmentFileName rieng de tranh dung nham database.'
}

function New-LocalSecret {
    $random=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes=New-Object byte[] 32
        $random.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes).TrimEnd('=')
    } finally { $random.Dispose() }
}

function Invoke-LocalDocker([string[]]$DockerArguments) {
    # Docker build progress uses stderr on Windows PowerShell, even on success.
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & docker @DockerArguments
        if ($LASTEXITCODE -ne 0) { throw 'Lenh Docker that bai; xem thong bao o tren.' }
    } finally { $ErrorActionPreference=$previousPreference }
}

try {
    if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Can cai Docker Desktop truoc khi chay. Khong can cai rieng Java, Maven hoac Node.'
    }
    try { $engine=& docker info --format '{{.OSType}}' 2>$null }
    catch { throw 'Hay mo Docker Desktop, doi Linux engine san sang, roi chay lai.' }
    if ($LASTEXITCODE -ne 0 -or $engine -ne 'linux') {
        throw 'Hay mo Docker Desktop va chon Linux containers, roi chay lai.'
    }

    if (!(Test-Path -LiteralPath $environmentFile)) {
        if ($Action -ne 'start') { throw 'Chua co cau hinh local. Chay run-local.cmd start truoc.' }
        $existingVolumes=@(& docker volume ls --filter "label=com.docker.compose.project=$projectName" --format '{{.Name}}')
        if ($LASTEXITCODE -ne 0) { throw 'Khong kiem tra duoc Docker volume.' }
        if ($existingVolumes.Count -gt 0) {
            throw 'Con database local nhung thieu .env.local. Hay khoi phuc file cau hinh cu; script khong xoa database hoac tu doi mat khau.'
        }
        $lines=@(
            '# Local demo only. Generated secrets: do not commit or share this file.',
            'LOCAL_HTTP_PORT=8088',
            'MAILPIT_HTTP_PORT=8026',
            'LOCAL_MYSQL_PORT=3307',
            'LOCAL_DEMO_ENABLED=true',
            "DEMO_PASSWORD=$(New-LocalSecret)",
            "DB_PASSWORD=$(New-LocalSecret)",
            "DB_ROOT_PASSWORD=$(New-LocalSecret)",
            'BOOTSTRAP_ADMIN_EMAIL=admin@example.test',
            "BOOTSTRAP_ADMIN_PASSWORD=$(New-LocalSecret)"
        )
        # CreateNew preserves another launch's configuration rather than overwriting secrets.
        $stream=[IO.File]::Open($environmentFile,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try {
            $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($lines -join "`n")+"`n")
            $stream.Write($bytes,0,$bytes.Length)
        } finally { $stream.Dispose() }
        Write-Host 'Da tao .env.local voi mat khau ngau nhien. File .env cu duoc giu nguyen.'
    }

    $settings=@{}
    Get-Content -LiteralPath $environmentFile | ForEach-Object {
        if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $settings[$matches[1]]=$matches[2] }
    }
    $requiredKeys=@('LOCAL_HTTP_PORT','MAILPIT_HTTP_PORT','DB_PASSWORD','DB_ROOT_PASSWORD','BOOTSTRAP_ADMIN_EMAIL','BOOTSTRAP_ADMIN_PASSWORD')
    $managedKeys=$requiredKeys+@('LOCAL_MYSQL_PORT','LOCAL_DEMO_ENABLED','DEMO_PASSWORD')
    foreach ($key in $requiredKeys) {
        if ([string]::IsNullOrWhiteSpace($settings[$key])) { throw "Thieu gia tri $key trong .env.local." }
    }
    if (!$settings.ContainsKey('LOCAL_MYSQL_PORT')) { $settings['LOCAL_MYSQL_PORT']='3307' }
    if (!$settings.ContainsKey('LOCAL_DEMO_ENABLED')) { $settings['LOCAL_DEMO_ENABLED']='false' }
    if ($settings['LOCAL_DEMO_ENABLED'] -notin @('true','false')) { throw 'LOCAL_DEMO_ENABLED phai la true hoac false.' }
    if ($settings['LOCAL_DEMO_ENABLED'] -eq 'true' -and [string]::IsNullOrWhiteSpace($settings['DEMO_PASSWORD'])) {
        throw 'Thieu DEMO_PASSWORD trong .env.local; khong tu doi mat khau database cu.'
    }
    foreach ($key in @('LOCAL_HTTP_PORT','MAILPIT_HTTP_PORT','LOCAL_MYSQL_PORT')) {
        $port=0
        if (![int]::TryParse($settings[$key],[ref]$port) -or $port -lt 1024 -or $port -gt 65535) {
            throw "Cong $key trong .env.local khong hop le."
        }
    }
    if (@($settings['LOCAL_HTTP_PORT'],$settings['MAILPIT_HTTP_PORT'],$settings['LOCAL_MYSQL_PORT'] | Select-Object -Unique).Count -ne 3) {
        throw 'LOCAL_HTTP_PORT, MAILPIT_HTTP_PORT va LOCAL_MYSQL_PORT phai khac nhau.'
    }

    # Shell variables override --env-file in Compose. Isolate this local stack from
    # credentials loaded into the shell for manual development or staging.
    $previous=@{}
    try {
        foreach ($key in $managedKeys) {
            $previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
            [Environment]::SetEnvironmentVariable($key,$null,'Process')
        }
        $compose=@('compose','--project-name',$projectName,'--env-file',$environmentFile,'-f',$configuration)
        Invoke-LocalDocker ($compose+@('config','--quiet'))
        switch ($Action) {
            'seed-demo' {
                if ([string]::IsNullOrWhiteSpace($settings['DEMO_PASSWORD'])) {
                    $demoFile=$environmentFile+'-demo'
                    if (!(Test-Path -LiteralPath $demoFile)) {
                        $stream=[IO.File]::Open($demoFile,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
                        try {
                            $bytes=[Text.UTF8Encoding]::new($false).GetBytes(('DEMO_PASSWORD='+(New-LocalSecret))+"`n")
                            $stream.Write($bytes,0,$bytes.Length)
                        } finally { $stream.Dispose() }
                    }
                    foreach ($line in Get-Content -LiteralPath $demoFile) {
                        if ($line -match '^DEMO_PASSWORD=(.+)$') { $settings['DEMO_PASSWORD']=$matches[1] }
                    }
                    if ([string]::IsNullOrWhiteSpace($settings['DEMO_PASSWORD'])) { throw 'Thieu DEMO_PASSWORD trong .env.local-demo.' }
                }
                # Credentials pass by environment name only, never as command-line values.
                [Environment]::SetEnvironmentVariable('DEMO_PASSWORD',$settings['DEMO_PASSWORD'],'Process')
                Invoke-LocalDocker ($compose+@('up','-d','--wait','mysql','mailpit'))
                $backupDirectory=Join-Path $project '.local-demo-backups'
                New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
                $backupName='before-demo-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N')+'.sql'
                Invoke-LocalDocker ($compose+@('cp',(Join-Path $PSScriptRoot 'backup-local-db.sh'),'mysql:/tmp/bridora-backup-local-db.sh'))
                Invoke-LocalDocker ($compose+@('exec','-T','mysql','sh','/tmp/bridora-backup-local-db.sh',('/tmp/'+$backupName)))
                Invoke-LocalDocker ($compose+@('cp',('mysql:/tmp/'+$backupName),(Join-Path $backupDirectory $backupName)))
                Invoke-LocalDocker ($compose+@('exec','-T','mysql','rm',('/tmp/'+$backupName),'/tmp/bridora-backup-local-db.sh'))
                Write-Host 'Da sao luu database vao .local-demo-backups (khong commit/chia se).'
                Invoke-LocalDocker ($compose+@('build','backend'))
                Invoke-LocalDocker ($compose+@('run','--rm','--no-deps','-e','DEMO_PASSWORD','--entrypoint','java','backend',
                    '-Dloader.main=vn.utbridora.auth.bootstrap.LocalDemoImport','-cp','/app/app.jar','org.springframework.boot.loader.launch.PropertiesLauncher'))
                Write-Host 'Da nap/kiem tra demo; mat khau tai khoan cu duoc giu nguyen.'
                Write-Host 'Mat khau demo xem DEMO_PASSWORD trong .env.local hoac .env.local-demo; khong in ra log.'
                Write-Host 'Chay run-local.cmd start de cap nhat frontend/backend; khong bat LOCAL_DEMO_ENABLED cho database cu.'
            }
            'start' {
                Write-Host 'Dang build va khoi dong MySQL, Mailpit, backend, frontend. Lan dau can tai image/dependencies.'
                Invoke-LocalDocker ($compose+@('up','-d','--build','--wait','--wait-timeout','300'))
                Write-Host ''
                Write-Host "Ung dung: http://localhost:$($settings['LOCAL_HTTP_PORT'])"
                Write-Host "Email thu nghiem: http://localhost:$($settings['MAILPIT_HTTP_PORT'])"
                Write-Host "Tai khoan admin: $($settings['BOOTSTRAP_ADMIN_EMAIL'])"
                Write-Host 'Mat khau: xem BOOTSTRAP_ADMIN_PASSWORD trong .env.local (khong in secret ra log).'
                if ($settings['LOCAL_DEMO_ENABLED'] -eq 'true') {
                    Write-Host 'Tai khoan nghiep vu demo: xem docs/guides/LOCAL_DEMO.md; mat khau DEMO_PASSWORD trong .env.local.'
                }
                Write-Host 'Dung ung dung: run-local.cmd stop. Du lieu database van duoc giu lai.'
            }
            'stop' {
                Invoke-LocalDocker ($compose+@('stop'))
                Write-Host 'Da dung cac container local; giu nguyen database va cau hinh.'
            }
            'logs' {
                Invoke-LocalDocker ($compose+@('logs','--tail=100','--follow'))
            }
        }
    } finally {
        foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
    }
} catch {
    Write-Host ("Loi: "+$_.Exception.Message) -ForegroundColor Red
    exit 1
}
exit 0
