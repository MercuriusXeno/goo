# goo quality gate. FleetView starts it through the gate field of .fleetview.json:
#   pwsh -NoProfile -File tools/gate.ps1 -Scope <ChangedFixtures|ChangedWholeProject|Everything> [-Legs a,b]
# and reads one line per leg, "gate: {name} {PASS|FAIL} {seconds}s whole", plus the
# exit code: 0 green, 1 red.
#
# Two legs. "python tests" runs the unittest suites under tools/, and "gradle check"
# runs the Gradle check lifecycle, whose finalizer writes build/reports/digest.txt.# A changed scope runs the legs the changed paths feed: a tools/*.py edit feeds the
# python leg, a Java, resource, config or Gradle edit feeds the gradle leg. Everything,
# the scope a merge fires, runs both. Neither leg narrows, so every leg runs whole.
#
# The legs run one after another and none of them stops the run, so one run names
# every red leg. A leg that found nothing to run is red: a leg grading nothing gates
# nothing.
param(
    [ValidateSet('ChangedFixtures', 'ChangedWholeProject', 'Everything')]
    [string] $Scope = 'Everything',
    [string] $Legs
)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$legOrder = @('python tests', 'gradle check')

# The working tree first, because that is what a session in the middle of a change
# asks about, untracked files included; a clean tree falls back to what this branch
# carries over the integration branch .fleetview.json names.
function Get-ChangedPaths {
    $held = @(git -C $repo status --porcelain --untracked-files=all |
        ForEach-Object { $_.Substring(3).Trim('"') })
    if ($held.Count -gt 0) { return $held }

    $config = Get-Content (Join-Path $repo '.fleetview.json') -Raw | ConvertFrom-Json
    $base = (git -C $repo merge-base HEAD "origin/$($config.integrationBranch)" 2>$null)
    if (-not $base) { return @() }
    @(git -C $repo diff --name-only $base HEAD)
}

function Get-FedLegs([string[]] $changed) {
    $fed = foreach ($path in $changed) {
        $p = $path -replace '\\', '/'
        if ($p -match '^tools/.*\.py$') { 'python tests' }
        elseif ($p -match '^(src|config|gradle)/' -or $p -match '^(build\.gradle|settings\.gradle|gradle\.properties|gradlew(\.bat)?)$') { 'gradle check' }
    }
    @($fed | Select-Object -Unique)
}

function Split-LegNames([string] $legs) {
    if ([string]::IsNullOrWhiteSpace($legs)) { return @() }
    @($legs -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Get-WantedLegs {
    $named = Split-LegNames $Legs
    if ($Scope -eq 'Everything') {
        $wanted = if ($named.Count -gt 0) { $named } else { $legOrder }
    }
    else {
        $wanted = if ($named.Count -gt 0) { $named } else { Get-FedLegs (Get-ChangedPaths) }
    }
    @($legOrder | Where-Object { $wanted -contains $_ })
}

# Zero discovered tests is a failure, not a pass: a suite that ran nothing graded nothing.
function Invoke-PythonTests {
    $said = @(& python -m unittest discover -s tools -p 'test_*.py' 2>&1 | ForEach-Object { [string]$_ })
    $green = $LASTEXITCODE -eq 0 -and -not ($said -match '^Ran 0 tests')
    [pscustomobject]@{ Green = $green; Output = $said }
}

function Invoke-GradleCheck {
    $gradlew = Join-Path $repo $(if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' })
    # A shared daemon outlives the run and stands outside its process tree (diagnose-then-fix-gate-collisions).
    $said = @(& $gradlew check --no-daemon --console=plain 2>&1 | ForEach-Object { [string]$_ })
    [pscustomobject]@{ Green = $LASTEXITCODE -eq 0; Output = $said }
}

function Invoke-Leg([string] $name) {
    Push-Location $repo
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $ran = switch ($name) {
            'python tests' { Invoke-PythonTests }
            'gradle check' { Invoke-GradleCheck }
        }
    }
    catch {
        $ran = [pscustomobject]@{ Green = $false; Output = @([string]$_) }
    }
    finally {
        $watch.Stop()
        Pop-Location
    }
    [pscustomobject]@{
        Name    = $name
        Verdict = if ($ran.Green) { 'PASS' } else { 'FAIL' }
        Seconds = [math]::Round($watch.Elapsed.TotalSeconds, 2)
        Rung    = 'whole'
        Output  = $ran.Output
    }
}

# The tail rather than the head: a runner writes its refusal or its summary last.
function Write-Replay([object] $result) {
    $tail = @($result.Output | Select-Object -Last 60)
    Write-Host ('gate: {0} said {1} lines, the last {2} of them below' -f $result.Name, $result.Output.Count, $tail.Count)
    foreach ($line in $tail) {
        $text = "  $line"
        Write-Host $(if ($text.Length -le 500) { $text } else { $text.Substring(0, 497) + '...' })
    }
}

# The run says itself to the daemon where the fleet's reporter is on the box, so the
# gate tab draws this run beside the ones a merge fired. A box holding no fleet, or a
# session FleetView did not spawn, reports nothing and the verdict is still the run's own.
$fleetRoot = if ($env:FLEET) { $env:FLEET } else { Join-Path $HOME '.fleet' }
$reporter = Join-Path $fleetRoot 'src\gatereport.ps1'
$reports = Test-Path $reporter
if ($reports) { . $reporter }
$runId = [guid]::NewGuid().ToString('n')
$commit = (& git -C $repo rev-parse --short HEAD 2>$null)
if (-not $commit) { $commit = 'unknown' }
if ($reports) {
    Send-GateRun -RunId $runId -Script 'tools/gate.ps1' -Cwd $repo -Status 'running' -Commit $commit | Out-Null
}

# A named leg the gate does not hold grades nothing, so the run is red rather than empty.
$unknownLegs = @(Split-LegNames $Legs | Where-Object { $legOrder -notcontains $_ })
if ($unknownLegs.Count -gt 0) {
    Write-Host ('gate: FAILED, no leg is named {0}; the legs are {1}' -f ($unknownLegs -join ', '), ($legOrder -join ', '))
    if ($reports) {
        Send-GateRun -RunId $runId -Script 'tools/gate.ps1' -Cwd $repo -Status 'red' -Commit $commit | Out-Null
    }
    exit 1
}

$wantedLegs = Get-WantedLegs
if ($wantedLegs.Count -eq 0) {
    Write-Host 'gate: no leg ran, because nothing this change touched feeds one'
    if ($reports) {
        Send-GateRun -RunId $runId -Script 'tools/gate.ps1' -Cwd $repo -Status 'green' -Commit $commit | Out-Null
    }
    exit 0
}

$results = @(foreach ($name in $wantedLegs) { Invoke-Leg $name })
foreach ($result in $results) {
    Write-Host ('gate: {0} {1} {2:n1}s {3}' -f $result.Name, $result.Verdict, $result.Seconds, $result.Rung)
}

$failed = @($results | Where-Object { $_.Verdict -ne 'PASS' })
foreach ($result in $failed | Where-Object { $_.Output.Count -gt 0 }) { Write-Replay $result }

$green = $failed.Count -eq 0
if ($green) { Write-Host 'gate: all green' }
else { Write-Host ('gate: FAILED {0}' -f (($failed | ForEach-Object { $_.Name }) -join ', ')) }

if ($reports) {
    Send-GateRun -RunId $runId -Script 'tools/gate.ps1' -Cwd $repo -Commit $commit `
        -Status $(if ($green) { 'green' } else { 'red' }) -Legs $results | Out-Null
}
if (-not $green) { exit 1 }
