import unittest

from previous_release_tag import latest_previous_release_tag


def release(tag: str, *, draft: bool = False) -> dict[str, object]:
    return {
        "tag_name": tag,
        "draft": draft,
        "published_at": None if draft else "2026-01-01T00:00:00Z",
    }


class PreviousReleaseTagTest(unittest.TestCase):
    def test_new_release_line_uses_latest_earlier_semver_release(self) -> None:
        releases = [
            release("8.10.0"),
            release("0.3.10"),
            release("0.3.9"),
            release("0.1.0-alpha5"),
        ]

        self.assertEqual(
            "0.3.10", latest_previous_release_tag(releases, "8.10.0")
        )

    def test_old_maintenance_line_ignores_newer_release_line(self) -> None:
        releases = [release("8.10.0"), release("0.3.10"), release("0.3.9")]

        self.assertEqual(
            "0.3.10", latest_previous_release_tag(releases, "0.3.11")
        )

    def test_patch_release_uses_previous_patch_in_its_line(self) -> None:
        releases = [
            release("8.11.0"),
            release("8.10.1"),
            release("8.10.0"),
            release("0.3.10"),
        ]

        self.assertEqual(
            "8.10.1", latest_previous_release_tag(releases, "8.10.2")
        )

    def test_drafts_current_tag_and_future_tags_are_not_candidates(self) -> None:
        unpublished = release("0.4.0")
        unpublished["published_at"] = None
        releases = [
            release("8.11.0"),
            release("8.10.0", draft=True),
            release("8.10.0"),
            unpublished,
            release("0.3.10"),
            release("nightly"),
        ]

        self.assertEqual(
            "0.3.10", latest_previous_release_tag(releases, "8.10.0")
        )

    def test_prerelease_ordering_uses_the_latest_published_prerelease(self) -> None:
        releases = [
            release("0.3.0-rc.2"),
            release("0.3.0-rc.1"),
            release("0.2.9"),
        ]

        self.assertEqual(
            "0.3.0-rc.2", latest_previous_release_tag(releases, "0.3.0")
        )

    def test_first_release_has_no_previous_tag(self) -> None:
        self.assertEqual(
            "", latest_previous_release_tag([], "1.0.0")
        )


if __name__ == "__main__":
    unittest.main()
