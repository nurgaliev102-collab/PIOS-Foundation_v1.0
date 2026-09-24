# Launches the production Cloudflare Tunnel connector in token mode.
#
# Authentication has exactly one source: the service process environment
# variable PIOS_CLOUDFLARED_TUNNEL_TOKEN, provisioned in the Windows SCM
# service Environment value. The connector token is never stored in this
# repository or printed.
# Ingress is non-secret and versioned in config.production.yml so a clean
# host deterministically exposes only the frontend on 127.0.0.1:4173.
$ErrorActionPreference = 'Stop'

$cloudflaredPath = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'
$configPath = Join-Path $PSScriptRoot 'config.production.yml'
$token = $env:PIOS_CLOUDFLARED_TUNNEL_TOKEN

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Error 'PIOS_CLOUDFLARED_TUNNEL_TOKEN is missing from the service environment; tunnel startup is refused.'
    exit 1
}
if (-not (Test-Path -LiteralPath $cloudflaredPath -PathType Leaf)) {
    Write-Error "cloudflared executable not found at $cloudflaredPath."
    exit 1
}
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    Write-Error "production ingress configuration not found at $configPath."
    exit 1
}

$env:TUNNEL_TOKEN = $token.Trim()
try {
    & $cloudflaredPath tunnel --config $configPath --protocol http2 run
    exit $LASTEXITCODE
} finally {
    Remove-Item Env:TUNNEL_TOKEN -ErrorAction SilentlyContinue
    $token = $null
}
