/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client;

import java.util.HashSet;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;

public class DeleteProcessesXmlRecipe extends ScanningRecipe<Set<String>> {

  private static final String C7_SCHEMA_URI =
      "http://www.camunda.org/schema/1.0/ProcessApplication";
  private static final String PROCESSES_XML_SUFFIX = "META-INF/processes.xml";

  @Override
  public String getDisplayName() {
    return "Delete META-INF/processes.xml";
  }

  @Override
  public String getDescription() {
    return "Removes the Camunda 7 process archive descriptor (processes.xml),"
        + " which has no equivalent in Camunda 8.";
  }

  @Override
  public Set<String> getInitialValue(ExecutionContext ctx) {
    return new HashSet<>();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(Set<String> filesToDelete) {
    return new TreeVisitor<>() {
      @Override
      public Tree visit(Tree tree, ExecutionContext ctx) {
        if (tree instanceof SourceFile sourceFile) {
          String path = sourceFile.getSourcePath().toString();
          if (path.endsWith(PROCESSES_XML_SUFFIX)
              && sourceFile.printAll().contains(C7_SCHEMA_URI)) {
            filesToDelete.add(path);
          }
        }
        return tree;
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> filesToDelete) {
    return new TreeVisitor<>() {
      @Override
      public Tree visit(Tree tree, ExecutionContext ctx) {
        if (tree instanceof SourceFile sourceFile
            && filesToDelete.contains(sourceFile.getSourcePath().toString())) {
          return null;
        }
        return tree;
      }
    };
  }
}
