param([Parameter(Mandatory)][string]$Distribution,[Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$Distribution=[IO.Path]::GetFullPath($Distribution)
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $OutputDirectory){throw 'Use a fresh verification directory'}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$cli=Join-Path $Distribution 'OneZipCLI.exe'
$source=Join-Path $OutputDirectory '中文 日本語 sample.txt'
[IO.File]::WriteAllText($source,('OneZip UTF-8 中文 日本語 test'+"`n")*1024,[Text.UTF8Encoding]::new($false))
$expected=(Get-FileHash -LiteralPath $source).Hash
$results=[Collections.Generic.List[string]]::new()
function Invoke-OneZip([string[]]$Arguments){
 $text=& $cli @Arguments 2>&1
 if($LASTEXITCODE -ne 0){throw "CLI verification failed ($LASTEXITCODE): $text"}
}
Invoke-OneZip -Arguments @('--version')
Invoke-OneZip -Arguments @('--help')
foreach($extension in @('zip','7z','tar','gz','tar.gz','lz4','tar.lz4','xz','tar.xz','bz2','tar.bz2')){
 $archive=Join-Path $OutputDirectory ('sample.'+$extension)
 $decoded=Join-Path $OutputDirectory ('decoded-'+$extension)
 Invoke-OneZip -Arguments @('c',$archive,$source,'--level','5','--threads','2','--quiet')
 Invoke-OneZip -Arguments @('l',$archive,'--quiet')
 Invoke-OneZip -Arguments @('t',$archive,'--quiet')
 Invoke-OneZip -Arguments @('x',$archive,'-o',$decoded,'--threads','2','--quiet')
 $files=@(Get-ChildItem -LiteralPath $decoded -File -Recurse)
 if($files.Count -ne 1 -or (Get-FileHash -LiteralPath $files[0].FullName).Hash -ne $expected){throw "Content mismatch: $extension"}
 $results.Add("PASS $extension create/list/test/extract: content SHA256 matched")
}
# Synthetic fixture password, not a user credential; never persisted as a preference.
$encrypted=Join-Path $OutputDirectory 'encrypted.zip'
Invoke-OneZip -Arguments @('c',$encrypted,$source,'-p','delivery-fixture','--quiet')
Invoke-OneZip -Arguments @('t',$encrypted,'-p','delivery-fixture','--quiet')
Invoke-OneZip -Arguments @('x',$encrypted,'-p','delivery-fixture','-o',(Join-Path $OutputDirectory 'encrypted-output'),'--quiet')
if((Get-FileHash -LiteralPath (Join-Path $OutputDirectory 'encrypted-output\中文 日本語 sample.txt')).Hash -ne $expected){throw 'AES ZIP content mismatch'}
$results.Add('PASS AES ZIP create/test/extract: content SHA256 matched')
$results | Set-Content -LiteralPath (Join-Path $OutputDirectory 'results.txt') -Encoding utf8
$results
