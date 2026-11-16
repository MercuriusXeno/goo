# Build slots: the builds of one repository on one machine share a fixed number of slots, and a build past them
# waits in arrival order until one frees.
#   pwsh -NoProfile -File tools/build-slots.ps1 reserve [-HolderPid <id>] [-Runs <what the build runs>]
#   pwsh -NoProfile -File tools/build-slots.ps1 release [-HolderPid <id>]
#   pwsh -NoProfile -File tools/build-slots.ps1 read
# Decision one-tools-script-reserves-slots-and-reads-the-queue
#
# A slot belongs to a holder process, the parent of this script unless -HolderPid names another, and stays held
# until release frees it or the holder ends. reserve returns once the holder holds a slot; while it waits it prints
# its place in the queue each time that place changes. read prints each slot and each waiter in queue order.
# -Runs, or BUILD_SLOT_RUNS, says what the build runs; gradlew and gradlew.bat reserve through here for every run.
#
# The store is one per repository: git's common directory is the same for the main checkout and every worktree,
# so every worktree counts against the same slots. -Store or BUILD_SLOT_STORE names another store, and -Slots or
# BUILD_SLOT_COUNT another slot count.
#
# Every Gradle run pays for two calls of this script, so it calls .NET and the language alone: a cmdlet from an
# autoloaded module costs a module load per call.
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('reserve', 'release', 'read')]
    [string] $Verb,
    [int] $HolderPid,
    [string] $Runs = $env:BUILD_SLOT_RUNS,
    [string] $Store,
    [int] $Slots
)

$ErrorActionPreference = 'Stop'

$pollMilliseconds = 500
$lockWaitSeconds = 30
$fieldNames = @('state', 'slot', 'holderPid', 'holderStarted', 'waiterPid', 'waiterStarted', 'worktree', 'branch', 'since', 'runs')

function Resolve-StorePath {
    if ($Store) { return $Store }
    if ($env:BUILD_SLOT_STORE) { return $env:BUILD_SLOT_STORE }
    $common = (git rev-parse --path-format=absolute --git-common-dir 2>$null)
    if (-not $common) {
        throw 'no slot store: run inside a git repository, or name one with -Store or BUILD_SLOT_STORE'
    }
    [IO.Path]::Combine([IO.Path]::GetFullPath($common), 'build-slots')
}

function Resolve-SlotCount {
    if ($Slots -gt 0) { return $Slots }
    if ($env:BUILD_SLOT_COUNT) { return [int]$env:BUILD_SLOT_COUNT }
    4
}

function Find-Process([int] $id) {
    try { [Diagnostics.Process]::GetProcessById($id) } catch { $null }
}

function Get-ProcessStarted([int] $id) {
    try { (Find-Process $id).StartTime.ToUniversalTime().Ticks.ToString() } catch { '' }
}

# The start time guards against a reused process id: a new process under a dead holder's id holds nothing.
function Test-ProcessRuns([int] $id, [string] $started) {
    $process = Find-Process $id
    if (-not $process -or $process.HasExited) { return $false }
    if (-not $started) { return $true }
    try { $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $started } catch { $true }
}

function Get-RunPlace {
    $top = (git rev-parse --show-toplevel 2>$null)
    $worktree = if ($top) { [IO.Path]::GetFullPath($top) } else { [Environment]::CurrentDirectory }
    $branch = (git branch --show-current 2>$null)
    @{ Worktree = $worktree; Branch = $(if ($branch) { $branch } else { 'no branch' }) }
}

# The queue file is its own lock: a caller holds it open with no sharing while it reads and rewrites the queue, so
# two reserves arriving together never both take the last slot.
function Open-QueueFile([string] $storePath) {
    $null = [IO.Directory]::CreateDirectory($storePath)
    $path = [IO.Path]::Combine($storePath, 'queue.tsv')
    $deadline = [DateTime]::UtcNow.AddSeconds($lockWaitSeconds)
    while ($true) {
        try { return [IO.File]::Open($path, 'OpenOrCreate', 'ReadWrite', 'None') }
        catch {
            if ([DateTime]::UtcNow -gt $deadline) { throw "the slot queue at $path stayed locked for $lockWaitSeconds s: $_" }
            [Threading.Thread]::Sleep(25)
        }
    }
}

# One entry per line, its fields tab separated, holders and waiters in queue order.
function Read-Entries([IO.Stream] $stream) {
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false), $false, 4096, $true)
    try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
    foreach ($line in $text -split "`n") {
        $values = $line.TrimEnd("`r") -split "`t"
        if ($values.Count -ne $fieldNames.Count) { continue }
        $entry = [ordered]@{}
        for ($i = 0; $i -lt $fieldNames.Count; $i++) { $entry[$fieldNames[$i]] = $values[$i] }
        $entry
    }
}

function Write-Entries([IO.Stream] $stream, [object[]] $entries) {
    $lines = foreach ($entry in $entries) {
        (@(foreach ($name in $fieldNames) { ([string]$entry[$name]) -replace "[`t`r`n]", ' ' })) -join "`t"
    }
    $stream.SetLength(0)
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false), 4096, $true)
    try { $writer.Write((@($lines) -join "`n")) } finally { $writer.Dispose() }
}

function Test-EntryLives([object] $entry) {
    if (-not (Test-ProcessRuns $entry.holderPid $entry.holderStarted)) { return $false }
    $entry.state -eq 'held' -or (Test-ProcessRuns $entry.waiterPid $entry.waiterStarted)
}

function Grant-FreeSlots([object] $queue, [int] $count) {
    while ($queue.Waiters.Count -gt 0) {
        $taken = @(foreach ($holder in $queue.Holders) { [int]$holder.slot })
        $free = @(foreach ($slot in 1..$count) { if ($taken -notcontains $slot) { $slot } })
        if ($free.Count -eq 0) { return }
        $next = $queue.Waiters[0]
        $queue.Waiters = @($queue.Waiters | Where-Object { $_ -ne $next })
        $next.state = 'held'
        $next.slot = [string]$free[0]
        $queue.Holders = @($queue.Holders) + $next
    }
}

# Every verb passes through here, so each one drops the entries of ended processes and hands freed slots to the
# waiters in arrival order before it answers.
function Update-Queue([scriptblock] $change) {
    $count = Resolve-SlotCount
    $stream = Open-QueueFile (Resolve-StorePath)
    try {
        $live = @(Read-Entries $stream | Where-Object { Test-EntryLives $_ })
        $queue = @{
            SlotCount = $count
            Holders = @($live | Where-Object { $_.state -eq 'held' })
            Waiters = @($live | Where-Object { $_.state -eq 'waiting' })
        }
        & $change $queue
        Grant-FreeSlots $queue $count
        Write-Entries $stream (@($queue.Holders) + @($queue.Waiters))
        $queue
    }
    finally { $stream.Dispose() }
}

function Find-HeldSlot([object] $queue, [string] $id, [string] $started) {
    foreach ($holder in $queue.Holders) {
        if ($holder.holderPid -eq $id -and $holder.holderStarted -eq $started) { return $holder }
    }
}

function Find-QueuePosition([object] $queue, [string] $waiterId) {
    for ($i = 0; $i -lt $queue.Waiters.Count; $i++) {
        if ($queue.Waiters[$i].waiterPid -eq $waiterId) { return $i + 1 }
    }
    0
}

function Resolve-HolderPid {
    if ($HolderPid -gt 0) { return $HolderPid }
    [Diagnostics.Process]::GetCurrentProcess().Parent.Id
}

function Invoke-Reserve {
    $holder = [string](Resolve-HolderPid)
    $holderStarted = Get-ProcessStarted $holder
    $place = Get-RunPlace
    $entry = [ordered]@{
        state = 'waiting'; slot = '0'; holderPid = $holder; holderStarted = $holderStarted
        waiterPid = [string]$PID; waiterStarted = (Get-ProcessStarted $PID)
        worktree = $place.Worktree; branch = $place.Branch; since = [DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss')
        runs = $Runs
    }
    $queue = Update-Queue {
        param($queue)
        # A holder reserving again keeps the slot it holds: one build takes one slot.
        if (-not (Find-HeldSlot $queue $holder $holderStarted)) { $queue.Waiters = @($queue.Waiters) + $entry }
    }
    $shownPosition = 0
    while ($true) {
        $held = Find-HeldSlot $queue $holder $holderStarted
        if ($held) { return 'holding build slot {0}' -f $held.slot }
        $position = Find-QueuePosition $queue $PID
        if ($position -eq 0) { throw "process $holder ended before a build slot freed for it" }
        if ($position -ne $shownPosition) {
            'waiting for a build slot: position {0} in the queue' -f $position
            $shownPosition = $position
        }
        [Threading.Thread]::Sleep($pollMilliseconds)
        $queue = Update-Queue { param($queue) }
    }
}

function Invoke-Release {
    $holder = [string](Resolve-HolderPid)
    $freed = [Collections.Generic.List[object]]::new()
    $null = Update-Queue {
        param($queue)
        foreach ($held in $queue.Holders) { if ($held.holderPid -eq $holder) { $freed.Add($held) } }
        $queue.Holders = @($queue.Holders | Where-Object { $_.holderPid -ne $holder })
        $queue.Waiters = @($queue.Waiters | Where-Object { $_.holderPid -ne $holder })
    }
    if ($freed.Count -eq 0) { return 'process {0} held no build slot' -f $holder }
    foreach ($held in $freed) { 'released build slot {0}' -f $held.slot }
}

function Invoke-Read {
    $queue = Update-Queue { param($queue) }
    'build slots: {0} of {1} held, {2} waiting' -f $queue.Holders.Count, $queue.SlotCount, $queue.Waiters.Count
    $highest = $queue.SlotCount
    foreach ($held in $queue.Holders) { if ([int]$held.slot -gt $highest) { $highest = [int]$held.slot } }
    foreach ($slot in 1..$highest) {
        $held = $null
        foreach ($holder in $queue.Holders) { if ([int]$holder.slot -eq $slot) { $held = $holder } }
        if ($held) { 'slot {0}: held since {1} by {2} on {3}, running {4}' -f $slot, $held.since, $held.worktree, $held.branch, $held.runs }
        else { 'slot {0}: free' -f $slot }
    }
    for ($i = 0; $i -lt $queue.Waiters.Count; $i++) {
        $waiter = $queue.Waiters[$i]
        'waiting {0}: since {1}, {2} on {3}, running {4}' -f ($i + 1), $waiter.since, $waiter.worktree, $waiter.branch, $waiter.runs
    }
}

switch ($Verb) {
    'reserve' { Invoke-Reserve }
    'release' { Invoke-Release }
    'read' { Invoke-Read }
}
