$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--app.address-sync.enabled=true --spring.main.web-application-type=none --spring.main.keep-alive=false --spring.jpa.hibernate.ddl-auto=validate'
    if ($LASTEXITCODE -ne 0) { throw "Administrative sync failed (exit code $LASTEXITCODE)." }
} finally {
    Pop-Location
}
