"""Proofs for tools/sca-digest.py: run with `python -m unittest tools.test_sca_digest` from the repo root."""

import contextlib
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "sca-digest.py"


def load_digest_module():
    """Loads the hyphenated script as a module, since its name is not importable by dotted path."""
    spec = importlib.util.spec_from_file_location("sca_digest", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


NESTED_FAILURE_XML = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<testsuite>
  <testsuite time="8.851" timestamp="2026-01-01T00:00:00Z">
    <testcase classname="minecraft:empty" name="goo:blob_insert" time="0.052"/>
    <testcase classname="minecraft:empty" name="goo:hub_insert" time="0.101">
      <failure message="Hub should have canister after insertion on tick 0" type="failure"/>
    </testcase>
  </testsuite>
</testsuite>
"""

AGGREGATING_OUTER_XML = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<testsuite name="all" tests="2" failures="0" errors="0">
  <testsuite name="first" tests="1" failures="0" errors="0">
    <testcase classname="goo.First" name="passes" time="0.001"/>
  </testsuite>
  <testsuite name="second" tests="1" failures="0" errors="0">
    <testcase classname="goo.Second" name="passes" time="0.001"/>
  </testsuite>
</testsuite>
"""


class ParseTestsShould(unittest.TestCase):

    def setUp(self):
        self.digest = load_digest_module()
        self.workspace = Path(tempfile.mkdtemp())
        self.digest.TEST_RESULTS = self.workspace / "test"
        self.digest.GAMETEST_RESULTS = self.workspace / "gameTest"
        self.digest.OUTPUT = self.workspace / "digest.txt"

    def write_gametest_result(self, xml):
        self.digest.GAMETEST_RESULTS.mkdir(parents=True)
        (self.digest.GAMETEST_RESULTS / "TEST-gametest.xml").write_text(xml, encoding="utf-8")

    def test_parse_tests_reports_a_failure_under_a_nested_suite(self):
        self.write_gametest_result(NESTED_FAILURE_XML)

        total, failed, errored, failures = self.digest.parse_tests()

        self.assertEqual((total, failed, errored), (2, 1, 0))
        self.assertEqual(failures, [("minecraft:empty", "goo:hub_insert", "Hub should have canister after insertion on tick 0")])

    def test_write_digest_prints_gate_fail_for_a_nested_failure(self):
        self.write_gametest_result(NESTED_FAILURE_XML)
        tests = self.digest.parse_tests()

        with contextlib.redirect_stdout(io.StringIO()):
            gate_failed = self.digest.write_digest({}, {}, {}, [], tests)

        self.assertTrue(gate_failed)
        digest_text = self.digest.OUTPUT.read_text(encoding="utf-8")
        self.assertIn("--- GATE: FAIL ---", digest_text)
        self.assertIn("  FAIL minecraft:empty.goo:hub_insert", digest_text)

    def test_parse_tests_counts_each_testcase_once_under_an_aggregating_outer_suite(self):
        self.write_gametest_result(AGGREGATING_OUTER_XML)

        total, failed, errored, failures = self.digest.parse_tests()

        self.assertEqual((total, failed, errored, failures), (2, 0, 0, []))


if __name__ == "__main__":
    unittest.main()
