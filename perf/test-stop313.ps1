param([Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference = 'Stop'
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString())
$manifestDirectory = Join-Path $testRoot 'build/perf/313'
$scriptDirectory = Join-Path $testRoot 'perf'
New-Item -ItemType Directory -Force $manifestDirectory, $scriptDirectory | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'stop313.ps1') $scriptDirectory
$source = Join-Path $testRoot 'Sleeper.java'
[IO.File]::WriteAllText($source, 'class Sleeper { public static void main(String[] args) throws Exception { Thread.sleep(60000); } }')
$process = $null
try {
    $process = Start-Process (Join-Path $JavaHome 'bin/java.exe') -ArgumentList @($source) -PassThru -WindowStyle Hidden
    $entry = [pscustomobject]@{ Id = $process.Id; StartTicks = $process.StartTime.ToUniversalTime().Ticks }
    $manifest = Join-Path $manifestDirectory 'processes.json'
    $json = ConvertTo-Json -InputObject @($entry)
    if (-not $json.Trim().StartsWith('[')) { throw 'Manifest must remain an array for one process' }
    $restored = $json | ConvertFrom-Json
    if ([long]$restored.StartTicks -ne $entry.StartTicks) { throw 'Process identity lost precision' }
    # A mismatched first PID must not prevent the next owned process from stopping.
    ConvertTo-Json -InputObject @([pscustomobject]@{ Id = $PID; StartTicks = 0 }, $entry) | Set-Content $manifest
    $failed = $false
    try { & (Join-Path $scriptDirectory 'stop313.ps1') } catch { $failed = $true }
    if (-not $failed) { throw 'Mismatch must be reported' }
    if (-not $process.WaitForExit(5000)) { throw 'Owned Java process remained alive after mismatch' }
    ConvertTo-Json -InputObject @($entry) | Set-Content $manifest
    & (Join-Path $scriptDirectory 'stop313.ps1')
    if (Test-Path $manifest) { throw 'Completed manifest was not removed' }
    Write-Output 'STOP313_CHECKS_PASSED'
} finally {
    if ($process -and -not $process.HasExited) { Stop-Process -InputObject $process }
    Remove-Item -LiteralPath $source -ErrorAction SilentlyContinue
    # Keep any unexpected failure artifacts available for diagnosis.
}

