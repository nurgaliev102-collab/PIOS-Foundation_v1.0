<#
.SYNOPSIS
    Backs up the five PIOS pilot PostgreSQL databases via pg_dump.

.DESCRIPTION
    Stage 3 Pilot Operations Hardening (B2). Dumps identity, driver-management,
    passenger-experience, order-management and dispatch into timestamped,
    per-database custom-format (pg_restore-compatible) files, verifies each
    dump is non-empty, prunes backups older than -RetentionDays, and exits
    non-zero if anything failed so a Scheduled Task's Last Run Result reflects
    real success/failure.

    Connects the same way the PIOS backend services themselves do
    (application.yml: user "postgres", blank password against 127.0.0.1) --
    i.e. it relies on the existing pg_hba.conf trust-auth entry for
    127.0.0.1, not a stored credential. No password is read, stored, or
    logged anywhere by this script. If trust-auth is ever tightened, pg_dump
    fails loudly and the script exits 1 -- it does not silently no-op.

    network-management's database (pios_network_management) is deliberately
    NOT included: that module is outside the pilot flow (ADR-037) and was
    not part of the five databases this task specified.

.PARAMETER BackupRoot
    Directory backups are written under (outside the git repository by
    design -- dumps contain real pilot participant data and must never be
    committed). Default: C:\PIOS-Backups.

.PARAMETER RetentionDays
    Backups older than this many days are deleted after a successful run.
    Default: 14.

.PARAMETER PgBinPath
    Directory containing pg_dump.exe. Default: the PostgreSQL 17 install
    already present on this machine.

.EXAMPLE
    .\backup-pios-databases.ps1
    Manual run with defaults.

.EXAMPLE
    .\backup-pios-databases.ps1 -BackupRoot D:\Backups\pios -RetentionDays 30
#>
[CmdletBinding()]
param(
    [string]$BackupRoot = "C:\PIOS-Backups",
    [int]$RetentionDays = 14,
    [string]$PgBinPath = "C:\Program Files\PostgreSQL\17\bin",
    [string]$PgHost = "127.0.0.1",
    [int]$PgPort = 5432,
    [string]$PgUser = "postgres"
)

$ErrorActionPreference = "Stop"

$databases = @(
    "pios_identity",
    "pios_driver_management",
    "pios_passenger_experience",
    "pios_order_management",
    "pios_dispatch"
)

$pgDump = Join-Path $PgBinPath "pg_dump.exe"
$timestamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$runDir = Join-Path $BackupRoot $timestamp
$logDir = Join-Path $BackupRoot "logs"
$logFile = Join-Path $logDir "backup-$timestamp.log"

function Write-Log {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Output $line
    Add-Content -Path $logFile -Value $line
}

New-Item -ItemType Directory -Force -Path $runDir | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (-not (Test-Path $pgDump)) {
    Write-Log "FATAL: pg_dump.exe not found at $pgDump"
    exit 1
}

Write-Log "PIOS backup starting. Target: $runDir"

$failures = @()

foreach ($db in $databases) {
    $outFile = Join-Path $runDir "$db.dump"
    Write-Log "Dumping $db -> $outFile"

    & $pgDump -h $PgHost -p $PgPort -U $PgUser -d $db -Fc -f $outFile 2>&1 |
        ForEach-Object { Write-Log "  pg_dump($db): $_" }
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        Write-Log "FAILED: pg_dump exited $exitCode for $db"
        $failures += $db
        continue
    }

    if (-not (Test-Path $outFile) -or (Get-Item $outFile).Length -eq 0) {
        Write-Log "FAILED: $db produced an empty or missing dump file"
        $failures += $db
        continue
    }

    $sizeKb = [math]::Round((Get-Item $outFile).Length / 1KB, 1)
    Write-Log "OK: $db ($sizeKb KB)"
}

if ($failures.Count -gt 0) {
    Write-Log "Backup run FAILED for: $($failures -join ', ')"
    Write-Log "Retention pruning skipped -- a failed run's own output is kept for inspection."
    exit 1
}

Write-Log "All 5 databases backed up successfully."

$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -Path $BackupRoot -Directory |
    Where-Object { $_.Name -match '^\d{4}-\d{2}-\d{2}_\d{6}$' -and $_.LastWriteTime -lt $cutoff } |
    ForEach-Object {
        Write-Log "Retention: removing backup older than $RetentionDays days: $($_.Name)"
        Remove-Item -Recurse -Force $_.FullName
    }

Write-Log "PIOS backup finished successfully."
exit 0
