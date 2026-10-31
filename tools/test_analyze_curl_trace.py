import importlib.util
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location(
    "curl_trace", Path(__file__).with_name("analyze-curl-trace.py")
)
curl_trace = importlib.util.module_from_spec(spec)
spec.loader.exec_module(curl_trace)


class CurlTraceTest(unittest.TestCase):
    def test_shared_pixel_reservations_are_reported_separately(self):
        report = self.analyze([
            (10, 10, 1.000, "B|10|iRead.snapshot.sharedPixels"),
            (10, 10, 1.001, "E"),
        ])
        self.assertEqual(1, report["iRead.snapshot.sharedPixels"]["count"])
        self.assertAlmostEqual(1, report["iRead.snapshot.sharedPixels"]["medianMs"])

    def test_epub_recording_keeps_nested_framework_time_in_outer_section(self):
        report = self.analyze([
            (10, 10, 1.000, "B|10|iRead.epub.record"),
            (10, 10, 1.001, "B|10|WebView.onDraw"),
            (10, 10, 1.004, "E"),
            (10, 10, 1.005, "E"),
        ])
        self.assertEqual(["iRead.epub.record"], list(report))
        self.assertAlmostEqual(5, report["iRead.epub.record"]["medianMs"])

    def analyze(self, events):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "curl.trace"
            path.write_text("\n".join(
                f"worker-{thread} ( {pid}) [000] ...1 {time:.6f}: tracing_mark_write: {marker}"
                for thread, pid, time, marker in events
            ), encoding="utf-8")
            return curl_trace.analyze(path)

    def test_startup_crosses_threads_without_including_cancelled_or_incomplete_turns(self):
        report = self.analyze([
            (10, 10, 1.000, "S|10|iRead.curl.startup|1"),
            (10, 10, 1.001, "S|10|iRead.curl.startup|2"),
            (20, 10, 1.006, "B|10|iRead.curl.cancelledStartup:2"),
            (20, 10, 1.006, "E"),
            (20, 10, 1.006, "F|10|iRead.curl.startup|2"),
            (20, 10, 1.021, "B|10|iRead.curl.firstSubmit:1"),
            (20, 10, 1.021, "E"),
            (20, 10, 1.021, "F|10|iRead.curl.startup|1"),
            (10, 10, 1.030, "S|10|iRead.curl.startup|3"),
            (10, 10, 1.040, "F|10|iRead.curl.startup|3"),
            (10, 10, 1.050, "S|10|iRead.curl.startup|4"),
        ])
        self.assertEqual(1, report["iRead.curl.startupToFirstSubmit"]["count"])
        self.assertAlmostEqual(21, report["iRead.curl.startupToFirstSubmit"]["medianMs"])
        self.assertEqual(1, report["iRead.curl.cancelledStartup"]["count"])
        self.assertAlmostEqual(5, report["iRead.curl.cancelledStartup"]["medianMs"])
        self.assertEqual(2, len(report))

    def test_cookie_reuse_in_another_process_does_not_supply_a_missing_outcome(self):
        report = self.analyze([
            (10, 10, 1.000, "S|10|iRead.curl.startup|1"),
            (30, 30, 1.000, "S|30|iRead.curl.startup|1"),
            (20, 10, 1.010, "B|10|iRead.curl.firstSubmit:1"),
            (20, 10, 1.010, "E"),
            (20, 10, 1.010, "F|10|iRead.curl.startup|1"),
            (40, 30, 1.100, "F|30|iRead.curl.startup|1"),
        ])
        self.assertEqual(1, report["iRead.curl.startupToFirstSubmit"]["count"])
        self.assertAlmostEqual(10, report["iRead.curl.startupToFirstSubmit"]["medianMs"])

    def test_upload_to_first_submit_uses_latest_upload_and_nested_present(self):
        report = self.analyze([
            (20, 10, 1.000, "B|10|iRead.curl.upload"),
            (20, 10, 1.003, "E"),
            (20, 10, 1.010, "B|10|iRead.curl.upload"),
            (20, 10, 1.013, "E"),
            (20, 10, 1.014, "B|10|iRead.curl.frame"),
            (20, 10, 1.015, "B|10|iRead.curl.present"),
            (20, 10, 1.017, "E"),
            (20, 10, 1.018, "E"),
            (20, 10, 1.020, "B|10|iRead.curl.frame"),
            (20, 10, 1.022, "E"),
        ])
        self.assertEqual(2, report["iRead.curl.upload"]["count"])
        self.assertEqual(2, report["iRead.curl.frame"]["count"])
        self.assertEqual(1, report["iRead.curl.uploadToFirstSubmit"]["count"])
        self.assertAlmostEqual(8, report["iRead.curl.uploadToFirstSubmit"]["medianMs"])


if __name__ == "__main__":
    unittest.main()
