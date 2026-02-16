/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static java.util.Map.of;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.migration.data.constants.MigratorConstants;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryFormMigrationTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldMigrateSingleForm() {
    // given - deploy a process with a form
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - history is migrated
    historyMigrator.migrate();

    // then - form definition should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);

    FormEntity form = forms.getFirst();
    assertThat(form.formId()).isEqualTo(prefixDefinitionId("simple-form"));
    assertThat(form.formKey()).isNotNull();
    assertThat(form.version()).isEqualTo(1L);
  }

  @Test
  public void shouldMigrateMultipleFormVersions() {
    // given - deploy same form multiple times (different versions)
    for (int i = 0; i < 3; i++) {
      repositoryService.createDeployment()
          .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // when - history is migrated
    historyMigrator.migrate();

    // then - all form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(3);

    // Verify all versions have unique keys and incremental versions
    assertThat(forms).extracting("formKey").doesNotHaveDuplicates();
    assertThat(forms).extracting("formId").containsOnly(prefixDefinitionId("simple-form"));
    assertThat(forms).extracting("version").containsExactlyInAnyOrder(1L, 2L, 3L);
  }

  @Test
  public void shouldMigrateFormWithTenant() {
    // given - deploy a form with tenant
    String tenantId = "tenant-123";
    repositoryService.createDeployment()
        .tenantId(tenantId)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - history is migrated
    historyMigrator.migrate();

    // then - form definition with tenant should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);

    FormEntity form = forms.getFirst();
    assertThat(form.formId()).isEqualTo(prefixDefinitionId("simple-form"));
    assertThat(form.tenantId()).isEqualTo(tenantId);
    assertThat(form.formKey()).isNotNull();
  }

  @Test
  public void shouldNotMigrateFormTwice() {
    // given - deploy a form
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - history is migrated twice
    historyMigrator.migrate();
    Long firstFormKey = searchForms("simple-form").getFirst().formKey();

    historyMigrator.migrate();

    // then - form should only be migrated once (same key)
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);
    assertThat(forms.getFirst().formKey()).isEqualTo(firstFormKey);
  }

  @Test
  public void shouldMigrateFormIncrementally() {
    // given - deploy first form
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - first migration
    historyMigrator.migrate();

    // then - first form should be migrated
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);
    assertThat(forms.getFirst().version()).isEqualTo(1L);

    // given - deploy second form version
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - second migration
    historyMigrator.migrate();

    // then - both forms should be migrated
    forms = searchForms("simple-form");
    assertThat(forms).hasSize(2);
    assertThat(forms).extracting(FormEntity::version).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  public void shouldMigrateFormWithMixedTenantAndNonTenant() {
    // given - deploy forms with and without tenant
    String tenantId = "tenant-A";

    repositoryService.createDeployment()
        .tenantId(null)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    repositoryService.createDeployment()
        .tenantId(tenantId)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - history is migrated
    historyMigrator.migrate();

    // then - both forms should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(2);

    // verify one with tenant and one without (default tenant)
    long withTenant = forms.stream()
        .filter(f -> tenantId.equals(f.tenantId()))
        .count();
    long withDefaultTenant = forms.stream()
        .filter(f -> MigratorConstants.C8_DEFAULT_TENANT.equals(f.tenantId()))
        .count();

    assertThat(withTenant).isEqualTo(1);
    assertThat(withDefaultTenant).isEqualTo(1);
  }


  @Test
  public void shouldHandleEmptyFormDeployment() {
    // given - no forms deployed
    // when - history is migrated
    historyMigrator.migrate();

    // then - migration should complete without errors (no forms to search)
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).isEmpty();
  }

  @Test
  public void shouldMigrateDifferentFormTypesInSameDeployment() {
    // given - deploy multiple different forms in same deployment
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithMultipleForms.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .addClasspathResource("io/camunda/migration/data/form/advanced-form.form")
        .deploy();

    // when - history is migrated
    historyMigrator.migrate();

    // then - both forms should be migrated
    List<FormEntity> simpleForms = searchForms("simple-form");
    List<FormEntity> advancedForms = searchForms("advanced-form");

    assertThat(simpleForms).hasSize(1);
    assertThat(advancedForms).hasSize(1);

    // verify different form ids
    assertThat(simpleForms.getFirst().formId()).isEqualTo(prefixDefinitionId("simple-form"));
    assertThat(advancedForms.getFirst().formId()).isEqualTo(prefixDefinitionId("advanced-form"));
  }

  @Test
  public void shouldMigrateFormVersionAcrossDifferentDeployments() {
    // given - deploy same form in different deployments
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithMultipleForms.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .addClasspathResource("io/camunda/migration/data/form/advanced-form.form")
        .deploy();

    // when - history is migrated
    historyMigrator.migrate();

    // then - simple-form should have 2 versions, advanced-form should have 1
    List<FormEntity> simpleForms = searchForms("simple-form");
    List<FormEntity> advancedForms = searchForms("advanced-form");

    assertThat(simpleForms).hasSize(2);
    assertThat(advancedForms).hasSize(1);
  }

  @Test
  public void shouldMigrateFormsWithSameKeyDifferentTenants() {
    // given - deploy same form key for different tenants
    String tenant1 = "tenant-X";
    String tenant2 = "tenant-Y";
    String tenant3 = "tenant-Z";

    repositoryService.createDeployment()
        .tenantId(tenant1)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    repositoryService.createDeployment()
        .tenantId(tenant2)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    repositoryService.createDeployment()
        .tenantId(tenant3)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - history is migrated
    historyMigrator.migrate();

    // then - all three forms should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(3);

    // verify all have same formId but different tenants
    assertThat(forms).allMatch(f -> prefixDefinitionId("simple-form").equals(f.formId()));
    assertThat(forms).extracting(FormEntity::tenantId).containsExactlyInAnyOrder(tenant1, tenant2, tenant3);

    // verify unique form keys
    List<Long> formKeys = forms.stream().map(FormEntity::formKey).toList();
    assertThat(formKeys).doesNotHaveDuplicates();
  }


  @Test
  public void shouldMigrateMultipleVersionsWithDifferentTenants() {
    // given - deploy multiple versions for different tenants
    String tenant1 = "tenant-alpha";
    String tenant2 = "tenant-beta";

    // Deploy 2 versions for tenant1
    for (int i = 0; i < 2; i++) {
      repositoryService.createDeployment()
          .tenantId(tenant1)
          .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // Deploy 3 versions for tenant2
    for (int i = 0; i < 3; i++) {
      repositoryService.createDeployment()
          .tenantId(tenant2)
          .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // when - history is migrated
    historyMigrator.migrate();

    // then - all versions for both tenants should be migrated
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(5);

    // verify tenant1 has versions 1 and 2
    List<FormEntity> tenant1Forms = forms.stream()
        .filter(f -> tenant1.equals(f.tenantId()))
        .toList();
    assertThat(tenant1Forms).hasSize(2);
    assertThat(tenant1Forms).extracting(FormEntity::version).containsExactlyInAnyOrder(1L, 2L);

    // verify tenant2 has versions 1, 2, and 3
    List<FormEntity> tenant2Forms = forms.stream()
        .filter(f -> tenant2.equals(f.tenantId()))
        .toList();
    assertThat(tenant2Forms).hasSize(3);
    assertThat(tenant2Forms).extracting(FormEntity::version).containsExactlyInAnyOrder(1L, 2L, 3L);
  }

  @Test
  public void shouldCountMigratedFormsCorrectly() {
    // given - deploy various forms
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithMultipleForms.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .addClasspathResource("io/camunda/migration/data/form/advanced-form.form")
        .deploy();

    repositoryService.createDeployment()
        .tenantId("tenant-1")
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - history is migrated
    historyMigrator.migrate();

    // then - count should be accurate: 3 forms total
    // simple-form v1 (default tenant), advanced-form v1 (default tenant), simple-form v1 (tenant-1)
    List<FormEntity> simpleForms = searchForms("simple-form");
    List<FormEntity> advancedForms = searchForms("advanced-form");

    assertThat(simpleForms).hasSize(2); // 1 default tenant + 1 tenant-1
    assertThat(advancedForms).hasSize(1); // 1 default tenant

    // verify all forms have valid keys
    assertThat(simpleForms).allMatch(f -> f.formKey() != null);
    assertThat(advancedForms).allMatch(f -> f.formKey() != null);
  }

  @Test
  public void shouldMigrateFormWithUserTask() {
    // given - deploy process with user task form in both C7 and C8
    deployer.deployCamunda7Process("processWithForm.bpmn");

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start process instances in C7
    for (int i = 0; i < 3; i++) {
      runtimeService.startProcessInstanceByKey("processWithFormId");
    }

    // and - history is migrated
    historyMigrator.migrate();

    // then - form should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);
    assertThat(forms.getFirst().formId()).isEqualTo(prefixDefinitionId("simple-form"));
    assertThat(forms.getFirst().formKey()).isNotNull();
    Long expectedFormKey = forms.getFirst().formKey();

    // and - process instances should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(3);

    // and - user tasks should reference the correct form
    for (var processInstance : processInstances) {
      var userTasks = searchHistoricUserTasks(processInstance.processInstanceKey());
      assertThat(userTasks).hasSize(1);
      assertThat(userTasks.getFirst().formKey()).isEqualTo(expectedFormKey);
    }
  }

  @Test
  public void shouldMigrateFormOnStartEvent() {
    // given - deploy process with start event
    deployer.deployCamunda7Process("processWithStartForm.bpmn");

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start process instances in C7
    for (int i = 0; i < 2; i++) {
      runtimeService.startProcessInstanceByKey("processWithStartForm");
    }

    // and - history is migrated
    historyMigrator.migrate();

    // then - form should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);
    assertThat(forms.getFirst().formId()).isEqualTo(prefixDefinitionId("simple-form"));
    assertThat(forms.getFirst().formKey()).isNotNull();

    // and - process instances with start form should be migrated
    var processInstances = searchHistoricProcessInstances("processWithStartForm");
    assertThat(processInstances).hasSize(2);

    // and - process definition should reference the correct start form
    var processWithStartForm = searchHistoricProcessDefinition("processWithStartForm");
    assertThat(processWithStartForm.formId()).isEqualTo(prefixDefinitionId("simple-form"));
  }

  @Test
  public void shouldMigrateUserTaskForms() {
    // given - deploy process with user task form
    deployer.deployCamunda7Process("processWithForm.bpmn");

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start process and complete user tasks
    runtimeService.startProcessInstanceByKey("processWithFormId");

    // and - history is migrated
    historyMigrator.migrate();

    // then - form should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);
    assertThat(forms.getFirst().formKey()).isNotNull();
    Long expectedFormKey = forms.getFirst().formKey();

    // and - process instance should be completed
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(1);

    // and - user task should be completed with correct form reference
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isEqualTo(expectedFormKey);
  }

  @Test
  public void shouldMigrateMultipleUserTaskFormsInSameProcess() {
    // given - deploy process with multiple user task forms
    deployer.deployCamunda7Process("processWithMultipleForms.bpmn");

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .addClasspathResource("io/camunda/migration/data/form/advanced-form.form")
        .deploy();

    // when - start process instances
    for (int i = 0; i < 2; i++) {
      runtimeService.startProcessInstanceByKey("processWithMultipleForms");
    }

    // and - history is migrated
    historyMigrator.migrate();

    // then - both forms should be migrated and searchable in C8
    List<FormEntity> simpleForms = searchForms("simple-form");
    List<FormEntity> advancedForms = searchForms("advanced-form");

    assertThat(simpleForms).hasSize(1);
    assertThat(advancedForms).hasSize(1);

    assertThat(simpleForms.getFirst().formId()).isEqualTo(prefixDefinitionId("simple-form"));
    assertThat(advancedForms.getFirst().formId()).isEqualTo(prefixDefinitionId("advanced-form"));

    // and - process instances should be migrated
    var processInstances = searchHistoricProcessInstances("processWithMultipleForms");
    assertThat(processInstances).hasSize(2);

    // and - user tasks should reference the appropriate forms
    for (var processInstance : processInstances) {
      var userTasks = searchHistoricUserTasks(processInstance.processInstanceKey());
      assertThat(userTasks).isNotEmpty();
      assertThat(userTasks).extracting("formKey")
          .containsAnyOf(simpleForms.getFirst().formKey(), advancedForms.getFirst().formKey());
    }
  }

  @Test
  public void shouldMigrateFormsWithUserTasksAcrossTenants() {
    // given - deploy same process with forms in different tenants
    String tenant1 = "tenant-A";
    String tenant2 = "tenant-B";

    deployer.deployCamunda7Process("processWithForm.bpmn", tenant1);
    deployer.deployCamunda7Process("processWithForm.bpmn", tenant2);

    repositoryService.createDeployment()
        .tenantId(tenant1)
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    repositoryService.createDeployment()
        .tenantId(tenant2)
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start process instances in both tenants
    runtimeService.createProcessInstanceByKey("processWithFormId").processDefinitionTenantId(tenant1).execute();
    runtimeService.createProcessInstanceByKey("processWithFormId").processDefinitionTenantId(tenant2).execute();

    // and - history is migrated
    historyMigrator.migrate();

    // then - forms for both tenants should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(2);

    // verify both tenants
    assertThat(forms).extracting(FormEntity::tenantId)
        .containsExactlyInAnyOrder(tenant1, tenant2);


    // and - process instances for both tenants should be migrated
    var allProcessInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(allProcessInstances).hasSize(2);
  }

  @Test
  public void shouldMigrateFormVersionsWithProcessInstancesPerVersion() {
    // given - deploy and run process with form version 1
    deployer.deployCamunda7Process("processWithForm.bpmn");
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();
    runtimeService.startProcessInstanceByKey("processWithFormId");

    // when - deploy and run process with form version 2
    deployer.deployCamunda7Process("processWithForm.bpmn");
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();
    runtimeService.startProcessInstanceByKey("processWithFormId");

    // and - history is migrated
    historyMigrator.migrate();

    // then - both form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(2);

    // verify versions
    assertThat(forms).extracting(FormEntity::version)
        .containsExactlyInAnyOrder(1L, 2L);


    // and - process instances using both form versions should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(2);

    // and - each process instance's user tasks should reference the correct form version
    for (var processInstance : processInstances) {
      var userTasks = searchHistoricUserTasks(processInstance.processInstanceKey());
      assertThat(userTasks).hasSize(1);
      // The form key should match one of the migrated form keys
      assertThat(userTasks.getFirst().formKey()).isIn(forms.stream().map(FormEntity::formKey).toList());
    }
  }

  // Version Binding Tests

  @Test
  public void shouldMigrateFormWithLatestBinding() {
    // given - deploy multiple form versions
    for (int i = 0; i < 3; i++) {
      repositoryService.createDeployment()
          .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // when - start process instance (should use latest form version = 3)
    runtimeService.startProcessInstanceByKey("processWithFormId");

    // and - history is migrated
    historyMigrator.migrate();

    // then - all form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(3);

    // verify all versions
    assertThat(forms).extracting(FormEntity::version)
        .containsExactlyInAnyOrder(1L, 2L, 3L);

    // and - process instance should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(1);

    // and - user task should reference a form (likely version 3 - latest)
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNotNull();
    assertThat(forms).anyMatch(f -> f.formKey().equals(userTasks.getFirst().formKey()));
  }

  @Test
  public void shouldMigrateFormWithVersionBinding() {
    // given - deploy multiple form versions
    for (int i = 0; i < 3; i++) {
      repositoryService.createDeployment()
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // when - deploy process that binds to version 1
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormVersionBinding.bpmn")
        .deploy();

    // and - start process instance (should use form version 1)
    runtimeService.startProcessInstanceByKey("processWithFormVersionBinding");

    // and - history is migrated
    historyMigrator.migrate();

    // then - all form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(3);

    // verify specific version 1 exists
    assertThat(forms).anyMatch(f -> f.version() == 1L);

    // and - process instance should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormVersionBinding");
    assertThat(processInstances).hasSize(1);

    // and - user task should reference version 1 form specifically
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNotNull();
    // The form key should match the version 1 form
    FormEntity version1Form = forms.stream().filter(f -> f.version() == 1L).findFirst().orElseThrow();
    assertThat(userTasks.getFirst().formKey()).isEqualTo(version1Form.formKey());
  }

  @Test
  public void shouldMigrateFormWithDeploymentBinding() {
    // given - deploy form version 1
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - deploy process with deployment binding (includes form)
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormDeploymentBinding.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // and - deploy another form version separately
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // and - start process instance (should use form from same deployment = version 2)
    runtimeService.startProcessInstanceByKey("processWithFormDeploymentBinding");

    // and - history is migrated
    historyMigrator.migrate();

    // then - all form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(3);

    // verify all versions
    assertThat(forms).extracting(FormEntity::version)
        .containsExactlyInAnyOrder(1L, 2L, 3L);

    // and - process instance should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormDeploymentBinding");
    assertThat(processInstances).hasSize(1);

    // and - user task should reference the form from the same deployment
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNotNull();
    // The form key should match the form version 2
    FormEntity formV1 = forms.stream().filter(f -> f.version().equals(2L)).toList().getFirst();
    assertThat(userTasks.getFirst().formKey()).isEqualTo(formV1.formKey());
  }

  @Test
  public void shouldMigrateMultipleProcessesWithDifferentFormVersionBindings() {
    // given - deploy 3 form versions
    for (int i = 0; i < 3; i++) {
      repositoryService.createDeployment()
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // when - deploy process with latest binding
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .deploy();

    // and - deploy process with specific version binding (version 1)
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormVersionBinding.bpmn")
        .deploy();

    // and - start instances of both processes
    runtimeService.startProcessInstanceByKey("processWithFormId");
    runtimeService.startProcessInstanceByKey("processWithFormVersionBinding");

    // and - history is migrated
    historyMigrator.migrate();

    // then - all form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(3);

    // verify all versions
    assertThat(forms).extracting(FormEntity::version)
        .containsExactlyInAnyOrder(1L, 2L, 3L);

    // and - both process instances should be migrated
    var latestBindingInstances = searchHistoricProcessInstances("processWithFormId");
    var versionBindingInstances = searchHistoricProcessInstances("processWithFormVersionBinding");
    assertThat(latestBindingInstances).hasSize(1);
    assertThat(versionBindingInstances).hasSize(1);

    // and - user tasks should reference the appropriate form versions
    // Latest binding should use version 3
    var latestBindingUserTasks = searchHistoricUserTasks(latestBindingInstances.getFirst().processInstanceKey());
    assertThat(latestBindingUserTasks).hasSize(1);
    FormEntity version3Form = forms.stream().filter(f -> f.version() == 3L).findFirst().orElseThrow();
    assertThat(latestBindingUserTasks.getFirst().formKey()).isEqualTo(version3Form.formKey());

    // Version binding should use version 1
    var versionBindingUserTasks = searchHistoricUserTasks(versionBindingInstances.getFirst().processInstanceKey());
    assertThat(versionBindingUserTasks).hasSize(1);
    assertThat(versionBindingUserTasks.getFirst().formKey()).isNotNull();
    FormEntity version1Form = forms.stream().filter(f -> f.version() == 1L).findFirst().orElseThrow();
    assertThat(versionBindingUserTasks.getFirst().formKey()).isEqualTo(version1Form.formKey());
  }

  @Test
  public void shouldMigrateFormsWithLatestBinding() {
    // given
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    runtimeService.startProcessInstanceByKey("processWithFormId");

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    runtimeService.startProcessInstanceByKey("processWithFormId");

    // when
    historyMigrator.migrate();

    // then - both form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(2);

    // verify versions
    assertThat(forms).extracting(FormEntity::version)
        .containsExactlyInAnyOrder(1L, 2L);

    // and - both process instances should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormId");

    assertThat(processInstances).hasSize(2);

    // and - each process instance's user tasks should reference the latest form version
    var formLatest = forms.stream().filter(f -> f.version() == 2L).findFirst().orElseThrow();
    ProcessInstanceEntity processInstanceFirst = processInstances.getFirst();
    var userTaskFirst = searchHistoricUserTasks(processInstanceFirst.processInstanceKey()).getFirst();
    assertThat(userTaskFirst.formKey()).isEqualTo(formLatest.formKey());

    ProcessInstanceEntity processInstanceSecond = processInstances.getLast();
    var userTaskSecond = searchHistoricUserTasks(processInstanceSecond.processInstanceKey()).getFirst();
    assertThat(userTaskSecond.formKey()).isEqualTo(formLatest.formKey());
  }

  @Test
  public void shouldMigrateFormWithVersionBindingAcrossTenants() {
    // given - deploy form versions for tenant 1
    String tenant1 = "tenant-version-1";
    for (int i = 0; i < 2; i++) {
      repositoryService.createDeployment()
          .tenantId(tenant1)
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // when - deploy process with version binding for tenant 1
    repositoryService.createDeployment()
        .tenantId(tenant1)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormVersionBinding.bpmn")
        .deploy();

    // and - start process instance (binds to version 1 of tenant 1)
    runtimeService.createProcessInstanceByKey("processWithFormVersionBinding")
        .processDefinitionTenantId(tenant1)
        .execute();

    // and - history is migrated
    historyMigrator.migrate();

    // then - all forms for tenant 1 should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(2);

    // verify both versions for tenant 1
    assertThat(forms).allMatch(f -> tenant1.equals(f.tenantId()));
    assertThat(forms).anyMatch(f -> f.version() == 1L);

    // and - process instance should be migrated
    assertThat(searchHistoricProcessInstances("processWithFormVersionBinding")).hasSize(1);
  }

  @Test
  public void shouldMigrateFormWithDeploymentBindingAcrossTenants() {
    // given - deploy form versions for different tenants with deployment binding
    String tenant1 = "tenant-deployment-1";
    String tenant2 = "tenant-deployment-2";

    // Deploy form v1 separately for tenant1
    repositoryService.createDeployment()
        .tenantId(tenant1)
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // Deploy process with form (deployment binding) for tenant1 - this creates form v2
    repositoryService.createDeployment()
        .tenantId(tenant1)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormDeploymentBinding.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // Deploy process with form (deployment binding) for tenant2 - this creates form v1 for tenant2
    repositoryService.createDeployment()
        .tenantId(tenant2)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormDeploymentBinding.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start process instances (should use forms from same deployment)
    runtimeService.createProcessInstanceByKey("processWithFormDeploymentBinding")
        .processDefinitionTenantId(tenant1)
        .execute();

    runtimeService.createProcessInstanceByKey("processWithFormDeploymentBinding")
        .processDefinitionTenantId(tenant2)
        .execute();

    // and - history is migrated
    historyMigrator.migrate();

    // then - all forms should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(3); // 2 versions for tenant1, 1 version for tenant2

    // verify tenant1 has 2 versions
    List<FormEntity> tenant1Forms = forms.stream()
        .filter(f -> tenant1.equals(f.tenantId()))
        .sorted(Comparator.comparing(FormEntity::version))
        .toList();
    assertThat(tenant1Forms).hasSize(2);
    assertThat(tenant1Forms).extracting(FormEntity::version).containsExactly(1L, 2L);

    // verify tenant2 has 1 version
    List<FormEntity> tenant2Forms = forms.stream()
        .filter(f -> tenant2.equals(f.tenantId()))
        .toList();
    assertThat(tenant2Forms).hasSize(1);
    assertThat(tenant2Forms.getFirst().version()).isEqualTo(1L);

    // and - process instances should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormDeploymentBinding");
    assertThat(processInstances).hasSize(2);

    var processInstanceTenant1 = processInstances.stream()
        .filter(f -> tenant1.equals(f.tenantId())).findFirst().orElseThrow();
    var userTaskTenant1 = searchHistoricUserTasks(processInstanceTenant1.processInstanceKey()).getFirst();
    assertThat(userTaskTenant1.formKey())
        .isEqualTo(forms.stream().filter(f -> tenant1.equals(f.tenantId()) && f.version() == 2L)
            .findFirst().orElseThrow().formKey());

    var processInstanceTenant2 = processInstances.stream()
        .filter(f -> tenant2.equals(f.tenantId())).findFirst().orElseThrow();
    var userTaskTenant2 = searchHistoricUserTasks(processInstanceTenant2.processInstanceKey()).getFirst();
    assertThat(userTaskTenant2.formKey())
        .isEqualTo(forms.stream().filter(f -> tenant2.equals(f.tenantId()))
            .findFirst().orElseThrow().formKey());
  }

  @Test
  public void shouldMigrateFormWithLatestBindingAcrossTenants() {
    // given - deploy multiple form versions for different tenants with latest binding
    String tenant1 = "tenant-latest-1";
    String tenant2 = "tenant-latest-2";

    // Deploy 2 versions of form and process for tenant1
    for (int i = 0; i < 2; i++) {
      repositoryService.createDeployment()
          .tenantId(tenant1)
          .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // Deploy 3 versions of form and process for tenant2
    for (int i = 0; i < 3; i++) {
      repositoryService.createDeployment()
          .tenantId(tenant2)
          .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // when - start process instances (should use latest form for each tenant)
    runtimeService.createProcessInstanceByKey("processWithFormId")
        .processDefinitionTenantId(tenant1)
        .execute();

    runtimeService.createProcessInstanceByKey("processWithFormId")
        .processDefinitionTenantId(tenant2)
        .execute();

    // and - history is migrated
    historyMigrator.migrate();

    // then - all forms should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(5); // 2 versions for tenant1, 3 versions for tenant2

    // verify tenant1 has 2 versions
    List<FormEntity> tenant1Forms = forms.stream()
        .filter(f -> tenant1.equals(f.tenantId()))
        .sorted(Comparator.comparing(FormEntity::version))
        .toList();
    assertThat(tenant1Forms).hasSize(2);
    assertThat(tenant1Forms).extracting(FormEntity::version).containsExactly(1L, 2L);

    // verify tenant2 has 3 versions
    List<FormEntity> tenant2Forms = forms.stream()
        .filter(f -> tenant2.equals(f.tenantId()))
        .sorted(Comparator.comparing(FormEntity::version))
        .toList();
    assertThat(tenant2Forms).hasSize(3);
    assertThat(tenant2Forms).extracting(FormEntity::version).containsExactly(1L, 2L, 3L);

    // and - process instances should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(2);

    var processInstanceTenant1 = processInstances.stream()
        .filter(f -> tenant1.equals(f.tenantId())).findFirst().orElseThrow();
    var userTaskTenant1 = searchHistoricUserTasks(processInstanceTenant1.processInstanceKey()).getFirst();
    assertThat(userTaskTenant1.formKey())
        .isEqualTo(forms.stream().filter(f -> tenant1.equals(f.tenantId()) && f.version() == 2L)
            .findFirst().orElseThrow().formKey());

    var processInstanceTenant2 = processInstances.stream()
        .filter(f -> tenant2.equals(f.tenantId())).findFirst().orElseThrow();
    var userTaskTenant2 = searchHistoricUserTasks(processInstanceTenant2.processInstanceKey()).getFirst();
    assertThat(userTaskTenant2.formKey())
        .isEqualTo(forms.stream().filter(f -> tenant2.equals(f.tenantId()) && f.version() == 3L)
            .findFirst().orElseThrow().formKey());
  }

  @Test
  public void shouldMigrateMixedBindingsAcrossMultipleTenants() {
    // given - complex scenario with multiple tenants and mixed bindings
    String tenant1 = "tenant-mixed-1";
    String tenant2 = "tenant-mixed-2";

    // Deploy 3 form versions for tenant1
    for (int i = 0; i < 3; i++) {
      repositoryService.createDeployment()
          .tenantId(tenant1)
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // Deploy processes with different bindings for tenant1
    repositoryService.createDeployment()
        .tenantId(tenant1)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn") // latest binding
        .deploy();

    repositoryService.createDeployment()
        .tenantId(tenant1)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormVersionBinding.bpmn") // version 1 binding
        .deploy();

    repositoryService.createDeployment()
        .tenantId(tenant1)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormDeploymentBinding.bpmn") // deployment binding
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form") // creates v4 for tenant1
        .deploy();

    // Deploy 2 form versions for tenant2
    for (int i = 0; i < 2; i++) {
      repositoryService.createDeployment()
          .tenantId(tenant2)
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // Deploy process with latest binding for tenant2
    repositoryService.createDeployment()
        .tenantId(tenant2)
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .deploy();

    // when - start process instances
    runtimeService.createProcessInstanceByKey("processWithFormId")
        .processDefinitionTenantId(tenant1)
        .execute(); // uses v4 (latest)

    runtimeService.createProcessInstanceByKey("processWithFormVersionBinding")
        .processDefinitionTenantId(tenant1)
        .execute(); // uses v1

    runtimeService.createProcessInstanceByKey("processWithFormDeploymentBinding")
        .processDefinitionTenantId(tenant1)
        .execute(); // uses v4

    runtimeService.createProcessInstanceByKey("processWithFormId")
        .processDefinitionTenantId(tenant2)
        .execute(); // uses v4 (latest)

    // and - history is migrated
    historyMigrator.migrate();

    // then - all forms should be migrated
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(6); // 4 versions for tenant1, 2 versions for tenant2

    // verify tenant1 forms
    List<FormEntity> tenant1Forms = forms.stream()
        .filter(f -> tenant1.equals(f.tenantId()))
        .sorted(Comparator.comparing(FormEntity::version))
        .toList();
    assertThat(tenant1Forms).hasSize(4);
    assertThat(tenant1Forms).extracting(FormEntity::version).containsExactly(1L, 2L, 3L, 4L);

    // verify tenant2 forms
    List<FormEntity> tenant2Forms = forms.stream()
        .filter(f -> tenant2.equals(f.tenantId()))
        .sorted(Comparator.comparing(FormEntity::version))
        .toList();
    assertThat(tenant2Forms).hasSize(2);
    assertThat(tenant2Forms).extracting(FormEntity::version).containsExactly(1L, 2L);

    // and - all process instances should be migrated
    var latestBindingTenant1 = searchHistoricProcessInstances("processWithFormId").stream()
        .filter(pi -> tenant1.equals(pi.tenantId()))
        .toList();
    var versionBindingTenant1 = searchHistoricProcessInstances("processWithFormVersionBinding");
    var deploymentBindingTenant1 = searchHistoricProcessInstances("processWithFormDeploymentBinding");
    var latestBindingTenant2 = searchHistoricProcessInstances("processWithFormId").stream()
        .filter(pi -> tenant2.equals(pi.tenantId()))
        .toList();

    assertThat(latestBindingTenant1).hasSize(1);
    assertThat(versionBindingTenant1).hasSize(1);
    assertThat(deploymentBindingTenant1).hasSize(1);
    assertThat(latestBindingTenant2).hasSize(1);

    // and - user tasks should reference the appropriate form versions based on their bindings
    // Tenant1 - latest binding should use version 4
    var userTasksLatestT1 = searchHistoricUserTasks(latestBindingTenant1.getFirst().processInstanceKey());
    FormEntity version3FormT1 = tenant1Forms.stream().filter(f -> f.version() == 4L).findFirst().orElseThrow();
    assertThat(userTasksLatestT1.getFirst().formKey()).isEqualTo(version3FormT1.formKey());

    // Tenant1 - version binding should use version 1
    var userTasksVersionT1 = searchHistoricUserTasks(versionBindingTenant1.getFirst().processInstanceKey());
    FormEntity version1FormT1 = tenant1Forms.stream().filter(f -> f.version() == 1L).findFirst().orElseThrow();
    assertThat(userTasksVersionT1.getFirst().formKey()).isEqualTo(version1FormT1.formKey());

    // Tenant1 - deployment binding should use version 4
    var userTasksDeploymentT1 = searchHistoricUserTasks(deploymentBindingTenant1.getFirst().processInstanceKey());
    FormEntity version4FormT1 = tenant1Forms.stream().filter(f -> f.version() == 4L).findFirst().orElseThrow();
    assertThat(userTasksDeploymentT1.getFirst().formKey()).isEqualTo(version4FormT1.formKey());

    // Tenant2 - latest binding should use version 2
    var userTasksLatestT2 = searchHistoricUserTasks(latestBindingTenant2.getFirst().processInstanceKey());
    FormEntity version2FormT2 = tenant2Forms.stream().filter(f -> f.version() == 2L).findFirst().orElseThrow();
    assertThat(userTasksLatestT2.getFirst().formKey()).isEqualTo(version2FormT2.formKey());
  }

  @Test
  public void shouldMigrateFormWithLatestBindingWhenNewVersionDeployed() {
    // given - deploy process with latest binding and form v1
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    runtimeService.startProcessInstanceByKey("processWithFormId");

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    runtimeService.startProcessInstanceByKey("processWithFormId");

    // and - history is migrated
    historyMigrator.migrate();

    // then - both form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(2);

    // verify both versions
    assertThat(forms).extracting(FormEntity::version)
        .containsExactlyInAnyOrder(1L, 2L);

    // and - both process instances should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(2);

    processInstances = processInstances.stream().sorted(Comparator.comparing(ProcessInstanceEntity::startDate)).toList();
    FormEntity formV2 = forms.stream().filter(f -> f.version() == 2L).findFirst().orElseThrow();

    var userTaskFirstProcess = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey()).getFirst();
    assertThat(userTaskFirstProcess.formKey()).isEqualTo(formV2.formKey());

    var userTaskLastProcess = searchHistoricUserTasks(processInstances.getLast().processInstanceKey()).getFirst();
    assertThat(userTaskLastProcess.formKey()).isEqualTo(formV2.formKey());
  }

  @Test
  public void shouldMigrateFormWithDeploymentBindingInSameDeployment() {
    // given - deploy process and form together (deployment binding)
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormDeploymentBinding.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - deploy another form version separately
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // and - start process instance (should use form from same deployment = v1)
    runtimeService.startProcessInstanceByKey("processWithFormDeploymentBinding");

    // and - history is migrated
    historyMigrator.migrate();

    // then - both form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(2);

    // verify version 1 (from same deployment) exists
    assertThat(forms).anyMatch(f -> f.version() == 1L);
    assertThat(forms).anyMatch(f -> f.version() == 2L);

    // and - process instance should be migrated
    var processInstances = searchHistoricProcessInstances("processWithFormDeploymentBinding");
    assertThat(processInstances).hasSize(1);

    // and - user task should reference the form from the same deployment (version 1)
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNotNull();
    FormEntity version1Form = forms.stream().filter(f -> f.version() == 1L).findFirst().orElseThrow();
    assertThat(userTasks.getFirst().formKey()).isEqualTo(version1Form.formKey());
  }

  @Test
  public void shouldMigrateMultipleFormVersionsWithMixedBindings() {
    // given - deploy 3 form versions
    for (int i = 0; i < 3; i++) {
      repositoryService.createDeployment()
          .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
          .deploy();
    }

    // when - deploy processes with different bindings
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn") // latest
        .deploy();

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormVersionBinding.bpmn") // version 1
        .deploy();

    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithFormDeploymentBinding.bpmn") // deployment
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form") // v4
        .deploy();

    // and - start instances of all processes
    runtimeService.startProcessInstanceByKey("processWithFormId"); // uses v3 (latest)
    runtimeService.startProcessInstanceByKey("processWithFormVersionBinding"); // uses v1
    runtimeService.startProcessInstanceByKey("processWithFormDeploymentBinding"); // uses v4

    // and - history is migrated
    historyMigrator.migrate();

    // then - all 4 form versions should be migrated and searchable in C8
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(4);

    // verify all versions
    assertThat(forms).extracting(FormEntity::version)
        .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);

    // and - all process instances should be migrated
    var latestBindingInstances = searchHistoricProcessInstances("processWithFormId");
    var versionBindingInstances = searchHistoricProcessInstances("processWithFormVersionBinding");
    var deploymentBindingInstances = searchHistoricProcessInstances("processWithFormDeploymentBinding");
    assertThat(latestBindingInstances).hasSize(1);
    assertThat(versionBindingInstances).hasSize(1);
    assertThat(deploymentBindingInstances).hasSize(1);

    // and - user tasks should reference the appropriate form versions based on their bindings
    // Latest binding should use version 3
    var latestBindingUserTasks = searchHistoricUserTasks(latestBindingInstances.getFirst().processInstanceKey());
    assertThat(latestBindingUserTasks).hasSize(1);
    assertThat(latestBindingUserTasks.getFirst().formKey()).isNotNull();

    // Version binding should use version 1
    var versionBindingUserTasks = searchHistoricUserTasks(versionBindingInstances.getFirst().processInstanceKey());
    assertThat(versionBindingUserTasks).hasSize(1);
    assertThat(versionBindingUserTasks.getFirst().formKey()).isNotNull();
    FormEntity version1Form = forms.stream().filter(f -> f.version() == 1L).findFirst().orElseThrow();
    assertThat(versionBindingUserTasks.getFirst().formKey()).isEqualTo(version1Form.formKey());

    // Deployment binding should use version 4 (from same deployment)
    var deploymentBindingUserTasks = searchHistoricUserTasks(deploymentBindingInstances.getFirst().processInstanceKey());
    assertThat(deploymentBindingUserTasks).hasSize(1);
    assertThat(deploymentBindingUserTasks.getFirst().formKey()).isNotNull();
    FormEntity version4Form = forms.stream().filter(f -> f.version() == 4L).findFirst().orElseThrow();
    assertThat(deploymentBindingUserTasks.getFirst().formKey()).isEqualTo(version4Form.formKey());
  }

  @Test
  public void shouldHandleFormReferenceExpression() {
    // given - deploy process with form reference as expression
    deployer.deployCamunda7Process("processWithFormExpression.bpmn");

    // and - deploy a form that could be referenced by the expression
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start process instance with form key variable
    runtimeService.startProcessInstanceByKey("processWithFormExpression", of("formKey", "simple-form"));

    // and - history is migrated
    historyMigrator.migrate();

    // then - process instance should be migrated successfully
    var processInstances = searchHistoricProcessInstances("processWithFormExpression");
    assertThat(processInstances).hasSize(1);

    // and - form should still be migrated (deployed separately)
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);
    assertThat(forms.getFirst().formKey()).isNotNull();
    var processInstance = searchHistoricProcessInstances("processWithFormExpression").getFirst();
    var userTasks = searchHistoricUserTasks(processInstance.processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNull();

    // Note: The form reference expression "${formKey}" cannot be evaluated during migration
    // since we only have the expression text, not the runtime variables.
    // The migration should handle this gracefully by continuing with the migration.
  }

  // Error Scenario Tests

  @Test
  public void shouldHandleEmbeddedFormKeyGracefully() {
    // given - deploy process with embedded form key (unsupported in C8)
    deployer.deployCamunda7Process("processWithEmbeddedFormKey.bpmn");

    // when - start process instance
    runtimeService.startProcessInstanceByKey("processWithEmbeddedFormKey");

    // and - history is migrated
    historyMigrator.migrate();

    // then - process instance should be migrated successfully despite unsupported form type
    var processInstances = searchHistoricProcessInstances("processWithEmbeddedFormKey");
    assertThat(processInstances).hasSize(1);

    // and - user task should be migrated without form reference (not supported in C8)
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNull();

    // and - no forms should be migrated (embedded forms are not Camunda Forms)
    List<FormEntity> forms = searchForms();
    assertThat(forms).isEmpty();
  }

  @Test
  public void shouldHandleDeployedFormKeyGracefully() {
    // given - deploy process with deployed form key (unsupported in C8)
    deployer.deployCamunda7Process("processWithDeployedFormKey.bpmn");

    // when - start process instance
    runtimeService.startProcessInstanceByKey("processWithDeployedFormKey");

    // and - history is migrated
    historyMigrator.migrate();

    // then - process instance should be migrated successfully despite unsupported form type
    var processInstances = searchHistoricProcessInstances("processWithDeployedFormKey");
    assertThat(processInstances).hasSize(1);

    // and - user task should be migrated without form reference (not supported in C8)
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNull();

    // and - no forms should be migrated (deployed HTML forms are not Camunda Forms)
    List<FormEntity> forms = searchForms("deployment");
    assertThat(forms).isEmpty();
  }

  @Test
  public void shouldHandleGeneratedTaskFormKeyGracefully() {
    // given - deploy process with generated task form key (unsupported in C8)
    deployer.deployCamunda7Process("processWithGeneratedTaskForm.bpmn");

    // when - start process instance
    runtimeService.startProcessInstanceByKey("processWithGeneratedTaskForm");

    // and - history is migrated
    historyMigrator.migrate();

    // then - process instance should be migrated successfully despite unsupported form type
    var processInstances = searchHistoricProcessInstances("processWithGeneratedTaskForm");
    assertThat(processInstances).hasSize(1);

    // and - user task should be migrated without form reference (not supported in C8)
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNull();

    // and - no forms should be migrated (generated task forms are not Camunda Forms)
    List<FormEntity> forms = searchForms();
    assertThat(forms).isEmpty();
  }

  @Test
  public void shouldHandleMissingFormReferenceGracefully() {
    // given - deploy process with reference to non-existent form
    deployer.deployCamunda7Process("processWithMissingFormRef.bpmn");

    // when - start process instance (note: form is NOT deployed)
    runtimeService.startProcessInstanceByKey("processWithMissingFormRef");

    // and - history is migrated
    historyMigrator.migrate();

    // then - process instance should be migrated successfully despite missing form
    var processInstances = searchHistoricProcessInstances("processWithMissingFormRef");
    assertThat(processInstances).hasSize(1);

    // and - user task should be migrated without form reference
    var userTasks = searchHistoricUserTasks(processInstances.getFirst().processInstanceKey());
    assertThat(userTasks).hasSize(1);
    assertThat(userTasks.getFirst().formKey()).isNull();

    // and - no forms should be migrated (form was never deployed)
    List<FormEntity> forms = searchForms();
    assertThat(forms).isEmpty();
  }

  @Test
  public void shouldMigrateMultipleProcessesWithMixedFormTypes() {
    // given - deploy processes with various form types
    deployer.deployCamunda7Process("processWithForm.bpmn");
    deployer.deployCamunda7Process("processWithEmbeddedFormKey.bpmn");
    deployer.deployCamunda7Process("processWithDeployedFormKey.bpmn");

    // and - deploy only the Camunda Form
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start instances of all processes
    runtimeService.startProcessInstanceByKey("processWithFormId");
    runtimeService.startProcessInstanceByKey("processWithEmbeddedFormKey");
    runtimeService.startProcessInstanceByKey("processWithDeployedFormKey");

    // and - history is migrated
    historyMigrator.migrate();

    // then - all process instances should be migrated successfully
    var supportedFormInstances = searchHistoricProcessInstances("processWithFormId");
    var embeddedFormInstances = searchHistoricProcessInstances("processWithEmbeddedFormKey");
    var deployedFormInstances = searchHistoricProcessInstances("processWithDeployedFormKey");

    assertThat(supportedFormInstances).hasSize(1);
    assertThat(embeddedFormInstances).hasSize(1);
    assertThat(deployedFormInstances).hasSize(1);

    // and - only the Camunda Form should be migrated
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);
    assertThat(forms.getFirst().formId()).isEqualTo(prefixDefinitionId("simple-form"));

    // and - only the supported form process should have a form key reference
    var supportedUserTasks = searchHistoricUserTasks(supportedFormInstances.getFirst().processInstanceKey());
    var embeddedUserTasks = searchHistoricUserTasks(embeddedFormInstances.getFirst().processInstanceKey());
    var deployedUserTasks = searchHistoricUserTasks(deployedFormInstances.getFirst().processInstanceKey());

    assertThat(supportedUserTasks.getFirst().formKey()).isNotNull();
    assertThat(embeddedUserTasks.getFirst().formKey()).isNull();
    assertThat(deployedUserTasks.getFirst().formKey()).isNull();
  }

  @Test
  public void shouldHandleProcessWithBothSupportedAndUnsupportedForms() {
    // given - deploy process with supported Camunda Form
    deployer.deployCamunda7Process("processWithForm.bpmn");
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start first instance (will use Camunda Form)
    runtimeService.startProcessInstanceByKey("processWithFormId");

    // and - deploy process with unsupported form type
    deployer.deployCamunda7Process("processWithEmbeddedFormKey.bpmn");
    runtimeService.startProcessInstanceByKey("processWithEmbeddedFormKey");

    // and - history is migrated
    historyMigrator.migrate();

    // then - both process instances should be migrated
    var supportedInstances = searchHistoricProcessInstances("processWithFormId");
    var unsupportedInstances = searchHistoricProcessInstances("processWithEmbeddedFormKey");

    assertThat(supportedInstances).hasSize(1);
    assertThat(unsupportedInstances).hasSize(1);

    // and - Camunda Form should be migrated
    List<FormEntity> forms = searchForms("simple-form");
    assertThat(forms).hasSize(1);

    // and - supported form process should have form key, unsupported should not
    var supportedUserTasks = searchHistoricUserTasks(supportedInstances.getFirst().processInstanceKey());
    var unsupportedUserTasks = searchHistoricUserTasks(unsupportedInstances.getFirst().processInstanceKey());

    assertThat(supportedUserTasks.getFirst().formKey()).isNotNull();
    assertThat(unsupportedUserTasks.getFirst().formKey()).isNull();
  }

  @Test
  public void shouldHandleFormMigrationBetweenDeployments() {
    // given - deploy form version 1
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when - start instance with form v1
    runtimeService.startProcessInstanceByKey("processWithFormId");

    // and - first migration
    historyMigrator.migrate();

    // then - form should be migrated
    var formAfterFirstMigration = searchForms("simple-form").getFirst();

    // when - deploy form version 2 and start another instance
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();
    runtimeService.startProcessInstanceByKey("processWithFormId");

    // and - second migration
    historyMigrator.migrate();

    // then - both form versions should be migrated
    List<FormEntity> formsAfterSecondMigration = searchForms("simple-form");
    assertThat(formsAfterSecondMigration).hasSize(2);
    assertThat(formsAfterSecondMigration).extracting(FormEntity::version)
        .containsExactlyInAnyOrder(1L, 2L);

    // and - all process instances should be migrated with their respective form versions
    var processInstances = searchHistoricProcessInstances("processWithFormId")
        .stream().sorted(Comparator.comparing(ProcessInstanceEntity::startDate)).toList();
    assertThat(processInstances).hasSize(2);
    assertThat(searchHistoricUserTasks(processInstances.getFirst().processInstanceKey()).getFirst().formKey())
        .isEqualTo(formAfterFirstMigration.formKey());
    assertThat(searchHistoricUserTasks(processInstances.getLast().processInstanceKey()).getFirst().formKey())
        .isEqualTo(formsAfterSecondMigration.stream()
            .filter(f -> !f.formKey().equals(formAfterFirstMigration.formKey())).findFirst().orElseThrow().formKey());
  }
}

