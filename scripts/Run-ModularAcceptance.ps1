param([Parameter(Mandatory)][string]$Distribution,[Parameter(Mandatory)][string]$OutputDirectory,[string]$JdkHome='JAVA_HOME',[ValidateSet('SettingsSmartProbe','I18nProbe')][string]$Probe='SettingsSmartProbe',[ValidateSet('zh-CN','zh-TW','ja','en')][string]$Locale='zh-CN')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$Distribution=[IO.Path]::GetFullPath($Distribution)
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$classes=Join-Path $OutputDirectory 'probe-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$modules=Join-Path $Distribution 'app\mods'
# These test-only access flags expose controllers to the external acceptance harness.
# The shipped launcher does not open implementation packages to arbitrary code.
$access=@('--add-modules','cn.shed4329.onezip')
foreach($package in @('i18n','application','themes.fxJava.fluent','themes.fxJava.fluent.component')){
 $access+=@('--add-exports',"cn.shed4329.onezip/cn.shed4329.onezip.$package=ALL-UNNAMED")
}
& (Join-Path $JdkHome 'bin\javac.exe') --module-path ((Join-Path $JdkHome 'jmods')+';'+$modules) @access -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'UiAcceptance.java') (Join-Path $PSScriptRoot 'SettingsSmartProbe.java') (Join-Path $PSScriptRoot 'AppearanceProbe.java') (Join-Path $PSScriptRoot 'I18nProbe.java')
if($LASTEXITCODE -ne 0){throw 'Probe compilation failed'}
$runtime=Join-Path $Distribution 'runtime\bin\java.exe'
$access+=@('--add-opens','cn.shed4329.onezip/cn.shed4329.onezip.themes.fxJava.fluent=ALL-UNNAMED','--add-opens','cn.shed4329.onezip/cn.shed4329.onezip.application=ALL-UNNAMED')
$settings=Join-Path $OutputDirectory 'settings'
& $runtime --module-path $modules @access "-Donezip.probe.module-path=$modules" "-Donezip.settings.dir=$settings" "-Donezip.settings.legacy-dir=$OutputDirectory\no-legacy" -cp $classes $Probe $Locale $OutputDirectory
if($LASTEXITCODE -ne 0){throw 'Modular GUI acceptance failed'}
