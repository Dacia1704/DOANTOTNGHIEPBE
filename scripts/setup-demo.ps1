param(
    [int]$Landlords = 68,
    [int]$Tenants = 1000,
    [ValidateSet(8, 16)][int]$PropertiesPerLandlord = 16,
    [int]$RentalsPerTenant = 2,
    [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
function Invoke-SetupNative {
    param([string]$Command, [string[]]$CommandArguments)
    # Windows PowerShell treats native stderr progress/warnings as errors when redirected.
    # Native commands must be judged by their exit code instead.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Command @CommandArguments 2>&1 | ForEach-Object { Write-Host $_.ToString() }
        $commandExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($commandExitCode -ne 0) { throw "$Command failed with exit code $commandExitCode" }
}
$backendRoot = Split-Path -Parent $PSScriptRoot
Push-Location $backendRoot
try {
    # Fresh/V1 schemas generate V2; already seeded schemas only apply pending migrations.
    Invoke-SetupNative -Command 'docker' -CommandArguments @('compose', 'up', '-d')
    # V1 has no notification_outbox yet; background delivery must wait until V6.
    $common = '--server.port=0 --debug=false --app.rental.jobs-enabled=false --app.notifications.relay-enabled=false --spring.rabbitmq.listener.simple.auto-startup=false --logging.level.root=WARN --logging.level.org.springframework=WARN --logging.level.com.tamdaptit.rentalmanagement=INFO --logging.level.org.hibernate.SQL=WARN'
    # Run without DevTools restart so a failing setup returns a non-zero Maven exit code.
    $jvmArgs = '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false'
    # The first pass intentionally has only V1; later migrations add mapped tables/columns.
    # Full entity validation runs on the second pass after all migrations have applied.
    $syncArgs = "$common --spring.flyway.target=1 --spring.jpa.hibernate.ddl-auto=none --app.address-sync.enabled=true --app.address-sync.require-schema-version=1 --app.address-sync.skip-if-seeded=true --app.address-sync.setup-state-file=target/setup-demo-state.json --app.address-sync.snapshot-file=scripts/data/administrative-units.json"
    Invoke-SetupNative -Command '.\mvnw.cmd' -CommandArguments @('clean', 'spring-boot:run', $jvmArgs, "-Dspring-boot.run.arguments=$syncArgs", '-q')
    $setupState = Get-Content -LiteralPath 'target/setup-demo-state.json' -Raw | ConvertFrom-Json
    if ($setupState.seedApplied) {
        Write-Host 'V2 is already applied. Keeping existing data and seed files; checking remaining migrations.'
    } else {
        Invoke-SetupNative -Command $Python -CommandArguments @('scripts/generate-demo-seed.py', '--landlords', $Landlords, '--tenants', $Tenants, '--properties-per-landlord', $PropertiesPerLandlord, '--rentals-per-tenant', $RentalsPerTenant)
    }
    $seedArgs = "$common --app.database-setup.enabled=true"
    Invoke-SetupNative -Command '.\mvnw.cmd' -CommandArguments @('spring-boot:run', $jvmArgs, "-Dspring-boot.run.arguments=$seedArgs", '-q')
    Write-Host 'rental_db is ready. Start the backend normally with .\mvnw.cmd spring-boot:run.'
} finally {
    Pop-Location
}
