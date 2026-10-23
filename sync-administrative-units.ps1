$ErrorActionPreference = 'Stop'
$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:PATH
Push-Location $PSScriptRoot
try {
    [xml]$pom = Get-Content (Join-Path $PSScriptRoot 'pom.xml') -Raw
    $requiredJava = [int]$pom.project.properties.'java.version'
    $jdkCandidates = @($env:JAVA_HOME)
    foreach ($jdkRoot in @((Join-Path $env:USERPROFILE '.jdks'), (Join-Path $env:ProgramFiles 'Java'), (Join-Path $env:ProgramFiles 'Eclipse Adoptium'))) {
        $jdkCandidates += @(Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    }
    $selectedJdk = $null
    foreach ($candidate in $jdkCandidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $releaseFile = Join-Path $candidate 'release'
        if (!(Test-Path -LiteralPath $releaseFile) -or !(Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe'))) { continue }
        $release = Get-Content -LiteralPath $releaseFile -Raw
        if ($release -match '(?m)^JAVA_VERSION="(\d+)' -and [int]$Matches[1] -eq $requiredJava) {
            $selectedJdk = $candidate
            break
        }
    }
    if (!$selectedJdk) {
        throw "JDK $requiredJava was not found. Install JDK $requiredJava and set JAVA_HOME to its installation directory."
    }
    $env:JAVA_HOME = $selectedJdk
    $env:PATH = "$(Join-Path $selectedJdk 'bin');$originalPath"
    Write-Host "Using JDK ${requiredJava}: $selectedJdk"
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--app.address-sync.enabled=true --spring.main.web-application-type=none --spring.main.keep-alive=false --spring.jpa.hibernate.ddl-auto=validate'
    if ($LASTEXITCODE -ne 0) { throw "Administrative sync failed (exit code $LASTEXITCODE)." }
} finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:PATH = $originalPath
    Pop-Location
}
