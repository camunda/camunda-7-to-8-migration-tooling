package org.camunda.migration.rewrite.recipes.utils;

import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

public class RecipeUtils {

  public static J.Identifier createSimpleIdentifier(String simpleName, String javaType) {
    return new J.Identifier(
        Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, simpleName, JavaType.ShallowClass.build(javaType), null);
  }

  public static J.Identifier createSimpleIdentifier(String simpleName, JavaType javaType) {
    return new J.Identifier(
            Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, simpleName, javaType, null);
  }

  public static JavaTemplate createSimpleJavaTemplate(String code) {
    return JavaTemplate.builder(code)
        .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
        .build();
  }
}
