#!/usr/bin/env python3
"""
Start both System A (mock) and System B services.
Usage: python run_services.py
"""

import subprocess
import sys
import time
import os
import signal

os.chdir(os.path.dirname(os.path.abspath(__file__)))

processes = []


def cleanup(signum=None, frame=None):
    print("\n[Runner] Shutting down services...")
    for p in processes:
        try:
            p.terminate()
            p.wait(timeout=5)
        except Exception:
            p.kill()
    sys.exit(0)


signal.signal(signal.SIGINT, cleanup)
signal.signal(signal.SIGTERM, cleanup)

print("[Runner] Starting Mock System A on port 5001...")
p_a = subprocess.Popen(
    [sys.executable, "-m", "mock_system_a.app"],
    stdout=sys.stdout,
    stderr=sys.stderr,
)
processes.append(p_a)
time.sleep(1)

print("[Runner] Starting System B on port 5000...")
p_b = subprocess.Popen(
    [sys.executable, "-m", "system_b.app"],
    stdout=sys.stdout,
    stderr=sys.stderr,
)
processes.append(p_b)
time.sleep(1)

print("\n[Runner] Both services running!")
print("  System A (mock): http://127.0.0.1:5001")
print("  System B:        http://127.0.0.1:5000")
print("\n  Test endpoint: POST http://127.0.0.1:5001/api/v1/analyze")
print("  Body: {\"query\": \"your question here\"}")
print("\n  Press Ctrl+C to stop.\n")

try:
    while True:
        if p_a.poll() is not None or p_b.poll() is not None:
            print("[Runner] A service exited unexpectedly!")
            break
        time.sleep(1)
except KeyboardInterrupt:
    pass
finally:
    cleanup()
