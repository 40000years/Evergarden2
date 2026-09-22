param([string]$PaperTestRoot = (Join-Path $PSScriptRoot '../../.audit-plugins/gardens-server'))
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$classes = Join-Path $workspaceRoot 'evergarden/target/garden-test-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$deps = @((Get-ChildItem (Join-Path $env:USERPROFILE '.m2/repository') -Recurse -Filter '*.jar').FullName)
$deps += @((Get-ChildItem (Join-Path $PaperTestRoot 'libraries') -Recurse -Filter '*.jar').FullName)
$deps += @((Join-Path $PaperTestRoot 'versions/26.2/paper-26.2.jar'),(Join-Path $workspaceRoot 'evergarden.jar'),(Join-Path $workspaceRoot 'advance-magic.jar'))
& javac --release 21 -proc:none -encoding UTF-8 -cp ([string]::Join([IO.Path]::PathSeparator,$deps)) -d $classes (Join-Path $PSScriptRoot 'GardensChecks.java')
if($LASTEXITCODE -ne 0){throw 'Garden integration compilation failed'}
@('name: GardensChecks','version: 1.0','main: GardensChecks',"api-version: '1.21'",'depend: [Evergarden, advance-magic]') | Set-Content -Encoding ASCII (Join-Path $classes 'plugin.yml')
& jar --create --file (Join-Path $workspaceRoot 'evergarden/target/gardens-checks.jar') -C $classes .
if($LASTEXITCODE -ne 0){throw 'Garden integration packaging failed'}
Write-Output 'Built target/gardens-checks.jar. Isolated Paper server only: generates chunks, creates a test actor, then shuts down.'
