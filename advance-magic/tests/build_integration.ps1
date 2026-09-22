param([string]$PaperTestRoot = (Join-Path $PSScriptRoot '../../.audit-plugins/voidscape-v2-server'))
$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $moduleRoot 'target/integration-classes'
New-Item -ItemType Directory -Force $testClasses | Out-Null
$testDeps = @((Get-ChildItem (Join-Path $env:USERPROFILE '.m2/repository') -Recurse -Filter '*.jar').FullName)
$testDeps += @((Get-ChildItem (Join-Path $PaperTestRoot 'libraries') -Recurse -Filter '*.jar').FullName)
$testDeps += @((Resolve-Path (Join-Path $PaperTestRoot 'versions/26.2/paper-26.2.jar')).Path)
$testDeps += @((Resolve-Path (Join-Path $moduleRoot 'dist/advance-magic-1.0.0.jar')).Path)
$testClasspath = [string]::Join([IO.Path]::PathSeparator,$testDeps)
& javac --release 21 -proc:none -encoding UTF-8 -cp $testClasspath -d $testClasses (Join-Path $PSScriptRoot 'IntegrationChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Integration test compilation failed.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'plugin.yml') -Destination $testClasses -Force
& jar --create --file (Join-Path $moduleRoot 'target/integration-checks.jar') -C $testClasses .
if ($LASTEXITCODE -ne 0) { throw 'Integration test packaging failed.' }
Write-Output 'Test plugin ready. Install ONLY in a disposable Paper 26.2 server; it edits its world and shuts the server down.'
