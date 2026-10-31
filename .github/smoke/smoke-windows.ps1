param(
    [Parameter(Mandatory)] [string] $Msi,
    [Parameter(Mandatory)] [string] $Work
)

$ErrorActionPreference = 'Stop'
$Scripts = $PSScriptRoot
$Out = Join-Path $Work 'out'
$Repository = Join-Path $Work 'main-repo'
$Report = Join-Path $Out 'report.json'
$ShimFolder = Join-Path $env:USERPROFILE '.shogunai\bin'
$Failures = 0

function Get-SessionPath {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    (@($machine, $user) | Where-Object { $_ }) -join ';'
}

function Start-WithSessionPath([string] $FilePath, [string[]] $Arguments, [hashtable] $Variables, [string] $Folder) {
    $info = [System.Diagnostics.ProcessStartInfo]::new($FilePath)
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.WorkingDirectory = $Folder
    $info.Environment['PATH'] = Get-SessionPath
    foreach ($name in $Variables.Keys) { $info.Environment[$name] = $Variables[$name] }
    $process = [System.Diagnostics.Process]::Start($info)
    $output = $process.StandardOutput.ReadToEnd()
    $process.WaitForExit()
    [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $output }
}

function Expect-Report($Actual, $Expected, [string] $Name) {
    if ("$Actual" -eq "$Expected") {
        Write-Host "ok: $Name = $Expected"
    } else {
        Write-Host "::error::Smoke test report: $Name is '$Actual', expected '$Expected'."
        $script:Failures++
    }
}

function Copy-Logs {
    $logs = Join-Path $env:USERPROFILE '.shogunai\logs'
    if (Test-Path $logs) { Copy-Item $logs (Join-Path $Out 'app-logs') -Recurse -Force }
}

New-Item -ItemType Directory -Force -Path $Out | Out-Null
if (Test-Path (Join-Path $env:USERPROFILE '.shogunai')) { throw "$env:USERPROFILE\.shogunai already exists, so this isn't a clean machine." }

try {
    $install = Start-Process msiexec.exe -ArgumentList @('/i', "`"$Msi`"", '/qn', '/l*v', "`"$(Join-Path $Out 'msi.log')`"") -Wait -PassThru
    if ($install.ExitCode -ne 0) { throw "msiexec exited with $($install.ExitCode); see msi.log." }
    $app = Get-ChildItem -Path $env:ProgramFiles, ${env:ProgramFiles(x86)}, (Join-Path $env:LOCALAPPDATA 'Programs') -Filter 'ShogunAi.exe' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $app) { throw 'The .msi installed no ShogunAi.exe.' }
    Write-Host "Installed $($app.FullName) from $(Split-Path $Msi -Leaf)."

    & bash (Join-Path $Scripts 'prepare-repository.sh') ($Repository -replace '\\', '/')
    if ($LASTEXITCODE -ne 0) { throw 'Preparing the sample repository failed.' }

    Write-Host "Starting the app with the PATH a new session reads from the registry."
    $run = Start-WithSessionPath $app.FullName @() @{
        SHOGUNAI_SMOKE_TEST_REPORT = $Report
        SHOGUNAI_SMOKE_TEST_REPOSITORY = $Repository
        SHOGUNAI_SMOKE_TEST_LOCAL_FILES = '.env.local'
    } $Work
    Write-Host "The app exited with $($run.ExitCode)."
    if (-not (Test-Path $Report)) { throw "The app exited without writing $Report." }
    Get-Content $Report | Write-Host

    $result = Get-Content $Report -Raw | ConvertFrom-Json
    $steps = @{}
    foreach ($step in $result.steps) { $steps[$step.name] = $step }
    Expect-Report $result.passed 'True' 'passed'
    Expect-Report $steps['install-command'].status 'passed' 'install-command'
    Expect-Report $result.commandLineTool.state 'INSTALLED_NOT_ON_PATH' 'commandLineTool.state'
    Expect-Report $result.commandLineTool.restartNeeded 'True' 'commandLineTool.restartNeeded'
    Expect-Report $result.commandLineTool.folder $ShimFolder 'commandLineTool.folder'
    Expect-Report $result.hooks.state 'INSTALLED' 'hooks.state'
    Expect-Report $result.tour.reached 'OPEN_AGENT' 'tour.reached'
    Expect-Report ($result.worktree.copiedFiles -join ',') '.env.local,config/local.json' 'worktree.copiedFiles'
    foreach ($step in $result.steps | Where-Object { $_.status -eq 'failed' }) {
        Write-Host "::error::Step $($step.name) failed: $($step.detail)"
    }
    if ($Failures -gt 0) { throw 'The smoke test report doesn''t match this machine.' }

    $sessionPath = Get-SessionPath
    if (-not ($sessionPath -split ';' | Where-Object { $_.TrimEnd('\') -ieq $ShimFolder })) { throw "$ShimFolder isn't on the user's PATH in the registry." }

    Write-Host 'Running shogun new from a new session, with the PATH read again from the registry.'
    $created = Start-WithSessionPath $env:ComSpec @('/d', '/c', 'shogun new smoke-cli --json') @{} $Repository
    Write-Host $created.Output
    if ($created.ExitCode -ne 0) { throw "shogun new exited with $($created.ExitCode)." }
    $worktree = ($created.Output | ConvertFrom-Json).path
    & bash (Join-Path $Scripts 'check-worktree.sh') ($Repository -replace '\\', '/') ($worktree -replace '\\', '/')
    if ($LASTEXITCODE -ne 0) { throw 'The worktree created by shogun new is missing local files.' }
} finally {
    Copy-Logs
}
