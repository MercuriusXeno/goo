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

    $config = Get-Content (Find-FleetConfig) -Raw | ConvertFrom-Json
    $base = (git -C $repo merge-base HEAD "origin/$($config.integrationBranch)" 2>$null)
    if (-not $base) { return @() }
    @(git -C $repo diff --name-only $base HEAD)
}

# .fleetview.json is gitignored, so a worktree holds none of its own: the main checkout's copy, found through git's
# common directory, names the integration branch for every worktree.
function Find-FleetConfig {
    $own = Join-Path $repo '.fleetview.json'
    if (Test-Path $own) { return $own }
    $common = (git -C $repo rev-parse --path-format=absolute --git-common-dir 2>$null)
    if ($common) {
        $main = Join-Path (Split-Path -Parent $common) '.fleetview.json'
        if (Test-Path $main) { return $main }
    }
    throw "no .fleetview.json in $repo or its main checkout, so the gate cannot name the integration branch"
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
    # The run builds in the worktree's daemon, which dies idle (decisions daemon-back-on, idle-daemon-dies-in-minutes).
    # sca carries the analysis check no longer runs, so the digest still counts it (decision analysis-runs-at-the-gate-only).
    # Gradle starts with none of the gate's handles and writes to files of this run's own: a daemon the build starts
    # inherits every handle its launcher holds and outlives it, and holding the caller's pipe it kept the gate's output
    # open until it died idle, so a caller reading the gate to its end waited minutes past the verdict. A process
    # started through the shell inherits no handle, so the hidden cmd redirects Gradle to the spool files itself
    # (operator ruling: a gate run never jams the flow of work).
    $spool = Join-Path ([IO.Path]::GetTempPath()) ('goo-gate-' + [guid]::NewGuid().ToString('n'))
    $outFile = "$spool.out"
    $errFile = "$spool.err"
    try {
        $command = '/d /c ""{0}" check sca --console=plain > "{1}" 2> "{2}""' -f $gradlew, $outFile, $errFile
        $gradle = Start-Process -FilePath 'cmd.exe' -ArgumentList $command -WorkingDirectory $repo `
            -WindowStyle Hidden -PassThru
        $null = $gradle.Handle
        $gradle.WaitForExit()
        $said = @(Get-Content $outFile, $errFile -ErrorAction SilentlyContinue | ForEach-Object { [string]$_ })
        [pscustomobject]@{ Green = $gradle.ExitCode -eq 0; Output = $said }
    }
    finally {
        # A daemon still holding a spool file keeps it until it dies; the temp folder takes it then.
        Remove-Item $outFile, $errFile -ErrorAction SilentlyContinue
    }
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

# A run that reported itself running always reports its end: every path below sets the verdict and leaves through the
# finally, and a throw anywhere reads red with its cause, so no failure can leave the gate holding a run that never
# ends (operator ruling: a failed gate run never jams the flow of work).
$status = 'red'
$exitCode = 1
$ranLegs = $null
try {
    if ($reports) {
        Send-GateRun -RunId $runId -Script 'tools/gate.ps1' -Cwd $repo -Status 'running' -Commit $commit | Out-Null
    }
    # A named leg the gate does not hold grades nothing, so the run is red rather than empty.
    $unknownLegs = @(Split-LegNames $Legs | Where-Object { $legOrder -notcontains $_ })
    if ($unknownLegs.Count -gt 0) {
        Write-Host ('gate: FAILED, no leg is named {0}; the legs are {1}' -f ($unknownLegs -join ', '),
            ($legOrder -join ', '))
    }
    else {
        $wantedLegs = Get-WantedLegs
        if ($wantedLegs.Count -eq 0) {
            Write-Host 'gate: no leg ran, because nothing this change touched feeds one'
            $status = 'green'
            $exitCode = 0
        }
        else {
            $results = @(foreach ($name in $wantedLegs) { Invoke-Leg $name })
            $ranLegs = $results
            foreach ($result in $results) {
                Write-Host ('gate: {0} {1} {2:n1}s {3}' -f $result.Name, $result.Verdict, $result.Seconds, $result.Rung)
            }
            $failed = @($results | Where-Object { $_.Verdict -ne 'PASS' })
            foreach ($result in $failed | Where-Object { $_.Output.Count -gt 0 }) { Write-Replay $result }
            if ($failed.Count -eq 0) {
                Write-Host 'gate: all green'
                $status = 'green'
                $exitCode = 0
            }
            else {
                Write-Host ('gate: FAILED {0}' -f (($failed | ForEach-Object { $_.Name }) -join ', '))
            }
        }
    }
}
catch {
    Write-Host ('gate: FAILED, the gate threw before its verdict: {0}' -f $_)
    $status = 'red'
    $exitCode = 1
}
finally {
    if ($reports) {
        try {
            $ended = @{ RunId = $runId; Script = 'tools/gate.ps1'; Cwd = $repo; Status = $status; Commit = $commit }
            if ($ranLegs) { $ended.Legs = $ranLegs }
            Send-GateRun @ended | Out-Null
        }
        catch {
            Write-Host ('gate: the run ended {0} but the reporter refused its end: {1}' -f $status, $_)
        }
    }
}
exit $exitCode
