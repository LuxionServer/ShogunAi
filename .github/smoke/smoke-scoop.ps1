param(
    [Parameter(Mandatory)] [string] $Msi,
    [Parameter(Mandatory)] [string] $Version,
    [Parameter(Mandatory)] [string] $DownloadBase,
    [Parameter(Mandatory)] [string] $Work
)

$ErrorActionPreference = 'Stop'
$Render = Join-Path $PSScriptRoot '..\scoop\render-manifest.sh'
$Out = Join-Path $Work 'out'
$Manifest = Join-Path $Out 'shogunai.json'
$ScoopRoot = Join-Path $env:USERPROFILE 'scoop'
$App = Join-Path $ScoopRoot 'apps\shogunai'
$Shim = Join-Path $ScoopRoot 'shims\shogun.cmd'
$Shortcut = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Scoop Apps\ShogunAi.lnk'

function Get-SessionPathWithoutJava {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    $folders = (@($machine, $user) | Where-Object { $_ }) -join ';' -split ';'
    ($folders | Where-Object { $_ -and -not (Test-Path (Join-Path $_ 'java.exe')) }) -join ';'
}

function Invoke-InNewSession([string] $Command) {
    $info = [System.Diagnostics.ProcessStartInfo]::new($env:ComSpec)
    foreach ($argument in @('/d', '/c', $Command)) { $info.ArgumentList.Add($argument) }
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.WorkingDirectory = $Work
    $info.Environment['PATH'] = Get-SessionPathWithoutJava
    $info.Environment.Remove('JAVA_HOME') | Out-Null
    $process = [System.Diagnostics.Process]::Start($info)
    $output = $process.StandardOutput.ReadToEnd()
    $process.WaitForExit()
    [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $output.Trim() }
}

New-Item -ItemType Directory -Force -Path $Out | Out-Null
if (Test-Path $ScoopRoot) { throw "$ScoopRoot already exists, so this isn't a clean machine." }

$installer = Join-Path $Work 'install-scoop.ps1'
Invoke-RestMethod -Uri 'https://get.scoop.sh' -OutFile $installer
& $installer -RunAsAdmin
if (-not (Get-Command scoop -ErrorAction SilentlyContinue)) { throw 'Installing Scoop left no scoop command.' }

$checksum = (Get-FileHash -Algorithm SHA256 $Msi).Hash.ToLowerInvariant()
$rendered = & bash ($Render -replace '\\', '/') $Version $checksum
if ($LASTEXITCODE -ne 0) { throw 'Rendering the Scoop manifest failed.' }
[System.IO.File]::WriteAllLines($Manifest, [string[]] $rendered)
Get-Content $Manifest | Write-Host

$parsed = Get-Content $Manifest -Raw | ConvertFrom-Json
$url = $parsed.architecture.'64bit'.url
$expectedUrl = "$($DownloadBase.TrimEnd('/'))/$(Split-Path $Msi -Leaf)"
if ($url -ne $expectedUrl) { throw "The manifest downloads $url, but the release publishes $expectedUrl." }
Write-Host "ok: the manifest downloads $url"
if ($parsed.architecture.'64bit'.hash -ne $checksum) { throw "The manifest expects sha256 $($parsed.architecture.'64bit'.hash), but $(Split-Path $Msi -Leaf) has $checksum." }
Write-Host "ok: the manifest's sha256 matches $(Split-Path $Msi -Leaf)"

$core = Join-Path $ScoopRoot 'apps\scoop\current\lib\core.ps1'
$cached = & pwsh -NoProfile -Command ". '$core'; cache_path 'shogunai' '$Version' '$url'"
if ($LASTEXITCODE -ne 0 -or -not $cached) { throw "Scoop's cache_path didn't say where it caches $url." }
New-Item -ItemType Directory -Force -Path (Split-Path $cached) | Out-Null
Copy-Item $Msi $cached
Write-Host "Installing the manifest with $(Split-Path $Msi -Leaf) in Scoop's download cache, at $cached."

scoop install $Manifest *>&1 | Tee-Object -FilePath (Join-Path $Out 'scoop-install.log')
if (-not (Test-Path (Join-Path $App 'current\ShogunAi.exe'))) { throw 'scoop install left no ShogunAi.exe.' }
if (-not (Test-Path $Shim)) { throw "scoop install left no $Shim." }
if (-not (Test-Path $Shortcut)) { throw "scoop install left no Start menu shortcut at $Shortcut." }
Write-Host 'ok: the app, the shogun command and the Start menu shortcut were installed'

Write-Host 'Running shogun --version from a new session, with the PATH read again from the registry and no Java on it.'
$printed = Invoke-InNewSession 'shogun --version'
if ($printed.ExitCode -ne 0 -or $printed.Output -ne "shogun $Version") { throw "shogun --version exited with $($printed.ExitCode) and printed '$($printed.Output)', expected 'shogun $Version'." }
Write-Host "ok: $($printed.Output)"

scoop uninstall shogunai *>&1 | Tee-Object -FilePath (Join-Path $Out 'scoop-uninstall.log')
foreach ($left in @($App, $Shim, $Shortcut)) {
    if (Test-Path $left) { throw "scoop uninstall left $left." }
}
$found = Invoke-InNewSession 'where shogun'
if ($found.ExitCode -eq 0) { throw "scoop uninstall left a shogun command: $($found.Output)." }
Write-Host 'ok: the app, the shogun command and the Start menu shortcut were removed'
