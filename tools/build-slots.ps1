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
#
# The store is one per repository: git's common directory is the same for the main checkout and every worktree,
# so every worktree counts against the same slots. -Store or BUILD_SLOT_STORE names another store, and -Slots or
# BUILD_SLOT_COUNT another slot count.
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('reserve', 'release', 'read')]
    [string] $Verb,
    [int] $HolderPid,
    [string] $Runs = '',
    [string] $Store,
    [int] $Slots
)

$ErrorActionPreference = 'Stop'

$pollMilliseconds = 500
$lockWaitSeconds = 30

function Resolve-StorePath {
    if ($Store) { return $Store }
    if ($env:BUILD_SLOT_STORE) { return $env:BUILD_SLOT_STORE }
    $common = (git rev-parse --path-format=absolute --git-common-dir 2>$null)
    if (-not $common) {
        throw 'no slot store: run inside a git repository, or name one with -Store or BUILD_SLOT_STORE'
    }
    Join-Path ([IO.Path]::GetFullPath($common)) 'build-slots'
}

function Resolve-SlotCount {
    if ($Slots -gt 0) { return $Slots }
    if ($env:BUILD_SLOT_COUNT) { return [int]$env:BUILD_SLOT_COUNT }
    4
}

function Get-ProcessStarted([int] $id) {
    try { (Get-Process -Id $id).StartTime.ToUniversalTime().Ticks.ToString() }
    catch { '' }
}

# The start time guards against a reused process id: a new process under a dead holder's id holds nothing.
function Test-ProcessRuns([int] $id, [string] $started) {
    $process = Get-Process -Id $id -ErrorAction SilentlyContinue
    if (-not $process -or $process.HasExited) { return $false }
    if (-not $started) { return $true }
    try { $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $started }
    catch { $true }
}

function Get-RunPlace {
    $top = (git rev-parse --show-toplevel 2>$null)
    $worktree = if ($top) { [IO.Path]::GetFullPath($top) } else { (Get-Location).Path }
    $branch = (git branch --show-current 2>$null)
    [pscustomobject]@{ Worktree = $worktree; Branch = $(if ($branch) { $branch } else { 'no branch' }) }
}

# The queue file is its own lock: a caller holds it open with no sharing while it reads and rewrites the queue, so
# two reserves arriving together never both take the last slot.
function Open-QueueFile([string] $storePath) {
    $null = New-Item -ItemType Directory -Force -Path $storePath
    $path = Join-Path $storePath 'queue.json'
    $deadline = [DateTime]::UtcNow.AddSeconds($lockWaitSeconds)
    while ($true) {
        try { return [IO.File]::Open($path, 'OpenOrCreate', 'ReadWrite', 'None') }
        catch {
            if ([DateTime]::UtcNow -gt $deadline) { throw "the slot queue at $path stayed locked for $lockWaitSeconds s: $_" }
            Start-Sleep -Milliseconds 25
        }
    }
}

function Read-QueueText([IO.Stream] $stream) {
    $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false), $false, 4096, $true)
    try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Write-QueueText([IO.Stream] $stream, [string] $text) {
    $stream.SetLength(0)
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false), 4096, $true)
    try { $writer.Write($text) } finally { $writer.Dispose() }
}

function Remove-EndedEntries([object] $queue) {
    $queue.holders = @($queue.holders | Where-Object { Test-ProcessRuns $_.holderPid $_.holderStarted })
    $queue.waiters = @($queue.waiters | Where-Object {
            (Test-ProcessRuns $_.waiterPid $_.waiterStarted) -and (Test-ProcessRuns $_.holderPid $_.holderStarted)
        })
}

function Grant-FreeSlots([object] $queue, [int] $count) {
    while ($queue.waiters.Count -gt 0) {
        $taken = @($queue.holders | ForEach-Object { [int]$_.slot })
        $free = @(1..$count | Where-Object { $taken -notcontains $_ })
        if ($free.Count -eq 0) { return }
        $next = $queue.waiters[0]
        $queue.waiters = @($queue.waiters | Select-Object -Skip 1)
        $queue.holders = @($queue.holders) + [pscustomobject]@{
            slot = $free[0]; holderPid = $next.holderPid; holderStarted = $next.holderStarted
            worktree = $next.worktree; branch = $next.branch; runs = $next.runs; since = $next.since
        }
    }
}

# Every verb passes through here, so each one drops the entries of ended processes and hands freed slots to the
# waiters in arrival order before it answers.
function Update-Queue([scriptblock] $change) {
    $storePath = Resolve-StorePath
    $count = Resolve-SlotCount
    $stream = Open-QueueFile $storePath
    try {
        $text = Read-QueueText $stream
        $queue = if ($text.Trim()) { $text | ConvertFrom-Json } else { [pscustomobject]@{ holders = @(); waiters = @() } }
        Remove-EndedEntries $queue
        & $change $queue
        Grant-FreeSlots $queue $count
        Write-QueueText $stream ($queue | ConvertTo-Json -Depth 5)
        $queue | Add-Member -NotePropertyName count -NotePropertyValue $count -PassThru
    }
    finally { $stream.Dispose() }
}

function Find-HeldSlot([object] $queue, [int] $id, [string] $started) {
    $queue.holders | Where-Object { $_.holderPid -eq $id -and $_.holderStarted -eq $started } | Select-Object -First 1
}

function Find-QueuePosition([object] $queue, [int] $waiterId) {
    for ($i = 0; $i -lt $queue.waiters.Count; $i++) {
        if ($queue.waiters[$i].waiterPid -eq $waiterId) { return $i + 1 }
    }
    0
}

function Resolve-HolderPid {
    if ($HolderPid -gt 0) { return $HolderPid }
    (Get-Process -Id $PID).Parent.Id
}

function Invoke-Reserve {
    $holder = Resolve-HolderPid
    $holderStarted = Get-ProcessStarted $holder
    $waiterStarted = Get-ProcessStarted $PID
    $place = Get-RunPlace
    $entry = [pscustomobject]@{
        waiterPid = $PID; waiterStarted = $waiterStarted; holderPid = $holder; holderStarted = $holderStarted
        worktree = $place.Worktree; branch = $place.Branch; runs = $Runs; since = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    }
    $queue = Update-Queue {
        param($queue)
        # A holder reserving again keeps the slot it holds: one build takes one slot.
        if (-not (Find-HeldSlot $queue $holder $holderStarted)) { $queue.waiters = @($queue.waiters) + $entry }
    }
    $shownPosition = 0
    while ($true) {
        $held = Find-HeldSlot $queue $holder $holderStarted
        if ($held) {
            Write-Output ('holding build slot {0}' -f $held.slot)
            return
        }
        $position = Find-QueuePosition $queue $PID
        if ($position -eq 0) { throw "process $holder ended before a build slot freed for it" }
        if ($position -ne $shownPosition) {
            Write-Output ('waiting for a build slot: position {0} in the queue' -f $position)
            $shownPosition = $position
        }
        Start-Sleep -Milliseconds $pollMilliseconds
        $queue = Update-Queue { param($queue) }
    }
}

function Invoke-Release {
    $holder = Resolve-HolderPid
    $freed = [Collections.Generic.List[object]]::new()
    $null = Update-Queue {
        param($queue)
        $queue.holders | Where-Object { $_.holderPid -eq $holder } | ForEach-Object { $freed.Add($_) }
        $queue.holders = @($queue.holders | Where-Object { $_.holderPid -ne $holder })
        $queue.waiters = @($queue.waiters | Where-Object { $_.holderPid -ne $holder })
    }
    if ($freed.Count -eq 0) { Write-Output ('process {0} held no build slot' -f $holder) }
    foreach ($slot in $freed) { Write-Output ('released build slot {0}' -f $slot.slot) }
}

function Invoke-Read {
    $queue = Update-Queue { param($queue) }
    Write-Output ('build slots: {0} of {1} held, {2} waiting' -f $queue.holders.Count, $queue.count, $queue.waiters.Count)
    $highest = [Math]::Max($queue.count, [int]($queue.holders | ForEach-Object { [int]$_.slot } | Measure-Object -Maximum).Maximum)
    foreach ($slot in 1..$highest) {
        $held = $queue.holders | Where-Object { [int]$_.slot -eq $slot } | Select-Object -First 1
        if ($held) {
            Write-Output ('slot {0}: held since {1} by {2} on {3}, running {4}' -f $slot, $held.since, $held.worktree, $held.branch, $held.runs)
        }
        else { Write-Output ('slot {0}: free' -f $slot) }
    }
    for ($i = 0; $i -lt $queue.waiters.Count; $i++) {
        $waiter = $queue.waiters[$i]
        Write-Output ('waiting {0}: since {1}, {2} on {3}, running {4}' -f ($i + 1), $waiter.since, $waiter.worktree, $waiter.branch, $waiter.runs)
    }
}

switch ($Verb) {
    'reserve' { Invoke-Reserve }
    'release' { Invoke-Release }
    'read' { Invoke-Read }
}
