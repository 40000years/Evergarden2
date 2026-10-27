# Execute the fixed cleanup inventory after both replacement JARs have been built.
$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$auditRoot = Join-Path $workspaceRoot 'output\clean-release-20261025'
$plan = Get-Content -LiteralPath (Join-Path $auditRoot 'disk-cleanup-plan.json') -Raw | ConvertFrom-Json
$build = Get-Content -LiteralPath (Join-Path $auditRoot 'build-report.json') -Raw | ConvertFrom-Json
if ($plan.workspace -ne $workspaceRoot -or $plan.reparse_points.Count -or $plan.scan_errors.Count) { throw 'Cleanup inventory is incomplete or outside this workspace' }
foreach ($module in @('advance-magic', 'evergarden')) {
    $jar = Join-Path $workspaceRoot ('dist\' + $module + '.jar')
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $build.$module.sha256) { throw ('New release changed: ' + $module) }
}
$keptReleases = @('advance-magic-1.28.0-legendary-spectacle.jar', 'advance-magic-1.29.0-light-lance.jar', 'advance-magic-1.29.1-seraph-boss-targeting.jar', 'evergarden-3.0.0-e2.128-pet-menagerie.jar', 'evergarden-3.0.0-e2.129-slim.jar')
$distRoot = Join-Path $workspaceRoot 'dist'
$targetRoot = Join-Path $workspaceRoot 'evergarden\target'
$packRoot = Join-Path $workspaceRoot 'evergarden\dist'
$verified = @()
foreach ($entry in $plan.entries) {
    $path = [IO.Path]::GetFullPath((Join-Path $workspaceRoot $entry.path))
    $parent = [IO.Path]::GetDirectoryName($path)
    $name = [IO.Path]::GetFileName($path)
    $allowed = ($parent -eq $distRoot -and $name -match '^(advance-magic-|evergarden-).+\.jar$' -and $name -notin $keptReleases) -or
        ($parent -eq $targetRoot -and $name -notin @('classes', 'evergarden.jar', 'javac.args')) -or
        ($parent -eq $packRoot -and $name -in @('evergarden-3.0.0.jar', 'voidscape-bedrock.mcpack', 'voidscape-java.zip'))
    if (-not $allowed -or -not $path.StartsWith($workspaceRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw ('Unexpected cleanup path: ' + $path) }
    if (-not (Test-Path -LiteralPath $path)) { continue }
    $resolved = (Resolve-Path -LiteralPath $path).Path
    if ($resolved -ne $path) { throw ('Cleanup path resolves elsewhere: ' + $path) }
    $item = Get-Item -LiteralPath $path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw ('Refusing reparse point: ' + $path) }
    if ($item.PSIsContainer) {
        $links = @(Get-ChildItem -LiteralPath $path -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint })
        if ($links.Count) { throw ('Refusing directory containing reparse points: ' + $path) }
    }
    $verified += [pscustomobject]@{ Path = $path; RelativePath = $entry.path; Bytes = [long]$entry.bytes }
}
$results = @()
foreach ($entry in $verified) {
    try {
        Remove-Item -LiteralPath $entry.Path -Recurse -Force
        if (Test-Path -LiteralPath $entry.Path) { throw 'Target still exists' }
        $results += [pscustomobject]@{ Path = $entry.RelativePath; Bytes = $entry.Bytes; Removed = $true; Error = '' }
    } catch {
        $results += [pscustomobject]@{ Path = $entry.RelativePath; Bytes = $entry.Bytes; Removed = $false; Error = $_.Exception.Message }
    }
}
$removed = @($results | Where-Object Removed)
$failed = @($results | Where-Object { -not $_.Removed })
$report = [pscustomobject]@{ RemovedEntries = $removed.Count; RemovedLogicalBytes = ($removed | Measure-Object -Property Bytes -Sum).Sum; FailedEntries = $failed.Count; Results = $results }
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $auditRoot 'disk-cleanup-result.json') -Encoding UTF8
[pscustomobject]@{ RemovedEntries = $removed.Count; RemovedLogicalBytes = $report.RemovedLogicalBytes; FailedEntries = $failed.Count } | ConvertTo-Json -Compress
if ($failed.Count) { $failed | Select-Object Path, Error | Format-Table -AutoSize; exit 1 }
