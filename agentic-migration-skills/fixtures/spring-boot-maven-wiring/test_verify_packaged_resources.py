import unittest

from verify_packaged_resources import (
    CLASS_ROOT,
    verify_packaged_resources,
)


class PackagedResourcesValidationTest(unittest.TestCase):
    def test_accepts_the_complete_packaged_inventory(self):
        verify_packaged_resources(
            [
                f"{CLASS_ROOT}converted-c8-message-start.bpmn",
                f"{CLASS_ROOT}converted-c8-message-decision.dmn",
            ]
        )

    def test_rejects_a_missing_resource(self):
        with self.assertRaisesRegex(ValueError, "matches no resources"):
            verify_packaged_resources(
                [f"{CLASS_ROOT}converted-c8-message-decision.dmn"]
            )

    def test_rejects_duplicate_archive_entries(self):
        with self.assertRaisesRegex(ValueError, "duplicate entries"):
            verify_packaged_resources(
                [
                    f"{CLASS_ROOT}converted-c8-message-start.bpmn",
                    f"{CLASS_ROOT}converted-c8-message-start.bpmn",
                    f"{CLASS_ROOT}converted-c8-message-decision.dmn",
                ]
            )

    def test_rejects_unexpected_matching_resources(self):
        with self.assertRaisesRegex(ValueError, "unexpected"):
            verify_packaged_resources(
                [
                    f"{CLASS_ROOT}converted-c8-message-start.bpmn",
                    f"{CLASS_ROOT}converted-c8-extra.bpmn",
                    f"{CLASS_ROOT}converted-c8-message-decision.dmn",
                ]
            )

    def test_ignores_resources_outside_the_classpath_root(self):
        with self.assertRaisesRegex(ValueError, "matches no resources"):
            verify_packaged_resources(
                [
                    f"{CLASS_ROOT}processes/converted-c8-message-start.bpmn",
                    f"{CLASS_ROOT}converted-c8-message-decision.dmn",
                ]
            )


if __name__ == "__main__":
    unittest.main()
