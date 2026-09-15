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

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              long count() {
                // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
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

                import org.camunda.bpm.engine.RepositoryService;

                class Definitions {
                  private RepositoryService repositoryService;

                  Object definition() {
                    // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
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

                import org.camunda.bpm.engine.RepositoryService;

                class Deployer {
                  private RepositoryService repositoryService;

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

                    import org.camunda.bpm.engine.RepositoryService;

                    class Definitions {
                      private RepositoryService repositoryService;

                      void inspect() {
                        // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
                        var query = repositoryService.createDecisionDefinitionQuery();
                        // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
                        consume(repositoryService.createDeploymentQuery().list());
                      }

                      void consume(Object value) {}
                    }
                    """));
  }

  @Test
  void preservesRepositoryServiceWhenQueriesPreventDeploymentMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class MixedUsage {
              private RepositoryService repositoryService;

              void deploy() {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }

              long count() {
                return repositoryService.createProcessDefinitionQuery().count();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class MixedUsage {
              private RepositoryService repositoryService;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }

              long count() {
                // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
                return repositoryService.createProcessDefinitionQuery().count();
              }
            }
            """));
  }

  @Test
  void leavesDeploymentResultContextsForManualMigration() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              Object deploy() {
                return repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              Object deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                return repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """));
  }

  @Test
  void mapsEachRepositoryServiceFieldToItsOwnClient() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployers {
              private RepositoryService orders;
              private RepositoryService invoices;

              void deploy() {
                orders.createDeployment().addClasspathResource("bpmn/orders.bpmn").deploy();
                invoices.createDeployment().addClasspathResource("bpmn/invoices.bpmn").deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;

            class Deployers {
              private CamundaClient orders;
              private CamundaClient invoices;

              void deploy() {
                  orders
                          .newDeployResourceCommand()
                          .addResourceFromClasspath("bpmn/orders.bpmn")
                          .send()
                          .join();
                  invoices
                          .newDeployResourceCommand()
                          .addResourceFromClasspath("bpmn/invoices.bpmn")
                          .send()
                          .join();
              }
            }
            """));
  }

  @Test
  void doesNotCreateClientForNonFieldRepositoryServiceReceiver() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.ProcessEngine;

            class Deployer {
              private ProcessEngine engine;

              void deploy() {
                engine.getRepositoryService()
                    .createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.ProcessEngine;

            class Deployer {
              private ProcessEngine engine;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                engine.getRepositoryService()
                    .createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """));
  }

  @Test
  void doesNotRewriteShadowedRepositoryServiceParameter() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy(RepositoryService repositoryService) {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy(RepositoryService repositoryService) {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """));
  }

  @Test
  void preservesRepositoryServiceForUnsupportedFieldReferences() {
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
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }

              void passThrough() {
                consume(repositoryService);
              }

              void consume(RepositoryService service) {}
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }

              void passThrough() {
                // TODO: RepositoryService usage was not migrated automatically. Migrate it manually.
                consume(repositoryService);
              }

              void consume(RepositoryService service) {}
            }
            """));
  }

  @Test
  void preservesImportForRepositoryServiceReturnTypes() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              RepositoryService current() {
                return null;
              }

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
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private CamundaClient repositoryService;

              RepositoryService current() {
                return null;
              }

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
  void annotatesSplitDeploymentBuilders() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy() {
                var builder = repositoryService.createDeployment();
                builder.deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                var builder = repositoryService.createDeployment();
                // TODO: RepositoryService deployment method was not migrated automatically
                builder.deploy();
              }
            }
            """));
  }

  @Test
  void doesNotRewriteWhenExistingClientIsShadowed() {
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

              void deploy(String client) {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private CamundaClient client;
              private RepositoryService repositoryService;

              void deploy(String client) {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """));
  }

  @Test
  void doesNotRewriteQualifiedRepositoryServiceField() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Holder {
              RepositoryService repositoryService;
            }

            class Deployer {
              private RepositoryService repositoryService;
              private Holder holder;

              void deploy() {
                holder.repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Holder {
              RepositoryService repositoryService;
            }

            class Deployer {
              private RepositoryService repositoryService;
              private Holder holder;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                holder.repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """));
  }

  @Test
  void doesNotAnnotateUnrelatedQueryText() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;
              private Other other;

              void inspect() {
                repositoryService.createProcessDefinitionQuery();
                other.createOrderQuery();
              }
            }

            class Other {
              void createOrderQuery() {}
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;
              private Other other;

              void inspect() {
                // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
                repositoryService.createProcessDefinitionQuery();
                other.createOrderQuery();
              }
            }

            class Other {
              void createOrderQuery() {}
            }
            """));
  }

  @Test
  void defersInitializedRepositoryServiceField() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private ProcessEngine engine;
              private RepositoryService repositoryService = engine.getRepositoryService();

              void deploy() {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.ProcessEngine;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private ProcessEngine engine;
              private RepositoryService repositoryService = engine.getRepositoryService();

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """));
  }

  @Test
  void defersFieldConversionWhenFieldIsReturned() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              RepositoryService current() {
                return repositoryService;
              }

              void deploy() {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              RepositoryService current() {
                // TODO: RepositoryService usage was not migrated automatically. Migrate it manually.
                return repositoryService;
              }

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """));
  }

  @Test
  void addsTodoForDirectRepositoryServiceReferences() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              void assign(Holder holder) {
                holder.service = repositoryService;
              }

            }

            class Holder {
              RepositoryService service;
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              void assign(Holder holder) {
                // TODO: RepositoryService usage was not migrated automatically. Migrate it manually.
                holder.service = repositoryService;
              }

            }

            class Holder {
              RepositoryService service;
            }
            """));
  }

  @Test
  void defersExistingClientBindingWhenRepositoryServiceHasUnsupportedUse() {
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

              long count() {
                return repositoryService.createProcessDefinitionQuery().count();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private CamundaClient client;
              private RepositoryService repositoryService;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }

              long count() {
                // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
                return repositoryService.createProcessDefinitionQuery().count();
              }
            }
            """));
  }

  @Test
  void preservesImportForNestedRepositoryServiceTypes() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import java.util.List;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              List<RepositoryService> services() {
                return null;
              }

              RepositoryService[] serviceArray() {
                return null;
              }

              void deploy() {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import java.util.List;

            import io.camunda.client.CamundaClient;
            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private CamundaClient repositoryService;

              List<RepositoryService> services() {
                return null;
              }

              RepositoryService[] serviceArray() {
                return null;
              }

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
  void defersEnclosingRepositoryServiceFieldForNestedClassUse() {
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
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }

              class NestedDeployer {
                void deployNested() {
                  repositoryService.createDeployment()
                      .addClasspathResource("bpmn/nested.bpmn")
                      .deploy();
                }
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }

              class NestedDeployer {
                void deployNested() {
                  // TODO: RepositoryService deployment method was not migrated automatically
                  repositoryService.createDeployment()
                      .addClasspathResource("bpmn/nested.bpmn")
                      .deploy();
                }
              }
            }
            """));
  }

  @Test
  void defersRepositoryServiceFieldForLocalClassUse() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy() {
                class LocalDeployer {
                  void deployNested() {
                    repositoryService.createDeployment()
                        .addClasspathResource("bpmn/nested.bpmn")
                        .deploy();
                  }
                }
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              private RepositoryService repositoryService;

              void deploy() {
                class LocalDeployer {
                  void deployNested() {
                    // TODO: RepositoryService deployment method was not migrated automatically
                    repositoryService.createDeployment()
                        .addClasspathResource("bpmn/nested.bpmn")
                        .deploy();
                  }
                }
              }
            }
            """));
  }

  @Test
  void defersExternallyAccessibleRepositoryServiceField() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              RepositoryService repositoryService;

              void deploy() {
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Deployer {
              RepositoryService repositoryService;

              void deploy() {
                // TODO: RepositoryService deployment method was not migrated automatically
                repositoryService.createDeployment()
                    .addClasspathResource("bpmn/order.bpmn")
                    .deploy();
              }
            }
            """));
  }

  @Test
  void addsGuidanceForUnsupportedRepositoryServiceMethods() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              void delete(String deploymentId) {
                repositoryService.deleteDeployment(deploymentId);
              }
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              void delete(String deploymentId) {
                // TODO: RepositoryService usage was not migrated automatically. Migrate it manually.
                repositoryService.deleteDeployment(deploymentId);
              }
            }
            """));
  }

  @Test
  void annotatesQueryAndDeploymentUsageInTheSameStatement() {
    rewriteRun(
        spec -> spec.recipe(new MigrateRepositoryServiceRecipe()),
        java(
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              void inspect() {
                consume(
                    repositoryService.createDeployment()
                        .addClasspathResource("bpmn/order.bpmn")
                        .deploy(),
                    repositoryService.createDecisionDefinitionQuery().count());
              }

              void consume(Object deployment, long count) {}
            }
            """,
            """
            package org.camunda.community.migration.example;

            import org.camunda.bpm.engine.RepositoryService;

            class Definitions {
              private RepositoryService repositoryService;

              void inspect() {
                // TODO: RepositoryService query was not migrated automatically. Migrate it manually with the corresponding Camunda 8 Java client search request or REST endpoint.
                // TODO: RepositoryService deployment method was not migrated automatically
                consume(
                    repositoryService.createDeployment()
                        .addClasspathResource("bpmn/order.bpmn")
                        .deploy(),
                    repositoryService.createDecisionDefinitionQuery().count());
              }

              void consume(Object deployment, long count) {}
            }
            """));
  }
}
