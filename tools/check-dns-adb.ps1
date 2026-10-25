# DNS check on the TV over adb: which ad/telemetry domains resolve, and are the
# normal services still fine? Read-only: nothing here changes any setting.
# Usage:  pwsh .\tools\check-dns-adb.ps1 [-Serial 192.168.1.50:5555]
# The ad list and the normal list are the same ones the port's verify.sh uses.
param(
    # Put your TV's address here, or pass -Serial. The placeholder is a doc example.
    [string]$Serial = '192.168.1.50:5555',
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'

$ads = @(
  'api.ad.xiaomi.com'
  'data.mistat.xiaomi.com'
  'tracking.miui.com'
  'sdkconfig.ad.xiaomi.com'
  'test.ad.xiaomi.com'
  'ad.mi.com'
  'ad.xiaomi.com'
)
$ok = @(
  'account.xiaomi.com'
  'app.market.xiaomi.com'
  'v.qq.com'
  'www.baidu.com'
)

function Resolve-OnTv([string]$name) {
  # ping prints the address in parentheses on the first line it can resolve
  $out = (& $Adb -s $Serial shell "ping -c 1 -W 3 $name 2>&1") -join ' '
  if ($out -match '\((\d{1,3}(?:\.\d{1,3}){3})\)') { return $matches[1] }
  return $null
}

"=== DNS servers the TV is using ==="
& $Adb -s $Serial shell "getprop net.dns1; getprop net.dns2" 2>&1
""

"=== ad domains (want: BLOCKED) ==="
$blocked = 0
foreach ($d in $ads) {
  $ip = Resolve-OnTv $d
  if (-not $ip -or $ip -eq '0.0.0.0' -or $ip -like '127.*') {
    $blocked++
    '{0,-32} BLOCKED' -f $d
  } else {
    '{0,-32} still resolves -> {1}' -f $d, $ip
  }
}
'{0}/{1} blocked' -f $blocked, $ads.Count
""

"=== normal services (want: OK) ==="
foreach ($d in $ok) {
  $ip = Resolve-OnTv $d
  if ($ip) { '{0,-32} OK -> {1}' -f $d, $ip } else { '{0,-32} FAILED (possible collateral)' -f $d }
}
