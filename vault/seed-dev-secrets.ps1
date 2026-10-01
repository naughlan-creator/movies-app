# Copies the backend's secrets into the dev Vault and gives the backend a least-privilege identity.
#
#  1. Stores Mongo + TMDB settings at secret/movies (KV v2), keyed by the Spring property they set
#  2. Creates policy "movies-read": read secret/movies, and nothing else
#  3. Creates AppRole "movies-backend" with that policy and writes its role_id / secret_id
#     into the backend's .env as VAULT_ROLE_ID / VAULT_SECRET_ID (other lines are untouched)
#
# Then set VAULT_ENABLED=true in movies-backend/.env and restart the backend.
# Dev Vault keeps everything in memory, so re-run this after restarting the vault container.
#
# Usage (repo root, with `docker compose up -d vault` running):  .\vault\seed-dev-secrets.ps1

param(
    [string] $BackendEnvFile = (Join-Path $PSScriptRoot '..\movies-backend\.env'),
    [string] $RootEnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string] $VaultAddr = 'http://localhost:18200'
)

$ErrorActionPreference = 'Stop'

function Read-EnvFile([string] $path) {
    $values = @{}
    if (Test-Path $path) {
        Get-Content $path | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
            $key, $value = $_ -split '=', 2
            $values[$key.Trim()] = $value.Trim()
        }
    }
    return $values
}

$rootEnv = Read-EnvFile $RootEnvFile
$backendEnv = Read-EnvFile $BackendEnvFile
$headers = @{ 'X-Vault-Token' = $rootEnv['VAULT_DEV_ROOT_TOKEN'] }
if (-not $headers['X-Vault-Token']) { throw "VAULT_DEV_ROOT_TOKEN is missing from $RootEnvFile" }

function Invoke-Vault([string] $method, [string] $path, $body = $null) {
    $params = @{ Method = $method; Uri = "$VaultAddr/v1/$path"; Headers = $headers; ContentType = 'application/json' }
    if ($null -ne $body) { $params['Body'] = ($body | ConvertTo-Json -Depth 5 -Compress) }
    return Invoke-RestMethod @params
}

# 1. Secrets, named after the Spring properties they set (Spring Cloud Vault maps keys to properties)
$mongoUri = $backendEnv['MONGO_URI']
if (-not $mongoUri) {
    $mongoUri = "mongodb+srv://$($backendEnv['MONGO_USER']):$($backendEnv['MONGO_PASSWORD'])@$($backendEnv['MONGO_CLUSTER'])/movies"
}
$secrets = @{
    'spring.mongodb.uri'      = $mongoUri
    'spring.mongodb.database' = $backendEnv['MONGO_DATABASE']
    'app.tmdb.api-token'      = $backendEnv['TMDB_API_TOKEN']
}
Invoke-Vault 'POST' 'secret/data/movies' @{ data = $secrets } | Out-Null
Write-Host "stored $($secrets.Count) secrets at secret/movies"

# 2. Least privilege: this policy can read one path and nothing else
$policy = 'path "secret/data/movies" { capabilities = ["read"] }'
Invoke-Vault 'PUT' 'sys/policies/acl/movies-read' @{ policy = $policy } | Out-Null
Write-Host 'policy movies-read: read secret/movies only'

# 3. AppRole: a machine identity. role_id is like a username, secret_id like a password that expires.
$authMethods = Invoke-Vault 'GET' 'sys/auth'
if (-not $authMethods.data.PSObject.Properties['approle/']) {
    Invoke-Vault 'POST' 'sys/auth/approle' @{ type = 'approle' } | Out-Null
}
Invoke-Vault 'POST' 'auth/approle/role/movies-backend' @{
    token_policies = @('movies-read')
    token_ttl      = '1h'
    token_max_ttl  = '4h'
    secret_id_ttl  = '24h'
} | Out-Null
$roleId = (Invoke-Vault 'GET' 'auth/approle/role/movies-backend/role-id').data.role_id
$secretId = (Invoke-Vault 'POST' 'auth/approle/role/movies-backend/secret-id').data.secret_id

# Write the identity into the backend .env, replacing any previous values
$lines = @()
if (Test-Path $BackendEnvFile) {
    $lines = @(Get-Content $BackendEnvFile | Where-Object { $_ -notmatch '^\s*VAULT_(ROLE_ID|SECRET_ID)=' })
}
$lines += "VAULT_ROLE_ID=$roleId"
$lines += "VAULT_SECRET_ID=$secretId"
# UTF-8 without a byte-order mark: Windows PowerShell's "-Encoding utf8" adds one, which .env parsers
# can mistake for part of the first key
[System.IO.File]::WriteAllLines((Resolve-Path $BackendEnvFile), [string[]] $lines, (New-Object System.Text.UTF8Encoding $false))
Write-Host "AppRole movies-backend ready; VAULT_ROLE_ID and VAULT_SECRET_ID written to $BackendEnvFile (secret_id expires in 24h)"
Write-Host 'Set VAULT_ENABLED=true in that file and restart the backend to load secrets from Vault.'
