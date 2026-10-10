#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
products=${1:?Pass the real Simulator Pod build Products directory}
test -f "$products/GycLocationKuikly.framework/GycLocationKuikly"
test -f "$products/OpenKuiklyIOSRender.framework/OpenKuiklyIOSRender"
output="$PWD/build/kuikly-module-check"
app="$output/ModuleCheck.app"
mkdir -p "$app/Frameworks"
cp -R "$products/GycLocationKuikly.framework" "$products/OpenKuiklyIOSRender.framework" "$app/Frameworks/"
xcrun swiftc -parse-as-library -target "$(uname -m)-apple-ios15.0-simulator"   -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" -F "$products"   -framework GycLocationKuikly -framework OpenKuiklyIOSRender   -Xlinker -rpath -Xlinker @executable_path/Frameworks   iosApp/KuiklyTests/ModuleLifecycleCheck.swift -o "$app/ModuleCheck"
cat > "$app/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleIdentifier</key><string>io.github.gycrosskit.location.module-check</string>
<key>CFBundleExecutable</key><string>ModuleCheck</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleName</key><string>ModuleCheck</string>
<key>CFBundleVersion</key><string>1</string>
<key>CFBundleShortVersionString</key><string>1.0</string>
<key>LSRequiresIPhoneOS</key><true/>
</dict></plist>
PLIST
codesign --force --sign - "$app/Frameworks/GycLocationKuikly.framework" "$app/Frameworks/OpenKuiklyIOSRender.framework" "$app" >/dev/null
simulator=${KUIKLY_MODULE_SIMULATOR:-$(xcrun simctl list devices available -j | python3 -c 'import json,sys; devices=[d for rows in json.load(sys.stdin)["devices"].values() for d in rows if d.get("isAvailable") and d["name"].startswith("iPhone")]; print(next((d for d in devices if d["state"]=="Booted"), devices[0])["udid"])')}
xcrun simctl bootstatus "$simulator" -b
xcrun simctl install "$simulator" "$app"
bundle=io.github.gycrosskit.location.module-check
data=$(xcrun simctl get_app_container "$simulator" "$bundle" data)
app_result="$data/Documents/module-check-result.log"
# 安装可能保留旧 app 数据；本轮 PASS 必须由本次进程重新写入。
rm -f "$app_result" "$output/app-result.log"
rm -rf ci-diagnostics/module-check
mkdir -p ci-diagnostics/module-check
touch "$output/launch-start"
launch_status=0
python3 - "$simulator" "$bundle" "$output/result.log" "$app_result" <<'LAUNCH' || launch_status=$?
import json, re, resource, subprocess, sys, time
from pathlib import Path
simulator, bundle, output, app_result = sys.argv[1:]
deadline = time.monotonic() + 90
try:
    # 子进程直接写有界 console 文件，避免 PIPE 无界缓存或 shell 子进程卡住 timeout。
    with Path(output).open('wb') as console:
        result = subprocess.run(['xcrun', 'simctl', 'launch', '--console', '--terminate-running-process', simulator, bundle],
                                stdout=console, stderr=subprocess.STDOUT, timeout=90,
                                preexec_fn=lambda: resource.setrlimit(resource.RLIMIT_FSIZE, (8388608, 8388608)))
    content, status = Path(output).read_bytes(), result.returncode
except subprocess.TimeoutExpired as error:
    content, status = Path(output).read_bytes(), 124
    try:
        subprocess.run(['xcrun', 'simctl', 'terminate', simulator, bundle], timeout=10, check=False)
    except subprocess.TimeoutExpired:
        content += b'\nFAIL: timed out terminating ModuleCheck\n'
    content += b'\nFAIL: Simulator launch console exceeded 90 seconds\n'
if len(content) >= 8388608:
    content += b'\nTRUNCATED: launch console reached 8 MiB\n'
    status = status or 1
Path(output).write_bytes(content)
sys.stdout.buffer.write(content)
fixture = b''
if status == 0:
    # simctl 可先返回 PID；直到 app 完成或明确失败，不把 launch 返回当完成。
    while time.monotonic() < deadline:
        path = Path(app_result)
        if path.is_file():
            with path.open('rb') as stream:
                fixture = stream.read(65536)
        if re.search(rb'^FAIL:', fixture, re.MULTILINE) or (
            re.search(rb'^PASS: location receiver', fixture, re.MULTILINE) and
            re.search(rb'^COMPLETE: exit=0$', fixture, re.MULTILINE)
        ):
            break
        time.sleep(0.2)
if status != 0:
    state = 'console-timeout' if status == 124 else 'launcher-failed'
elif re.search(rb'^FAIL:', fixture, re.MULTILINE):
    state = 'fixture-failed'
elif re.search(rb'^PASS: location receiver', fixture, re.MULTILINE) and re.search(rb'^COMPLETE: exit=0$', fixture, re.MULTILINE):
    state = 'completed'
else:
    state = 'fixture-incomplete' if fixture else 'no-fixture-stage'
print(f'ModuleCheck fixture state: {state}')
pid = re.search(rb'^' + re.escape(bundle.encode()) + rb': (\d+)$', content, re.MULTILINE)
Path('ci-diagnostics/module-check/launch.json').write_text(json.dumps({
    'simulator': simulator, 'bundle': bundle, 'pid': int(pid[1]) if pid else None,
    'simctl_returncode': status, 'timed_out': status == 124, 'fixture_state': state,
}, indent=2) + '\n')
sys.exit(status)
LAUNCH
cp "$output/result.log" ci-diagnostics/module-check/console.log
if [[ -f "$app_result" ]]; then
    cp "$app_result" "$output/app-result.log"
    cp "$app_result" ci-diagnostics/module-check/app-result.log
    cat "$output/app-result.log"
fi
echo "ModuleCheck launch pipeline exit: $launch_status"
fixture_state=$(python3 -c 'import json; print(json.load(open("ci-diagnostics/module-check/launch.json"))["fixture_state"])')
# console 归属与 fixture 完成分开：必须有本次 app 的原始 PASS 和完成记录。
if [[ "$launch_status" -ne 0 || "$fixture_state" != completed ]] || ! grep -q '^PASS: location receiver' "$output/app-result.log" 2>/dev/null || ! grep -qx 'COMPLETE: exit=0' "$output/app-result.log" 2>/dev/null || grep -q '^FAIL:' "$output/app-result.log" 2>/dev/null; then
    echo "ModuleCheck failed or its required PASS/completion result is missing" >&2
    pid=$(python3 -c 'import json; print(json.load(open("ci-diagnostics/module-check/launch.json"))["pid"] or "")')
    if [[ -n "$pid" ]]; then
        xcrun simctl spawn "$simulator" launchctl procinfo "$pid" > ci-diagnostics/module-check/process.txt 2>&1 || true
    fi
    xcrun simctl spawn "$simulator" log show --last 5m --debug --info --style compact \
      --predicate 'process == "ModuleCheck" OR eventMessage CONTAINS "io.github.gycrosskit.location.module-check" OR eventMessage CONTAINS "ModuleCheck"' \
      2>&1 | head -c 8388608 > ci-diagnostics/module-check/unified.log || true
    if [[ $(wc -c < ci-diagnostics/module-check/unified.log) -ge 8388608 ]]; then
        echo 'TRUNCATED: scoped unified log reached 8 MiB' >> ci-diagnostics/module-check/unified.log
    fi
    python3 - "$HOME/Library/Logs/DiagnosticReports" "$HOME/Library/Developer/CoreSimulator/Devices/$simulator/data/Library/Logs/DiagnosticReports" "$HOME/Library/Developer/CoreSimulator/Devices/$simulator/data/Library/Logs/CrashReporter" "$output/launch-start" <<'CRASH_REPORTS' || true
from pathlib import Path
import sys, time
folders, stamp = list(map(Path, sys.argv[1:-1])), Path(sys.argv[-1])
for attempt in range(6):
    reports = [(index, path) for index, folder in enumerate(folders) for path in folder.glob('ModuleCheck*')
               if path.is_file() and path.stat().st_mtime_ns >= stamp.stat().st_mtime_ns]
    if reports or attempt == 5:
        break
    time.sleep(1)
for index, report in sorted(reports, key=lambda item: item[1].stat().st_mtime_ns, reverse=True)[:6]:
    with report.open('rb') as source:
        content = source.read(2097152)
    if report.stat().st_size > len(content):
        content += b'\nTRUNCATED: crash report reached 2 MiB\n'
    Path('ci-diagnostics/module-check', str(index) + '-' + report.name + '.txt').write_bytes(content)
print(f'ModuleCheck new crash reports: {len(reports)} (host and Simulator, up to 5 seconds for async reports; saved at most 6 reports)')
CRASH_REPORTS
    exit 1
fi
