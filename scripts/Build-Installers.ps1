param(
 [Parameter(Mandatory)][string]$ApplicationImage,
 [Parameter(Mandatory)][string]$OutputDirectory,
 [string]$JdkHome=$env:JAVA_HOME,
 [string]$WixHome,
 [ValidateSet('msi','exe')][string[]]$Types=@('msi','exe')
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'Resolve-Jdk.ps1')
$JdkHome=Resolve-OneZipJdk $JdkHome
$ApplicationImage=[IO.Path]::GetFullPath($ApplicationImage)
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
$version=([xml](Get-Content -LiteralPath (Join-Path $repo 'pom.xml') -Raw)).project.version
$oldPath=$env:PATH
try {
 if($WixHome){$env:PATH=[IO.Path]::GetFullPath($WixHome)+';'+$env:PATH}
 foreach($tool in @('candle.exe','light.exe')){if(-not(Get-Command $tool -ErrorAction SilentlyContinue)){throw "Missing WiX 3 tool: $tool. Pass -WixHome (portable WiX is sufficient)."}}
 if(-not(Test-Path -LiteralPath (Join-Path $ApplicationImage 'OneZip.exe'))){throw 'Build an app-image first'}
 New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
 $associations=Join-Path $OutputDirectory 'associations'
 New-Item -ItemType Directory -Force -Path $associations | Out-Null
 $options=@()
 foreach($extension in @('zip','7z','rar','rar5','tar','gz','gzip','tgz','lz4','xz','txz','bz2','bzip2','tbz','tbz2')){
  $file=Join-Path $associations ($extension+'.properties')
  $mime=switch($extension){'zip'{'application/zip'}'7z'{'application/x-7z-compressed'}'rar'{'application/vnd.rar'}'rar5'{'application/vnd.rar'}'tar'{'application/x-tar'}'gz'{'application/gzip'}'gzip'{'application/gzip'}'xz'{'application/x-xz'}'bz2'{'application/x-bzip2'}'bzip2'{'application/x-bzip2'}default{"application/x-onezip-$extension"}}
  [IO.File]::WriteAllText($file,"extension=$extension`nmime-type=$mime`ndescription=OneZip archive`n",[Text.UTF8Encoding]::new($false))
  $options+=@('--file-associations',$file)
 }
 $binary=[IO.File]::ReadAllBytes((Join-Path $ApplicationImage 'OneZip.exe'))
 $pe=[BitConverter]::ToInt32($binary,60)
 $machine=[BitConverter]::ToUInt16($binary,$pe+4)
 if($machine -notin 0x14c,0x8664){throw 'Unsupported executable architecture'}
 $x86=$machine -eq 0x14c
 if($x86){throw '32-bit portable images are supported, but the verified JDK21 Windows jpackage backend emits x64 MSI metadata. Do not publish a mislabeled x86 installer. Build x86 without -InstallerTypes.'}
 $architecture=if($x86){'x86'}else{'x64'}
 $upgrade=if($x86){'b9954883-bc4d-46e6-92aa-652ef2b21da4'}else{'bdbb1b80-672c-4771-9bfa-2ed941b50be8'}
 foreach($type in $Types){
  $destination=Join-Path $OutputDirectory $type
  New-Item -ItemType Directory -Force -Path $destination | Out-Null
  & (Join-Path $JdkHome 'bin/jpackage.exe') --type $type --name OneZip --app-version $version --vendor shed4329 --description 'OneZip file archiver' --app-image $ApplicationImage --dest $destination --install-dir "OneZip-$architecture" --win-per-user-install --win-dir-chooser --win-menu --win-menu-group OneZip --win-shortcut --win-upgrade-uuid $upgrade --license-file (Join-Path $repo 'LICENSE') @options
  if($LASTEXITCODE -ne 0){throw "jpackage $type failed"}
  $generated=Join-Path $destination ('OneZip-'+$version+'.'+$type)
  if(-not(Test-Path -LiteralPath $generated)){throw "Installer missing: $generated"}
  $named=Join-Path $OutputDirectory ("OneZip-$version-windows-$architecture-setup.$type")
  if(Test-Path -LiteralPath $named){throw "Refusing to replace existing installer: $named"}
  Move-Item -LiteralPath $generated -Destination $named
  Get-FileHash -LiteralPath $named -Algorithm SHA256
 }
} finally {$env:PATH=$oldPath}
