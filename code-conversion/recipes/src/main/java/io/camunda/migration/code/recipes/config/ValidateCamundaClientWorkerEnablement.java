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
          scanProperties(propertiesFile, sourcePath, state, ctx);
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
      Properties.File propertiesFile,
      String sourcePath,
      WorkerEnablementState state,
      ExecutionContext ctx) {
    new PropertiesIsoVisitor<ExecutionContext>() {
      @Override
      public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
        Properties.Entry visited = super.visitEntry(entry, ctx);
        state.addSetting(sourcePath, visited.getKey(), visited.getValue().getText());
        return visited;
      }
    }.visit(propertiesFile, ctx);
  }

  private static void scanYaml(
      Yaml.Documents yamlDocuments,
      String sourcePath,
      WorkerEnablementState state,
      ExecutionContext ctx) {
    new YamlIsoVisitor<ExecutionContext>() {
      @Override
      public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
        Yaml.Mapping visited = super.visitMapping(mapping, ctx);
        String parentPath = ValidateCamundaClientYaml.ancestorPath(getCursor());
        for (Yaml.Mapping.Entry entry : visited.getEntries()) {
          if (entry.getValue() instanceof Yaml.Scalar value) {
            String key =
                ValidateCamundaClientYaml.join(parentPath, entry.getKey().getValue());
            state.addSetting(sourcePath, key, value.getValue());
          }
        }
        return visited;
      }
    }.visit(yamlDocuments, ctx);
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
            String type =
                annotationString(annotation, "type", stringConstants)
                    .filter(ValidateCamundaClientWorkerEnablement::isResolvedAnnotationValue)
                    .orElse(null);
            String name =
                annotationString(annotation, "name", stringConstants)
                    .filter(ValidateCamundaClientWorkerEnablement::isResolvedAnnotationValue)
                    .orElse(null);
            state.addWorker(sourcePath, type, name);
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
    if (annotation.getArguments() == null) {
      return Optional.empty();
    }
    for (Expression argument : annotation.getArguments()) {
      if (argument instanceof J.Assignment assignment
          && assignment.getVariable() instanceof J.Identifier identifier
          && argumentName.equals(identifier.getSimpleName())) {
        return resolveString(assignment.getAssignment(), constants, new HashSet<>());
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
    WORKER_OVERRIDE
  }

  private record WorkerDeclaration(String sourcePath, String type, String name) {}

  private record WorkerSetting(
      String sourcePath,
      String key,
      String value,
      String target,
      SettingKind kind) {}

  private record EntryKey(String sourcePath, String key, String value) {}

  private record ParsedBoolean(Boolean value, boolean conditional) {}

  private record EffectiveBoolean(
      Boolean value, boolean conditional, List<WorkerSetting> sources) {}

  private record OverrideSelection(List<WorkerSetting> settings, boolean targetConditional) {}

  public static final class WorkerEnablementState {
    private final List<WorkerDeclaration> workers = new ArrayList<>();
    private final List<WorkerSetting> settings = new ArrayList<>();
    private final Set<String> moduleRoots = new LinkedHashSet<>();

    private synchronized void addModuleRoot(String moduleRoot) {
      moduleRoots.add(moduleRoot);
    }

    private synchronized void addWorker(String sourcePath, String type, String name) {
      workers.add(new WorkerDeclaration(sourcePath, type, name));
    }

    private synchronized void addSetting(String sourcePath, String key, String value) {
      setting(sourcePath, key, value).ifPresent(settings::add);
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

      Map<EntryKey, LinkedHashSet<String>> findings = new LinkedHashMap<>();
      workersByModule.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry ->
                  analyzeModule(
                      entry.getValue(),
                      settingsByModule.getOrDefault(entry.getKey(), List.of()),
                      findings));

      Map<EntryKey, List<String>> result = new LinkedHashMap<>();
      findings.forEach((key, messages) -> result.put(key, List.copyOf(messages)));
      return Map.copyOf(result);
    }

    private static void analyzeModule(
        List<WorkerDeclaration> workers,
        List<WorkerSetting> moduleSettings,
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

      EffectiveBoolean clientEnabled = effectiveBoolean(clientSettings, true);
      for (WorkerDeclaration worker : workers) {
        if (isConditionalOrDisabled(clientEnabled)) {
          addFinding(
              findings,
              clientEnabled.sources(),
              worker,
              SettingKind.CLIENT,
              clientEnabled);
          continue;
        }

        OverrideSelection overrides = selectOverrides(worker, overrideSettings);
        if (!overrides.settings().isEmpty()) {
          EffectiveBoolean workerEnabled = effectiveBoolean(overrides.settings(), true);
          if (overrides.targetConditional()) {
            workerEnabled =
                new EffectiveBoolean(workerEnabled.value(), true, workerEnabled.sources());
          }
          if (isConditionalOrDisabled(workerEnabled)) {
            List<WorkerSetting> relatedSettings =
                overrides.targetConditional()
                    ? combine(defaultSettings, overrides.settings())
                    : overrides.settings();
            addFinding(
                findings,
                relatedSettings,
                worker,
                SettingKind.WORKER_OVERRIDE,
                workerEnabled);
          }
        } else {
          EffectiveBoolean workersEnabled = effectiveBoolean(defaultSettings, true);
          if (isConditionalOrDisabled(workersEnabled)) {
            addFinding(
                findings,
                workersEnabled.sources(),
                worker,
                SettingKind.WORKER_DEFAULTS,
                workersEnabled);
          }
        }
      }
    }

    private static OverrideSelection selectOverrides(
        WorkerDeclaration worker, List<WorkerSetting> overrideSettings) {
      List<WorkerSetting> selected = new ArrayList<>();
      boolean targetConditional = false;
      for (WorkerSetting setting : overrideSettings) {
        String target = setting.target();
        if (target.contains("${")) {
          selected.add(setting);
          targetConditional = true;
        } else if (target.equals(worker.type()) || target.equals(worker.name())) {
          selected.add(setting);
        } else if (worker.type() == null || worker.name() == null) {
          selected.add(setting);
          targetConditional = true;
        }
      }
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
                || (profileSpecific(setting.sourcePath())
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
        SettingKind kind,
        EffectiveBoolean enabled) {
      if (sources.isEmpty()) {
        return;
      }
      String message = findingMessage(worker, kind, enabled);
      for (WorkerSetting source : sources) {
        EntryKey entryKey = new EntryKey(source.sourcePath(), source.key(), source.value());
        findings.computeIfAbsent(entryKey, unused -> new LinkedHashSet<>()).add(message);
      }
    }

    private static String findingMessage(
        WorkerDeclaration worker, SettingKind kind, EffectiveBoolean enabled) {
      boolean definitelyDisabled =
          Boolean.FALSE.equals(enabled.value()) && !enabled.conditional();
      if (definitelyDisabled && kind == SettingKind.CLIENT) {
        return "The "
            + workerDescription(worker)
            + " is disabled because 'camunda.client.enabled=false' prevents client creation. "
            + "Verify the effective runtime configuration and job worker registration before "
            + "marking workers ready.";
      }
      if (definitelyDisabled && kind == SettingKind.WORKER_DEFAULTS) {
        return "The "
            + workerDescription(worker)
            + " is disabled by 'camunda.client.worker.defaults.enabled=false'. Verify the "
            + "effective runtime configuration and job worker registration before marking "
            + "workers ready.";
      }
      if (definitelyDisabled && kind == SettingKind.WORKER_OVERRIDE) {
        return "The "
            + workerDescription(worker)
            + " is disabled by its per-worker 'enabled=false' setting. Verify the effective "
            + "runtime configuration and job worker registration before marking workers ready.";
      }

      String settingDescription =
          switch (kind) {
            case CLIENT -> "'camunda.client.enabled'";
            case WORKER_DEFAULTS -> "'camunda.client.worker.defaults.enabled'";
            case WORKER_OVERRIDE -> "a per-worker 'enabled' override";
          };
      return "Job-worker readiness is conditional because "
          + settingDescription
          + " may disable "
          + "the "
          + workerDescription(worker)
          + ". Resolve profile and environment overrides, then verify its registration at runtime.";
    }

    private static String workerDescription(WorkerDeclaration worker) {
      if (worker.type() != null) {
        return "worker for job type '" + worker.type() + "'";
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

    private static Optional<WorkerSetting> setting(String sourcePath, String key, String value) {
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
                SettingKind.WORKER_OVERRIDE));
      }

      if (effectiveKey.equals(CLIENT_ENABLED_PROPERTY)) {
        return Optional.of(
            new WorkerSetting(sourcePath, key, value, null, SettingKind.CLIENT));
      }
      if (effectiveKey.equals(WORKER_DEFAULT_ENABLED_PROPERTY)) {
        return Optional.of(
            new WorkerSetting(
                sourcePath,
                key,
                value,
                null,
                SettingKind.WORKER_DEFAULTS));
      }
      return Optional.empty();
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
