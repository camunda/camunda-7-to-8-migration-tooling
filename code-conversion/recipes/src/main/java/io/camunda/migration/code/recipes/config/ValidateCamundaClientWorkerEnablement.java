/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

/** Flags global settings that may disable job workers declared in the same module. */
public class ValidateCamundaClientWorkerEnablement
    extends ScanningRecipe<ValidateCamundaClientWorkerEnablement.WorkerModules> {

  private static final Set<String> JOB_WORKER_TYPES =
      Set.of(
          "io.camunda.client.annotation.JobWorker",
          "io.camunda.zeebe.spring.client.annotation.JobWorker");
  private static final Set<String> TRUE_VALUES = Set.of("true", "yes", "on", "1");
  private static final String SETTING_FINDING =
      "This setting may disable a declared job worker. Resolve the effective configuration."
          + " Verify worker registration at runtime.";
  private static final String DISABLED_ANNOTATION =
      "This @JobWorker is disabled unless configuration overrides it. Verify registration at"
          + " runtime.";
  private static final String UNKNOWN_ANNOTATION =
      "This @JobWorker has an unresolved enabled value. Verify registration at runtime.";

  /** Instantiates a Camunda client worker enablement validator. */
  public ValidateCamundaClientWorkerEnablement() {}

  @Override
  public @NonNull String getDisplayName() {
    return "Validate Camunda client job worker enablement";
  }

  @Override
  public @NonNull String getDescription() {
    return "Flags client-wide settings and annotations that may disable declared job workers.";
  }

  @Override
  public WorkerModules getInitialValue(ExecutionContext ctx) {
    return new WorkerModules();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(WorkerModules state) {
    return new TreeVisitor<>() {
      @Override
      public Tree visit(Tree tree, ExecutionContext ctx) {
        if (!(tree instanceof SourceFile file)) {
          return tree;
        }
        String path = normalize(file.getSourcePath());
        String sourceRoot = sourceRoot(path);
        if (sourceRoot != null) {
          state.roots.add(sourceRoot);
        }
        if (isBuildFile(path)) {
          Path parent = Path.of(path).getParent();
          state.roots.add(parent == null ? "" : normalize(parent));
        }
        if (tree instanceof J.CompilationUnit java && isProductionJava(path)) {
          Set<String> imports = imports(java);
          new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
              J.Annotation visited = super.visitAnnotation(annotation, ctx);
              if (isJobWorker(visited, imports)) {
                state.workers.add(sourceRoot);
              }
              return visited;
            }
          }.visit(java, ctx);
        }
        return tree;
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(WorkerModules state) {
    return new TreeVisitor<>() {
      @Override
      public Tree visit(Tree tree, ExecutionContext ctx) {
        if (tree instanceof J.CompilationUnit java
            && isProductionJava(normalize(java.getSourcePath()))) {
          Set<String> imports = imports(java);
          return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
              J.Annotation visited = super.visitAnnotation(annotation, ctx);
              if (!isJobWorker(visited, imports)) {
                return visited;
              }
              String finding = annotationFinding(visited);
              return finding == null ? visited : SearchResult.found(visited, finding);
            }
          }.visit(java, ctx);
        }
        if (tree instanceof Properties.File properties
            && isApplicationFile(normalize(properties.getSourcePath()))
            && state.hasWorkers(normalize(properties.getSourcePath()))) {
          return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
              Properties.Entry visited = super.visitEntry(entry, ctx);
              return mayDisableWorkers(visited.getKey(), visited.getValue().getText())
                  ? SearchResult.found(visited, SETTING_FINDING)
                  : visited;
            }
          }.visit(properties, ctx);
        }
        if (tree instanceof Yaml.Documents yaml
            && isApplicationFile(normalize(yaml.getSourcePath()))
            && state.hasWorkers(normalize(yaml.getSourcePath()))) {
          return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
              Yaml.Mapping visited = super.visitMapping(mapping, ctx);
              String parent = ValidateCamundaClientYaml.ancestorPath(getCursor());
              return visited.withEntries(
                  ListUtils.map(
                      visited.getEntries(),
                      entry -> {
                        if (entry.getValue() instanceof Yaml.Scalar value
                            && mayDisableWorkers(
                                ValidateCamundaClientYaml.join(
                                    parent, entry.getKey().getValue()),
                                value.getValue())) {
                          return SearchResult.found(entry, SETTING_FINDING);
                        }
                        return entry;
                      }));
            }
          }.visit(yaml, ctx);
        }
        return tree;
      }
    };
  }

  private static boolean mayDisableWorkers(String key, String value) {
    String effectiveKey = CamundaClientConfigurationValidation.effectivePropertyName(key);
    return (effectiveKey.equals("camunda.client.enabled")
            || effectiveKey.equals("camunda.client.worker.defaults.enabled"))
        && (value.contains("${") || !TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT)));
  }

  private static String annotationFinding(J.Annotation annotation) {
    if (annotation.getArguments() == null) {
      return null;
    }
    for (Expression argument : annotation.getArguments()) {
      if (argument instanceof J.Assignment assignment
          && assignment.getVariable() instanceof J.Identifier identifier
          && identifier.getSimpleName().equals("enabled")) {
        Expression value = assignment.getAssignment();
        if (value instanceof J.NewArray array) {
          if (array.getInitializer() != null
              && array.getInitializer().stream().allMatch(J.Empty.class::isInstance)) {
            return null;
          }
          if (array.getInitializer() == null || array.getInitializer().size() != 1) {
            return UNKNOWN_ANNOTATION;
          }
          value = array.getInitializer().getFirst();
        }
        if (value instanceof J.Literal literal) {
          if (Boolean.TRUE.equals(literal.getValue())) {
            return null;
          }
          if (Boolean.FALSE.equals(literal.getValue())) {
            return DISABLED_ANNOTATION;
          }
        }
        return UNKNOWN_ANNOTATION;
      }
    }
    return null;
  }

  private static Set<String> imports(J.CompilationUnit java) {
    return java.getImports().stream()
        .map(imported -> imported.getQualid().toString())
        .collect(Collectors.toSet());
  }

  private static boolean isJobWorker(J.Annotation annotation, Set<String> imports) {
    JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
    if (type != null && JOB_WORKER_TYPES.contains(type.getFullyQualifiedName())) {
      return true;
    }
    if (JOB_WORKER_TYPES.contains(annotation.getAnnotationType().toString())) {
      return true;
    }
    if (!annotation.getSimpleName().equals("JobWorker")) {
      return false;
    }
    return JOB_WORKER_TYPES.stream()
        .anyMatch(
            name ->
                imports.contains(name)
                    || imports.contains(name.substring(0, name.lastIndexOf('.')) + ".*"));
  }

  private static boolean isProductionJava(String path) {
    return path.startsWith("src/main/java/") || path.contains("/src/main/java/");
  }

  private static boolean isApplicationFile(String path) {
    if (path.startsWith("src/test/") || path.contains("/src/test/")) {
      return false;
    }
    String name = Path.of(path).getFileName().toString();
    return name.startsWith("application")
        && (name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml"));
  }

  private static boolean isBuildFile(String path) {
    String name = Path.of(path).getFileName().toString();
    return name.equals("pom.xml") || name.equals("build.gradle") || name.equals("build.gradle.kts");
  }

  private static String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  private static String sourceRoot(String path) {
    int marker = path.indexOf("/src/main/");
    return marker >= 0 ? path.substring(0, marker) : path.startsWith("src/main/") ? "" : null;
  }

  public static final class WorkerModules {
    private final Set<String> workers = ConcurrentHashMap.newKeySet();
    private final Set<String> roots = ConcurrentHashMap.newKeySet();

    private boolean hasWorkers(String path) {
      String root = sourceRoot(path);
      if (root == null) {
        root =
            roots.stream()
                .filter(candidate -> candidate.isEmpty() || path.startsWith(candidate + "/"))
                .max(Comparator.comparingInt(String::length))
                .orElse("");
      }
      return workers.contains(root);
    }
  }
}
