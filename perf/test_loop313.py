import io
import unittest
import argparse
import json
import pathlib
import subprocess
import tempfile
from unittest.mock import patch
import loop313
from loop313 import events, percentile


class MeasurementTest(unittest.TestCase):
    def test_receives_complete_frames_with_monotonic_clock(self):
        stream = io.BytesIO(b': ping\r\nevent: youtube-sync.accepted\r\ndata: first\r\ndata: second\r\n\r\n'
                            b'event: youtube-sync.success\ndata: done\n\n')
        ticks = iter([100, 3100])
        self.assertEqual(list(events(stream, lambda: next(ticks))), [
            ('youtube-sync.accepted', 'first\nsecond', 100),
            ('youtube-sync.success', 'done', 3100)])

    def test_does_not_emit_unterminated_frame(self):
        self.assertEqual(list(events(io.BytesIO(b'event: youtube-sync.success\n'))), [])

    def test_percentile_interpolates_small_sample(self):
        self.assertEqual(percentile([40, 10, 30, 20], .5), 25)
        self.assertAlmostEqual(percentile([40, 10, 30, 20], .95), 38.5)

    def test_preserves_failed_round_for_process_errors(self):
        errors = [subprocess.TimeoutExpired(["docker"], 360, output=b"partial stdout\xff", stderr=b"partial stderr"),
                  FileNotFoundError("docker unavailable")]
        for error in errors:
            with self.subTest(error=type(error).__name__), tempfile.TemporaryDirectory(dir=loop313.ROOT) as directory:
                root = pathlib.Path(directory)
                tokens = root / "build/perf/313/base/tokens.json"
                tokens.parent.mkdir(parents=True)
                tokens.write_text(json.dumps({"members": [{"id": i, "token": "test"} for i in range(40)]}))
                # 도구 해시에 필요한 실제 소스는 읽고 측정 결과만 임시 디렉터리에 쓴다.
                for source in ["perf/loop313.py", "perf/k6/loop-313.js", "perf/compose.yaml",
                               "src/mysqlIntegrationTest/java/eightjbbm/perf/PerfHarnessApplication.java",
                               "src/mysqlIntegrationTest/java/eightjbbm/perf/PerfHarnessConfiguration.java",
                               "src/test/java/eightjbbm/keepgo/integration/stub/AiServerStub.java"]:
                    target = root / source
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes((loop313.ROOT / source).read_bytes())
                args = argparse.Namespace(output="results", base_sha="base", candidate_sha="candidate",
                                          warmups=0, repeats=1, port=0, scenarios=["first"], concurrency=[1])
                sample = {"ok": False, "events": [{"event": "youtube-sync.accepted", "received_ns": 100}]}
                reading = {"active": 1, "pending": 0}

                def fail_process(*_, **__):
                    server.samples.append(sample)
                    raise error

                real_server = loop313.http.server.ThreadingHTTPServer
                server = real_server(("127.0.0.1", 0), loop313.Receiver)
                with patch.object(loop313, "ROOT", root), patch.object(loop313, "quiet"), \
                        patch.object(loop313, "request", return_value={}), \
                        patch.object(loop313.http.server, "ThreadingHTTPServer", return_value=server), \
                        patch.object(loop313, "scrape", side_effect=lambda version, stop, readings: readings.append(reading)), \
                        patch.object(loop313.subprocess, "run", side_effect=fail_process):
                    with self.assertRaises(type(error)) as caught:
                        loop313.run(args)
                self.assertIs(caught.exception, error)
                out = root / "results"
                raw = json.loads((out / "first-n1-00-base.json").read_text())
                self.assertEqual(raw["samples"], [sample])
                self.assertEqual(raw["db_readings"], [reading])
                self.assertIn(type(error).__name__, raw["error"])
                log = (out / "first-n1-00-base-k6.log").read_text(encoding="utf-8")
                self.assertIn(str(error), log)
                if isinstance(error, subprocess.TimeoutExpired):
                    self.assertIn("partial stdout\ufffdpartial stderr", log)
                self.assertFalse(json.loads((out / "metadata.json").read_text())["complete"])
                self.assertEqual(json.loads((out / "summary.json").read_text()), [])


if __name__ == '__main__':
    unittest.main()
