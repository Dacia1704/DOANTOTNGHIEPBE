param(
    [int]$ProxyPort = 18080,
    [string]$CloudflaredPath = ''
)

$ErrorActionPreference = 'Stop'
$vnPayProjectRoot = Split-Path -Parent $PSScriptRoot
$vnPayRuntimeDirectory = Join-Path $vnPayProjectRoot 'target/vnpay-diagnostics'
if (-not $CloudflaredPath) {
    $CloudflaredPath = Join-Path $vnPayRuntimeDirectory 'bin/cloudflared.exe'
}
if (-not (Test-Path -LiteralPath $CloudflaredPath)) {
    throw "cloudflared is missing: $CloudflaredPath. See docs/VNPAY_SANDBOX.md."
}
$vnPayExpectedHash = 'd521b3bf68982165f0d06d76732f22d9a105ef057ff3ae4c0986f3a23190950f'
if ((Get-FileHash -LiteralPath $CloudflaredPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $vnPayExpectedHash) {
    throw 'Expected the verified cloudflared 2026.10.6 Windows amd64 release.'
}
$vnPayNodeCommand = Get-Command node -ErrorAction SilentlyContinue
$vnPayNodeBinary = if ($vnPayNodeCommand) { $vnPayNodeCommand.Source } elseif (Test-Path -LiteralPath 'D:/node.exe') { 'D:/node.exe' } else { $null }
if (-not $vnPayNodeBinary) { throw 'Node.js 22 or newer is required.' }
if ($ProxyPort -lt 1 -or $ProxyPort -gt 65535) { throw 'ProxyPort is invalid.' }

# This unsigned local request only checks that the real backend rejects invalid IPNs.
$vnPayBackendProbe = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/payments/vnpay/ipn' -TimeoutSec 5
if ($vnPayBackendProbe.RspCode -ne '97') { throw 'The backend IPN signature guard is not ready.' }

New-Item -ItemType Directory -Path $vnPayRuntimeDirectory -Force | Out-Null
$vnPayPreviousProxyPort = $env:VNPAY_IPN_PROXY_PORT
$env:VNPAY_IPN_PROXY_PORT = [string]$ProxyPort
$vnPayProxyProcess = $null
try {
    $vnPayProxyScript = Join-Path $PSScriptRoot 'vnpay-ipn-proxy.mjs'
    $vnPayProxyProcess = Start-Process -FilePath $vnPayNodeBinary -ArgumentList ('"{0}"' -f $vnPayProxyScript) `
        -WorkingDirectory $vnPayProjectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $vnPayRuntimeDirectory 'ipn-proxy.out.log') `
        -RedirectStandardError (Join-Path $vnPayRuntimeDirectory 'ipn-proxy.err.log')
    Start-Sleep -Milliseconds 750
    if ($vnPayProxyProcess.HasExited) { throw 'The IPN proxy could not start; check its log and whether the port is already in use.' }
    $vnPayProxyProbe = Invoke-RestMethod -Uri "http://127.0.0.1:$ProxyPort/api/payments/vnpay/ipn" -TimeoutSec 5
    if ($vnPayProxyProbe.RspCode -ne '97') { throw 'The proxy signature guard check failed.' }
    Write-Host 'Append /api/payments/vnpay/ipn to the HTTPS URL printed below and register it in VNPay Merchant Admin.'
    Write-Host 'Keep this terminal and the backend running while testing. Ctrl+C stops the temporary tunnel.'
    & $CloudflaredPath tunnel --no-autoupdate --url "http://127.0.0.1:$ProxyPort" --protocol http2 `
        --metrics '127.0.0.1:18081' --logfile (Join-Path $vnPayRuntimeDirectory 'ipn-tunnel.log') --loglevel info
} finally {
    if ($vnPayProxyProcess -and -not $vnPayProxyProcess.HasExited) {
        Stop-Process -Id $vnPayProxyProcess.Id -ErrorAction SilentlyContinue
    }
    $env:VNPAY_IPN_PROXY_PORT = $vnPayPreviousProxyPort
}
