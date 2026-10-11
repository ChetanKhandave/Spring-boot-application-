#!/usr/bin/env python3
"""Starts real applications; proves Eureka registration, gateway routing and distribution."""
import collections
import json
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
processes = []
logs = []
def get(path):
    with urllib.request.urlopen('http://localhost:' + path, timeout=5) as response:
        return json.load(response)
def wait(check, description, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if any(p.poll() is not None for p in processes):
            raise RuntimeError('An application exited; inspect printed logs')
        try:
            if check():
                return
        except Exception:
            pass
        time.sleep(2)
    raise AssertionError('Timed out: ' + description)
def start(module, port):
    log = tempfile.TemporaryFile(mode='w+')
    logs.append((module, port, log))
    processes.append(subprocess.Popen(['java', '-Xmx256m', '-jar', str(ROOT / module / 'target' / (module + '.jar')), '--server.port=' + str(port)], stdout=log, stderr=subprocess.STDOUT))
try:
    start('eureka-server', 8761)
    wait(lambda: get('8761/actuator/health')['status'] == 'UP', 'Eureka startup')
    for port in (8081, 8082, 8083):
        start('student-service', port)
    start('employee-service', 8091)
    start('college-service', 8101)
    start('api-gateway', 8080)
    wait(lambda: get('8080/students/101')['service'] == 'student-service', 'discovery propagation')
    def sample():
        counts = collections.Counter(get('8080/students/101')['instancePort'] for _ in range(12))
        return counts
    wait(lambda: set(sample()) == {'8081', '8082', '8083'}, 'all three student registrations')
    counts = sample()
    assert set(counts) == {'8081', '8082', '8083'}, counts
    assert all(value == 4 for value in counts.values()), counts
    for path, service in [('employees', 'employee-service'), ('colleges', 'college-service')]:
        wait(lambda: get('8080/' + path + '/101')['service'] == service, service + ' route')
    print('PASS: real Eureka discovery, three gateway routes, round-robin distribution:', dict(counts))
except Exception:
    for module, port, log in logs:
        log.seek(0)
        print(module, port, log.read()[-6000:])
    raise
finally:
    for process in reversed(processes):
        process.terminate()
    for process in processes:
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    for _, _, log in logs:
        log.close()
