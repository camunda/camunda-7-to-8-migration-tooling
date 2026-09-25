package org.example;

class SampleStarter {

  void start(CamundaClient client) {
    client.newCreateInstanceCommand()
        .bpmnProcessId("Sample")
        .latestVersion()
        .send()
        .join();
  }
}
