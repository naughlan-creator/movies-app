# Creates the dev users movie_fan_43 (USER + ADMIN) and movie_fan_42 (USER) in the movie-gold realm.
# Users are data, not configuration, so they are not in the realm export. Passwords come from ../.env.
# Usage (from the repo root, with Keycloak running):  .\keycloak\create-dev-users.ps1

# Not 'Stop': kcadm.sh writes progress messages to stderr, which Windows PowerShell 5.1 would treat
# as errors. Failures are detected through exit codes instead.
$ErrorActionPreference = 'Continue'
$envFile = Join-Path $PSScriptRoot '..\.env'
$settings = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    $settings[$key.Trim()] = $value.Trim()
}

function Invoke-Kcadm([string[]] $kcArgs) {
    $output = docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh @kcArgs 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "kcadm.sh $($kcArgs[0]) failed: $output" }
}

Invoke-Kcadm @('config', 'credentials', '--server', 'http://localhost:8080', '--realm', 'master',
               '--user', $settings['KEYCLOAK_ADMIN_USER'], '--password', $settings['KEYCLOAK_ADMIN_PASSWORD'])

$devUsers = @(
    @{ Username = 'movie_fan_43'; Roles = @('ADMIN') },
    @{ Username = 'movie_fan_42'; Roles = @() }
)

foreach ($user in $devUsers) {
    $name = $user.Username
    $existing = docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get users -r movie-gold -q "username=$name" --fields id 2>&1 | ForEach-Object { "$_" }
    if ("$existing" -match '"id"') {
        Write-Host "$name already exists, skipping"
        continue
    }
    Invoke-Kcadm @('create', 'users', '-r', 'movie-gold', '-s', "username=$name", '-s', 'enabled=true',
                   '-s', "email=$name@example.test", '-s', 'emailVerified=true',
                   '-s', 'firstName=Movie', '-s', "lastName=Fan $($name.Split('_')[-1])")
    Invoke-Kcadm @('set-password', '-r', 'movie-gold', '--username', $name, '--new-password', $settings['KEYCLOAK_DEV_USER_PASSWORD'])
    foreach ($role in $user.Roles) {
        Invoke-Kcadm @('add-roles', '-r', 'movie-gold', '--uusername', $name, '--rolename', $role)
    }
    Write-Host "created $name (extra roles: $($user.Roles -join ', '))"
}
