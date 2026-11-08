param(
    [string]$Manifest = 'output/output-cleanup-20261029/manifest.json',
    [switch]$Execute
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$outputRoot = Join-Path $workspaceRoot 'output'
$outputItem = Get-Item -LiteralPath $outputRoot -Force
if (($outputItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'output must not be a junction or symbolic link' }
$manifestPath = [IO.Path]::GetFullPath((Join-Path $workspaceRoot $Manifest))
if (-not $manifestPath.StartsWith($outputRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Manifest must stay under output' }
$plan = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($plan.workspace -ne $workspaceRoot -or $plan.output -ne $outputRoot) { throw 'Manifest belongs to another workspace' }

function Assert-OutputPath([string]$path) {
    if ($path.StartsWith('\\?\', [StringComparison]::Ordinal)) { $path = $path.Substring(4) }
    $absolute = [IO.Path]::GetFullPath($path)
    if (-not $absolute.StartsWith($outputRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Deletion target escaped output: $absolute" }
    return $absolute
}

$preserved = New-Object 'System.Collections.Generic.List[string]'
foreach ($relative in @($plan.keep_files) + @($plan.keep_trees)) { $preserved.Add((Assert-OutputPath (Join-Path $workspaceRoot $relative))) }
$targets = New-Object 'System.Collections.Generic.List[string]'
foreach ($relative in $plan.delete_targets) {
    $absolute = Assert-OutputPath (Join-Path $workspaceRoot $relative)
    foreach ($keep in $preserved) {
        if ($keep.Equals($absolute, [StringComparison]::OrdinalIgnoreCase) -or $keep.StartsWith($absolute + '\', [StringComparison]::OrdinalIgnoreCase) -or $absolute.StartsWith($keep + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Target overlaps preserved data: $absolute" }
    }
    # Check every ancestor without following a junction into another directory.
    $ancestor = [IO.Path]::GetDirectoryName($absolute)
    while ($ancestor -ne $outputRoot) {
        $attributes = [IO.File]::GetAttributes($ancestor)
        if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Target has a reparse ancestor: $absolute" }
        $ancestor = [IO.Path]::GetDirectoryName($ancestor)
    }
    if (Test-Path -LiteralPath $absolute) {
        $targetItem = Get-Item -LiteralPath $absolute -Force
        if (($targetItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) {
            $resolvedTarget = (Resolve-Path -LiteralPath $absolute).ProviderPath
            $null = Assert-OutputPath $resolvedTarget
        }
    }
    $targets.Add($absolute)
}
foreach ($property in $plan.protected_sha256.PSObject.Properties) {
    $path = Join-Path $workspaceRoot $property.Name
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $property.Value) { throw "Protected build/release file changed: $path" }
}
$archivePath = Join-Path $workspaceRoot $plan.historical_archive
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $plan.archive_sha256) { throw 'Evidence archive changed' }
Write-Output ("Validated {0} targets; estimated removable {1:N2} GiB" -f $targets.Count, ($plan.delete_bytes_estimate / 1GB))
if (-not $Execute) { Write-Output 'Dry run only. Pass -Execute to apply the validated manifest.'; exit 0 }

$script:deletedBytes = [long]0
$script:deletedFiles = [long]0
$script:deletedDirectories = [long]0
$script:deletedLinks = [long]0
$script:failures = New-Object 'System.Collections.Generic.List[object]'
$script:newFilesRetained = New-Object 'System.Collections.Generic.List[string]'
$cutoff = [DateTimeOffset]::FromUnixTimeMilliseconds([long]($plan.created_unix * 1000)).UtcDateTime

function Remove-OutputEntry([string]$path) {
    $absolute = Assert-OutputPath $path
    $ioPath = '\\?\' + $absolute
    try {
        $attributes = [IO.File]::GetAttributes($ioPath)
        $directory = ($attributes -band [IO.FileAttributes]::Directory) -ne 0
        if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            # Nonrecursive deletion removes the link itself, never its target.
            if ($directory) { [IO.Directory]::Delete($ioPath, $false) } else { [IO.File]::Delete($ioPath) }
            $script:deletedLinks++
            return
        }
        if ($directory) {
            foreach ($child in [IO.Directory]::GetFileSystemEntries($ioPath)) { Remove-OutputEntry $child }
            if ([IO.Directory]::GetFileSystemEntries($ioPath).Length -eq 0) {
                [IO.Directory]::Delete($ioPath, $false)
                $script:deletedDirectories++
            }
        } else {
            $file = New-Object IO.FileInfo($ioPath)
            if ($file.LastWriteTimeUtc -gt $cutoff) { $script:newFilesRetained.Add($absolute); return }
            $bytes = $file.Length
            if (($attributes -band [IO.FileAttributes]::ReadOnly) -ne 0) { [IO.File]::SetAttributes($ioPath, ($attributes -band (-bnot [IO.FileAttributes]::ReadOnly))) }
            [IO.File]::Delete($ioPath)
            $script:deletedFiles++
            $script:deletedBytes += $bytes
            if (($script:deletedFiles % 10000) -eq 0) { Write-Output ("Removed {0} files, {1:N2} GiB" -f $script:deletedFiles, ($script:deletedBytes / 1GB)) }
        }
    } catch [IO.FileNotFoundException] { return }
      catch [IO.DirectoryNotFoundException] { return }
      catch { $script:failures.Add([pscustomobject]@{ path = $absolute; error = $_.Exception.Message }) }
}

$started = Get-Date
$processed = 0
foreach ($target in $targets) {
    Remove-OutputEntry $target
    $processed++
    if (($processed % 20) -eq 0) { Write-Output ("Processed {0}/{1}; removed {2:N2} GiB, {3} files" -f $processed, $targets.Count, ($script:deletedBytes / 1GB), $script:deletedFiles) }
}
foreach ($property in $plan.protected_sha256.PSObject.Properties) {
    $path = Join-Path $workspaceRoot $property.Name
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $property.Value) { throw "Protected file mismatch after cleanup: $path" }
}
$report = [pscustomobject]@{
    completed = (Get-Date).ToString('o'); duration_seconds = ((Get-Date) - $started).TotalSeconds
    deleted_logical_bytes = $script:deletedBytes; deleted_files = $script:deletedFiles
    deleted_directories = $script:deletedDirectories; unlinked_reparse_entries = $script:deletedLinks
    targets_processed = $processed; errors = @($script:failures.ToArray()); new_files_retained = @($script:newFilesRetained.ToArray())
    protected_hashes_verified = $true
}
$reportPath = Join-Path ([IO.Path]::GetDirectoryName($manifestPath)) 'deletion-result.json'
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Output ("Cleanup complete: removed {0:N2} GiB; errors {1}; new files retained {2}" -f ($script:deletedBytes / 1GB), $script:failures.Count, $script:newFilesRetained.Count)
Write-Output $reportPath
