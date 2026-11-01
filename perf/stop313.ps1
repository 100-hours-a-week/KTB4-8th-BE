$ErrorActionPreference = 'Stop'
$manifest = Join-Path $PSScriptRoot '../build/perf/313/processes.json'
$skipped = $false
foreach ($entry in (Get-Content -Raw -LiteralPath $manifest | ConvertFrom-Json)) {
    $process = Get-Process -Id $entry.Id -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq 'java' -and $process.StartTime.ToUniversalTime().Ticks -eq [long]$entry.StartTicks) {
        Stop-Process -InputObject $process
    } elseif ($process) {
        Write-Warning "PID $($entry.Id)가 다른 프로세스에 재사용됐습니다. 종료하지 않습니다"
        $skipped = $true
    }
}
if ($skipped) { throw '일부 PID의 신원이 달라 종료하지 않았습니다' }
Remove-Item -LiteralPath $manifest
