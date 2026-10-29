param([switch]$Check,[string]$JdkHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'Resolve-Jdk.ps1')
$JdkHome=Resolve-OneZipJdk $JdkHome
$tool=Join-Path $repo '.mvn\tools\google-java-format-1.29.0-all-deps.jar'
$expected='AEB4E1831D56011E7E06F3393C4E17340C7CCA0FD7BA9076AB8DC0624759F7F0'
if(-not(Test-Path -LiteralPath $tool)){throw 'Install google-java-format 1.29.0 all-deps from Maven Central into .mvn/tools; see docs/DEVELOPMENT.md'}
if((Get-FileHash -LiteralPath $tool -Algorithm SHA256).Hash -ne $expected){throw 'Formatter checksum mismatch'}
$sources=@(Get-ChildItem -LiteralPath (Join-Path $repo 'src') -Filter '*.java' -Recurse -File | ForEach-Object FullName)
[string[]]$formatOptions=if($Check){@('--dry-run','--set-exit-if-changed')}else{@('--replace')}
& (Join-Path $JdkHome 'bin\java.exe') -jar $tool --aosp @formatOptions @sources
if($LASTEXITCODE -ne 0){throw 'Java formatting check failed'}
