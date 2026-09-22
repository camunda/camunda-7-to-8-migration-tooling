# Apache HttpClient dependency compatibility fixture

This fixture covers the dependency graph that can result when a migrated
Spring Boot 3.2 application overrides `httpclient5` without overriding its
HttpCore family. The `broken` project represents the red reproducer. The
`fixed` project aligns all three artifacts. The `control` project imports the
compatible Apache dependency management and verifies that the migration adds no
project-level override.

The projects use `camunda-spring-boot-3-starter:8.9.21` and a Spring Boot
`3.2.12` parent. The application context test creates a `CamundaClient` bean.
It does not contact a Camunda cluster.

## Running the evaluation

Run `./verify.sh` from this directory. The script:

1. asserts that `broken` resolves `httpclient5:5.6.3` with
   `httpcore5:5.2.5` and `httpcore5-h2:5.2.5`;
2. asserts that `fixed` resolves all three artifacts to the compatible
   `5.6.3` and `5.4.3` family;
3. runs the Spring context test for `fixed`;
4. asserts that `control` resolves the same family through an imported
   compatible dependency-management BOM; and
5. runs the Spring context test for `control`.

The `broken` project is an expected red reproducer. Its dependency tree
demonstrates why a compile-only migration check is insufficient.

## Expected migration decision

The migration must inspect the selected `httpclient5` POM and the active
dependency management before writing an override. It must manage all three
artifacts when the active graph is incompatible. It must leave the build
unchanged when the graph is already compatible.
