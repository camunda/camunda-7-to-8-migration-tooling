/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.migrator.exception.EntityInterceptorException;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.impl.logging.ProcessDefinitionConverterLogs;
import io.camunda.migrator.interceptor.EntityInterceptor;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessDefinitionConverter implements EntityInterceptor {

  @Autowired
  private C7Client c7Client;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(ProcessDefinition.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    ProcessDefinition c7ProcessDefinition = (ProcessDefinition) context.getC7Entity();
    ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder builder =
        (ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 ProcessDefinitionDbModel.Builder is null in context");
    }

    String bpmnXml = getBpmnXmlAsString(c7ProcessDefinition);

    builder.processDefinitionKey(getNextKey())
        .processDefinitionId(c7ProcessDefinition.getKey())
        .resourceName(c7ProcessDefinition.getResourceName())
        .name(c7ProcessDefinition.getName())
        .tenantId(c7ProcessDefinition.getTenantId())
        .versionTag(c7ProcessDefinition.getVersionTag())
        .version(c7ProcessDefinition.getVersion())
        .bpmnXml(bpmnXml)
        .formId(null); // TODO https://github.com/camunda/camunda-bpm-platform/issues/5347
  }

  protected String getBpmnXmlAsString(ProcessDefinition processDefinition) {
    try {
      var resourceStream = c7Client.getResourceAsStream(processDefinition.getDeploymentId(),
          processDefinition.getResourceName());

      return readInputStreamToString(resourceStream);
    } catch (IOException e) {
      ProcessDefinitionConverterLogs.failedFetchingResourceStream(processDefinition.getId(), e.getMessage());
      return null;
    }
  }

  protected String readInputStreamToString(InputStream inputStream) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];

    for (int length; (length = inputStream.read(buffer)) != -1; ) {
      result.write(buffer, 0, length);
    }

    return result.toString(StandardCharsets.UTF_8);
  }
}
