"""Proofs for tools/build-slots.ps1, driven as a process against a store in a temp directory: run with
`python -m unittest tools.test_build_slots` from the repo root."""

import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "build-slots.ps1"
WAIT_SECONDS = 60


def slot_environment(store=None):
    """The caller's environment with the slot settings replaced, so no test reaches the real queue."""
    env = {k: v for k, v in os.environ.items()
           if k not in ("BUILD_SLOT_STORE", "BUILD_SLOT_COUNT", "BUILD_SLOT_HOLDER")}
    if store is not None:
        env["BUILD_SLOT_STORE"] = str(store)
    return env


def slot_command(verb, *args):
    return ["pwsh", "-NoProfile", "-File", str(SCRIPT), verb, *args]


class RunningReserve:
    """A reserve started in the background, its output gathered line by line as it arrives."""

    def __init__(self, process):
        self.process = process
        self.lines = []
        self.closed = False
        self.arrived = threading.Condition()
        self.reader = threading.Thread(target=self.gather_lines, daemon=True)
        self.reader.start()

    def gather_lines(self):
        for line in self.process.stdout:
            with self.arrived:
                self.lines.append(line.rstrip("\r\n"))
                self.arrived.notify_all()
        with self.arrived:
            self.closed = True
            self.arrived.notify_all()

    def wait_for_line(self, pattern, seconds=WAIT_SECONDS):
        deadline = time.monotonic() + seconds
        with self.arrived:
            while True:
                if any(re.search(pattern, line) for line in self.lines):
                    return
                remaining = deadline - time.monotonic()
                if remaining <= 0 or self.closed:
                    raise AssertionError(f"no line matched {pattern!r}; the reserve said {self.lines!r}")
                self.arrived.wait(remaining)

    def finish(self):
        exit_code = self.process.wait(WAIT_SECONDS)
        self.reader.join(WAIT_SECONDS)
        return exit_code


class SlotQueueHarness(unittest.TestCase):
    """Spawns holder processes and reserves for them, ending every process a test leaves running."""

    def setUp(self):
        self.workspace = Path(tempfile.mkdtemp(prefix="build-slots-"))
        self.store = self.workspace / "store"
        self.spawned = []

    def tearDown(self):
        for process in self.spawned:
            if process.poll() is None:
                process.kill()
            process.wait(WAIT_SECONDS)
            if process.stdout:
                process.stdout.close()
        shutil.rmtree(self.workspace, ignore_errors=True)

    def start_holder_process(self):
        holder = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(600)"])
        self.spawned.append(holder)
        return holder

    def run_slots(self, verb, *args, cwd=None, store="default"):
        store = self.store if store == "default" else store
        done = subprocess.run(slot_command(verb, *args), cwd=cwd or self.workspace, env=slot_environment(store),
                              capture_output=True, text=True, timeout=WAIT_SECONDS)
        self.assertEqual(done.returncode, 0, f"{verb} failed: {done.stdout}{done.stderr}")
        return done.stdout

    def start_reserve(self, holder, runs, cwd=None, store="default"):
        store = self.store if store == "default" else store
        process = subprocess.Popen(slot_command("reserve", "-HolderPid", str(holder.pid), "-Runs", runs),
                                   cwd=cwd or self.workspace, env=slot_environment(store),
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        self.spawned.append(process)
        return RunningReserve(process)

    def hold_slot(self, runs, cwd=None, store="default"):
        holder = self.start_holder_process()
        self.run_slots("reserve", "-HolderPid", str(holder.pid), "-Runs", runs, cwd=cwd, store=store)
        return holder

    def fill_slots(self, cwd=None):
        return [self.hold_slot(f"held run {n}", cwd=cwd) for n in range(1, 5)]

    def release(self, holder):
        self.run_slots("release", "-HolderPid", str(holder.pid))

    def read_queue(self, cwd=None, store="default"):
        return self.run_slots("read", cwd=cwd, store=store).splitlines()


def make_git_repository(path, branch):
    path.mkdir(parents=True)
    git = ["git", "-C", str(path)]
    subprocess.run(["git", "init", "-q", "-b", branch, str(path)], check=True)
    subprocess.run(git + ["-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "start"],
                   check=True)
    return path


def held_line(lines, runs):
    return next((line for line in lines if re.match(r"slot \d+: held", line) and line.endswith(f"running {runs}")),
                None)


class ReserveShould(SlotQueueHarness):

    def test_fifth_reserve_waits_and_waiters_take_freed_slots_in_arrival_order(self):
        holders = self.fill_slots()
        fifth = self.start_reserve(self.start_holder_process(), "fifth run")
        fifth.wait_for_line(r"^waiting for a build slot: position 1 in the queue$")
        sixth = self.start_reserve(self.start_holder_process(), "sixth run")
        sixth.wait_for_line(r"^waiting for a build slot: position 2 in the queue$")

        self.release(holders[0])

        self.assertEqual(fifth.finish(), 0)
        fifth.wait_for_line(r"^holding build slot 1$")
        lines = self.read_queue()
        self.assertIsNotNone(held_line(lines, "fifth run"), lines)
        self.assertTrue(any(re.match(r"waiting 1: since .*, running sixth run$", line) for line in lines), lines)

    def test_waiting_reserve_prints_each_position_then_its_slot(self):
        holders = self.fill_slots()
        first_waiter = self.start_reserve(self.start_holder_process(), "first waiter")
        first_waiter.wait_for_line(r"position 1 in the queue")
        second_waiter = self.start_reserve(self.start_holder_process(), "second waiter")
        second_waiter.wait_for_line(r"position 2 in the queue")

        self.release(holders[0])
        second_waiter.wait_for_line(r"position 1 in the queue")
        self.release(holders[1])

        self.assertEqual(second_waiter.finish(), 0)
        self.assertEqual(second_waiter.lines, ["waiting for a build slot: position 2 in the queue",
                                               "waiting for a build slot: position 1 in the queue",
                                               "holding build slot 2"])

    def test_slot_of_a_dead_holder_goes_to_the_next_waiter(self):
        for n in range(1, 4):
            self.hold_slot(f"held run {n}")
        doomed = self.hold_slot("doomed run")
        waiter = self.start_reserve(self.start_holder_process(), "patient run")
        waiter.wait_for_line(r"position 1 in the queue")

        doomed.kill()

        self.assertEqual(waiter.finish(), 0)
        waiter.wait_for_line(r"^holding build slot 4$")
        lines = self.read_queue()
        self.assertTrue(held_line(lines, "patient run").startswith("slot 4: held"), lines)
        self.assertFalse(any("doomed run" in line for line in lines), lines)


class ReadShould(SlotQueueHarness):

    def test_read_lists_each_holder_and_each_waiter_in_arrival_order(self):
        self.fill_slots()
        north = make_git_repository(self.workspace / "worktree-north", "north-branch")
        south = make_git_repository(self.workspace / "worktree-south", "south-branch")
        north_waiter = self.start_reserve(self.start_holder_process(), "north run", cwd=north)
        north_waiter.wait_for_line(r"position 1 in the queue")
        south_waiter = self.start_reserve(self.start_holder_process(), "south run", cwd=south)
        south_waiter.wait_for_line(r"position 2 in the queue")

        lines = self.read_queue()

        self.assertEqual(lines[0], "build slots: 4 of 4 held, 2 waiting")
        for n in range(1, 5):
            self.assertRegex(lines[n], rf"^slot {n}: held since \S+ \S+ by .+ on .+, running held run {n}$")
        self.assertRegex(lines[5], rf"^waiting 1: since \S+ \S+, {re.escape(str(north))} on north-branch, "
                                   rf"running north run$")
        self.assertRegex(lines[6], rf"^waiting 2: since \S+ \S+, {re.escape(str(south))} on south-branch, "
                                   rf"running south run$")
        self.assertEqual(len(lines), 7, lines)


class StoreShould(SlotQueueHarness):

    def test_two_worktrees_of_one_repository_share_one_queue(self):
        main = make_git_repository(self.workspace / "main-checkout", "main-branch")
        second = self.workspace / "second-worktree"
        subprocess.run(["git", "-C", str(main), "worktree", "add", "-q", "-b", "second-branch", str(second)],
                       check=True)
        self.hold_slot("main run", cwd=main, store=None)
        self.hold_slot("second run", cwd=second, store=None)

        lines = self.read_queue(cwd=second, store=None)

        self.assertEqual(lines[0], "build slots: 2 of 4 held, 0 waiting")
        self.assertRegex(held_line(lines, "main run"), rf" by {re.escape(str(main))} on main-branch, ")
        self.assertRegex(held_line(lines, "second run"), rf" by {re.escape(str(second))} on second-branch, ")


if __name__ == "__main__":
    unittest.main()
