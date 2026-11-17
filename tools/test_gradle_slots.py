"""Proofs that every Gradle run through goo's wrappers holds one build slot for its whole life: run with
`python -m unittest tools.test_gradle_slots` from the repo root.

Each test copies goo's gradlew, gradlew.bat, gradle/wrapper and tools/build-slots.ps1 into a small temp build with
a small heap, so a test starts real Gradle through the real wrappers without starting goo's own build."""

import re
import shutil
import subprocess
import sys
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_build_slots import WAIT_SECONDS, SlotQueueHarness, held_line, slot_environment  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
GRADLE_WAIT_SECONDS = 180

PROBE_BUILD = """
// holdOpen stands in for a run that lasts, as runClient lasts until the client closes: it writes its daemon's
// process id, then runs until released.flag appears.
tasks.register('holdOpen') {
    def released = file('released.flag')
    def daemonPid = file('daemon.pid')
    doLast {
        daemonPid.text = ProcessHandle.current().pid().toString()
        while (!released.exists()) { Thread.sleep(100) }
    }
}
"""

PROBE_PROPERTIES = """org.gradle.jvmargs=-Xmx256m
org.gradle.daemon=true
org.gradle.daemon.idletimeout=60000
org.gradle.configuration-cache=true
"""


class RunningGradle:
    """A Gradle run through gradlew.bat, its output written to a file: the daemon it starts inherits the run's
    output handles and outlives it, so a pipe would stay open past the run's end."""

    def __init__(self, project, store, *tasks):
        self.output = project / f"gradle-{time.monotonic_ns()}.out"
        command = f'""{project / "gradlew.bat"}" {" ".join(tasks)} --console=plain > "{self.output}" 2>&1"'
        self.process = subprocess.Popen(f"cmd.exe /d /c {command}", cwd=project, env=slot_environment(store),
                                        stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                        stderr=subprocess.DEVNULL)

    def lines(self):
        try:
            return self.output.read_text(encoding="utf-8", errors="replace").splitlines()
        except FileNotFoundError:
            return []

    def wait_for_line(self, pattern):
        deadline = time.monotonic() + GRADLE_WAIT_SECONDS
        while not any(re.search(pattern, line) for line in self.lines()):
            if time.monotonic() > deadline or self.process.poll() is not None and \
                    not any(re.search(pattern, line) for line in self.lines()):
                raise AssertionError(f"no line matched {pattern!r}; the run said {self.lines()!r}")
            time.sleep(0.2)

    def finish(self):
        return self.process.wait(GRADLE_WAIT_SECONDS)


def wait_for_file(path):
    deadline = time.monotonic() + GRADLE_WAIT_SECONDS
    while not path.exists() or not path.read_text().strip():
        if time.monotonic() > deadline:
            raise AssertionError(f"{path} never appeared")
        time.sleep(0.2)


@unittest.skipUnless(sys.platform == "win32", "drives gradlew.bat")
class GradleRunShould(SlotQueueHarness):

    def setUp(self):
        super().setUp()
        self.project = self.workspace / "probe"
        (self.project / "gradle").mkdir(parents=True)
        shutil.copytree(REPO / "gradle" / "wrapper", self.project / "gradle" / "wrapper")
        (self.project / "tools").mkdir()
        shutil.copy2(REPO / "tools" / "build-slots.ps1", self.project / "tools")
        for wrapper in ("gradlew", "gradlew.bat"):
            shutil.copy2(REPO / wrapper, self.project)
        (self.project / "settings.gradle").write_text("rootProject.name = 'slot-probe'\n")
        (self.project / "build.gradle").write_text(PROBE_BUILD)
        (self.project / "gradle.properties").write_text(PROBE_PROPERTIES)
        self.runs = []

    def tearDown(self):
        (self.project / "released.flag").write_text("done")
        for run in self.runs:
            if run.process.poll() is None:
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(run.process.pid)], capture_output=True)
            run.process.wait(WAIT_SECONDS)
        super().tearDown()

    def start_gradle(self, *tasks):
        run = RunningGradle(self.project, self.store, *tasks)
        self.runs.append(run)
        return run

    def wait_for_task_to_start(self):
        wait_for_file(self.project / "daemon.pid")
        return int((self.project / "daemon.pid").read_text())

    def test_gradle_run_waits_for_a_slot_before_its_first_task(self):
        holders = self.fill_slots()
        run = self.start_gradle("help")
        run.wait_for_line(r"^waiting for a build slot: position 1 in the queue$")

        lines = self.read_queue()

        self.assertTrue(any(re.match(r"waiting 1: .*, running gradlew help --console=plain$", line)
                            for line in lines), lines)
        self.assertFalse(any(line.startswith("> Task") for line in run.lines()), run.lines())
        self.release(holders[0])
        self.assertEqual(run.finish(), 0, run.lines())

    def test_gradle_run_holds_its_slot_until_its_last_task_ends(self):
        run = self.start_gradle("holdOpen")
        self.wait_for_task_to_start()

        self.assertIsNotNone(held_line(self.read_queue(), "gradlew holdOpen --console=plain"), self.read_queue())
        (self.project / "released.flag").write_text("done")
        self.assertEqual(run.finish(), 0, run.lines())

        self.assertEqual(self.read_queue()[0], "build slots: 0 of 4 held, 0 waiting")

    def test_gradle_run_reusing_the_configuration_cache_reserves_a_slot(self):
        first = self.start_gradle("help")
        self.assertEqual(first.finish(), 0, first.lines())
        holders = self.fill_slots()
        second = self.start_gradle("help")
        second.wait_for_line(r"position 1 in the queue")

        self.assertTrue(any(line.startswith("waiting 1: ") for line in self.read_queue()), self.read_queue())
        self.release(holders[0])

        self.assertEqual(second.finish(), 0, second.lines())
        self.assertIn("Reusing configuration cache.", second.lines())

    def test_force_stopped_gradle_run_leaves_its_slot_free(self):
        run = self.start_gradle("holdOpen")
        daemon = self.wait_for_task_to_start()
        self.assertIsNotNone(held_line(self.read_queue(), "gradlew holdOpen --console=plain"), self.read_queue())

        stop = (f"$wrapper = @(Get-CimInstance Win32_Process -Filter 'ParentProcessId={run.process.pid}' | "
                f"ForEach-Object ProcessId); Stop-Process -Id (@({run.process.pid}, {daemon}) + $wrapper) -Force")
        subprocess.run(["pwsh", "-NoProfile", "-Command", stop], check=True, timeout=WAIT_SECONDS)
        run.process.wait(WAIT_SECONDS)

        self.assertEqual(self.read_queue()[0], "build slots: 0 of 4 held, 0 waiting")


if __name__ == "__main__":
    unittest.main()
