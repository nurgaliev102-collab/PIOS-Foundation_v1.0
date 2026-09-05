# Launches the existing "pios-pilot" Cloudflare Tunnel in token mode.
#
# Why token mode: this tunnel (70a49741-67dd-4f86-8ab1-7c42186c2fbf, DNS
# already routed to piosapp.ru) has no local credentials JSON on this
# machine -- only the account-level cert.pem exists. `cloudflared tunnel
# run pios-pilot` fails with "tunnel credentials file not found" without
# one. A token (`cloudflared tunnel token pios-pilot`) is the connector
# credential for the same, already-existing tunnel -- it does not create a
# new tunnel and carries no local ingress configuration of its own (this
# tunnel's public-hostname routing lives on Cloudflare's side).
#
# Why a wrapper script instead of an inline WinSW <env>: `pios-cloudflared.xml`
# is committed to git. The token must never appear in a tracked file's
# content, so it lives only in `tunnel.token` (gitignored, see .gitignore's
# "Cloudflare named tunnel" block) and is read into an environment variable
# here, at process start, never echoed.
$tokenPath = Join-Path $PSScriptRoot 'tunnel.token'
if (-not (Test-Path $tokenPath)) {
    Write-Error "tunnel.token not found at $tokenPath -- run: cloudflared tunnel token pios-pilot > `"$tokenPath`""
    exit 1
}
$env:TUNNEL_TOKEN = (Get-Content -Raw -Path $tokenPath).Trim()
& 'C:\Program Files (x86)\cloudflared\cloudflared.exe' tunnel run
exit $LASTEXITCODE
