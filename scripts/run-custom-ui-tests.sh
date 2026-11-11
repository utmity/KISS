#!/usr/bin/env bash
set -euo pipefail
# These tests deliberately modify preferences. Refuse to run against a phone.
devices=$(adb devices | awk 'NR > 1 && NF { print $1 }')
if [[ ! "$devices" =~ ^emulator-[0-9]+$ ]]; then
    echo 'Exactly one disposable emulator is required; physical devices are not allowed.' >&2
    exit 1
fi
bash ./gradlew installDebug installDebugAndroidTest --stacktrace
adb shell appwidget grantbind --package fr.neamar.kiss.sequence.debug --user 0
for test_class in WidgetGridTest WidgetSwipeTest RealWidgetTest SequentialInputTest WidgetScrollTest; do
    # A single class per invocation avoids silent truncation of class lists.
    result=0
    bash ./gradlew connectedDebugAndroidTest \
        "-Pandroid.testInstrumentationRunnerArguments.class=fr.neamar.kiss.androidTest.$test_class" \
        --stacktrace || result=$?
    destination="app/build/custom-ui-reports/$test_class"
    mkdir -p "$destination"
    cp -R app/build/outputs/androidTest-results "$destination/"
    cp -R app/build/reports/androidTests "$destination/"
    if [[ "$result" != 0 ]]; then exit "$result"; fi
    python3 scripts/check-ui-results.py "$test_class" "$destination/androidTest-results"
done
