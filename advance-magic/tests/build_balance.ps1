param([string]$PaperTestRoot = (Join-Path $PSScriptRoot '../../.audit-plugins/balance-server'))
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$classes = Join-Path $workspaceRoot 'advance-magic/target/balance-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$deps = @((Get-ChildItem (Join-Path $env:USERPROFILE '.m2/repository') -Recurse -Filter '*.jar').FullName)
$deps += @((Get-ChildItem (Join-Path $PaperTestRoot 'libraries') -Recurse -Filter '*.jar').FullName)
$deps += @((Join-Path $PaperTestRoot 'versions/26.2/paper-26.2.jar'),(Join-Path $workspaceRoot 'advance-magic.jar'))
& javac --release 21 -proc:none -encoding UTF-8 -cp ([string]::Join([IO.Path]::PathSeparator,$deps)) -d $classes (Join-Path $PSScriptRoot 'BalanceChecks.java')
if($LASTEXITCODE -ne 0){throw 'Balance integration compilation failed'}
@('name: BalanceChecks','version: 1.0','main: BalanceChecks',"api-version: '1.21'",'depend: [advance-magic]') | Set-Content -Encoding ASCII (Join-Path $classes 'plugin.yml')
& jar --create --file (Join-Path $workspaceRoot 'advance-magic/target/balance-checks.jar') -C $classes .
if($LASTEXITCODE -ne 0){throw 'Balance integration packaging failed'}
Write-Output 'Built target/balance-checks.jar. Use only in a disposable Paper 26.2 server: edits terrain, creates test actor, then shuts down.'
