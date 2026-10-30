"""Summarize iRead curl Trace sections from an uncompressed atrace capture."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import re
from statistics import median


def analyze(path):
    stacks = defaultdict(list)
    samples = defaultdict(list)
    uploads = {}
    presented = {}
    marker = re.compile(r"-(\d+)\s+.*?\s(\d+\.\d+): tracing_mark_write: ([BE])(?:\|(\d+)\|(.+))?")
    with path.open(encoding="utf-8", errors="replace") as trace:
        for line in trace:
            match = marker.search(line)
            if match is None:
                continue
            thread, timestamp, kind, _, name = match.groups()
            timestamp = float(timestamp)
            if kind == "B":
                stacks[thread].append((timestamp, name))
            elif stacks[thread]:
                started, name = stacks[thread].pop()
                if name and name.startswith("iRead.curl."):
                    samples[name].append((timestamp - started) * 1000)
                    if name == "iRead.curl.upload":
                        uploads[thread] = started
                    elif name == "iRead.curl.present":
                        presented[thread] = timestamp
                    elif name == "iRead.curl.frame" and thread in uploads:
                        upload = uploads.pop(thread)
                        if presented.get(thread, -1) >= started:
                            samples["iRead.curl.uploadToFirstSubmit"].append((timestamp - upload) * 1000)
    return {
        name: {"count": len(values), "medianMs": median(values),
               "p95Ms": sorted(values)[int((len(values) - 1) * .95)], "maxMs": max(values)}
        for name, values in sorted(samples.items())
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = analyze(args.trace)
    if not report:
        parser.error("No iRead.curl sections found; enable tracing for the Demo package.")
    output = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.write_text(output, encoding="utf-8")
    print(output, end="")
