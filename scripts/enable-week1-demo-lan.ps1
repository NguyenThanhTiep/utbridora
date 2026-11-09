param([string]$InterfaceAlias='Wi-Fi 2',[string]$ReceiptPath)
$ErrorActionPreference='Stop'
$identity=[Security.Principal.WindowsIdentity]::GetCurrent()
$principal=New-Object Security.Principal.WindowsPrincipal($identity)
if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {throw 'Run this script as Administrator to add the specific LAN firewall rule.'}
$ruleName='UTBridoraWeek1HttpsLan'
if (!(Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name $ruleName -DisplayName 'UTBridora Week1 HTTPS LAN' -Direction Inbound -Action Allow `
        -Protocol TCP -LocalPort 8443 -RemoteAddress LocalSubnet -InterfaceAlias $InterfaceAlias -Profile Private,Public | Out-Null
}
$rule=Get-NetFirewallRule -Name $ruleName
$ports=$rule | Get-NetFirewallPortFilter
$addresses=$rule | Get-NetFirewallAddressFilter
$interfaces=$rule | Get-NetFirewallInterfaceFilter
if ($rule.Enabled -ne 'True' -or $ports.LocalPort -ne '8443' -or $addresses.RemoteAddress -ne 'LocalSubnet' -or $interfaces.InterfaceAlias -ne $InterfaceAlias) {
    throw 'Existing firewall rule differs; review it without silently broadening access.'
}
if ($ReceiptPath) {
    @{name=$ruleName;enabled=$true;port=8443;protocol='TCP';remoteAddress='LocalSubnet';interfaceAlias=$InterfaceAlias} |
        ConvertTo-Json | Set-Content -LiteralPath $ReceiptPath -Encoding utf8
}
Write-Host 'TCP 8443 is allowed only from the local subnet on the selected interface.'
