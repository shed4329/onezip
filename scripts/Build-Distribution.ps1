param(
 [string]$JdkHome=$env:JAVA_HOME,
 [string]$OutputDirectory,
 [switch]$SkipBuild,
 [ValidateSet('msi','exe')][string[]]$InstallerTypes=@(),
 [string]$WixHome
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'Resolve-Jdk.ps1')
$JdkHome=Resolve-OneZipJdk $JdkHome
$JdkHome=[IO.Path]::GetFullPath($JdkHome)
foreach($tool in @('java','jlink','jpackage')){if(-not(Test-Path -LiteralPath (Join-Path $JdkHome "bin\$tool.exe"))){throw "Missing JDK tool: $tool"}}
if(-not(Test-Path -LiteralPath (Join-Path $JdkHome 'jmods\javafx.controls.jmod'))){throw 'Use a Java21 JDK with matching JavaFX21 jmods'}
if(-not $OutputDirectory){$OutputDirectory=Join-Path $repo ('target\distribution-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $OutputDirectory){throw 'Output already exists; choose a fresh output directory'}
Push-Location $repo
try{
 if(-not $SkipBuild){$env:JAVA_HOME=$JdkHome;& (Join-Path $repo 'mvnw.cmd') -q -DskipTests package;if($LASTEXITCODE -ne 0){throw 'Build failed'}}
 $version=([xml](Get-Content -LiteralPath (Join-Path $repo 'pom.xml') -Raw)).project.version
 $app=Join-Path $repo "target\onezip-$version.jar"
 $m2=Join-Path $env:USERPROFILE '.m2\repository'
 $metro=Join-Path $m2 'org\jfxtras\jmetro\11.6.16\jmetro-11.6.16.jar'
 $controls=Join-Path $m2 'org\controlsfx\controlsfx\11.0.0\controlsfx-11.0.0.jar'
 foreach($inputFile in @($app,$metro,$controls)){if(-not(Test-Path -LiteralPath $inputFile)){throw "Missing input: $inputFile"}}
 New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
 $appModules=Join-Path $OutputDirectory 'app-modules'
 New-Item -ItemType Directory -Path $appModules | Out-Null
 foreach($inputFile in @($app,$metro,$controls)){Copy-Item -LiteralPath $inputFile -Destination $appModules}
 $runtime=Join-Path $OutputDirectory 'runtime'
 # Strip only platform modules. Keep app JAR line tables outside the linked runtime.
 & (Join-Path $JdkHome 'bin\jlink.exe') --module-path (Join-Path $JdkHome 'jmods') --add-modules java.desktop,javafx.controls,javafx.fxml,jdk.charsets,jdk.localedata,jdk.crypto.ec --include-locales=en,zh,ja --compress=zip-6 --strip-debug --no-header-files --no-man-pages --output $runtime
 if($LASTEXITCODE -ne 0){throw 'jlink failed'}
 $cliConfig=Join-Path $OutputDirectory 'cli-launcher.properties'
 [IO.File]::WriteAllText($cliConfig,"win-console=true`nmodule=cn.shed4329.onezip/cn.shed4329.onezip.CliLauncher`n",[Text.UTF8Encoding]::new($false))
 # Wrap the existing 128px PNG in an ICO container; preserve its original pixels.
 $icon=Join-Path $OutputDirectory 'onezip.ico'
 $png=[IO.File]::ReadAllBytes((Join-Path $repo 'src\cn\shed4329\onezip\themes\fxJava\fluent\img\zip.png'))
 if($png[16] -ne 0 -or $png[17] -ne 0 -or $png[18] -ne 0 -or $png[19] -ne 128 -or $png[23] -ne 128){throw 'Update ICO dimensions for the changed source logo'}
 $writer=[IO.BinaryWriter]::new([IO.File]::Create($icon))
 try{$writer.Write([uint16]0);$writer.Write([uint16]1);$writer.Write([uint16]1);$writer.Write([byte]128);$writer.Write([byte]128);$writer.Write([byte]0);$writer.Write([byte]0);$writer.Write([uint16]1);$writer.Write([uint16]32);$writer.Write([uint32]$png.Length);$writer.Write([uint32]22);$writer.Write($png)}finally{$writer.Dispose()}
 & (Join-Path $JdkHome 'bin\jpackage.exe') --type app-image --name OneZip --app-version $version --vendor shed4329 --dest $OutputDirectory --runtime-image $runtime --module-path $appModules --module cn.shed4329.onezip/cn.shed4329.onezip.Launcher --icon $icon --java-options '-Xms32m' --add-launcher "OneZipCLI=$cliConfig"
 if($LASTEXITCODE -ne 0){throw 'jpackage failed'}
 $portable=Join-Path $OutputDirectory 'OneZip'
 & (Join-Path $portable 'runtime\bin\java.exe') --list-modules | Set-Content -LiteralPath (Join-Path $OutputDirectory 'modules.txt') -Encoding utf8
 if($LASTEXITCODE -ne 0){throw 'Packaged runtime could not start'}
 & (Join-Path $portable 'OneZipCLI.exe') --version
 if($LASTEXITCODE -ne 0){throw 'Packaged CLI could not start'}
 $userDocs=Join-Path $portable 'docs'
 New-Item -ItemType Directory -Path $userDocs | Out-Null
 foreach($document in @('CLI.md','SETTINGS.md','THREADING.md','RAR.md','TAR-GZIP.md','LZ4.md','XZ-BZIP2.md')){Copy-Item -LiteralPath (Join-Path $repo ('docs\'+$document)) -Destination $userDocs}
 $notices=Join-Path $portable 'licenses'
 New-Item -ItemType Directory -Path $notices | Out-Null
 Copy-Item -LiteralPath (Join-Path $repo 'LICENSE') -Destination (Join-Path $notices 'OneZip-Apache-2.0.txt')
 Get-ChildItem -LiteralPath (Join-Path $repo 'licenses') -File | Copy-Item -Destination $notices
 Copy-Item -LiteralPath (Join-Path $repo 'src\cn\shed4329\onezip\engine\RAR-NOTICE.txt') -Destination $notices
 Copy-Item -LiteralPath (Join-Path $repo 'src\cn\shed4329\onezip\themes\fxJava\fluent\icons\fluent\LICENSE.txt') -Destination (Join-Path $notices 'Fluent-Icons-MIT.txt')
 Copy-Item -LiteralPath (Join-Path $repo 'src\cn\shed4329\onezip\themes\fxJava\fluent\icons\fluent\SOURCE.txt') -Destination (Join-Path $notices 'Fluent-Icons-SOURCE.txt')
 Copy-Item -LiteralPath (Join-Path $repo 'docs\THIRD-PARTY.md') -Destination $notices
 [IO.File]::WriteAllText((Join-Path $portable 'README.txt'),"OneZip $version`r`nStart GUI: OneZip.exe`r`nCommand line: OneZipCLI.exe --help`r`nRuntime: bundled modular Java21, no Java installation required.`r`nSettings: %LOCALAPPDATA%\OneZip\settings.properties`r`n",[Text.UTF8Encoding]::new($false))
 $portablePrefix=$portable.TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
 Get-ChildItem -LiteralPath $portable -File -Recurse | Get-FileHash -Algorithm SHA256 | ForEach-Object {
  if(-not $_.Path.StartsWith($portablePrefix,[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected checksum path'}
  $_.Hash+'  '+$_.Path.Substring($portablePrefix.Length)
 } | Set-Content -LiteralPath (Join-Path $OutputDirectory 'SHA256SUMS.txt') -Encoding utf8
 Write-Output "Portable application: $portable"
 if($InstallerTypes.Count -gt 0){
  & (Join-Path $PSScriptRoot 'Build-Installers.ps1') -ApplicationImage $portable -OutputDirectory (Join-Path $OutputDirectory 'installers') -JdkHome $JdkHome -WixHome $WixHome -Types $InstallerTypes
 }
}finally{Pop-Location}
