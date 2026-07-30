[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'reset', 'status')]
    [string]$Action = 'start',
    [switch]$OpenBrowser,
    [switch]$RealAi
)

$ErrorActionPreference = 'Stop'
$ContainerName = 'worktaskflow-ai-report-manual-mysql'
$DatabasePort = 13307
$BackendPort = 8081
$FrontendPort = 5174
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepositoryRoot = Split-Path -Parent $ScriptRoot
$BackendRoot = Join-Path $RepositoryRoot 'backend'
$FrontendRoot = Join-Path $RepositoryRoot 'frontend'
$RuntimeRoot = Join-Path ([IO.Path]::GetTempPath()) 'worktaskflow-ai-report-manual'
$StateFile = Join-Path $RuntimeRoot 'state.json'
$BackendLog = Join-Path $RuntimeRoot 'backend.out.log'
$BackendErrorLog = Join-Path $RuntimeRoot 'backend.err.log'
$FrontendLog = Join-Path $RuntimeRoot 'frontend.out.log'
$FrontendErrorLog = Join-Path $RuntimeRoot 'frontend.err.log'

function Get-ListenerProcessId([int]$Port) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($listener) { return [int]$listener.OwningProcess }
    return $null
}

function Wait-ForPort([int]$Port, [int]$Seconds, [string]$Name) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $processId = Get-ListenerProcessId $Port
        if ($processId) { return $processId }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not start on port $Port within $Seconds seconds."
}

function Stop-TrackedProcess([object]$ProcessId) {
    if (-not $ProcessId) { return }
    $process = Get-Process -Id ([int]$ProcessId) -ErrorAction SilentlyContinue
    if ($process) { Stop-Process -Id $process.Id -Force }
}

function Test-ContainerExists {
    $containerId = docker ps -aq --filter "name=^/$ContainerName$"
    return -not [string]::IsNullOrWhiteSpace(($containerId | Select-Object -First 1))
}

function Stop-Environment {
    if (Test-Path -LiteralPath $StateFile) {
        $state = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        Stop-TrackedProcess $state.frontendProcessId
        Stop-TrackedProcess $state.frontendLauncherProcessId
        Stop-TrackedProcess $state.backendProcessId
        Stop-TrackedProcess $state.backendLauncherProcessId
        Remove-Item -LiteralPath $StateFile -Force
    }

    if (Test-ContainerExists) {
        docker stop $ContainerName | Out-Null
    }

    Write-Host 'AI weekly report manual test environment stopped.'
}

function Show-Status {
    $databasePid = Get-ListenerProcessId $DatabasePort
    $backendPid = Get-ListenerProcessId $BackendPort
    $frontendPid = Get-ListenerProcessId $FrontendPort
    [pscustomobject]@{
        MySQL = if ($databasePid) { "RUNNING pid=$databasePid port=$DatabasePort" } else { 'STOPPED' }
        Backend = if ($backendPid) { "RUNNING pid=$backendPid port=$BackendPort" } else { 'STOPPED' }
        Frontend = if ($frontendPid) { "RUNNING pid=$frontendPid port=$FrontendPort" } else { 'STOPPED' }
        Url = "http://127.0.0.1:$FrontendPort/login"
        Username = 'ai_report_tester'
        Password = 'password123!'
    } | Format-List
}

function Start-Environment {
    foreach ($port in @($DatabasePort, $BackendPort, $FrontendPort)) {
        if (Get-ListenerProcessId $port) {
            throw "Port $port is already in use. Run this script with 'status' or 'stop' first."
        }
    }
    if (Test-ContainerExists) {
        throw "Container '$ContainerName' already exists. Run this script with 'stop' first."
    }

    New-Item -ItemType Directory -Path $RuntimeRoot -Force | Out-Null
    $backendLauncher = $null
    $frontendLauncher = $null

    try {
        docker run --rm -d `
            --name $ContainerName `
            -e MYSQL_ROOT_PASSWORD=manual-test-password `
            -e MYSQL_DATABASE=worktaskflow_ai_manual `
            -p "127.0.0.1:${DatabasePort}:3306" `
            mysql:8.4 | Out-Null

        $databaseDeadline = (Get-Date).AddSeconds(45)
        do {
            docker exec $ContainerName mysqladmin ping -uroot -pmanual-test-password --silent 2>$null
            if ($LASTEXITCODE -eq 0) { break }
            Start-Sleep -Milliseconds 500
        } while ((Get-Date) -lt $databaseDeadline)
        if ($LASTEXITCODE -ne 0) { throw 'Disposable MySQL did not become ready.' }

        $env:SPRING_DATASOURCE_URL =
            "jdbc:mysql://127.0.0.1:$DatabasePort/worktaskflow_ai_manual?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
        $env:SPRING_DATASOURCE_USERNAME = 'root'
        $env:SPRING_DATASOURCE_PASSWORD = 'manual-test-password'
        $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
        $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
        $env:SPRING_FLYWAY_ENABLED = 'true'
        $env:SERVER_PORT = "$BackendPort"
        $env:FRONTEND_URL = "http://127.0.0.1:$FrontendPort"
        $env:APP_FRONTEND_URL = "http://127.0.0.1:$FrontendPort"
        $env:AI_REPORT_ENABLED = 'false'
        $env:MAIL_ENABLED = 'false'
        $env:DEMO_ENABLED = 'false'
        if ($RealAi) {
            # 실제 provider 호출은 과금된다. .env의 키만 읽고 값은 출력하지 않는다.
            $envFile = Join-Path $RepositoryRoot '.env'
            if (-not (Test-Path -LiteralPath $envFile)) {
                throw "-RealAi requires $envFile with OPENAI_API_KEY."
            }
            foreach ($line in Get-Content -LiteralPath $envFile) {
                if ($line -match '^\s*(OPENAI_API_KEY|OPENAI_MODEL|OPENAI_REQUEST_TIMEOUT)\s*=\s*(.+)$') {
                    Set-Item -Path "env:$($Matches[1])" -Value $Matches[2].Trim()
                }
            }
            if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
                throw 'OPENAI_API_KEY is empty; cannot run with -RealAi.'
            }
            $env:AI_REPORT_ENABLED = 'true'
        }

        $backendLauncher = Start-Process `
            -FilePath (Join-Path $BackendRoot 'mvnw.cmd') `
            -ArgumentList @('spring-boot:run', '-Pmanual-ai-report', '-DskipTests') `
            -WorkingDirectory $BackendRoot `
            -WindowStyle Hidden `
            -RedirectStandardOutput $BackendLog `
            -RedirectStandardError $BackendErrorLog `
            -PassThru
        $backendProcessId = Wait-ForPort $BackendPort 180 'Backend'

        $frontendLauncher = Start-Process `
            -FilePath 'npm.cmd' `
            -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1') `
            -WorkingDirectory $FrontendRoot `
            -WindowStyle Hidden `
            -RedirectStandardOutput $FrontendLog `
            -RedirectStandardError $FrontendErrorLog `
            -PassThru
        $frontendProcessId = Wait-ForPort $FrontendPort 30 'Frontend'

        [ordered]@{
            containerName = $ContainerName
            backendLauncherProcessId = $backendLauncher.Id
            backendProcessId = $backendProcessId
            frontendLauncherProcessId = $frontendLauncher.Id
            frontendProcessId = $frontendProcessId
            startedAt = (Get-Date).ToString('o')
        } | ConvertTo-Json | Set-Content -LiteralPath $StateFile -Encoding utf8

        Write-Host ''
        Write-Host 'AI weekly report manual test environment is ready.' -ForegroundColor Green
        Write-Host "URL      : http://127.0.0.1:$FrontendPort/login"
        Write-Host 'Username : ai_report_tester'
        Write-Host 'Password : password123!'
        Write-Host ("AI       : " + $(if ($RealAi) {
            "REAL OpenAI ($($env:OPENAI_MODEL ?? 'gpt-5.6-luna')) - billed per generation"
        } else { 'deterministic Fake AI (no OpenAI key or network call)' }))
        Write-Host 'Database : disposable MySQL 8.4 on port 13307'
        Write-Host "Logs     : $RuntimeRoot"

        if ($OpenBrowser) {
            Start-Process "http://127.0.0.1:$FrontendPort/login"
        }
    } catch {
        if ($frontendLauncher) { Stop-TrackedProcess $frontendLauncher.Id }
        if ($backendLauncher) { Stop-TrackedProcess $backendLauncher.Id }
        Stop-TrackedProcess (Get-ListenerProcessId $FrontendPort)
        Stop-TrackedProcess (Get-ListenerProcessId $BackendPort)
        if (Test-ContainerExists) { docker stop $ContainerName | Out-Null }
        Write-Host ''
        Write-Host "Backend log: $BackendLog"
        Write-Host "Frontend log: $FrontendLog"
        throw
    }
}

switch ($Action) {
    'start' { Start-Environment }
    'stop' { Stop-Environment }
    'reset' {
        Stop-Environment
        Start-Environment
    }
    'status' { Show-Status }
}
