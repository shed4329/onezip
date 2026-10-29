function Resolve-OneZipJdk {
 param([string]$JdkHome)
 if(-not $JdkHome){$JdkHome=$env:JAVA_HOME}
 if(-not $JdkHome){
  $javac=Get-Command javac.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if($javac){$JdkHome=Split-Path (Split-Path $javac.Source -Parent) -Parent}
 }
 if(-not $JdkHome -or -not(Test-Path -LiteralPath (Join-Path $JdkHome 'bin/javac.exe'))){
  throw 'Set JAVA_HOME to a JDK 21 installation or pass -JdkHome. No machine-specific default is used.'
 }
 return [IO.Path]::GetFullPath($JdkHome)
}
