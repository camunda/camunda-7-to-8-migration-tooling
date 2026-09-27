# Customer review

This module uses the embedded Camunda 7 engine.

Start the application with `mvn spring-boot:run -Dspring-boot.run.profiles=local`.
The application listens on port 8080.
Open Tasklist at `http://localhost:8080/camunda/app/tasklist`.
Use `http://localhost:8080/engine-rest/engine` as the engine health check.
The review form uses `embedded:app:forms/review.html`.
Start the `customer-lookup` external-task worker before you run the process.
The synchronous billing delegate rolls back its transaction when it throws an exception.
