param([ValidateSet('start','stop','logs')][string]$Action='start',[switch]$Demo)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$configuration=Join-Path $project 'compose.local.yaml'
$environmentName=if ($Demo) { '.env.demo' } else { '.env.local' }
$environmentFile=Join-Path $project $environmentName
$projectName=if ($Demo) { 'utbridora-demo' } else { 'utbridora-local' }
$httpPort=if ($Demo) { '18088' } else { '8088' }
$mailpitPort=if ($Demo) { '18026' } else { '8026' }
$mysqlPort=if ($Demo) { '13307' } else { '3307' }
$commandSuffix=if ($Demo) { ' -Demo' } else { '' }

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
        if ($Action -ne 'start') { throw "Chua co cau hinh local. Chay run-local.cmd start$commandSuffix truoc." }
        $existingVolumes=@(& docker volume ls --filter "label=com.docker.compose.project=$projectName" --format '{{.Name}}')
        if ($LASTEXITCODE -ne 0) { throw 'Khong kiem tra duoc Docker volume.' }
        if ($existingVolumes.Count -gt 0) {
            throw "Con database local nhung thieu $environmentName. Hay khoi phuc file cau hinh cu; script khong xoa database hoac tu doi mat khau."
        }
        $lines=@(
            '# Local demo only. Generated secrets: do not commit or share this file.',
            "LOCAL_HTTP_PORT=$httpPort",
            "MAILPIT_HTTP_PORT=$mailpitPort",
            "LOCAL_MYSQL_PORT=$mysqlPort",
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
        Write-Host "Da tao $environmentName voi mat khau ngau nhien."
    }

    $settings=@{}
    Get-Content -LiteralPath $environmentFile | ForEach-Object {
        if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $settings[$matches[1]]=$matches[2] }
    }
    $requiredKeys=@('LOCAL_HTTP_PORT','MAILPIT_HTTP_PORT','DB_PASSWORD','DB_ROOT_PASSWORD','BOOTSTRAP_ADMIN_EMAIL','BOOTSTRAP_ADMIN_PASSWORD')
    $managedKeys=$requiredKeys+@('LOCAL_MYSQL_PORT','LOCAL_DEMO_ENABLED','LOCAL_DEMO_ALLOW_EXISTING_ADMIN','DEMO_PASSWORD')
    foreach ($key in $requiredKeys) {
        if ([string]::IsNullOrWhiteSpace($settings[$key])) { throw "Thieu gia tri $key trong $environmentName." }
    }
    if (!$settings.ContainsKey('LOCAL_MYSQL_PORT')) { $settings['LOCAL_MYSQL_PORT']=$mysqlPort }
    if (!$settings.ContainsKey('LOCAL_DEMO_ENABLED')) { $settings['LOCAL_DEMO_ENABLED']='false' }
    if ($settings['LOCAL_DEMO_ENABLED'] -notin @('true','false')) { throw 'LOCAL_DEMO_ENABLED phai la true hoac false.' }
    if ($settings.ContainsKey('LOCAL_DEMO_ALLOW_EXISTING_ADMIN') -and $settings['LOCAL_DEMO_ALLOW_EXISTING_ADMIN'] -notin @('true','false')) {
        throw 'LOCAL_DEMO_ALLOW_EXISTING_ADMIN phai la true hoac false.'
    }
    if ($settings['LOCAL_DEMO_ENABLED'] -eq 'true' -and [string]::IsNullOrWhiteSpace($settings['DEMO_PASSWORD'])) {
        throw "Thieu DEMO_PASSWORD trong $environmentName; khong tu doi mat khau database cu."
    }
    foreach ($key in @('LOCAL_HTTP_PORT','MAILPIT_HTTP_PORT','LOCAL_MYSQL_PORT')) {
        $port=0
        if (![int]::TryParse($settings[$key],[ref]$port) -or $port -lt 1024 -or $port -gt 65535) {
            throw "Cong $key trong $environmentName khong hop le."
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
            'start' {
                Write-Host 'Dang build va khoi dong MySQL, Mailpit, backend, frontend. Lan dau can tai image/dependencies.'
                Invoke-LocalDocker ($compose+@('up','-d','--build','--wait','--wait-timeout','300'))
                Write-Host ''
                Write-Host "Ung dung: http://localhost:$($settings['LOCAL_HTTP_PORT'])"
                Write-Host "Email thu nghiem: http://localhost:$($settings['MAILPIT_HTTP_PORT'])"
                Write-Host "Tai khoan admin: $($settings['BOOTSTRAP_ADMIN_EMAIL'])"
                Write-Host "Mat khau: xem BOOTSTRAP_ADMIN_PASSWORD trong $environmentName (khong in secret ra log)."
                if ($settings['LOCAL_DEMO_ENABLED'] -eq 'true') {
                    Write-Host "Tai khoan nghiep vu demo: xem docs/guides/LOCAL_DEMO.md; mat khau DEMO_PASSWORD trong $environmentName."
                }
                Write-Host "Dung ung dung: run-local.cmd stop$commandSuffix. Du lieu database van duoc giu lai."
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
