"""Proofs that tools/gate.ps1 takes its Gradle leg's build slot itself and says when the leg waits and when it runs:
run with `python -m unittest tools.test_gate_waiting` from the repo root.

Each test copies goo's gradlew, gradlew.bat, gradle/wrapper, tools/build-slots.ps1 and tools/gate.ps1 into a small
temp build with a small heap, and points FLEET at a temp folder whose src/gatereport.ps1 records every Send-GateRun
call, so a test drives the real gate through the real wrappers without reaching goo's own build, the machine's slot
queue or the FleetView daemon."""

import json
import re
import shutil
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_build_slots import WAIT_SECONDS, RunningReserve, SlotQueueHarness, held_line, slot_environment  # noqa: E402
from test_gradle_slots import GRADLE_WAIT_SECONDS, PROBE_PROPERTIES, REPO, wait_for_file  # noqa: E402

GATE_RUNS = "tools/gate.ps1 gradle check"
WAITING_LINE = "gate: gradle check WAITING 0.0s whole"
RUNNING_LINE = "gate: gradle check RUNNING 0.0s whole"
SETTLED_LINE = r"^gate: gradle check PASS \d+\.\d+s whole$"

PROBE_BUILD = """
// check stands in for goo's check lifecycle: it writes its daemon's process id, then runs until released.flag
// appears. sca stands in for the analysis the gate runs beside it.
tasks.register('check') {
    def released = file('released.flag')
    def daemonPid = file('daemon.pid')
    doLast {
        daemonPid.text = ProcessHandle.current().pid().toString()
        while (!released.exists()) { Thread.sleep(100) }
    }
}
tasks.register('sca')
"""

RECORDING_REPORTER = """
# Records each report the gate sends about itself, one JSON line per call, beside this file.
function Send-GateRun {
    param([string] $RunId, [string] $Script, [string] $Cwd, [string] $Status, [object[]] $Legs = @(),
        [string] $Commit, [string] $RunScope, [string] $RunLegs)
    $legs = @($Legs | ForEach-Object { [pscustomobject]@{ name = $_.Name; verdict = $_.Verdict } })
    $call = [pscustomobject]@{ status = $Status; legs = $legs }
    Add-Content -Path (Join-Path $PSScriptRoot 'calls.jsonl') -Value ($call | ConvertTo-Json -Compress -Depth 4)
    $true
}
"""


@unittest.skipUnless(sys.platform == "win32", "drives gradlew.bat")
class GateGradleLegShould(SlotQueueHarness):

    def setUp(self):
        super().setUp()
        self.project = self.workspace / "probe"
        (self.project / "gradle").mkdir(parents=True)
        shutil.copytree(REPO / "gradle" / "wrapper", self.project / "gradle" / "wrapper")
        (self.project / "tools").mkdir()
        for script in ("build-slots.ps1", "gate.ps1"):
            shutil.copy2(REPO / "tools" / script, self.project / "tools")
        for wrapper in ("gradlew", "gradlew.bat"):
            shutil.copy2(REPO / wrapper, self.project)
        (self.project / "settings.gradle").write_text("rootProject.name = 'gate-probe'\n")
        (self.project / "build.gradle").write_text(PROBE_BUILD)
        (self.project / "gradle.properties").write_text(PROBE_PROPERTIES)
        self.fleet = self.workspace / "fleet"
        (self.fleet / "src").mkdir(parents=True)
        (self.fleet / "src" / "gatereport.ps1").write_text(RECORDING_REPORTER)
        self.gates = []

    def tearDown(self):
        self.let_check_end()
        for gate in self.gates:
            if gate.process.poll() is None:
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(gate.process.pid)], capture_output=True)
            gate.process.wait(WAIT_SECONDS)
        super().tearDown()

    def let_check_end(self):
        (self.project / "released.flag").write_text("done")

    def start_gate(self, slot_count):
        env = slot_environment(self.store)
        env["BUILD_SLOT_COUNT"] = str(slot_count)
        env["FLEET"] = str(self.fleet)
        process = subprocess.Popen(
            ["pwsh", "-NoProfile", "-File", str(self.project / "tools" / "gate.ps1"), "-Legs", "gradle check"],
            cwd=self.project, env=env, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True)
        self.spawned.append(process)
        gate = RunningReserve(process)
        self.gates.append(gate)
        return gate

    def finish_gate(self, gate):
        exit_code = gate.process.wait(GRADLE_WAIT_SECONDS)
        gate.reader.join(WAIT_SECONDS)
        self.assertEqual(exit_code, 0, gate.lines)

    def gradle_leg_lines(self, gate):
        return [line for line in gate.lines if line.startswith("gate: gradle check ")]

    def recorded_reports(self):
        calls = self.fleet / "src" / "calls.jsonl"
        return [json.loads(line) for line in calls.read_text(encoding="utf-8-sig").splitlines() if line.strip()]

    def test_gate_prints_waiting_then_running_before_its_settled_line(self):
        self.let_check_end()
        holder = self.hold_slot("held run")
        gate = self.start_gate(slot_count=1)
        gate.wait_for_line(rf"^{re.escape(WAITING_LINE)}$", GRADLE_WAIT_SECONDS)

        self.assertEqual(self.gradle_leg_lines(gate), [WAITING_LINE], gate.lines)
        self.release(holder)
        self.finish_gate(gate)

        said = self.gradle_leg_lines(gate)
        self.assertEqual(said[:2], [WAITING_LINE, RUNNING_LINE], gate.lines)
        self.assertRegex(said[2], SETTLED_LINE)
        self.assertEqual(len(said), 3, gate.lines)

    def test_gate_with_a_free_slot_prints_running_alone(self):
        self.let_check_end()
        gate = self.start_gate(slot_count=4)
        self.finish_gate(gate)

        said = self.gradle_leg_lines(gate)
        self.assertEqual(said[0], RUNNING_LINE, gate.lines)
        self.assertRegex(said[1], SETTLED_LINE)
        self.assertEqual(len(said), 2, gate.lines)

    def test_gate_and_its_gradle_run_share_one_slot_and_free_it(self):
        gate = self.start_gate(slot_count=1)
        wait_for_file(self.project / "daemon.pid")

        lines = self.run_slots("read", "-Slots", "1").splitlines()
        self.assertEqual(lines[0], "build slots: 1 of 1 held, 0 waiting", lines)
        self.assertIsNotNone(held_line(lines, GATE_RUNS), lines)
        self.let_check_end()
        self.finish_gate(gate)

        self.assertEqual(self.run_slots("read", "-Slots", "1").splitlines()[0], "build slots: 0 of 1 held, 0 waiting")

    def test_gate_reports_its_gradle_leg_waiting_then_running(self):
        self.let_check_end()
        holder = self.hold_slot("held run")
        gate = self.start_gate(slot_count=1)
        gate.wait_for_line(rf"^{re.escape(WAITING_LINE)}$", GRADLE_WAIT_SECONDS)
        self.release(holder)
        self.finish_gate(gate)

        self.assertEqual(self.recorded_reports(), [
            {"status": "running", "legs": []},
            {"status": "running", "legs": [{"name": "gradle check", "verdict": "WAITING"}]},
            {"status": "running", "legs": [{"name": "gradle check", "verdict": "RUNNING"}]},
            {"status": "green", "legs": [{"name": "gradle check", "verdict": "PASS"}]},
        ])


if __name__ == "__main__":
    unittest.main()
