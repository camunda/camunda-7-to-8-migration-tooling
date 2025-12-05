/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config;

import io.camunda.migration.data.config.property.InterceptorProperty;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.impl.interceptor.ByteArrayVariableValidator;
import io.camunda.migration.data.impl.interceptor.DateVariableTransformer;
import io.camunda.migration.data.impl.interceptor.FileVariableValidator;
import io.camunda.migration.data.impl.interceptor.NullVariableTransformer;
import io.camunda.migration.data.impl.interceptor.ObjectJavaVariableValidator;
import io.camunda.migration.data.impl.interceptor.ObjectJsonVariableTransformer;
import io.camunda.migration.data.impl.interceptor.ObjectXmlVariableTransformer;
import io.camunda.migration.data.impl.interceptor.PrimitiveVariableTransformer;
import io.camunda.migration.data.impl.interceptor.SpinJsonVariableTransformer;
import io.camunda.migration.data.impl.interceptor.SpinXmlVariableTransformer;
import io.camunda.migration.data.impl.logging.ConfigurationLogs;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for managing interceptors from both Spring context and config data files.
 */
@Configuration
public class InterceptorConfiguration {

  @Autowired
  protected ApplicationContext context;

  @Autowired
  protected MigratorProperties migratorProperties;

  /**
   * Creates a composite list of variable interceptors from both Spring context and config data files.
   *
   * @return List of configured variable interceptors
   */
  @Bean
  public List<VariableInterceptor> configuredVariableInterceptors() {
    ConfigurationLogs.logConfiguringInterceptors();

    // Get interceptors from Spring context (annotated with @Component)
    List<VariableInterceptor> contextInterceptors = new ArrayList<>(
        context.getBeansOfType(VariableInterceptor.class).values());

    // Handle unified interceptor configuration (supports both custom and built-in interceptor control)
    processUnifiedInterceptorConfiguration(contextInterceptors, migratorProperties.getInterceptors());

    // Sort by order annotation if present
    AnnotationAwareOrderComparator.sort(contextInterceptors);

    ConfigurationLogs.logTotalInterceptorsConfigured(contextInterceptors.size());
    return contextInterceptors;
  }

  /**
   * Processes unified interceptor configuration that supports both custom interceptors
   * and property-based configuration including the enabled property.
   *
   * @param contextInterceptors List of interceptors discovered from Spring context
   * @param interceptorConfigs List of interceptor configurations from config files
   */
  protected void processUnifiedInterceptorConfiguration(List<VariableInterceptor> contextInterceptors,
                                                       List<InterceptorProperty> interceptorConfigs) {
    if (interceptorConfigs == null || interceptorConfigs.isEmpty()) {
      ConfigurationLogs.logNoInterceptorsConfigured();
      return;
    }

    for (InterceptorProperty interceptorConfig : interceptorConfigs) {
      if (!interceptorConfig.isEnabled()) {
        // Handle interceptor disable by removing from context
        handleInterceptorDisable(contextInterceptors, interceptorConfig);
      } else {
        // Handle interceptor creation/registration or property binding
        VariableInterceptor existingInterceptor = findExistingInterceptor(contextInterceptors, interceptorConfig.getClassName());
        if (existingInterceptor != null) {
          // Interceptor already loaded by Spring @Component - log that it's already loaded
          ConfigurationLogs.logInterceptorAlreadyLoaded(interceptorConfig.getClassName());
        } else {
          // Create new interceptor instance
          registerCustomInterceptor(contextInterceptors, interceptorConfig);
        }
      }
    }
  }

  /**
   * Finds an existing interceptor in the context by class name.
   *
   * @param contextInterceptors List of interceptors in context
   * @param className Class name to search for
   * @return The interceptor if found, null otherwise
   */
  protected VariableInterceptor findExistingInterceptor(List<VariableInterceptor> contextInterceptors, String className) {
    return contextInterceptors.stream()
        .filter(interceptor -> interceptor.getClass().getName().equals(className))
        .findFirst()
        .orElse(null);
  }

  /**
   * Handles disabling any interceptor (built-in, programmatically, or declarative) by removing it from the context.
   *
   * @param contextInterceptors List of context interceptors to modify
   * @param interceptorConfig Configuration for the interceptor to disable
   */
  protected void handleInterceptorDisable(List<VariableInterceptor> contextInterceptors,
                                         InterceptorProperty interceptorConfig) {
    boolean removed = contextInterceptors.removeIf(interceptor ->
        interceptor.getClass().getName().equals(interceptorConfig.getClassName()));

    if (removed) {
      ConfigurationLogs.logInterceptorDisabled(interceptorConfig.getClassName());
    } else {
      ConfigurationLogs.logInterceptorNotFoundForDisabling(interceptorConfig.getClassName());
    }
  }

  /**
   * Registers a custom interceptor from configuration.
   *
   * @param contextInterceptors List of interceptors to add to
   * @param interceptorConfig Configuration for the custom interceptor
   */
  protected void registerCustomInterceptor(List<VariableInterceptor> contextInterceptors,
                                          InterceptorProperty interceptorConfig) {
    try {
      VariableInterceptor interceptor = createInterceptorInstance(interceptorConfig);
      contextInterceptors.add(interceptor);
      ConfigurationLogs.logSuccessfullyRegistered(interceptorConfig.getClassName());
    } catch (Exception e) {
      ConfigurationLogs.logFailedToRegister(interceptorConfig.getClassName(), e);
      throw new MigratorException(ConfigurationLogs.getFailedToRegisterError(interceptorConfig.getClassName()), e);
    }
  }

  /**
   * Creates a variable interceptor instance from the configuration.
   *
   * @param interceptorProperty Interceptor configuration
   * @return VariableInterceptor instance
   * @throws Exception if instantiation fails
   */
  protected VariableInterceptor createInterceptorInstance(InterceptorProperty interceptorProperty) throws Exception {
    String className = interceptorProperty.getClassName();
    if (className == null || className.trim().isEmpty()) {
      throw new IllegalArgumentException(ConfigurationLogs.getClassNameNullOrEmptyError());
    }

    ConfigurationLogs.logCreatingInstance(className);

    Class<?> clazz = Class.forName(className);
    if (!VariableInterceptor.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException(ConfigurationLogs.getClassNotImplementInterfaceError(className));
    }

    VariableInterceptor interceptor = (VariableInterceptor) clazz.getDeclaredConstructor().newInstance();

    // Set properties if provided
    Map<String, Object> properties = interceptorProperty.getProperties();
    if (properties != null && !properties.isEmpty()) {
      ConfigurationLogs.logSettingProperties(className);
      applyProperties(interceptor, properties, false);
    }

    return interceptor;
  }

  protected static <T> void applyProperties(T target, Map<String, Object> sourceMap, boolean ignoreUnknownFields) {
    ConfigurationPropertySource source = new MapConfigurationPropertySource(sourceMap);
    Binder binder = new Binder(source);
    try {
      if (ignoreUnknownFields) {
        binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(target));
      } else {
        binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(target),
            new NoUnboundElementsBindHandler(BindHandler.DEFAULT));
      }
    } catch (Exception e) {
      throw new MigratorException(ConfigurationLogs.getParsingConfigurationError(), e);
    }
  }

  @Bean
  public ByteArrayVariableValidator byteArrayVariableValidator() {
    return new ByteArrayVariableValidator();
  }

  @Bean
  public DateVariableTransformer dateVariableTransformer() {
    return new DateVariableTransformer();
  }

  @Bean
  public FileVariableValidator fileVariableValidator() {
    return new FileVariableValidator();
  }

  @Bean
  public NullVariableTransformer nullVariableTransformer() {
    return new NullVariableTransformer();
  }

  @Bean
  public ObjectJavaVariableValidator objectJavaVariableValidator() {
    return new ObjectJavaVariableValidator();
  }

  @Bean
  public ObjectJsonVariableTransformer objectJsonVariableTransformer() {
    return new ObjectJsonVariableTransformer();
  }

  @Bean
  public ObjectXmlVariableTransformer objectXmlVariableTransformer() {
    return new ObjectXmlVariableTransformer();
  }

  @Bean
  public PrimitiveVariableTransformer primitiveVariableTransformer() {
    return new PrimitiveVariableTransformer();
  }

  @Bean
  public SpinJsonVariableTransformer spinJsonVariableTransformer() {
    return new SpinJsonVariableTransformer();
  }

  @Bean
  public SpinXmlVariableTransformer spinXmlVariableTransformer() {
    return new SpinXmlVariableTransformer();
  }

}
