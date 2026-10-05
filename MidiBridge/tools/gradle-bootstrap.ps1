# Only official Gradle wrapper bytes are executed, after a pinned SHA-256 check.
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$Jar = Join-Path $Root 'gradle\wrapper\gradle-wrapper.jar'
$Expected = '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'
if (-not (Test-Path $Jar)) {
    Write-Host 'Downloading the official Gradle 8.13 wrapper...'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -TimeoutSec 180 -Uri `
      'https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar' `
      -OutFile "$Jar.part"
    $Candidate = "$Jar.part"
} else { $Candidate = $Jar }
$Actual = (Get-FileHash -Algorithm SHA256 $Candidate).Hash.ToLowerInvariant()
if ($Actual -ne $Expected) { throw 'Gradle wrapper checksum mismatch. Nothing executed.' }
if ($Candidate -ne $Jar) { Move-Item -Force $Candidate $Jar }
Write-Host 'Official Gradle wrapper SHA-256 verified.'
