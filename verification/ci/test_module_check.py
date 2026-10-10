"""Exercise the real shell gate with delayed app records and SDK command fixtures."""
import json
import os
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import time


ROOT = Path(__file__).resolve().parents[2]
PASS = 'PASS: location receiver repeated invalidate, queued call, factory race, Context callback and SDK dealloc\n'
with TemporaryDirectory() as directory:
    root = Path(directory)
    (root / 'scripts').mkdir()
    source = (ROOT / 'scripts/verify-kuikly-module.sh').read_text()
    # Only shorten the production deadlines and redirect crash folders into this fixture.
    assert source.count('timeout=90') == source.count('deadline = time.monotonic() + 90') == 1
    source = source.replace('timeout=90', 'timeout=1').replace('deadline = time.monotonic() + 90', 'deadline = time.monotonic() + 1')
    source = source.replace('$HOME/Library', '$FIXTURE_HOME/Library')
    (root / 'scripts/verify-kuikly-module.sh').write_text(source)
    products = root / 'Products'
    for name in ('GycLocationKuikly', 'OpenKuiklyIOSRender'):
        folder = products / (name + '.framework')
        folder.mkdir(parents=True)
        (folder / name).touch()
    tools = root / 'tools'
    tools.mkdir()
    (tools / 'codesign').write_text('#!/usr/bin/env bash\nexit 0\n')
    (tools / 'xcrun').write_text(f'#!{sys.executable}\n' + '''import os, subprocess, sys, time
from pathlib import Path
args = sys.argv[1:]
data = Path(os.environ['FIXTURE_DATA'])
result = data / 'Documents/module-check-result.log'
if args[:2] == ['simctl', 'get_app_container']:
    print(data)
elif args[:2] == ['simctl', 'launch']:
    mode = os.environ['FIXTURE_MODE']
    report = Path(os.environ['FIXTURE_HOME']) / 'Library/Logs/DiagnosticReports/ModuleCheck-fixture.ips'
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text('fixture crash diagnostics')
    payload = os.environ['FIXTURE_PASS'] + 'COMPLETE: exit=0\\n'
    if mode == 'delayed':
        subprocess.Popen([sys.executable, '-c', 'import sys,time;from pathlib import Path;time.sleep(0.2);Path(sys.argv[1]).write_text(sys.argv[2])', str(result), payload], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    elif mode == 'failed':
        result.write_text('STAGE: Context drain requested\\nFAIL: fixture failure\\n')
    elif mode == 'incomplete':
        result.write_text(os.environ['FIXTURE_PASS'])
    elif mode in ('silent', 'nonzero'):
        result.write_text(payload)
    print('io.github.gycrosskit.location.module-check: 4242', flush=True)
    if mode == 'console_only':
        print(payload, flush=True)
    if mode == 'timeout':
        time.sleep(2)
    sys.exit(7 if mode == 'nonzero' else 0)
elif args[:2] == ['simctl', 'spawn']:
    print('fixture scoped process/termination diagnostics')
elif args and args[0] == '--sdk':
    print('/fixture/sdk')
''')
    for tool in tools.iterdir():
        tool.chmod(0o755)
    data = root / 'Data'
    (data / 'Documents').mkdir(parents=True)
    environment = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ['PATH'],
                       FIXTURE_HOME=str(root), FIXTURE_DATA=str(data), FIXTURE_PASS=PASS,
                       KUIKLY_MODULE_SIMULATOR='fixture-simulator')
    for mode, expected, state in [
        ('delayed', True, 'completed'), ('silent', True, 'completed'),
        ('console_only', False, 'no-fixture-stage'), ('stale', False, 'no-fixture-stage'),
        ('failed', False, 'fixture-failed'), ('incomplete', False, 'fixture-incomplete'),
        ('nonzero', False, 'launcher-failed'), ('timeout', False, 'console-timeout'),
    ]:
        if mode == 'stale':
            (data / 'Documents/module-check-result.log').write_text(PASS + 'COMPLETE: exit=0\n')
        started = time.monotonic()
        result = subprocess.run(['bash', str(root / 'scripts/verify-kuikly-module.sh'), str(products)],
                                env=dict(environment, FIXTURE_MODE=mode), capture_output=True, text=True, timeout=10)
        assert (result.returncode == 0) == expected, (mode, result.stdout, result.stderr)
        receipt = json.loads((root / 'ci-diagnostics/module-check/launch.json').read_text())
        assert receipt['fixture_state'] == state, (mode, receipt)
        assert receipt['pid'] == 4242
        if mode == 'delayed':
            assert time.monotonic() - started >= 0.2, 'launch returning 0 must not end the app wait'
        if not expected:
            assert (root / 'ci-diagnostics/module-check/unified.log').is_file()
            assert list((root / 'ci-diagnostics/module-check').glob('*ModuleCheck*.txt'))
        if mode == 'timeout':
            assert receipt['simctl_returncode'] == 124 and receipt['timed_out']
        if mode == 'nonzero':
            assert receipt['simctl_returncode'] == 7
print('PASS: delayed completion, independent app PASS, stale/console-only/missing completion, FAIL, nonzero launch and timeout; SDK calls are fixtures.')
