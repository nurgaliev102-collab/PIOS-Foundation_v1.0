<#
.SYNOPSIS
    Renders the six PIOS WinSW service XML files from their .xml.template
    counterparts, substituting the shared ADR-044 owner credential (and, for
    ai-advisor, the Qwen provider config) read from Machine-scope environment
    variables.

.DESCRIPTION
    Git tracks only the *.xml.template files (no secrets). The real
    pios-<module>.xml files WinSW actually reads are generated artifacts,
    gitignored, and never committed. This script is the only place that
    performs the substitution -- WinSW's own %VAR% expansion is deliberately
    NOT used, because it resolves from WinSW's own (SCM/LocalSystem) process
    environment, which was proven unreliable for this exact purpose (see
    the owner-auth root-cause investigation this script's introduction is
    part of).

    Secret values are read once into memory, substituted directly into the
    rendered XML text, and never written to stdout, stderr, or any log --
    including on error paths.

.NOTES
    Run from any location; paths are resolved relative to this script's own
    directory. Does not install, start, restart, or stop any Windows
    service -- generation and service lifecycle are deliberately separate
    steps.
#>

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot

$commonSecretNames = @("PIOS_OWNER_USERNAME", "PIOS_OWNER_PASSWORD_HASH", "PIOS_OWNER_PASSWORD_SALT")
$aiAdvisorExtraNames = @("PIOS_AI_ADVISOR_PROVIDER", "PIOS_AI_ADVISOR_QWEN_MODEL", "PIOS_AI_ADVISOR_QWEN_API_KEY")
$identityExtraNames = @("PIOS_SMS_API_ID", "PIOS_SMS_SENDER")
$identityOptionalDefaults = @{
    PIOS_SMS_API_BASE_URL = "https://sms.ru"
    PIOS_SMS_CONNECT_TIMEOUT_MS = "2000"
    PIOS_SMS_READ_TIMEOUT_MS = "5000"
}

$services = @(
    @{ Dir = "ai-advisor"; File = "pios-ai-advisor.xml"; Names = $commonSecretNames + $aiAdvisorExtraNames },
    @{ Dir = "dispatch"; File = "pios-dispatch.xml"; Names = $commonSecretNames },
    @{ Dir = "driver-management"; File = "pios-driver-management.xml"; Names = $commonSecretNames },
    @{ Dir = "identity"; File = "pios-identity.xml"; Names = $commonSecretNames + $identityExtraNames + @($identityOptionalDefaults.Keys) },
    @{ Dir = "order-management"; File = "pios-order-management.xml"; Names = $commonSecretNames },
    @{ Dir = "passenger-experience"; File = "pios-passenger-experience.xml"; Names = $commonSecretNames }
)

function Fail([string]$message) {
    # $message must never itself contain a secret value -- callers only pass
    # variable NAMES or file paths, never resolved secret content.
    Write-Error "generate-service-xml: $message"
    exit 1
}

# --- Step 1: read every required value from Machine-scope, once -----------
$allRequiredNames = ($services | ForEach-Object { $_.Names } | Select-Object -Unique | Where-Object { -not $identityOptionalDefaults.ContainsKey($_) })
$regPath = "HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment"
try {
    $envProps = Get-ItemProperty -Path $regPath -ErrorAction Stop
} catch {
    Fail "could not read Machine-scope environment registry key ($regPath)."
}

$values = @{}
$missing = @()
foreach ($name in $allRequiredNames) {
    $v = $envProps.$name
    if ([string]::IsNullOrEmpty($v)) {
        $missing += $name
    } else {
        $values[$name] = $v
    }
}

if ($missing.Count -gt 0) {
    # Names only -- never values -- even in this failure path.
    Fail "missing required Machine-scope environment variable(s): $($missing -join ', '). No XML files were generated."
}

foreach ($name in $identityOptionalDefaults.Keys) {
    $v = $envProps.$name
    $values[$name] = if ([string]::IsNullOrEmpty($v)) { $identityOptionalDefaults[$name] } else { $v }
}

# --- Step 2: validate every template exists before writing anything -------
foreach ($svc in $services) {
    $templatePath = Join-Path $scriptDir "$($svc.Dir)\$($svc.File).template"
    if (-not (Test-Path $templatePath)) {
        Fail "template not found: $templatePath"
    }
}

# --- Step 3: render each service's XML atomically --------------------------
$generated = @()
try {
    foreach ($svc in $services) {
        $templatePath = Join-Path $scriptDir "$($svc.Dir)\$($svc.File).template"
        $targetPath = Join-Path $scriptDir "$($svc.Dir)\$($svc.File)"
        $tempPath = "$targetPath.generating.tmp"

        $content = Get-Content -Path $templatePath -Raw

        foreach ($name in $svc.Names) {
            $token = "__${name}__"
            if ($content.IndexOf($token) -lt 0) {
                Fail "template $templatePath does not contain expected placeholder $token."
            }
            # Values are inserted into XML attributes, including the SMS
            # credential. Escape XML metacharacters without printing values.
            $content = $content.Replace($token, [System.Security.SecurityElement]::Escape($values[$name]))
        }

        # Any remaining __UPPER_SNAKE__ token means a placeholder was missed
        # or a stray one exists that this service doesn't know how to fill --
        # fail rather than ship a broken/half-substituted XML.
        if ([regex]::IsMatch($content, '__[A-Z0-9_]+__')) {
            Fail "unresolved placeholder(s) remain in generated $($svc.File) after substitution. Generation aborted for this file; no output written."
        }

        [System.IO.File]::WriteAllText($tempPath, $content, [System.Text.UTF8Encoding]::new($false))
        Move-Item -Path $tempPath -Destination $targetPath -Force
        $generated += $svc.File
    }
} catch {
    # Clean up any leftover temp file from the failing iteration.
    Get-ChildItem -Path $scriptDir -Recurse -Filter "*.generating.tmp" -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
    Fail "generation failed: $($_.Exception.Message)"
}

Write-Output "Generated $($generated.Count)/$($services.Count) service XML file(s): $($generated -join ', ')"
Write-Output "No secret values were printed. No service was installed, started, restarted, or stopped by this script."
exit 0
