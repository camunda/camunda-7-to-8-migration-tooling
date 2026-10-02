"""Black-box regressions for M2 expression-prefix validation."""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


FIXTURE = Path(__file__).resolve().parent
SCRIPT = (
    FIXTURE.parents[1]
    / "skills"
    / "migrate-c7-to-c8-code"
    / "scripts"
    / "validate_model_expressions.py"
)
BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn"
ZEEBE_NS = "http://camunda.org/schema/zeebe/1.0"


def bpmn(body, attributes=""):
    return (
        f'<bpmn:definitions xmlns:bpmn="{BPMN_NS}" '
        f'xmlns:camunda="{CAMUNDA_NS}" xmlns:zeebe="{ZEEBE_NS}" {attributes}>'
        f"{body}</bpmn:definitions>"
    )


class ModelExpressionPrefixTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.source = self.root / "source.bpmn"
        self.converted = self.root / "converted-c8-source.bpmn"

    def run_validator(self, source, converted):
        self.source.write_text(source, encoding="utf-8")
        self.converted.write_text(converted, encoding="utf-8")
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--pair",
                str(self.source),
                str(self.converted),
            ],
            capture_output=True,
            check=False,
            text=True,
        )

    def test_dynamic_values_without_prefix_fail_across_model_fields(self):
        cases = (
            (
                "conditionExpression",
                bpmn(
                    '<bpmn:sequenceFlow id="flow">'
                    '<bpmn:conditionExpression language="feel">'
                    "decision.wait</bpmn:conditionExpression></bpmn:sequenceFlow>"
                ),
                bpmn(
                    '<bpmn:sequenceFlow id="flow">'
                    "<bpmn:conditionExpression>decision.wait"
                    "</bpmn:conditionExpression></bpmn:sequenceFlow>"
                ),
                "conditionExpression",
            ),
            (
                "zeebe:input/@source",
                bpmn(
                    '<bpmn:serviceTask id="task"><bpmn:extensionElements>'
                    "<camunda:inputOutput><camunda:inputParameter name=\"orderId\">"
                    "${order.id}</camunda:inputParameter></camunda:inputOutput>"
                    "</bpmn:extensionElements></bpmn:serviceTask>"
                ),
                bpmn(
                    '<bpmn:serviceTask id="task"><bpmn:extensionElements>'
                    '<zeebe:ioMapping><zeebe:input source="order.id" target="orderId"/>'
                    "</zeebe:ioMapping></bpmn:extensionElements></bpmn:serviceTask>"
                ),
                "@source",
            ),
            (
                "zeebe:output/@source",
                bpmn(
                    '<bpmn:serviceTask id="task"><bpmn:extensionElements>'
                    "<camunda:inputOutput><camunda:outputParameter name=\"status\">"
                    "${order.status}</camunda:outputParameter></camunda:inputOutput>"
                    "</bpmn:extensionElements></bpmn:serviceTask>"
                ),
                bpmn(
                    '<bpmn:serviceTask id="task"><bpmn:extensionElements>'
                    '<zeebe:ioMapping><zeebe:output source="order.status" target="status"/>'
                    "</zeebe:ioMapping></bpmn:extensionElements></bpmn:serviceTask>"
                ),
                "@source",
            ),
            (
                "zeebe:calledElement/@processId",
                bpmn('<bpmn:callActivity id="call" calledElement="${processId}"/>'),
                bpmn(
                    '<bpmn:callActivity id="call"><bpmn:extensionElements>'
                    '<zeebe:calledElement processId="processId"/>'
                    "</bpmn:extensionElements></bpmn:callActivity>"
                ),
                "@processId",
            ),
            (
                "zeebe:taskDefinition/@type",
                bpmn('<bpmn:serviceTask id="task" camunda:topic="${jobType}"/>'),
                bpmn(
                    '<bpmn:serviceTask id="task"><bpmn:extensionElements>'
                    '<zeebe:taskDefinition type="jobType"/>'
                    "</bpmn:extensionElements></bpmn:serviceTask>"
                ),
                "@type",
            ),
            (
                "zeebe:subscription/@correlationKey",
                bpmn(
                    '<bpmn:intermediateCatchEvent id="event"><bpmn:extensionElements>'
                    '<camunda:subscription correlationKey="${correlationKey}"/>'
                    "</bpmn:extensionElements></bpmn:intermediateCatchEvent>"
                ),
                bpmn(
                    '<bpmn:intermediateCatchEvent id="event"><bpmn:extensionElements>'
                    '<zeebe:subscription correlationKey="correlationKey"/>'
                    "</bpmn:extensionElements></bpmn:intermediateCatchEvent>"
                ),
                "@correlationKey",
            ),
            (
                "zeebe:assignmentDefinition",
                bpmn('<bpmn:userTask id="task" camunda:assignee="${reviewer}"/>'),
                bpmn(
                    '<bpmn:userTask id="task"><bpmn:extensionElements>'
                    '<zeebe:assignmentDefinition assignee="reviewer"/>'
                    "</bpmn:extensionElements></bpmn:userTask>"
                ),
                "@assignee",
            ),
            (
                "zeebe:formDefinition/@formId",
                bpmn('<bpmn:userTask id="task" camunda:formRef="${formId}"/>'),
                bpmn(
                    '<bpmn:userTask id="task"><bpmn:extensionElements>'
                    '<zeebe:formDefinition formId="formId"/>'
                    "</bpmn:extensionElements></bpmn:userTask>"
                ),
                "@formId",
            ),
        )

        for name, source, converted, expected in cases:
            with self.subTest(name=name):
                result = self.run_validator(source, converted)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn(expected, result.stderr)

    def test_prefixed_expressions_and_static_values_pass(self):
        source = bpmn(
            '<bpmn:sequenceFlow id="flow"><bpmn:conditionExpression '
            'language="feel">decision.wait</bpmn:conditionExpression></bpmn:sequenceFlow>'
            '<bpmn:sequenceFlow id="literalFlow"><bpmn:conditionExpression>'
            "true</bpmn:conditionExpression></bpmn:sequenceFlow>"
            '<bpmn:serviceTask id="task" camunda:topic="${jobType}"/>'
            '<bpmn:serviceTask id="expressionTask" '
            'camunda:expression="${sampleBean}"/>'
            '<bpmn:callActivity id="call" calledElement="${processId}"/>'
            '<bpmn:userTask id="user" camunda:assignee="${reviewer}" '
            'camunda:candidateGroups="approvers" camunda:formRef="${formId}"/>'
            '<bpmn:callActivity id="staticCall" calledElement="approval-process"/>'
            '<bpmn:userTask id="staticUser" camunda:assignee="jane" '
            'camunda:candidateGroups="approvers" camunda:formRef="review-form"/>'
            '<bpmn:intermediateCatchEvent id="staticEvent"><bpmn:extensionElements>'
            '<camunda:subscription correlationKey="literal-key"/>'
            "</bpmn:extensionElements></bpmn:intermediateCatchEvent>"
            '<bpmn:serviceTask id="staticMapping"><bpmn:extensionElements>'
            '<camunda:inputOutput><camunda:inputParameter name="source">approved'
            '</camunda:inputParameter><camunda:outputParameter name="status">done'
            '</camunda:outputParameter></camunda:inputOutput></bpmn:extensionElements>'
            '</bpmn:serviceTask>'
        )
        converted = bpmn(
            '<bpmn:sequenceFlow id="flow"><bpmn:conditionExpression>'
            "=decision.wait</bpmn:conditionExpression></bpmn:sequenceFlow>"
            '<bpmn:sequenceFlow id="literalFlow"><bpmn:conditionExpression>'
            "true</bpmn:conditionExpression></bpmn:sequenceFlow>"
            '<bpmn:serviceTask id="task"><bpmn:extensionElements>'
            '<zeebe:taskDefinition type="=jobType"/></bpmn:extensionElements>'
            "</bpmn:serviceTask>"
            '<bpmn:serviceTask id="expressionTask"><bpmn:extensionElements>'
            '<zeebe:taskDefinition type="sampleBean"/></bpmn:extensionElements>'
            "</bpmn:serviceTask>"
            '<bpmn:callActivity id="call"><bpmn:extensionElements>'
            '<zeebe:calledElement processId="=processId"/></bpmn:extensionElements>'
            "</bpmn:callActivity>"
            '<bpmn:callActivity id="staticCall"><bpmn:extensionElements>'
            '<zeebe:calledElement processId="approval-process"/>'
            "</bpmn:extensionElements></bpmn:callActivity>"
            '<bpmn:userTask id="user"><bpmn:extensionElements>'
            '<zeebe:assignmentDefinition assignee="=reviewer" '
            'candidateGroups="approvers"/>'
            '<zeebe:formDefinition formId="=formId"/>'
            "</bpmn:extensionElements></bpmn:userTask>"
            '<bpmn:userTask id="staticUser"><bpmn:extensionElements>'
            '<zeebe:assignmentDefinition assignee="jane" '
            'candidateGroups="approvers"/>'
            '<zeebe:formDefinition formId="review-form"/>'
            "</bpmn:extensionElements></bpmn:userTask>"
            '<bpmn:intermediateCatchEvent id="staticEvent">'
            '<bpmn:extensionElements><zeebe:subscription '
            'correlationKey="literal-key"/></bpmn:extensionElements>'
            "</bpmn:intermediateCatchEvent>"
            '<bpmn:serviceTask id="staticMapping"><bpmn:extensionElements>'
            '<zeebe:ioMapping><zeebe:input source="approved" target="source"/>'
            '<zeebe:output source="done" target="status"/></zeebe:ioMapping>'
            "</bpmn:extensionElements></bpmn:serviceTask>"
        )
        result = self.run_validator(source, converted)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_leftover_expression_language_attributes_fail(self):
        converted = bpmn(
            '<bpmn:sequenceFlow id="feel"><bpmn:conditionExpression '
            'language="feel">=true</bpmn:conditionExpression></bpmn:sequenceFlow>'
            '<bpmn:sequenceFlow id="juel"><bpmn:conditionExpression '
            'language="juel">=true</bpmn:conditionExpression></bpmn:sequenceFlow>',
            'expressionLanguage="http://www.w3.org/1999/XPath"',
        )
        result = self.run_validator(bpmn(""), converted)
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn('language="feel"', result.stderr)
        self.assertIn('language="juel"', result.stderr)
        self.assertIn("expressionLanguage", result.stderr)

    def test_valid_dmn_expression_language_is_not_rejected(self):
        dmn_namespace = "https://www.omg.org/spec/DMN/20191111/MODEL/"
        source = (
            f'<definitions xmlns="{dmn_namespace}" id="source">'
            "<decision id=\"decision\"/></definitions>"
        )
        converted = (
            f'<definitions xmlns="{dmn_namespace}" id="converted" '
            'expressionLanguage="https://www.omg.org/spec/DMN/20191111/FEEL/">'
            "<decision id=\"decision\"/></definitions>"
        )
        result = self.run_validator(source, converted)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_malformed_converted_xml_fails_with_an_error(self):
        result = self.run_validator(bpmn(""), "<bpmn:definitions>")
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn("cannot parse converted XML", result.stderr)


if __name__ == "__main__":
    unittest.main()
