param(
    [ValidatePattern('^(\d{1,3}\.){3}\d{1,3}$')][string]$LanIp,
    [ValidateSet('start','stop','status','logs')][string]$Action='start'
)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$configFile=Join-Path $repoRoot '.env.week1-demo'
if ($Action -eq 'start' -and !(Test-Path -LiteralPath $configFile)) {
    if (!$LanIp) { throw 'Pass -LanIp with this computer LAN IPv4 address.' }
    function New-DemoSecret {
        $bytes=New-Object byte[] 24
        $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
        try {$rng.GetBytes($bytes)} finally {$rng.Dispose()}
        return 'Demo-'+[Convert]::ToBase64String($bytes).Replace('+','-').Replace('/','_')
    }
    $settings=@(('DEMO_HOST='+$LanIp),('DB_PASSWORD='+(New-DemoSecret)),('DB_ROOT_PASSWORD='+(New-DemoSecret)),('DEMO_PASSWORD='+(New-DemoSecret)))
    [IO.File]::WriteAllLines($configFile,$settings,(New-Object Text.UTF8Encoding($false)))
}
if (!(Test-Path -LiteralPath $configFile)) { throw 'Demo has not been initialized.' }
Push-Location $repoRoot
$previousDemoRevision=$env:DEMO_SOURCE_REVISION
try {
    $demoRevision=(& git -c "safe.directory=$repoRoot" rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {throw 'Cannot identify demo source revision.'}
    $demoDirty=& git -c "safe.directory=$repoRoot" status --porcelain
    $env:DEMO_SOURCE_REVISION=$demoRevision+$(if ($demoDirty) {'-dirty'} else {''})
    $composeArgs=@('compose','--env-file',$configFile,'-f','compose.demo.yaml')
    switch ($Action) {
        'start' { & docker @composeArgs up -d --build }
        'stop' { & docker @composeArgs stop }
        'status' { & docker @composeArgs ps }
        'logs' { & docker @composeArgs logs --tail 100 }
    }
    if ($LASTEXITCODE -ne 0) {throw "Demo command failed with exit code $LASTEXITCODE"}
    if ($Action -eq 'start') {
        Write-Host "Synthetic demo started. Credentials are in $configFile (DEMO_PASSWORD)."
        Write-Host 'Only this separate Docker project is managed; stop preserves its database.'
    }
} finally {$env:DEMO_SOURCE_REVISION=$previousDemoRevision;Pop-Location}
