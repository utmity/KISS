"""Fail CI if instrumentation silently omits a requested regression class."""
import pathlib
import sys
import xml.etree.ElementTree as ET

expected = {"WidgetGridTest": 5, "WidgetSwipeTest": 8, "RealWidgetTest": 1,
            "SequentialInputTest": 4, "WidgetScrollTest": 1}
name, directory = sys.argv[1:]
cases = []
for report in pathlib.Path(directory).rglob("TEST-*.xml"):
    cases.extend(case for case in ET.parse(report).iter("testcase")
                 if case.get("classname") == "fr.neamar.kiss.androidTest." + name)
if len(cases) != expected[name]:
    raise SystemExit(f"{name}: expected {expected[name]} cases, found {len(cases)}")
skipped = sum(case.find("skipped") is not None for case in cases)
if any(case.find("failure") is not None or case.find("error") is not None for case in cases):
    raise SystemExit(f"{name}: failures found")
if skipped and name != "RealWidgetTest":
    raise SystemExit(f"{name}: unexpected skipped tests")
print(f"{name}: {len(cases)} cases, {skipped} skipped")
