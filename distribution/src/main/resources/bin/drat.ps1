# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.

[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet('audit', 'start', 'stop', 'status', 'help')]
    [string] $Command,

    [Parameter(Position = 1, ValueFromRemainingArguments = $true)]
    [string[]] $Arguments,

    [string] $DratHome = (Split-Path -Parent $PSScriptRoot),
    [string] $WorkflowUrl = 'http://localhost:9001'
)

$ErrorActionPreference = 'Stop'
$DratHome = [IO.Path]::GetFullPath($DratHome)
$env:Path = @(
    [Environment]::GetEnvironmentVariable('Path', 'Machine'),
    [Environment]::GetEnvironmentVariable('Path', 'User'),
    $env:Path) -join ';'

function Resolve-Java {
    foreach ($javaHome in @(
        $env:JAVA_HOME,
        [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'),
        [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine'))) {
        if (-not $javaHome) { continue }
        $candidate = Join-Path $javaHome 'bin\java.exe'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) { return $java.Source }
    throw 'Java was not found. Set JAVA_HOME to a JDK installation.'
}

function Set-PythonEnvironment {
    $uv = Get-Command uv.exe -ErrorAction SilentlyContinue
    if ($uv) {
        $python = (& $uv.Source python find 3.11 2>$null | Select-Object -First 1)
        if ($python -and (Test-Path -LiteralPath $python)) {
            $env:PYTHON_EXECUTABLE = '"' + ($python -replace '\\', '/') + '"'
            return
        }
    }
    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($python -and $python.Source -notlike '*\Microsoft\WindowsApps\*') {
        $env:PYTHON_EXECUTABLE = '"' + ($python.Source -replace '\\', '/') + '"'
        return
    }
    throw 'Python 3.11 was not found. Install it with `uv python install 3.11`.'
}

function Invoke-Oodt([string] $OodtCommand) {
    $launcher = Join-Path $DratHome 'bin\oodt.ps1'
    if (-not (Test-Path -LiteralPath $launcher)) {
        throw "The Windows OODT launcher was not found: $launcher"
    }
    & $launcher $OodtCommand -OodtHome $DratHome
    if (-not $?) { exit 1 }
}

function Test-Port([int] $Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.ConnectAsync('127.0.0.1', $Port)
        return $pending.Wait(500) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Assert-Services {
    foreach ($port in 9000, 9001, 9002, 8080, 8983) {
        if (-not (Test-Port $port)) {
            throw "DRAT service port $port is not available. Run 'drat.ps1 start' first."
        }
    }
}

function Convert-Excludes([string[]] $Names) {
    if (-not $Names -or $Names.Count -eq 0) { return $null }
    $escaped = $Names | ForEach-Object { [regex]::Escape($_) }
    # The crawler receives native Windows paths on Windows and slash-separated
    # paths on Unix. Match a complete path component in either representation
    # so the crawl and the repository-size denominator exclude the same files.
    return ".*(^|[\\/])($($escaped -join '|'))([\\/]|`$).*"
}

function Write-Utf8NoBom([string] $Path, [string] $Value) {
    [IO.File]::WriteAllText($Path, $Value + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false))
}

function Set-CurrentRepository([string] $Repository) {
    $dataHome = Join-Path $DratHome 'data'
    New-Item -ItemType Directory -Force -Path $dataHome | Out-Null
    $name = Split-Path -Leaf $Repository
    $record = [ordered]@{
        id = "id:$Repository"
        repo = $Repository
        name = $name
        loc_url = ''
        description = "$name from the command line"
        type = 'project'
    }
    $temporary = Join-Path $dataHome 'repo.tmp'
    Write-Utf8NoBom $temporary ($record | ConvertTo-Json -Compress)
    Move-Item -LiteralPath $temporary -Destination (Join-Path $dataHome 'repo') -Force
}

function Set-RunMarker([string] $Phase, [string] $Repository, [string[]] $Excludes) {
    $dataHome = Join-Path $DratHome 'data'
    New-Item -ItemType Directory -Force -Path $dataHome | Out-Null
    $previousTotal = $null
    $currentMarker = Join-Path $dataHome 'run'
    if (Test-Path -LiteralPath $currentMarker) {
        $previous = Get-Content -Raw -LiteralPath $currentMarker | ConvertFrom-Json
        if ($previous.repo -eq $Repository) {
            $previousTotal = $previous.totalFiles
        }
    }
    if ($null -eq $previousTotal) {
        $rootLength = $Repository.TrimEnd('\').Length
        $previousTotal = @(Get-ChildItem -LiteralPath $Repository -Recurse -File -Force |
            Where-Object {
                $_.Length -gt 0 -and -not (@($_.FullName.Substring($rootLength).TrimStart('\').Split('\')) |
                    Where-Object { $Excludes -contains $_ })
            }).Count
    }
    $record = [ordered]@{
        phase = $Phase
        startedBy = 'cli'
        startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
        repo = $Repository
        excludes = @($Excludes)
        totalFiles = $previousTotal
    }
    $temporary = Join-Path $dataHome 'run.tmp'
    Write-Utf8NoBom $temporary ($record | ConvertTo-Json -Compress)
    Move-Item -LiteralPath $temporary -Destination (Join-Path $dataHome 'run') -Force
}

function Complete-RunMarker([string] $Outcome) {
    $dataHome = Join-Path $DratHome 'data'
    $run = Join-Path $dataHome 'run'
    if (-not (Test-Path -LiteralPath $run)) { return }
    $record = Get-Content -Raw -LiteralPath $run | ConvertFrom-Json
    $record | Add-Member -NotePropertyName outcome -NotePropertyValue $Outcome -Force
    $temporary = Join-Path $dataHome 'last-run.tmp'
    Write-Utf8NoBom $temporary ($record | ConvertTo-Json -Compress)
    Move-Item -LiteralPath $temporary -Destination (Join-Path $dataHome 'last-run') -Force
    Remove-Item -LiteralPath $run -Force
}

function Clear-RepositoryStatistics([string] $Repository) {
    # Historical statistics for other repositories survive a normal reset,
    # but this repository's previous result must not masquerade as progress
    # for the run that is replacing it.
    foreach ($query in @(
        "{!term f=parent}$Repository",
        "{!term f=id}$Repository",
        "{!term f=id}id:$Repository")) {
        $body = @{ delete = @{ query = $query } } | ConvertTo-Json -Compress
        Invoke-WebRequest -UseBasicParsing -Method Post `
            -ContentType 'application/json' -Body $body `
            -Uri 'http://localhost:8983/solr/statistics/update' | Out-Null
    }
    Invoke-WebRequest -UseBasicParsing -Method Post `
        -ContentType 'application/json' -Body '{"commit":{}}' `
        -Uri 'http://localhost:8983/solr/statistics/update' | Out-Null
}

function Reset-LiveState([string] $Repository) {
    Write-Host 'Resetting live DRAT state'
    # Stop producers before clearing their indexes. Otherwise a workflow from
    # the previous run can publish one last document after the delete commits,
    # making a fresh run incorrectly begin at "1 file Indexed".
    Invoke-Oodt 'stop'
    try {
        foreach ($relative in @(
            'filemgr\catalog', 'data\archive', 'data\jobs', 'data\winstdb')) {
            $path = [IO.Path]::GetFullPath((Join-Path $DratHome $relative))
            if (-not $path.StartsWith($DratHome + '\',
                    [StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing to clear a path outside DRAT_HOME: $path"
            }
            if ([IO.Directory]::Exists($path)) {
                [IO.Directory]::Delete($path, $true)
            }
            [void][IO.Directory]::CreateDirectory($path)
        }
    } finally {
        Set-PythonEnvironment
        Invoke-Oodt 'start'
    }

    Clear-RepositoryStatistics $Repository
    $delete = '<delete><query>*:*</query></delete>'
    Invoke-WebRequest -UseBasicParsing -Method Post -ContentType 'text/xml' `
        -Body $delete -Uri 'http://localhost:8983/solr/drat/update?commit=true' |
        Out-Null
}

function Get-WorkflowInstances {
    $instances = [Collections.Generic.List[object]]::new()
    $pageNumber = 1
    do {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/pcs/services/workflow/instances?status=ALL&page=$pageNumber"
        foreach ($instance in @($response.page.instances)) { $instances.Add($instance) }
        $totalPages = [Math]::Max(1, [int]$response.page.totalPages)
        $pageNumber++
    } while ($pageNumber -le $totalPages)
    return $instances
}

function Wait-Audit([Collections.Generic.HashSet[string]] $Before) {
    Write-Host -NoNewline 'Waiting for the audit to finish'
    $waited = 0
    while ($true) {
        $pending = @(Get-WorkflowInstances | Where-Object {
            -not $Before.Contains([string]$_.id) -and
            [string]::IsNullOrWhiteSpace([string]$_.endDateTime)
        }).Count
        if ($pending -eq 0 -and $waited -gt 0) {
            Write-Host "`nThe audit finished."
            return
        }
        Start-Sleep -Seconds 2
        $waited += 2
        Write-Host -NoNewline '.'
    }
}

function Invoke-WorkflowClient([string[]] $ClientArguments) {
    $java = Resolve-Java
    $workflowHome = Join-Path $DratHome 'workflow'
    $actions = ([Uri](Join-Path $workflowHome 'policy\cmd-line-actions.xml')).AbsoluteUri
    $options = ([Uri](Join-Path $workflowHome 'policy\cmd-line-options.xml')).AbsoluteUri
    $javaArgs = @(
        "-Dlog4j.configurationFile=$(Join-Path $workflowHome 'etc\log4j2.xml')",
        "-Djava.util.logging.config.file=$(Join-Path $workflowHome 'etc\logging.properties')",
        "-Dorg.apache.oodt.cas.cli.action.spring.config=$actions",
        "-Dorg.apache.oodt.cas.cli.option.spring.config=$options",
        '-cp', (Join-Path $workflowHome 'lib\*'),
        'org.apache.oodt.cas.workflow.system.WorkflowManagerClientStarter') + $ClientArguments
    Push-Location (Join-Path $workflowHome 'bin')
    try {
        $output = @(& $java @javaArgs 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $output | ForEach-Object { Write-Host $_ }
    if ($exitCode -ne 0) { throw "Workflow client failed with exit code $exitCode." }
    if ($output -match '(?m)^ERROR:\s') {
        throw 'The Workflow Manager rejected the request. See the error above.'
    }
}

function Start-Audit([string[]] $AuditArguments) {
    Assert-Services
    $excludes = [Collections.Generic.List[string]]::new()
    $repository = $null
    for ($i = 0; $i -lt $AuditArguments.Count; $i++) {
        if ($AuditArguments[$i] -eq '--exclude') {
            if (++$i -ge $AuditArguments.Count) { throw "Expected a value after '--exclude'." }
            $AuditArguments[$i].Split(',', [StringSplitOptions]::RemoveEmptyEntries) |
                ForEach-Object { $excludes.Add($_) }
        } elseif ($repository) {
            throw "Unexpected argument: $($AuditArguments[$i])"
        } else {
            $repository = $AuditArguments[$i]
        }
    }
    if (-not $repository) { throw "Expected a repository path for 'audit'." }
    $repository = (Resolve-Path -LiteralPath $repository).Path
    try {
        Set-RunMarker 'reset' $repository $excludes
        Reset-LiveState $repository
        Set-CurrentRepository $repository

        $before = [Collections.Generic.HashSet[string]]::new()
        Get-WorkflowInstances | ForEach-Object { [void]$before.Add([string]$_.id) }
        Set-RunMarker 'audit' $repository $excludes

        $clientArgs = @('--url', $WorkflowUrl, '--operation', '--sendEvent',
            '--eventName', 'urn:drat:AuditPipeline', '--metaData',
            '--key', 'ProductPath', $repository)
        $excludePattern = Convert-Excludes $excludes
        if ($excludePattern) {
            Write-Host "Excluding path components: $($excludes -join ' ')"
            $clientArgs += @('--key', 'SysProp.DRAT_EXCLUDE', $excludePattern)
        }
        Invoke-WorkflowClient $clientArgs
        Write-Host "Audit submitted for $repository"
        Write-Host 'Follow it at http://localhost:8080/proteus/'
        Wait-Audit $before
        Complete-RunMarker 'finished'
    } catch {
        Complete-RunMarker 'aborted'
        throw
    }
}

function Show-Help {
    @'
Usage: drat.ps1 <command>

  start                         Start the DRAT services
  stop                          Stop the DRAT services
  status                        Show service status
  audit [--exclude NAME] PATH   Submit a repository audit
  help                          Show this help
'@ | Write-Host
}

switch ($Command) {
    'start' { Set-PythonEnvironment; Invoke-Oodt 'start' }
    'stop' { Invoke-Oodt 'stop' }
    'status' { Invoke-Oodt 'status' }
    'audit' { Start-Audit $Arguments }
    'help' { Show-Help }
}
