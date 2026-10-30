"""Find sampled Java call stacks in an ART version-3 dual-clock method trace."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import struct


def analyze(path, method_filter, thread_filter=None, limit=20):
    data = path.read_bytes()
    marker = b"*end\n"
    end = data.find(marker)
    if end < 0:
        raise ValueError("Expected a non-streaming ART method trace with a text header.")
    end += len(marker)
    header = data[:end].decode("utf-8", errors="replace")
    magic, version, offset = struct.unpack_from("<4sHH", data, end)
    if magic != b"SLOW" or version != 3 or "clock=dual" not in header:
        raise ValueError("Supported format: ART version 3, clock=dual, non-streaming.")
    record_size = struct.unpack_from("<H", data, end + 16)[0]
    if (
        offset < 18
        or end + offset > len(data)
        or record_size != 14
        or (len(data) - end - offset) % record_size
    ):
        raise ValueError("Invalid or incomplete dual-clock records.")
    methods, threads, metadata = {}, {}, {}
    section = ""
    for line in header.splitlines():
        if line.startswith("*"):
            section = line
            continue
        fields = line.split("\t")
        if section == "*methods" and fields[0].startswith("0x"):
            methods[int(fields[0], 16)] = ".".join(fields[1:3])
        elif section == "*threads" and len(fields) > 1:
            threads[int(fields[0])] = fields[1]
        elif "=" in line:
            key, value = line.split("=", 1)
            metadata[key] = value
    stacks = defaultdict(list)
    calls = []
    for at in range(end + offset, len(data), record_size):
        thread_id, code, cpu, wall = struct.unpack_from("<HIII", data, at)
        method, event = code & ~3, code & 3
        stack = stacks[thread_id]
        if event == 0:
            stack.append((method, cpu, wall))
        elif event in (1, 2):
            match = next((i for i in range(len(stack) - 1, -1, -1)
                          if stack[i][0] == method), None)
            if match is None:
                continue
            _, started_cpu, started_wall = stack[match]
            name = methods.get(method, "<root>")
            thread = threads.get(thread_id, str(thread_id))
            if method_filter in name and (thread_filter is None or thread == thread_filter):
                calls.append({
                    "thread": thread,
                    "sampledWallMs": ((wall - started_wall) & 0xFFFFFFFF) / 1000,
                    "sampledCpuMs": ((cpu - started_cpu) & 0xFFFFFFFF) / 1000,
                    "stack": [methods.get(item[0], "<root>") for item in stack[:match + 1]],
                })
            del stack[match:]
    calls.sort(key=lambda call: call["sampledWallMs"], reverse=True)
    return {"methodFilter": method_filter, "threadFilter": thread_filter,
            "clock": "dual", "overflow": metadata.get("data-file-overflow") == "true",
            "sampledCalls": len(calls), "calls": calls[:limit]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--method", required=True)
    parser.add_argument("--thread")
    parser.add_argument("--limit", type=int, default=20)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.limit <= 0:
        parser.error("--limit must be positive")
    try:
        report = analyze(args.trace, args.method, args.thread, args.limit)
    except (ValueError, struct.error) as error:
        parser.error(str(error))
    output = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(output, encoding="utf-8")
    print(output, end="")
