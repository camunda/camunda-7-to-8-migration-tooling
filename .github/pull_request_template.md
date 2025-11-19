# Pull Request Template

## Description
<!-- Describe your changes in detail -->

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Refactoring (no functional changes)

## Testing Checklist

### Black-Box Testing Requirements
- [ ] All new tests follow **black-box testing approach** (see [TESTING_GUIDELINES.md](TESTING_GUIDELINES.md))
- [ ] Tests verify behavior through **observable outputs** (logs, C8 API queries)
- [ ] Tests use **natural skip scenarios** instead of manipulating internal state
- [ ] Tests **DO NOT** access `DbClient` or `IdKeyMapper` (except in abstract test classes for cleanup)
- [ ] Tests **DO NOT** access classes in `..impl..` package (except logging constants)
- [ ] Architecture tests pass (`ArchitectureTest` validates these rules)

### Test Coverage
- [ ] Added tests for new functionality
- [ ] Updated tests for modified functionality
- [ ] All tests pass locally

## Architecture Compliance

Run architecture tests to ensure compliance:
```bash
mvn test -Dtest=ArchitectureTest
```

If architecture tests fail, refactor your tests to use:
- `LogCapturer` for log assertions
- `camundaClient.newProcessInstanceSearchRequest()` for C8 queries
- Natural skip scenarios (e.g., migrate children without parents)

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
<!-- Link to related issues: Fixes #123, Related to #456 -->

