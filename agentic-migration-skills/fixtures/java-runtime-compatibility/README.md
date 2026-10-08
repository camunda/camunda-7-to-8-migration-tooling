# Java Runtime Compatibility

This fixture covers issue #2424. It checks the skill's phase-specific Java requirements.
Issue #2424 records a successful release 0.3.6 conversion under Java 26, so M1 and E1 set no upper
Java bound.

## Run the guidance regression

From the repository root, run:

```bash
python3 agentic-migration-skills/fixtures/java-runtime-compatibility/test_phase_java_compatibility.py
```

## Run the released CLI matrix

Use the procedure in
[`start-event-listener-artifact`](../start-event-listener-artifact/README.md).
Pass Java 21 and a newer runtime, such as Java 26 or the latest runtime available in CI.
The probe runs the same released CLI JAR under both runtimes.

## Check the migration workflow

1. For models-only M1, use Java 26 without a Java 21-25 installation. The skill must run the
   released Diagram Converter CLI without a repository build.
2. For Code + models with OpenRewrite and M1, run the model phase with Java 26. Give OpenRewrite a
   separate Java 21-25 runtime or select Approach B.
3. For M1 with Java 20, check that the skill reports the detected major and offers M2 or M3.
4. For a valid Java runtime and a failing converter command, check that the skill records the
   command, exit code, stdout, and stderr. It must not report a missing or incompatible JDK.
5. For Approach B, M2, or M3 without a separate M1 or E1 phase, check that the skill does not
   require a Java preflight.
