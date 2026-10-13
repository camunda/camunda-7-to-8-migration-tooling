#!/usr/bin/env python3

import json
import re
import sys
from pathlib import Path
from typing import Any

_SEMVER = re.compile(
    r"^v?(?P<major>0|[1-9]\d*)\."
    r"(?P<minor>0|[1-9]\d*)\."
    r"(?P<patch>0|[1-9]\d*)"
    r"(?:-(?P<prerelease>[0-9A-Za-z.-]+))?"
    r"(?:\+[0-9A-Za-z.-]+)?$"
)
_VersionKey = tuple[int, int, int, tuple[int, tuple[tuple[int, int | str], ...]]]


def _version_key(tag: str) -> _VersionKey | None:
    match = _SEMVER.fullmatch(tag)
    if match is None:
        return None

    prerelease = match.group("prerelease")
    if prerelease is None:
        prerelease_key = (1, ())
    else:
        identifiers = []
        for identifier in prerelease.split("."):
            if identifier.isdigit():
                identifiers.append((0, int(identifier)))
            else:
                identifiers.append((1, identifier))
        prerelease_key = (0, tuple(identifiers))

    return (
        int(match.group("major")),
        int(match.group("minor")),
        int(match.group("patch")),
        prerelease_key,
    )


def latest_previous_release_tag(
    releases: list[dict[str, Any]], release_version: str
) -> str:
    release_key = _version_key(release_version)
    if release_key is None:
        raise ValueError(f"Release version '{release_version}' is not a SemVer tag.")

    previous_releases = []
    for release in releases:
        if release.get("draft") is not False or release.get("published_at") is None:
            continue

        tag = release.get("tag_name")
        if not isinstance(tag, str) or tag == release_version:
            continue

        tag_key = _version_key(tag)
        if tag_key is not None and tag_key < release_key:
            previous_releases.append((tag_key, tag))

    return max(previous_releases, default=(None, ""))[1]


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(
            "Usage: previous_release_tag.py RELEASE_VERSION RELEASES_JSON",
            file=sys.stderr,
        )
        return 2

    release_version, releases_path = argv[1:]
    try:
        pages = json.loads(Path(releases_path).read_text(encoding="utf-8"))
        if not isinstance(pages, list) or any(not isinstance(page, list) for page in pages):
            raise ValueError("Expected paginated GitHub releases JSON.")
        releases = [release for page in pages for release in page]
        if any(not isinstance(release, dict) for release in releases):
            raise ValueError("Expected each GitHub release to be a JSON object.")
        print(latest_previous_release_tag(releases, release_version))
    except (OSError, json.JSONDecodeError, ValueError) as error:
        print(f"Could not select a previous release tag: {error}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
