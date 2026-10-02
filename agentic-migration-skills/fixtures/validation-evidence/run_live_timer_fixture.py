#!/usr/bin/env python3
"""Run the disposable Camunda Process Test and record its observed outcomes."""

import json
import os
import re
import subprocess
import sys
import uuid
from pathlib import Path


HERE = Path(__file__).resolve().parent
FIXTURE = HERE / "live-timer-fixture"
MODULE = FIXTURE / "process-test"
BUILD = MODULE / "target"
SESSION_FILE = BUILD / "testcontainers-session-id"
OBSERVATION = BUILD / "acceptance-observation.json"
VERSION = "8.9.21"


def docker(*args):
    result = subprocess.run(["docker", *args], capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(f"docker {' '.join(args)}: {result.stderr.strip()}")
    return result.stdout


def require_java_21():
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        raise RuntimeError("Set JAVA_HOME to a JDK 21 installation")
    result = subprocess.run(
        [str(Path(java_home) / "bin" / "java"), "-version"],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode or not re.search(r'version "21(?:[."])', result.stderr):
        raise RuntimeError("The disposable fixture requires JAVA_HOME to point to JDK 21")


def session_id():
    if SESSION_FILE.is_symlink():
        raise RuntimeError("Refusing to read a symlinked Testcontainers session file")
    try:
        value = SESSION_FILE.read_text(encoding="utf-8").strip()
        if str(uuid.UUID(value)) != value:
            raise ValueError("noncanonical UUID")
    except (OSError, ValueError) as exc:
        raise RuntimeError(
            "Fixture did not report a valid Testcontainers session; cleanup cannot be verified"
        ) from exc
    return value


def target_containers(session):
    listing = docker(
        "container",
        "ls",
        "--all",
        "--no-trunc",
        "--format",
        "{{.ID}}\t{{.Image}}",
        "--filter",
        f"label=org.testcontainers.sessionId={session}",
    )
    containers = []
    for line in listing.splitlines():
        container_id, separator, image = line.partition("\t")
        if not separator or not container_id:
            raise RuntimeError(f"Unexpected Docker container listing: {line}")
        if image.startswith("camunda/") and image.endswith(f":{VERSION}"):
            containers.append(container_id)
    return containers


def cleanup_session(session):
    errors = []
    for container_id in target_containers(session):
        try:
            docker("container", "rm", "--force", container_id)
        except (OSError, RuntimeError) as exc:
            errors.append(str(exc))
    try:
        remaining = target_containers(session)
    except (OSError, RuntimeError) as exc:
        errors.append(str(exc))
        remaining = []
    if remaining or errors:
        raise RuntimeError(f"Camunda {VERSION} cleanup failed: {remaining}; {'; '.join(errors)}")


def main():
    require_java_21()
    docker("info", "--format", "{{.ServerVersion}}")
    for artifact in (SESSION_FILE, OBSERVATION):
        if artifact.is_symlink():
            raise RuntimeError(f"Refusing to overwrite symlinked fixture artifact: {artifact}")
    if SESSION_FILE.exists():
        cleanup_session(session_id())
        SESSION_FILE.unlink()
    OBSERVATION.unlink(missing_ok=True)

    result = None
    failure = None
    try:
        result = subprocess.run(
            ["mvn", "--batch-mode", "-pl", "process-test", "-am", "test"],
            cwd=FIXTURE,
            capture_output=True,
            text=True,
            check=False,
            timeout=900,
        )
    except (OSError, subprocess.SubprocessError, KeyboardInterrupt) as exc:
        failure = exc
    if result is not None:
        print(result.stdout, end="")
        print(result.stderr, end="", file=sys.stderr)

    try:
        session = session_id()
        cleanup_session(session)
    except (OSError, RuntimeError) as exc:
        if failure is not None:
            raise RuntimeError(f"Fixture failed: {failure}; cleanup failed: {exc}") from exc
        if result is not None and result.returncode:
            raise RuntimeError(f"Maven exited {result.returncode}; cleanup failed: {exc}") from exc
        raise
    SESSION_FILE.unlink(missing_ok=True)
    if failure is not None:
        raise failure
    if result.returncode:
        return result.returncode
    if OBSERVATION.is_symlink() or not OBSERVATION.is_file():
        raise RuntimeError("Passing fixture did not produce a regular observation file")
    observed = json.loads(OBSERVATION.read_text(encoding="utf-8"))
    if not isinstance(observed, dict) or not all(
        isinstance(observed.get(case), dict) for case in ("case1", "active_timer")
    ):
        raise RuntimeError("Fixture observation must contain case1 and active_timer objects")
    print(
        json.dumps(
            {
                "deployment": {
                    "performed": True,
                    "reference": f"Camunda {VERSION}, Testcontainers session {session}",
                    "environment": "local",
                    "target_disposable": True,
                    "target_version": VERSION,
                },
                "cleanup": {
                    "completed": True,
                    "evidence_reference": (
                        f"Testcontainers session {session}: no Camunda {VERSION} containers remain"
                    ),
                },
                "observation": observed,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as exc:
        print(f"Live timer fixture failed: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        print("Live timer fixture interrupted; cleanup may need a retry", file=sys.stderr)
        sys.exit(130)
