<#
.SYNOPSIS
    Isolated regression verification for production WinSW configuration.

.DESCRIPTION
    Uses fixture values in a temporary HKCU key; never reads or writes real
    production secrets, never installs or starts a service, and never invokes
    cloudflared. Verifies canonical origin, token-mode ingress, and the ACL of
    every generated secret-bearing XML file.
#>

$ErrorActionPreference = 'Stop'
$sourceRoot = $PSScriptRoot
$temporaryBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$temporaryRoot = Join-Path $temporaryBase ("pios-winsw-security-" + [Guid]::NewGuid().ToString('N'))
$temporaryServiceRoot = Join-Path $temporaryRoot 'windows-services'
$registryPath = "HKCU:\Software\PIOS\Tests\WinSWSecurity\" + [Guid]::NewGuid().ToString('N')
$currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
$systemSid = [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18')
$administratorsSid = [System.Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
$allowedSidValues = @($currentSid.Value, $systemSid.Value, $administratorsSid.Value) | Select-Object -Unique

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) {
        throw $message
    }
}

try {
    New-Item -Path $temporaryServiceRoot -ItemType Directory -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $sourceRoot 'generate-service-xml.ps1') -Destination $temporaryServiceRoot
    $serviceDirectories = @('ai-advisor', 'dispatch', 'driver-management', 'identity', 'order-management', 'passenger-experience')
    foreach ($directory in $serviceDirectories) {
        $destination = Join-Path $temporaryServiceRoot $directory
        New-Item -Path $destination -ItemType Directory | Out-Null
        Copy-Item -LiteralPath (Join-Path $sourceRoot "$directory\pios-$directory.xml.template") -Destination $destination
    }

    New-Item -Path $registryPath -Force | Out-Null
    $fixtureValues = @{
        PIOS_OWNER_USERNAME = 'fixture-owner'
        PIOS_OWNER_PASSWORD_HASH = 'fixture-owner-hash'
        PIOS_OWNER_PASSWORD_SALT = 'fixture-owner-salt'
        PIOS_AI_ADVISOR_PROVIDER = 'fixture-provider'
        PIOS_AI_ADVISOR_QWEN_MODEL = 'fixture-model'
        PIOS_AI_ADVISOR_QWEN_API_KEY = 'fixture-ai-key'
        PIOS_SMS_LOGIN = 'fixture-login'
        PIOS_SMS_API_KEY = 'fixture-sms-key'
        PIOS_SMS_SENDER = 'FixtureSender'
        PIOS_OTP_RELAY_KEY = [Convert]::ToBase64String([byte[]](1..32))
        PIOS_PUSH_VAPID_PUBLIC_KEY = 'fixture-public-key'
        PIOS_PUSH_VAPID_PRIVATE_KEY = 'fixture-private-key'
        PIOS_PUSH_VAPID_SUBJECT = 'mailto:fixture@example.invalid'
    }
    foreach ($entry in $fixtureValues.GetEnumerator()) {
        New-ItemProperty -Path $registryPath -Name $entry.Key -Value $entry.Value -PropertyType String -Force | Out-Null
    }

    $renderer = Join-Path $temporaryServiceRoot 'generate-service-xml.ps1'
    $output = & $renderer -EnvironmentRegistryPath $registryPath -ServiceAccount $currentSid.Value 2>&1
    Assert-True ($LASTEXITCODE -eq 0) 'WinSW renderer failed in the isolated ACL test.'
    $outputText = [string]::Join([Environment]::NewLine, [string[]]$output)
    foreach ($secret in @($fixtureValues.PIOS_OWNER_PASSWORD_HASH, $fixtureValues.PIOS_SMS_API_KEY, $fixtureValues.PIOS_OTP_RELAY_KEY, $fixtureValues.PIOS_PUSH_VAPID_PRIVATE_KEY)) {
        Assert-True (-not $outputText.Contains($secret)) 'WinSW renderer exposed a fixture secret in output.'
    }

    foreach ($directory in $serviceDirectories) {
        $generatedPath = Join-Path $temporaryServiceRoot "$directory\pios-$directory.xml"
        Assert-True (Test-Path -LiteralPath $generatedPath -PathType Leaf) "Generated XML missing for $directory."
        $acl = Get-Acl -LiteralPath $generatedPath
        Assert-True $acl.AreAccessRulesProtected "ACL inheritance is enabled for $directory."
        $rules = @($acl.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]))
        $actualSidValues = @($rules | ForEach-Object { $_.IdentityReference.Value } | Select-Object -Unique)
        Assert-True (@($actualSidValues | Where-Object { $_ -notin $allowedSidValues }).Count -eq 0) "Unexpected ACL principal can access $directory."
        foreach ($requiredSid in $allowedSidValues) {
            $hasFullControl = @($rules | Where-Object {
                $_.IdentityReference.Value -eq $requiredSid -and
                $_.AccessControlType -eq [System.Security.AccessControl.AccessControlType]::Allow -and
                (($_.FileSystemRights -band [System.Security.AccessControl.FileSystemRights]::FullControl) -eq
                    [System.Security.AccessControl.FileSystemRights]::FullControl)
            }).Count -gt 0
            Assert-True $hasFullControl "Required ACL is missing for $directory."
        }
    }

    [xml]$frontendService = Get-Content -LiteralPath (Join-Path $sourceRoot 'frontend\pios-frontend.xml') -Raw
    $publicOrigin = @($frontendService.service.env | Where-Object { $_.name -eq 'PUBLIC_ORIGIN' })
    Assert-True ($publicOrigin.Count -eq 1 -and $publicOrigin[0].value -eq 'https://piosapp.ru') 'Frontend production origin is not canonical.'

    foreach ($directory in @('dispatch', 'driver-management', 'identity', 'order-management', 'passenger-experience')) {
        [xml]$template = Get-Content -LiteralPath (Join-Path $sourceRoot "$directory\pios-$directory.xml.template") -Raw
        $corsOrigin = @($template.service.env | Where-Object { $_.name -eq 'PIOS_PILOT_FRONTEND_ORIGIN' })
        Assert-True ($corsOrigin.Count -eq 1 -and $corsOrigin[0].value -eq 'https://piosapp.ru') "Backend CORS origin is not canonical for $directory."
    }

    [xml]$cloudflaredService = Get-Content -LiteralPath (Join-Path $sourceRoot 'cloudflared\pios-cloudflared.xml') -Raw
    Assert-True ($cloudflaredService.service.executable -like '*powershell.exe') 'Cloudflared WinSW service does not invoke the token-mode wrapper.'
    Assert-True ($cloudflaredService.service.arguments -like '*run-tunnel.ps1*') 'Cloudflared WinSW wrapper argument is missing.'
    $wrapper = Get-Content -LiteralPath (Join-Path $sourceRoot 'cloudflared\run-tunnel.ps1') -Raw
    Assert-True ($wrapper.Contains('$env:PIOS_CLOUDFLARED_TUNNEL_TOKEN')) 'Cloudflared token is not sourced from the service runtime environment.'
    Assert-True (-not $wrapper.Contains('tunnel.token')) 'Cloudflared wrapper still depends on a machine-local token file.'
    $wrapperPath = Join-Path $sourceRoot 'cloudflared\run-tunnel.ps1'
    $escapedWrapperPath = $wrapperPath.Replace("'", "''")
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $missingTokenOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item Env:PIOS_CLOUDFLARED_TUNNEL_TOKEN -ErrorAction SilentlyContinue; & '$escapedWrapperPath'" 2>&1
        $missingTokenExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    Assert-True ($missingTokenExitCode -eq 1) 'Cloudflared wrapper did not fail closed when the connector token was absent.'
    Assert-True (-not ([string]::Join([Environment]::NewLine, [string[]]$missingTokenOutput)).Contains('TUNNEL_TOKEN=')) 'Cloudflared missing-token failure exposed a token-shaped value.'
    $ingress = Get-Content -LiteralPath (Join-Path $sourceRoot 'cloudflared\config.production.yml') -Raw
    Assert-True ($ingress.Contains('hostname: piosapp.ru')) 'Cloudflared production hostname is missing.'
    Assert-True ($ingress.Contains('service: http://127.0.0.1:4173')) 'Cloudflared does not route to the frontend.'
    Assert-True ($ingress.Contains('service: http_status:404')) 'Cloudflared catch-all deny route is missing.'
    Assert-True (-not [regex]::IsMatch($ingress, ':(808[0-9]|809[0-9])')) 'Cloudflared ingress exposes a backend port.'

    Write-Output 'PASS: production origins, token-mode ingress, and generated WinSW XML ACLs are deterministic and restricted.'
} finally {
    if (Test-Path -LiteralPath $registryPath) {
        Remove-Item -LiteralPath $registryPath -Recurse -Force
    }
    $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
    if ($resolvedTemporaryRoot.StartsWith($temporaryBase, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $resolvedTemporaryRoot) -like 'pios-winsw-security-*' -and
        (Test-Path -LiteralPath $resolvedTemporaryRoot)) {
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}
