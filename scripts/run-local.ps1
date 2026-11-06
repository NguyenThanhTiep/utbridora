param([ValidateSet('start','stop','logs')][string]$Action='start')
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$configuration=Join-Path $project 'compose.local.yaml'
$environmentFile=Join-Path $project '.env.local'
$projectName='utbridora-local'

function New-LocalSecret {
    $random=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes=New-Object byte[] 32
        $random.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes).TrimEnd('=')
    } finally { $random.Dispose() }
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
    $managedKeys=@('LOCAL_HTTP_PORT','MAILPIT_HTTP_PORT','DB_PASSWORD','DB_ROOT_PASSWORD','BOOTSTRAP_ADMIN_EMAIL','BOOTSTRAP_ADMIN_PASSWORD')
    foreach ($key in $managedKeys) {
        if ([string]::IsNullOrWhiteSpace($settings[$key])) { throw "Thieu gia tri $key trong .env.local." }
    }
    foreach ($key in @('LOCAL_HTTP_PORT','MAILPIT_HTTP_PORT')) {
        $port=0
        if (![int]::TryParse($settings[$key],[ref]$port) -or $port -lt 1024 -or $port -gt 65535) {
            throw "Cong $key trong .env.local khong hop le."
        }
    }
    if ($settings['LOCAL_HTTP_PORT'] -eq $settings['MAILPIT_HTTP_PORT']) {
        throw 'LOCAL_HTTP_PORT va MAILPIT_HTTP_PORT phai khac nhau.'
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
        & docker @compose config --quiet
        if ($LASTEXITCODE -ne 0) { throw 'Cau hinh Compose khong hop le. Kiem tra .env.local.' }
        switch ($Action) {
            'start' {
                Write-Host 'Dang build va khoi dong MySQL, Mailpit, backend, frontend. Lan dau can tai image/dependencies.'
                & docker @compose up -d --build --wait --wait-timeout 300
                if ($LASTEXITCODE -ne 0) {
                    throw 'Khoi dong chua thanh cong. Kiem tra Docker, cong 8088/8026 hoac chay run-local.cmd logs.'
                }
                Write-Host ''
                Write-Host "Ung dung: http://localhost:$($settings['LOCAL_HTTP_PORT'])"
                Write-Host "Email thu nghiem: http://localhost:$($settings['MAILPIT_HTTP_PORT'])"
                Write-Host "Tai khoan admin: $($settings['BOOTSTRAP_ADMIN_EMAIL'])"
                Write-Host 'Mat khau: xem BOOTSTRAP_ADMIN_PASSWORD trong .env.local (khong in secret ra log).'
                Write-Host 'Dung ung dung: run-local.cmd stop. Du lieu database van duoc giu lai.'
            }
            'stop' {
                & docker @compose stop
                if ($LASTEXITCODE -ne 0) { throw 'Chua dung duoc mot so container.' }
                Write-Host 'Da dung cac container local; giu nguyen database va cau hinh.'
            }
            'logs' {
                & docker @compose logs --tail=100 --follow
                if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc log local.' }
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
