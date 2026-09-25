/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

/** Flags worker settings that can prevent declared job workers from registering. */
public class ValidateCamundaClientWorkerEnablement
    extends ScanningRecipe<ValidateCamundaClientWorkerEnablement.WorkerEnablementState> {

  private static final String CLIENT_ENABLED_PROPERTY = "camunda.client.enabled";
  private static final String WORKER_DEFAULT_ENABLED_PROPERTY =
      "camunda.client.worker.defaults.enabled";
  private static final String WORKER_DEFAULT_TYPE_PROPERTY =
      "camunda.client.worker.defaults.type";
  private static final String PROFILE_ACTIVATION_PROPERTY = "spring.config.activate.on-profile";
  private static final String ENABLED_SUFFIX = ".enabled";
  private static final Set<String> JOB_WORKER_ANNOTATION_TYPES =
      Set.of(
          "io.camunda.client.annotation.JobWorker",
          "io.camunda.zeebe.spring.client.annotation.JobWorker");

  /** Instantiates a Camunda client worker enablement validator. */
  public ValidateCamundaClientWorkerEnablement() {}

  @Override
  public @NonNull String getDisplayName() {
    return "Validate Camunda client job worker enablement";
  }

  @Override
  public @NonNull String getDescription() {
    return "Flags Camunda client settings that disable or make job worker registration"
        + " conditional.";
  }

  @Override
  public WorkerEnablementState getInitialValue(ExecutionContext ctx) {
    return new WorkerEnablementState();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(WorkerEnablementState state) {
    return new TreeVisitor<>() {
      @Override
      public Tree visit(Tree tree, ExecutionContext ctx) {
        if (!(tree instanceof SourceFile sourceFile)) {
          return tree;
        }

        String sourcePath = normalizePath(sourceFile.getSourcePath());
        sourceModuleRoot(sourcePath).ifPresent(state::addModuleRoot);
        if (isBuildDescriptor(sourcePath)) {
          state.addModuleRoot(parentPath(sourcePath));
        }

        if (tree instanceof J.CompilationUnit compilationUnit && isProductionJava(sourcePath)) {
          scanWorkers(compilationUnit, sourcePath, state, ctx);
        } else if (tree instanceof Properties.File propertiesFile
            && isApplicationConfiguration(sourcePath)) {
          scanProperties(propertiesFile, sourcePath, state);
        } else if (tree instanceof Yaml.Documents yamlDocuments
            && isApplicationConfiguration(sourcePath)) {
          scanYaml(yamlDocuments, sourcePath, state, ctx);
        }
        return tree;
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(WorkerEnablementState state) {
    Map<EntryKey, List<String>> findings = state.findings();
    return new TreeVisitor<>() {
      @Override
      public Tree visit(Tree tree, ExecutionContext ctx) {
        if (tree instanceof Properties.File propertiesFile) {
          String sourcePath = normalizePath(propertiesFile.getSourcePath());
          return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
              Properties.Entry visited = super.visitEntry(entry, ctx);
              return mark(
                  visited,
                  sourcePath,
                  visited.getKey(),
                  visited.getValue().getText(),
                  findings);
            }
          }.visit(propertiesFile, ctx);
        }
        if (tree instanceof Yaml.Documents yamlDocuments) {
          String sourcePath = normalizePath(yamlDocuments.getSourcePath());
          return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
              Yaml.Mapping visited = super.visitMapping(mapping, ctx);
              String parentPath = ValidateCamundaClientYaml.ancestorPath(getCursor());
              return visited.withEntries(
                  ListUtils.map(
                      visited.getEntries(),
                      entry -> {
                        if (entry.getValue() instanceof Yaml.Scalar value) {
                          String key =
                              ValidateCamundaClientYaml.join(
                                  parentPath, entry.getKey().getValue());
                          return mark(entry, sourcePath, key, value.getValue(), findings);
                        }
                        return entry;
                      }));
            }
          }.visit(yamlDocuments, ctx);
        }
        return tree;
      }
    };
  }

  private static void scanProperties(
      Properties.File propertiesFile, String sourcePath, WorkerEnablementState state) {
    List<Properties.Entry> documentEntries = new ArrayList<>();
    boolean conditionallyActive = false;
    for (Properties.Content content : propertiesFile.getContent()) {
      if (content instanceof Properties.Comment comment && comment.getMessage().equals("---")) {
        addPropertiesSettings(documentEntries, sourcePath, state, conditionallyActive);
        documentEntries.clear();
        conditionallyActive = false;
      } else if (content instanceof Properties.Entry entry) {
        documentEntries.add(entry);
        if (PROFILE_ACTIVATION_PROPERTY.equals(entry.getKey())
            && !entry.getValue().getText().isBlank()) {
          conditionallyActive = true;
        }
      }
    }
    addPropertiesSettings(documentEntries, sourcePath, state, conditionallyActive);
  }

  private static void addPropertiesSettings(
      List<Properties.Entry> entries,
      String sourcePath,
      WorkerEnablementState state,
      boolean conditionallyActive) {
    for (Properties.Entry entry : entries) {
      state.addSetting(
          sourcePath, entry.getKey(), entry.getValue().getText(), conditionallyActive);
    }
  }

  private static void scanYaml(
      Yaml.Documents yamlDocuments,
      String sourcePath,
      WorkerEnablementState state,
      ExecutionContext ctx) {
    for (Yaml.Document document : yamlDocuments.getDocuments()) {
      boolean conditionallyActive = hasProfileActivation(document, ctx);
      new YamlIsoVisitor<ExecutionContext>() {
        @Override
        public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
          Yaml.Mapping visited = super.visitMapping(mapping, ctx);
          String parentPath = ValidateCamundaClientYaml.ancestorPath(getCursor());
          for (Yaml.Mapping.Entry entry : visited.getEntries()) {
            if (entry.getValue() instanceof Yaml.Scalar value) {
              String key =
                  ValidateCamundaClientYaml.join(parentPath, entry.getKey().getValue());
              state.addSetting(sourcePath, key, value.getValue(), conditionallyActive);
            }
          }
          return visited;
        }
      }.visit(document, ctx);
    }
  }

  private static boolean hasProfileActivation(Yaml.Document document, ExecutionContext ctx) {
    boolean[] conditionallyActive = {false};
    new YamlIsoVisitor<ExecutionContext>() {
      @Override
      public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
        Yaml.Mapping visited = super.visitMapping(mapping, ctx);
        String parentPath = ValidateCamundaClientYaml.ancestorPath(getCursor());
        for (Yaml.Mapping.Entry entry : visited.getEntries()) {
          if (ValidateCamundaClientYaml.join(parentPath, entry.getKey().getValue())
                  .equals(PROFILE_ACTIVATION_PROPERTY)
              && entry.getValue() instanceof Yaml.Scalar profile
              && !profile.getValue().isBlank()) {
            conditionallyActive[0] = true;
          }
        }
        return visited;
      }
    }.visit(document, ctx);
    return conditionallyActive[0];
  }

  private static void scanWorkers(
      J.CompilationUnit compilationUnit,
      String sourcePath,
      WorkerEnablementState state,
      ExecutionContext ctx) {
    Map<String, List<Expression>> stringConstants = stringConstants(compilationUnit, ctx);
    Set<String> imports =
        compilationUnit.getImports().stream()
            .map(imported -> imported.getQualid().toString())
            .collect(Collectors.toSet());

    new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
        for (J.Annotation annotation : visited.getLeadingAnnotations()) {
          if (isJobWorker(annotation, imports)) {
            Optional<String> annotationType =
                annotationString(annotation, "type", stringConstants);
            String type =
                annotationType
                    .filter(ValidateCamundaClientWorkerEnablement::isResolvedAnnotationValue)
                    .orElse(null);
            boolean unresolvedType =
                hasAnnotationArgument(annotation, "type")
                    && annotationType
                        .map(value -> !value.isBlank() && !isResolvedAnnotationValue(value))
                        .orElse(true);
            String name =
                annotationString(annotation, "name", stringConstants)
                    .filter(ValidateCamundaClientWorkerEnablement::isResolvedAnnotationValue)
                    .orElse(null);
            state.addWorker(sourcePath, type, name, visited.getSimpleName(), unresolvedType);
          }
        }
        return visited;
      }
    }.visit(compilationUnit, ctx);
  }

  private static boolean isResolvedAnnotationValue(String value) {
    return !value.isBlank() && !value.contains("${");
  }

  private static Map<String, List<Expression>> stringConstants(
      J.CompilationUnit compilationUnit, ExecutionContext ctx) {
    Map<String, List<Expression>> initializers = new HashMap<>();
    new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.VariableDeclarations visitVariableDeclarations(
          J.VariableDeclarations declarations, ExecutionContext ctx) {
        J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
        boolean isFinal =
            visited.getModifiers().stream()
                .anyMatch(modifier -> modifier.getType() == J.Modifier.Type.Final);
        if (isFinal) {
          for (J.VariableDeclarations.NamedVariable variable : visited.getVariables()) {
            Expression initializer = variable.getInitializer();
            if (initializer != null) {
              initializers
                  .computeIfAbsent(variable.getSimpleName(), unused -> new ArrayList<>())
                  .add(initializer);
            }
          }
        }
        return visited;
      }
    }.visit(compilationUnit, ctx);
    return initializers;
  }

  private static Optional<String> annotationString(
      J.Annotation annotation, String argumentName, Map<String, List<Expression>> constants) {
    return annotationArgument(annotation, argumentName)
        .flatMap(argument -> resolveString(argument, constants, new HashSet<>()));
  }

  private static boolean hasAnnotationArgument(J.Annotation annotation, String argumentName) {
    return annotationArgument(annotation, argumentName).isPresent();
  }

  private static Optional<Expression> annotationArgument(J.Annotation annotation, String name) {
    if (annotation.getArguments() == null) {
      return Optional.empty();
    }
    for (Expression argument : annotation.getArguments()) {
      if (argument instanceof J.Assignment assignment
          && assignment.getVariable() instanceof J.Identifier identifier
          && name.equals(identifier.getSimpleName())) {
        return Optional.of(assignment.getAssignment());
      }
    }
    return Optional.empty();
  }

  private static Optional<String> resolveString(
      Expression expression,
      Map<String, List<Expression>> constants,
      Set<String> resolvingConstants) {
    if (expression instanceof J.Literal literal && literal.getValue() instanceof String value) {
      return Optional.of(value);
    }

    String constantName = null;
    if (expression instanceof J.Identifier identifier) {
      constantName = identifier.getSimpleName();
    } else if (expression instanceof J.FieldAccess fieldAccess) {
      constantName = fieldAccess.getName().getSimpleName();
    }
    if (constantName == null || !resolvingConstants.add(constantName)) {
      return Optional.empty();
    }
    List<Expression> initializers = constants.get(constantName);
    if (initializers == null || initializers.size() != 1) {
      resolvingConstants.remove(constantName);
      return Optional.empty();
    }
    Optional<String> resolved =
        resolveString(initializers.getFirst(), constants, resolvingConstants);
    resolvingConstants.remove(constantName);
    return resolved;
  }

  private static boolean isJobWorker(J.Annotation annotation, Set<String> imports) {
    JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
    if (type != null && JOB_WORKER_ANNOTATION_TYPES.contains(type.getFullyQualifiedName())) {
      return true;
    }
    String annotationType = annotation.getAnnotationType().toString();
    if (JOB_WORKER_ANNOTATION_TYPES.contains(annotationType)) {
      return true;
    }
    if (!annotation.getSimpleName().equals("JobWorker")) {
      return false;
    }
    return JOB_WORKER_ANNOTATION_TYPES.stream()
        .anyMatch(
            jobWorkerType -> {
              String packageWildcard =
                  jobWorkerType.substring(0, jobWorkerType.lastIndexOf('.')) + ".*";
              return imports.contains(jobWorkerType) || imports.contains(packageWildcard);
            });
  }

  private static boolean isApplicationConfiguration(String sourcePath) {
    if (sourcePath.contains("/src/test/") || sourcePath.startsWith("src/test/")) {
      return false;
    }
    String fileName = Path.of(sourcePath).getFileName().toString();
    return fileName.startsWith("application")
        && (fileName.endsWith(".properties")
            || fileName.endsWith(".yml")
            || fileName.endsWith(".yaml"));
  }

  private static boolean isProductionJava(String sourcePath) {
    return sourcePath.contains("/src/main/java/")
        || sourcePath.startsWith("src/main/java/");
  }

  private static boolean isBuildDescriptor(String sourcePath) {
    String fileName = Path.of(sourcePath).getFileName().toString();
    return fileName.equals("pom.xml")
        || fileName.equals("build.gradle")
        || fileName.equals("build.gradle.kts");
  }

  private static String parentPath(String sourcePath) {
    Path parent = Path.of(sourcePath).getParent();
    return parent == null || parent.toString().equals(".") ? "" : normalizePath(parent);
  }

  private static String normalizePath(Path path) {
    String normalized = path.toString().replace('\\', '/');
    return normalized.equals(".") ? "" : normalized;
  }

  private static Optional<String> sourceModuleRoot(String sourcePath) {
    String marker = "/src/main/";
    int markerIndex = sourcePath.indexOf(marker);
    if (markerIndex >= 0) {
      return Optional.of(sourcePath.substring(0, markerIndex));
    }
    if (sourcePath.startsWith("src/main/")) {
      return Optional.of("");
    }
    return Optional.empty();
  }

  private static Properties.Entry mark(
      Properties.Entry entry,
      String sourcePath,
      String key,
      String value,
      Map<EntryKey, List<String>> findings) {
    EntryKey entryKey = new EntryKey(sourcePath, key, value);
    Properties.Entry marked = entry;
    for (String message : findings.getOrDefault(entryKey, List.of())) {
      marked = SearchResult.found(marked, message);
    }
    return marked;
  }

  private static Yaml.Mapping.Entry mark(
      Yaml.Mapping.Entry entry,
      String sourcePath,
      String key,
      String value,
      Map<EntryKey, List<String>> findings) {
    EntryKey entryKey = new EntryKey(sourcePath, key, value);
    Yaml.Mapping.Entry marked = entry;
    for (String message : findings.getOrDefault(entryKey, List.of())) {
      marked = SearchResult.found(marked, message);
    }
    return marked;
  }

  private enum SettingKind {
    CLIENT,
    WORKER_DEFAULTS,
    WORKER_OVERRIDE,
    WORKER_DEFAULTS_AND_OVERRIDE
  }

  private record WorkerDeclaration(
      String sourcePath, String type, String name, String methodName, boolean unresolvedType) {}

  private record WorkerSetting(
      String sourcePath,
      String key,
      String value,
      String target,
      SettingKind kind,
      boolean conditionallyActive) {}

  private record WorkerTypeSetting(
      String sourcePath, String key, String value, boolean conditionallyActive) {}

  private record EntryKey(String sourcePath, String key, String value) {}

  private record ParsedBoolean(Boolean value, boolean conditional) {}

  private record EffectiveWorkerType(
      String value, Set<String> possibleValues, boolean conditional) {}

  private record EffectiveString(
      String value,
      Set<String> possibleValues,
      boolean hasBlankValue,
      boolean conditional,
      boolean configured) {}

  private record EffectiveBoolean(
      Boolean value, boolean conditional, List<WorkerSetting> sources) {}

  private record OverrideSelection(List<WorkerSetting> settings, boolean targetConditional) {}

  public static final class WorkerEnablementState {
    private final List<WorkerDeclaration> workers = new ArrayList<>();
    private final List<WorkerSetting> settings = new ArrayList<>();
    private final List<WorkerTypeSetting> workerTypeSettings = new ArrayList<>();
    private final Set<String> moduleRoots = new LinkedHashSet<>();

    private synchronized void addModuleRoot(String moduleRoot) {
      moduleRoots.add(moduleRoot);
    }

    private synchronized void addWorker(
        String sourcePath, String type, String name, String methodName, boolean unresolvedType) {
      workers.add(new WorkerDeclaration(sourcePath, type, name, methodName, unresolvedType));
    }

    private synchronized void addSetting(
        String sourcePath, String key, String value, boolean conditionallyActiveDocument) {
      boolean conditionallyActive = conditionallyActiveDocument || profileSpecific(sourcePath);
      setting(sourcePath, key, value, conditionallyActive).ifPresent(settings::add);
      workerTypeSetting(sourcePath, key, value, conditionallyActive)
          .ifPresent(workerTypeSettings::add);
    }

    private synchronized Map<EntryKey, List<String>> findings() {
      Map<String, List<WorkerDeclaration>> workersByModule = new HashMap<>();
      for (WorkerDeclaration worker : workers) {
        String module = moduleKey(worker.sourcePath(), moduleRoots);
        workersByModule.computeIfAbsent(module, unused -> new ArrayList<>()).add(worker);
      }

      Map<String, List<WorkerSetting>> settingsByModule = new HashMap<>();
      for (WorkerSetting setting : settings) {
        String module = moduleKey(setting.sourcePath(), moduleRoots);
        settingsByModule.computeIfAbsent(module, unused -> new ArrayList<>()).add(setting);
      }

      Map<String, List<WorkerTypeSetting>> workerTypeSettingsByModule = new HashMap<>();
      for (WorkerTypeSetting setting : workerTypeSettings) {
        String module = moduleKey(setting.sourcePath(), moduleRoots);
        workerTypeSettingsByModule.computeIfAbsent(module, unused -> new ArrayList<>()).add(setting);
      }

      Map<EntryKey, LinkedHashSet<String>> findings = new LinkedHashMap<>();
      workersByModule.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry ->
                  analyzeModule(
                      entry.getValue(),
                      settingsByModule.getOrDefault(entry.getKey(), List.of()),
                      workerTypeSettingsByModule.getOrDefault(entry.getKey(), List.of()),
                      findings));

      Map<EntryKey, List<String>> result = new LinkedHashMap<>();
      findings.forEach((key, messages) -> result.put(key, List.copyOf(messages)));
      return Map.copyOf(result);
    }

    private static void analyzeModule(
        List<WorkerDeclaration> workers,
        List<WorkerSetting> moduleSettings,
        List<WorkerTypeSetting> moduleWorkerTypeSettings,
        Map<EntryKey, LinkedHashSet<String>> findings) {
      List<WorkerSetting> clientSettings =
          moduleSettings.stream()
              .filter(setting -> setting.kind() == SettingKind.CLIENT)
              .toList();
      List<WorkerSetting> defaultSettings =
          moduleSettings.stream()
              .filter(setting -> setting.kind() == SettingKind.WORKER_DEFAULTS)
              .toList();
      List<WorkerSetting> overrideSettings =
          moduleSettings.stream()
              .filter(setting -> setting.kind() == SettingKind.WORKER_OVERRIDE)
              .toList();
      List<WorkerTypeSetting> defaultTypeSettings = moduleWorkerTypeSettings;

      EffectiveBoolean clientEnabled = effectiveBoolean(clientSettings, true);
      for (WorkerDeclaration worker : workers) {
        EffectiveWorkerType workerType = effectiveWorkerType(worker, defaultTypeSettings);
        if (isConditionalOrDisabled(clientEnabled)) {
          addFinding(
              findings,
              clientEnabled.sources(),
              worker,
              workerType,
              SettingKind.CLIENT,
              clientEnabled);
          continue;
        }

        OverrideSelection overrides = selectOverrides(worker, workerType, overrideSettings);
        if (!overrides.settings().isEmpty()) {
          EffectiveBoolean workerEnabled = effectiveBoolean(overrides.settings(), true);
          EffectiveBoolean defaultsEnabled = effectiveBoolean(defaultSettings, true);
          boolean defaultFallbackMayDisableWorker =
              overrides.settings().stream().anyMatch(WorkerSetting::conditionallyActive)
                  && isConditionalOrDisabled(defaultsEnabled);
          if (overrides.targetConditional() || defaultFallbackMayDisableWorker) {
            workerEnabled =
                new EffectiveBoolean(workerEnabled.value(), true, workerEnabled.sources());
          }
          if (isConditionalOrDisabled(workerEnabled)) {
            List<WorkerSetting> relatedSettings =
                overrides.targetConditional() || defaultFallbackMayDisableWorker
                    ? combine(defaultSettings, overrides.settings())
                    : overrides.settings();
            addFinding(
                findings,
                relatedSettings,
                worker,
                workerType,
                defaultFallbackMayDisableWorker
                    ? SettingKind.WORKER_DEFAULTS_AND_OVERRIDE
                    : SettingKind.WORKER_OVERRIDE,
                workerEnabled);
          }
        } else {
          EffectiveBoolean workersEnabled = effectiveBoolean(defaultSettings, true);
          if (isConditionalOrDisabled(workersEnabled)) {
            addFinding(
                findings,
                workersEnabled.sources(),
                worker,
                workerType,
                SettingKind.WORKER_DEFAULTS,
                workersEnabled);
          }
        }
      }
    }

    private static EffectiveWorkerType effectiveWorkerType(
        WorkerDeclaration worker, List<WorkerTypeSetting> defaultTypeSettings) {
      if (worker.type() != null) {
        return new EffectiveWorkerType(worker.type(), Set.of(worker.type()), false);
      }
      if (worker.unresolvedType()) {
        return new EffectiveWorkerType(null, Set.of(), true);
      }

      EffectiveString configuredType = effectiveString(defaultTypeSettings);
      if (!configuredType.configured()) {
        return new EffectiveWorkerType(worker.methodName(), Set.of(worker.methodName()), false);
      }
      Set<String> possibleTypes = new HashSet<>(configuredType.possibleValues());
      if (configuredType.hasBlankValue()) {
        possibleTypes.add(worker.methodName());
      }
      if (!configuredType.conditional() && possibleTypes.isEmpty()) {
        return new EffectiveWorkerType(worker.methodName(), Set.of(worker.methodName()), false);
      }
      return new EffectiveWorkerType(
          configuredType.value(), Set.copyOf(possibleTypes), configuredType.conditional());
    }

    private static EffectiveString effectiveString(List<WorkerTypeSetting> settings) {
      if (settings.isEmpty()) {
        return new EffectiveString(null, Set.of(), false, false, false);
      }

      Set<String> values = new HashSet<>();
      boolean hasBlankValue = false;
      boolean unknownValue = false;
      boolean conditional = false;
      for (WorkerTypeSetting setting : settings) {
        Optional<String> candidate =
            CamundaClientConfigurationValidation.bindingCandidate(setting.value());
        if (candidate.isEmpty()) {
          unknownValue = true;
        } else if (candidate.get().isBlank()) {
          hasBlankValue = true;
        } else {
          values.add(candidate.get());
        }
        conditional |=
            setting.value().contains("${") || setting.conditionallyActive();
      }
      if (values.size() > 1 || unknownValue || (hasBlankValue && !values.isEmpty())) {
        conditional = true;
      }
      String value =
          values.size() == 1 && !unknownValue && !hasBlankValue
              ? values.iterator().next()
              : null;
      return new EffectiveString(value, Set.copyOf(values), hasBlankValue, conditional, true);
    }

    private static OverrideSelection selectOverrides(
        WorkerDeclaration worker,
        EffectiveWorkerType workerType,
        List<WorkerSetting> overrideSettings) {
      List<WorkerSetting> typeOverrides = new ArrayList<>();
      List<WorkerSetting> nameOverrides = new ArrayList<>();
      List<WorkerSetting> unresolvedTargets = new ArrayList<>();
      boolean targetConditional = false;
      for (WorkerSetting setting : overrideSettings) {
        String target = setting.target();
        if (target.contains("${")) {
          unresolvedTargets.add(setting);
          targetConditional = true;
        } else if (workerType.possibleValues().contains(target)) {
          typeOverrides.add(setting);
          targetConditional |=
              workerType.conditional() || workerType.value() == null;
        } else if (worker.name() != null && target.equals(worker.name())) {
          nameOverrides.add(setting);
        }
      }

      boolean typeOverrideTakesPrecedence =
          workerType.value() != null
              && !workerType.conditional()
              && !typeOverrides.isEmpty()
              && typeOverrides.stream().anyMatch(setting -> !setting.conditionallyActive())
              && unresolvedTargets.isEmpty();
      List<WorkerSetting> selected = new ArrayList<>(typeOverrides);
      if (!typeOverrideTakesPrecedence) {
        selected.addAll(nameOverrides);
      }
      selected.addAll(unresolvedTargets);
      return new OverrideSelection(List.copyOf(selected), targetConditional);
    }

    private static EffectiveBoolean effectiveBoolean(
        List<WorkerSetting> settings, boolean defaultValue) {
      if (settings.isEmpty()) {
        return new EffectiveBoolean(defaultValue, false, List.of());
      }

      Set<Boolean> values = new HashSet<>();
      boolean unknownValue = false;
      boolean conditional = false;
      for (WorkerSetting setting : settings) {
        ParsedBoolean parsed = parseBoolean(setting.value());
        conditional |=
            parsed.conditional()
                || (setting.conditionallyActive()
                    && !Boolean.TRUE.equals(parsed.value()));
        if (parsed.value() == null) {
          unknownValue = true;
        } else {
          values.add(parsed.value());
        }
      }
      if (unknownValue || values.size() != 1) {
        return new EffectiveBoolean(null, true, List.copyOf(settings));
      }
      return new EffectiveBoolean(values.iterator().next(), conditional, List.copyOf(settings));
    }

    private static ParsedBoolean parseBoolean(String value) {
      if (value.contains("${")) {
        Optional<String> defaultValue =
            CamundaClientConfigurationValidation.bindingCandidate(value);
        return new ParsedBoolean(
            defaultValue.map(WorkerEnablementState::booleanValue).orElse(null), true);
      }
      return new ParsedBoolean(booleanValue(value), false);
    }

    private static Boolean booleanValue(String value) {
      return switch (value.trim().toLowerCase(Locale.ROOT)) {
        case "true", "yes", "on", "1" -> true;
        case "false", "no", "off", "0" -> false;
        default -> null;
      };
    }

    private static boolean profileSpecific(String sourcePath) {
      return Path.of(sourcePath)
          .getFileName()
          .toString()
          .matches("application-.+\\.(properties|yml|yaml)");
    }

    private static boolean isConditionalOrDisabled(EffectiveBoolean setting) {
      return setting.conditional() || !Boolean.TRUE.equals(setting.value());
    }

    private static List<WorkerSetting> combine(
        List<WorkerSetting> first, List<WorkerSetting> second) {
      LinkedHashSet<WorkerSetting> combined = new LinkedHashSet<>(first);
      combined.addAll(second);
      return List.copyOf(combined);
    }

    private static void addFinding(
        Map<EntryKey, LinkedHashSet<String>> findings,
        List<WorkerSetting> sources,
        WorkerDeclaration worker,
        EffectiveWorkerType workerType,
        SettingKind kind,
        EffectiveBoolean enabled) {
      if (sources.isEmpty()) {
        return;
      }
      String message = findingMessage(worker, workerType, kind, enabled);
      for (WorkerSetting source : sources) {
        EntryKey entryKey = new EntryKey(source.sourcePath(), source.key(), source.value());
        findings.computeIfAbsent(entryKey, unused -> new LinkedHashSet<>()).add(message);
      }
    }

    private static String findingMessage(
        WorkerDeclaration worker,
        EffectiveWorkerType workerType,
        SettingKind kind,
        EffectiveBoolean enabled) {
      boolean definitelyDisabled =
          Boolean.FALSE.equals(enabled.value()) && !enabled.conditional();
      if (definitelyDisabled && kind == SettingKind.CLIENT) {
        return "The "
            + workerDescription(worker, workerType)
            + " is disabled because 'camunda.client.enabled=false' prevents client creation. "
            + "Verify the effective runtime configuration and job worker registration before "
            + "marking workers ready.";
      }
      if (definitelyDisabled && kind == SettingKind.WORKER_DEFAULTS) {
        return "The "
            + workerDescription(worker, workerType)
            + " is disabled by 'camunda.client.worker.defaults.enabled=false'. Verify the "
            + "effective runtime configuration and job worker registration before marking "
            + "workers ready.";
      }
      if (definitelyDisabled && kind == SettingKind.WORKER_OVERRIDE) {
        return "The "
            + workerDescription(worker, workerType)
            + " is disabled by its per-worker 'enabled=false' setting. Verify the effective "
            + "runtime configuration and job worker registration before marking workers ready.";
      }

      String settingDescription =
          switch (kind) {
            case CLIENT -> "'camunda.client.enabled'";
            case WORKER_DEFAULTS -> "'camunda.client.worker.defaults.enabled'";
            case WORKER_OVERRIDE -> "a per-worker 'enabled' override";
            case WORKER_DEFAULTS_AND_OVERRIDE ->
                "worker defaults or a per-worker 'enabled' override";
          };
      return "Job-worker readiness is conditional because "
          + settingDescription
          + " may disable "
          + "the "
          + workerDescription(worker, workerType)
          + ". Resolve profile and environment overrides, then verify its registration at runtime.";
    }

    private static String workerDescription(
        WorkerDeclaration worker, EffectiveWorkerType workerType) {
      if (workerType.value() != null) {
        return "worker for job type '" + workerType.value() + "'";
      }
      if (worker.name() != null) {
        return "worker named '" + worker.name() + "'";
      }
      return "worker with an unresolved job type";
    }

    private static String moduleKey(String sourcePath, Set<String> moduleRoots) {
      Optional<String> sourceRoot = sourceModuleRoot(sourcePath);
      if (sourceRoot.isPresent()) {
        return sourceRoot.get();
      }
      return moduleRoots.stream()
          .filter(
              root ->
                  root.isEmpty()
                      || sourcePath.equals(root)
                      || sourcePath.startsWith(root + "/"))
          .max(Comparator.comparingInt(String::length))
          .orElse("");
    }

    private static Optional<WorkerSetting> setting(
        String sourcePath, String key, String value, boolean conditionallyActive) {
      Optional<String> overrideTarget = overrideTarget(key);
      String effectiveKey = CamundaClientConfigurationValidation.effectivePropertyName(key);
      if (overrideTarget.isEmpty()) {
        overrideTarget = overrideTarget(effectiveKey);
      }
      if (overrideTarget.isPresent()) {
        return Optional.of(
            new WorkerSetting(
                sourcePath,
                key,
                value,
                overrideTarget.get(),
                SettingKind.WORKER_OVERRIDE,
                conditionallyActive));
      }

      if (effectiveKey.equals(CLIENT_ENABLED_PROPERTY)) {
        return Optional.of(
            new WorkerSetting(
                sourcePath, key, value, null, SettingKind.CLIENT, conditionallyActive));
      }
      if (effectiveKey.equals(WORKER_DEFAULT_ENABLED_PROPERTY)) {
        return Optional.of(
            new WorkerSetting(
                sourcePath,
                key,
                value,
                null,
                SettingKind.WORKER_DEFAULTS,
                conditionallyActive));
      }
      return Optional.empty();
    }

    private static Optional<WorkerTypeSetting> workerTypeSetting(
        String sourcePath, String key, String value, boolean conditionallyActive) {
      String effectiveKey = CamundaClientConfigurationValidation.effectivePropertyName(key);
      if (!effectiveKey.equals(WORKER_DEFAULT_TYPE_PROPERTY)) {
        return Optional.empty();
      }
      return Optional.of(new WorkerTypeSetting(sourcePath, key, value, conditionallyActive));
    }

    private static Optional<String> overrideTarget(String key) {
      List<String> prefixes =
          List.of(
              "camunda.client.worker.override",
              "camunda.client.zeebe.override",
              "camunda.client.zeebe.worker.override");
      for (String prefix : prefixes) {
        if (!key.regionMatches(true, 0, prefix, 0, prefix.length())) {
          continue;
        }
        String suffix = key.substring(prefix.length());
        if (suffix.startsWith("[")) {
          int end = suffix.indexOf(']');
          if (end > 1 && suffix.substring(end + 1).equalsIgnoreCase(ENABLED_SUFFIX)) {
            return Optional.of(suffix.substring(1, end));
          }
        } else if (suffix.startsWith(".")
            && suffix.length() > ENABLED_SUFFIX.length() + 1
            && suffix.regionMatches(
                true,
                suffix.length() - ENABLED_SUFFIX.length(),
                ENABLED_SUFFIX,
                0,
                ENABLED_SUFFIX.length())) {
          return Optional.of(
              suffix.substring(1, suffix.length() - ENABLED_SUFFIX.length()));
        }
      }
      return Optional.empty();
    }
  }
}
