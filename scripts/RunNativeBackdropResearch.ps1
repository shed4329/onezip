param([string]$Image='test-background.jpg')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $repo
try {
 $jdk=$env:JAVA_HOME
 if(-not $jdk -or -not (Test-Path -LiteralPath (Join-Path $jdk 'bin\javac.exe'))){$jdk='JAVA_HOME'}
 $compiler=Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
 $metadata=Join-Path $env:SystemRoot 'System32\WinMetadata'
 $framework=Split-Path $compiler
 $research=Join-Path $repo 'target\native-research';$driver=Join-Path $research 'driver'
 New-Item -ItemType Directory -Force -Path $driver | Out-Null
 $effect=Join-Path $research 'CompositionBlurEffect.dll'
 & $compiler /nologo /target:library "/out:$effect" "/r:$(Join-Path $metadata 'Windows.Graphics.winmd')" "/r:$(Join-Path $metadata 'Windows.Foundation.winmd')" "/r:$(Join-Path $framework 'System.Runtime.dll')" "/r:$(Join-Path $framework 'System.Runtime.WindowsRuntime.dll')" (Join-Path $PSScriptRoot 'native\CompositionBlurEffect.cs')
 if($LASTEXITCODE -ne 0){throw 'Effect descriptor build failed'}
 $jar=Join-Path $repo 'target\onezip-0.6.0-SNAPSHOT-all.jar'
 & (Join-Path $jdk 'bin\javac.exe') -encoding UTF-8 -cp $jar -d $driver (Join-Path $PSScriptRoot 'AppearanceProbe.java') (Join-Path $PSScriptRoot 'UiAcceptance.java') (Join-Path $PSScriptRoot 'WinRtBackdropProbe.java') (Join-Path $PSScriptRoot 'OneZipNativeCarrierProbe.java')
 if($LASTEXITCODE -ne 0){throw 'Research driver build failed'}
 $output=Join-Path $research ('rerun-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
 Write-Host 'This research run temporarily foregrounds its own test windows. It does not install a production backdrop feature.'
 & (Join-Path $jdk 'bin\java.exe') '-Dfile.encoding=UTF-8' "-Donezip.settings.dir=$output\settings" "-Donezip.settings.legacy-dir=$output\no-legacy" -cp "$jar;$driver" OneZipNativeCarrierProbe $Image $output
 if($LASTEXITCODE -ne 0){throw "Research failed; evidence: $output"}
 Write-Host "Research evidence: $output"
}finally{Pop-Location}
