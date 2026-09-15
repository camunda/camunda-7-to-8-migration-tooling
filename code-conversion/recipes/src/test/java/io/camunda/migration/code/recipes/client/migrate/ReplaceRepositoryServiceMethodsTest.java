/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.MigrateRepositoryServiceRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class ReplaceRepositoryServiceMethodsTest implements RewriteTest {

  @Test
  void replacesClasspathDeployment() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.RepositoryService;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            class Deployer {
              private CamundaClient camundaClient;

              @Autowired
              private RepositoryService repositoryService;

              void deploy() {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .name("orders")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;

            @Component
            class Deployer {
              private CamundaClient camundaClient;

              void deploy() {
                  camundaClient
                          .newDeployResourceCommand()
                          .addResourceFromClasspath("bpmn/order.bpmn")
                          .send()
                          .join();
              }
            }
            """));
  }

  @Test
  void replacesInputStreamAndStringDeployments() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import java.io.InputStream;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy(InputStream stream, String text) {
                repositoryService.createDeployment().addInputStream("stream.bpmn", stream).deploy();
                repositoryService.createDeployment().addString("text.bpmn", text).deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;

            import java.io.InputStream;

            class Deployer {
              private CamundaClient repositoryService;

              void deploy(InputStream stream, String text) {
                  repositoryService
                          .newDeployResourceCommand()
                          .addResourceStream(stream, "stream.bpmn")
                          .send()
                          .join();
                  repositoryService
                          .newDeployResourceCommand()
                          .addResourceStringUtf8("text.bpmn", text)
                          .send()
                          .join();
              }
            }
            """));
  }

  @Test
  void addsTodoForRepositoryQueries() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              long count() {
                return repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey("order")
                    .count();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;

            class Definitions {
              private CamundaClient repositoryService;

              long count() {
                // TODO: RepositoryService queries have no Java client equivalent in C8. Use CamundaClient REST: newProcessDefinitionSearchRequest() or direct REST call.
                return repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey("order")
                    .count();
              }
            }
            """));
  }

  @Test
  void usesExistingCamundaClientIdentifier() {
    rewriteRun(
            spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
            java(
                """
                package org.camunda.community.migration.example;

                import io.camunda.client.CamundaClient;
                import org.camunda.bpm.engine.RepositoryService;

                class Deployer {
                  private CamundaClient client;
                  private RepositoryService repositoryService;

                  void deploy() {
                    repositoryService.createDeployment()
                        .addClasspathResource("bpmn/order.bpmn")
                        .deploy();
                  }
                }
                """,
                """
                package org.camunda.community.migration.example;

                import io.camunda.client.CamundaClient;

                class Deployer {
                  private CamundaClient client;

                  void deploy() {
                      client
                              .newDeployResourceCommand()
                              .addResourceFromClasspath("bpmn/order.bpmn")
                              .send()
                              .join();
                  }
                }
                """));
  }

  @Test
  void addsTodoForOtherRepositoryQueries() {
    rewriteRun(
            spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
            java(
                """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.RepositoryService;

                class Definitions {
                  private RepositoryService repositoryService;

                  Object definition() {
                    return repositoryService.createDecisionDefinitionQuery().list();
                  }
                }
                """,
                """
                package org.camunda.community.migration.example;

                import io.camunda.client.CamundaClient;

                class Definitions {
                  private CamundaClient repositoryService;

                  Object definition() {
                    // TODO: RepositoryService queries have no Java client equivalent in C8. Use CamundaClient REST: newProcessDefinitionSearchRequest() or direct REST call.
                    return repositoryService.createDecisionDefinitionQuery().list();
                  }
                }
                """));
  }

  @Test
  void leavesUnsupportedDeploymentWithTodo() {
    rewriteRun(
            spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
            java(
                """
                package org.camunda.community.migration.example;

                import org.camunda.bpm.engine.RepositoryService;

                class Deployer {
                  private RepositoryService repositoryService;

                  void deploy() {
                    repositoryService.createDeployment().name("orders").deploy();
                  }
                }
                """,
                """
                package org.camunda.community.migration.example;

                import io.camunda.client.CamundaClient;

                class Deployer {
                  private CamundaClient repositoryService;

                  void deploy() {
                    // TODO: RepositoryService deployment method was not migrated automatically
                    repositoryService.createDeployment().name("orders").deploy();
                  }
                }
                """));
  }

  @Test
  void preservesTenantAfterResourcesRegardlessOfSourceOrder() {
    rewriteRun(
                spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
                java(
                    """
                    package org.camunda.community.migration.example;

                    import org.camunda.bpm.engine.RepositoryService;

                    class Deployer {
                      private RepositoryService repositoryService;

                      void deploy() {
                        repositoryService.createDeployment()
                            .tenantId("tenant")
                            .addClasspathResource("bpmn/order.bpmn")
                            .deploy();
                      }
                    }
                    """,
                    """
                    package org.camunda.community.migration.example;

                    import io.camunda.client.CamundaClient;

                    class Deployer {
                      private CamundaClient repositoryService;

                      void deploy() {
                          repositoryService
                                  .newDeployResourceCommand()
                                  .addResourceFromClasspath("bpmn/order.bpmn")
                                  .tenantId("tenant")
                                  .send()
                                  .join();
                      }
                    }
                    """));
  }

  @Test
  void rewritesFullyQualifiedRepositoryServiceType() {
    rewriteRun(
                spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
                java(
                    """
                    package org.camunda.community.migration.example;

                    class Deployer {
                      private org.camunda.bpm.engine.RepositoryService repositoryService;

                      void deploy() {
                        repositoryService.createDeployment()
                            .addClasspathResource("bpmn/order.bpmn")
                            .deploy();
                      }
                    }
                    """,
                    """
                    package org.camunda.community.migration.example;

                    import io.camunda.client.CamundaClient;

                    class Deployer {
                      private CamundaClient repositoryService;

                      void deploy() {
                          repositoryService
                                  .newDeployResourceCommand()
                                  .addResourceFromClasspath("bpmn/order.bpmn")
                                  .send()
                                  .join();
                      }
                    }
                    """));
  }

  @Test
  void addsTodoToNonReturnQueryStatements() {
    rewriteRun(
                spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
                java(
                    """
                    package org.camunda.community.migration.example;

                    import org.camunda.bpm.engine.RepositoryService;

                    class Definitions {
                      private RepositoryService repositoryService;

                      void inspect() {
                        var query = repositoryService.createDecisionDefinitionQuery();
                        consume(repositoryService.createDeploymentQuery().list());
                      }

                      void consume(Object value) {}
                    }
                    """,
                    """
                    package org.camunda.community.migration.example;

                    import io.camunda.client.CamundaClient;

                    class Definitions {
                      private CamundaClient repositoryService;

                      void inspect() {
                        // TODO: RepositoryService queries have no Java client equivalent in C8. Use CamundaClient REST: newProcessDefinitionSearchRequest() or direct REST call.
                        var query = repositoryService.createDecisionDefinitionQuery();
                        // TODO: RepositoryService queries have no Java client equivalent in C8. Use CamundaClient REST: newProcessDefinitionSearchRequest() or direct REST call.
                        consume(repositoryService.createDeploymentQuery().list());
                      }

                      void consume(Object value) {}
                    }
                    """));
  }
}
