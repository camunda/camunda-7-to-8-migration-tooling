# Pull Request Template

## Description
<!-- Describe your changes in detail -->

## Work tracking (Required)

- **Tracking issue or PR:** For an issue, use `Closes #...` for full scope or `Refs #...` for partial; for a PR, use `Refs #...` or its URL.
- **Owner:** `@github-handle` (must match the tracking item assignee)
- **Project status:** `In Progress` in project `182` owned by `camunda`
- [ ] I linked this PR to a tracking issue or PR before implementation.
- [ ] The tracking item is assigned to the owner and marked `In Progress`.
- **Tracking exception (only when no issue or PR can be linked):** <!-- State the reason, scope, owner, and approver. Otherwise write N/A. -->

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Refactoring (no functional changes)
- [ ] Test-only changes (no production code changes)

## Testing Checklist

### Black-Box Testing Requirements
- [ ] Tests follow **black-box testing approach**: verify behavior through observable outputs (logs, C8 API queries, real-world skip scenarios)
- [ ] Tests **DO NOT** access implementation details (`DbClient`, `IdKeyMapper`, `..impl..` packages except logging constants)
- [ ] Architecture tests pass (`ArchitectureTest` validates these rules)

### Test Coverage
- [ ] Added tests for new functionality
- [ ] Updated tests for modified functionality
- [ ] All tests pass locally

## Defect evidence (Required for bug fixes; write N/A for other changes)

- **Defect class:** <!-- Use the taxonomy in AGENTS.md -->
- **Red reproducer or failing check before the fix:** <!-- Link the command, test, API check, or other deterministic evidence -->
- **Green evidence after the fix:** <!-- Link the passing result -->
- **Category surface covered:** <!-- Explain how the test/check covers the defect class, not only the reported instance -->

## Architecture Compliance

Run architecture tests to ensure compliance:
```bash
mvn install -DskipTests -pl data-migrator/distro -am && \
  mvn test -Pintegration -pl data-migrator/qa/integration-tests -Dtest=ArchitectureTest
```

If architecture tests fail, refactor your tests to use:
- `LogCapturer` for log assertions
- `camundaClient.new*SearchRequest()` for C8 queries
- Real-World skip scenarios (e.g., migrate children without parents)

## Documentation
- [ ] Updated TESTING_GUIDELINES.md if adding new test patterns
- [ ] Added Javadoc comments for public APIs
- [ ] Updated README if user-facing changes

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-reviewed the code
- [ ] Added comments for complex logic
- [ ] No new compiler warnings
- [ ] Dependent changes have been merged

## Related Issues
<!-- Link every implementation change: for an issue, use Closes #123 for full scope or Refs #123 for partial scope; for a PR, use Refs #123 or its URL. -->
