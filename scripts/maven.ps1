$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$mavenVersion = '3.9.11'
$mavenBase = Join-Path $projectRoot '.mvn'
$mavenHome = Join-Path $mavenBase "apache-maven-$mavenVersion"
$mavenExe = Join-Path $mavenHome 'bin\mvn.cmd'
if (!(Test-Path -LiteralPath $mavenExe)) {
    New-Item -ItemType Directory -Path $mavenBase -Force | Out-Null
    $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    $archive = Join-Path $mavenBase 'maven.zip'
    Invoke-WebRequest $url -OutFile $archive -UseBasicParsing
    $expected = ((Invoke-WebRequest "$url.sha512" -UseBasicParsing).Content.Trim() -split '\s+')[0]
    $archiveStream = [System.IO.File]::OpenRead($archive)
    try { $actual = [BitConverter]::ToString([System.Security.Cryptography.SHA512]::Create().ComputeHash($archiveStream)).Replace('-', '') } finally { $archiveStream.Dispose() }
    if ($actual -ne $expected) { throw 'Maven checksum mismatch' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($archive, $mavenBase)
}
Push-Location $projectRoot
try { & $mavenExe @args; $buildExitCode = $LASTEXITCODE } finally { Pop-Location }
exit $buildExitCode
