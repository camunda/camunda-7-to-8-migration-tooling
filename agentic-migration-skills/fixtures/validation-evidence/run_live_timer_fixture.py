#!/usr/bin/env python3
"""Run the Camunda 8.9.21 timer fixture and record target cleanup."""

import json
import re
import subprocess
import sys
import threading
import time
from pathlib import Path


VERSION = "8.9.21"
IMAGE_TAG = f":{VERSION}"
FIXTURE = Path(__file__).resolve().parent / "live-timer-fixture"
OBSERVATION = FIXTURE / "process-test" / "target" / "acceptance-observation.json"


def docker(*arguments):
    return subprocess.run(
        ["docker", *arguments],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=30,
    ).stdout.strip()


def require_java_21():
    result = subprocess.run(
        ["mvn", "--version"],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError(f"Maven version check failed: {result.stdout.strip()}")
    match = re.search(r"Java version: (\d+)", result.stdout)
    if match is None or match.group(1) != "21":
        raise RuntimeError(
            "The live fixture requires Maven to run with Java 21. "
            f"Set JAVA_HOME to a Java 21 JDK. Maven reported: {result.stdout.splitlines()[0]}"
        )


def container_ids(all_containers=True):
    options = ["container", "ls", "--no-trunc", "--quiet"]
    if all_containers:
        options.insert(2, "--all")
    output = docker(*options)
    return set(output.splitlines()) if output else set()


def collect_events(process, lines):
    for line in process.stdout:
        lines.append(line)


def stop_event_stream(process, reader):
    if process.poll() is None:
        process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)
    reader.join(timeout=10)


def target_container_events(lines, existing_ids):
    created = set()
    destroyed = set()
    for line in lines:
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        actor = event.get("Actor") or {}
        container_id = event.get("id") or actor.get("ID")
        action = event.get("status") or event.get("Action")
        image = (actor.get("Attributes") or {}).get("image") or event.get("from", "")
        if not isinstance(container_id, str) or container_id in existing_ids:
            continue
        if action == "create":
            if image.startswith("camunda/") and image.endswith(IMAGE_TAG):
                created.add(container_id)
        elif action == "destroy" and (
            container_id in created
            or image.startswith("camunda/") and image.endswith(IMAGE_TAG)
        ):
            destroyed.add(container_id)
    return created, destroyed


def remove_created_containers(created):
    cleanup_errors = []
    try:
        remaining_ids = container_ids()
    except (OSError, subprocess.SubprocessError) as exc:
        return set(created), [f"Could not inspect fixture containers: {exc}"]

    for container_id in sorted(created):
        is_present = any(
            container_id.startswith(remaining) or remaining.startswith(container_id)
            for remaining in remaining_ids
        )
        if not is_present:
            continue
        try:
            docker("container", "rm", "--force", container_id)
        except (OSError, subprocess.SubprocessError) as exc:
            cleanup_errors.append(f"Could not remove fixture container {container_id}: {exc}")

    try:
        remaining_ids = container_ids()
    except (OSError, subprocess.SubprocessError) as exc:
        return set(created), cleanup_errors + [f"Could not verify fixture cleanup: {exc}"]

    leaked = {
        container_id
        for container_id in created
        if any(
            container_id.startswith(remaining) or remaining.startswith(container_id)
            for remaining in remaining_ids
        )
    }
    return leaked, cleanup_errors


def main():
    require_java_21()
    if not docker("info", "--format", "{{.ServerVersion}}"):
        raise RuntimeError("Docker did not return a server version")

    existing_ids = container_ids()
    running_targets = [
        image
        for image in docker("container", "ls", "--format", "{{.Image}}").splitlines()
        if image.startswith("camunda/") and image.endswith(IMAGE_TAG)
    ]
    if running_targets:
        raise RuntimeError(
            "Refusing to run while an existing Camunda 8.9.21 container is active"
        )

    if OBSERVATION.is_symlink():
        raise RuntimeError("Refusing to replace a symlinked acceptance observation")
    OBSERVATION.unlink(missing_ok=True)
    event_stream = subprocess.Popen(
        [
            "docker",
            "events",
            "--filter",
            "type=container",
            "--format",
            "{{json .}}",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        bufsize=1,
    )
    event_lines = []
    reader = threading.Thread(
        target=collect_events, args=(event_stream, event_lines), daemon=True
    )
    reader.start()
    time.sleep(0.5)
    if event_stream.poll() is not None:
        raise RuntimeError("Docker event capture did not start")

    completed = None
    command_error = None
    event_stream_error = None
    try:
        try:
            completed = subprocess.run(
                [
                    "mvn",
                    "--batch-mode",
                    "--no-transfer-progress",
                    "-f",
                    str(FIXTURE / "pom.xml"),
                    "test",
                ],
                cwd=FIXTURE,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                errors="replace",
                timeout=900,
                check=False,
            )
        except (OSError, subprocess.SubprocessError) as exc:
            command_error = exc
    finally:
        try:
            stop_event_stream(event_stream, reader)
        except (OSError, subprocess.SubprocessError) as exc:
            event_stream_error = exc

    created, destroyed = target_container_events(event_lines, existing_ids)
    leaked, cleanup_errors = remove_created_containers(created)
    if event_stream_error is not None:
        cleanup_errors.append(f"Could not stop Docker event capture: {event_stream_error}")
    if cleanup_errors or leaked:
        command_failure = (
            str(command_error)
            if command_error is not None
            else f"Maven exited with code {completed.returncode}"
            if completed is not None and completed.returncode != 0
            else "Maven fixture did not complete successfully"
        )
        raise RuntimeError(
            f"{command_failure}; fixture container cleanup failed: "
            f"errors={cleanup_errors}, remaining={sorted(leaked)}"
        ) from command_error
    if command_error is not None:
        raise command_error
    if completed is None:
        raise RuntimeError("Maven fixture did not return a process result")
    print(completed.stdout, end="" if completed.stdout.endswith("\n") else "\n")
    if completed.returncode != 0:
        return completed.returncode
    if not created:
        raise RuntimeError(f"No Camunda {VERSION} Testcontainers target was observed")
    if created - destroyed:
        raise RuntimeError(
            "The disposable Camunda target did not report destruction: "
            f"created={sorted(created)}, destroyed={sorted(destroyed)}, "
            "any remaining fixture containers were removed by the runner"
        )
    if not OBSERVATION.is_file():
        raise RuntimeError("Process Test did not write its acceptance observation")

    observation = json.loads(OBSERVATION.read_text(encoding="utf-8"))
    result = {
        "deployment": {
            "performed": True,
            "reference": f"Testcontainers image camunda/*:{VERSION}; containers {sorted(created)}",
            "environment": "local",
            "target_disposable": True,
            "target_version": VERSION,
        },
        "observation": observation,
        "cleanup": {
            "completed": True,
            "evidence_reference": f"Docker destroy events for {sorted(created)}",
        },
    }
    print(json.dumps(result, separators=(",", ":"), sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as exc:
        print(f"Live timer fixture failed: {exc}", file=sys.stderr)
        sys.exit(1)
