# Setup Maven Action

## Intro

A Maven setup wrapper that configures a specific Maven and Java version, GitHub Cache, and
Camunda-Nexus cache. It reuses Maven on the runner when its version matches `maven-version`;
otherwise, it installs the requested version.

See [setup-java](https://github.com/actions/setup-java) for possible distribution keywords or how to define java versions.

## Usage

### Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| java-version | Allows setting a version version to overwrite the default | false | 21      |
| distribution | Allows changing the java distribution | false | temurin |
| maven-version | Allows overwriting the maven version installed by default | false | 3.9.16  |
| secrets | JSON wrapped secrets for easier secret passing | true |         |

## Example of using the action

```yaml
steps:
- uses: actions/checkout@v3
- name: Setup Maven
    uses: ./.github/actions/setup-maven
    with:
        secrets: ${{ toJSON(secrets) }}
        java-version: 21
        distribution: zulu
```
