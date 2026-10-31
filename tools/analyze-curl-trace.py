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
    async_starts = {}
    async_ends = []
    outcomes = {}
    async_marker = re.compile(r"\s(\d+\.\d+): tracing_mark_write: ([SF])\|(\d+)\|([^|]+)\|(-?\d+)")
    marker = re.compile(r"-(\d+)\s+.*?\s(\d+\.\d+): tracing_mark_write: ([BE])(?:\|(\d+)\|(.+))?")
    with path.open(encoding="utf-8", errors="replace") as trace:
        for line in trace:
            event = async_marker.search(line)
            if event is not None:
                timestamp, kind, pid, name, cookie = event.groups()
                key = (pid, name, cookie)
                if kind == "S":
                    async_starts[key] = float(timestamp)
                elif key in async_starts:
                    async_ends.append((key, (float(timestamp) - async_starts.pop(key)) * 1000))
                continue
            match = marker.search(line)
            if match is None:
                continue
            thread, timestamp, kind, pid, name = match.groups()
            timestamp = float(timestamp)
            if kind == "B":
                stacks[thread].append((timestamp, name))
                if name and name.startswith(("iRead.curl.firstSubmit:", "iRead.curl.cancelledStartup:")):
                    outcome, cookie = name.rsplit(":", 1)
                    outcomes[(pid, cookie)] = outcome
            elif stacks[thread]:
                started, name = stacks[thread].pop()
                if name and name.startswith(("iRead.curl.", "iRead.epub.")):
                    if name.startswith(("iRead.curl.firstSubmit:", "iRead.curl.cancelledStartup:")):
                        continue
                    samples[name].append((timestamp - started) * 1000)
                    if name == "iRead.curl.upload":
                        uploads[thread] = started
                    elif name == "iRead.curl.present":
                        presented[thread] = timestamp
                    elif name == "iRead.curl.frame" and thread in uploads:
                        upload = uploads.pop(thread)
                        if presented.get(thread, -1) >= started:
                            samples["iRead.curl.uploadToFirstSubmit"].append((timestamp - upload) * 1000)
    for (pid, name, cookie), elapsed in async_ends:
        if name == "iRead.curl.startup":
            outcome = outcomes.get((pid, cookie))
            if outcome:
                name = "iRead.curl.startupToFirstSubmit" if outcome.endswith("firstSubmit") else "iRead.curl.cancelledStartup"
                samples[name].append(elapsed)
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
        parser.error("No iRead curl/EPUB sections found; enable tracing for the Demo package.")
    output = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.write_text(output, encoding="utf-8")
    print(output, end="")
