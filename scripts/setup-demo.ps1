param(
    [int]$Landlords = 68,
    [int]$Tenants = 1000,
    [ValidateSet(8, 16)][int]$PropertiesPerLandlord = 16,
    [int]$RentalsPerTenant = 2,
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
Push-Location $backendRoot
try {
    # Intended for a fresh DB, or one stopped at V1. Never drops schemas or data.
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start infrastructure' }
    $common = '--server.port=0 --app.rental.jobs-enabled=false --logging.level.root=WARN --logging.level.com.tamdaptit.rentalmanagement=INFO --logging.level.org.hibernate.SQL=WARN'
    $syncArgs = "$common --spring.flyway.target=1 --app.address-sync.enabled=true --app.address-sync.require-schema-version=1 --app.address-sync.snapshot-file=scripts/data/administrative-units.json"
    & .\mvnw.cmd clean spring-boot:run "-Dspring-boot.run.arguments=$syncArgs" -q
    if ($LASTEXITCODE -ne 0) { throw 'V1/address synchronization failed; V2 was not generated' }
    & $Python scripts/generate-demo-seed.py --landlords $Landlords --tenants $Tenants --properties-per-landlord $PropertiesPerLandlord --rentals-per-tenant $RentalsPerTenant
    if ($LASTEXITCODE -ne 0) { throw 'Demo generation failed' }
    $seedArgs = "$common --app.database-setup.enabled=true"
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=$seedArgs" -q
    if ($LASTEXITCODE -ne 0) { throw 'V2 migration failed' }
    Write-Host 'rental_db is ready. Start the backend normally with .\mvnw.cmd spring-boot:run.'
} finally {
    Pop-Location
}
