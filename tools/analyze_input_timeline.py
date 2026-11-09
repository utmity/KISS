#!/usr/bin/env python3
"""Summarize app-side observations; never infer IME internal state or hardware latency."""
import argparse
import json
import re
import statistics
from pathlib import Path


def fields(row):
    return {key: int(value) for key, value in re.findall(r"(\w+)=(-?\d+)", row.get("detail", ""))}


def analyze(path):
    objects = [json.loads(line) for line in Path(path).read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    metadata = [row for row in objects if "seq" not in row]
    rows = [row for row in objects if "seq" in row]
    if any(a["seq"] >= b["seq"] for a, b in zip(rows, rows[1:])):
        raise ValueError("Non-increasing sequence: mixed sessions or corrupt export")
    costs = sorted(row["encode_ns"] / 1000 for row in rows)
    segments = []
    for index, row in enumerate(rows):
        if row["stage"] != "preIme.enter":
            continue
        key = fields(row)
        if key.get("key") != 34 or key.get("action") != 0 or key.get("repeat") != 0 or row["length"] != 0:
            continue
        segment = []
        up = None
        for later in rows[index + 1:]:
            segment.append(later)
            if later["stage"] == "preIme.enter":
                other = fields(later)
                if other.get("key") == key["key"] and other.get("device") == key["device"] and other.get("action") == 1:
                    up = later
                    break
            if later["stage"] in ("activity.pause", "test.begin"):
                break

        def first_ms(stage, nonempty=False):
            event = next((r for r in segment if r["stage"] == stage and (not nonempty or r["length"] > 0)), None)
            return None if event is None else round((event["t_ns"] - row["t_ns"]) / 1e6, 3)

        segments.append({
            "start_seq": row["seq"], "device": key.get("device"), "meta": key.get("meta"),
            "down_event_to_observation_ms": row["uptime_ms"] - key.get("event_ms", row["uptime_ms"]),
            "release_ms": None if up is None else fields(up).get("event_ms", 0) - key.get("down_ms", 0),
            "first_text_change_ms": first_ms("text.changed", True),
            "first_search_submit_ms": first_ms("search.submit", True),
            "first_draw_return_ms": first_ms("draw.return", True),
            "hold_marker_ms": first_ms("hold.700ms.observed"),
            "repeat_downs": sum(r["stage"] == "preIme.enter" and fields(r).get("key") == 34
                                and fields(r).get("repeat", 0) > 0 for r in segment),
            "composing_observed": any(r.get("composingStart", -1) >= 0 for r in segment),
            "ic_callbacks": sorted({r["stage"] for r in segment if r["stage"].startswith("ic.")}),
        })
    return {
        "metadata": metadata, "events": len(rows),
        "encoding_cost_us": None if not costs else {
            "median": round(statistics.median(costs), 3),
            "p95": round(costs[min(len(costs) - 1, int(len(costs) * .95))], 3),
            "max": round(max(costs), 3),
            "limitation": "JSON preparation only; excludes lock wait, buffer insertion, GC and wrapper overhead",
        },
        "empty_field_f_presses": segments,
        "limitations": ["No inference of IME language mode; annotate test sessions separately",
                        "draw.return is not screen presentation time",
                        "No baseline comparison: these numbers do not establish zero added latency",
                        "InputConnection can change text without one-to-one correspondence to keys"],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("trace")
    args = parser.parse_args()
    print(json.dumps(analyze(args.trace), ensure_ascii=False, indent=2))
