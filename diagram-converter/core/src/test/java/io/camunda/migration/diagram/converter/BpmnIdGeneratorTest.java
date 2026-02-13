/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BpmnIdGeneratorTest {

  @AfterEach
  void tearDown() {
    BpmnIdGenerator.clearInstances();
  }

  @Test
  void shouldGenerateIdWithCorrectPrefix() {
    DomDocument document = createEmptyBpmnDocument();
    BpmnIdGenerator generator = BpmnIdGenerator.getInstance(document);

    String id = generator.generateUniqueId("ConditionalEventDefinition");

    assertThat(id).startsWith("ConditionalEventDefinition_");
  }

  @Test
  void shouldGenerateIdWithCorrectSuffixLength() {
    DomDocument document = createEmptyBpmnDocument();
    BpmnIdGenerator generator = BpmnIdGenerator.getInstance(document);

    String id = generator.generateUniqueId("ConditionalEventDefinition");

    String suffix = id.substring("ConditionalEventDefinition_".length());
    assertThat(suffix).hasSize(BpmnIdGenerator.SUFFIX_LENGTH);
  }

  @Test
  void shouldGenerateAlphanumericSuffix() {
    DomDocument document = createEmptyBpmnDocument();
    BpmnIdGenerator generator = BpmnIdGenerator.getInstance(document);

    String id = generator.generateUniqueId("Test");
    String suffix = id.substring("Test_".length());

    assertThat(suffix).matches("[0-9a-z]+");
  }

  @Test
  void shouldReturnSameInstanceForSameDocument() {
    DomDocument document = createEmptyBpmnDocument();

    BpmnIdGenerator generator1 = BpmnIdGenerator.getInstance(document);
    BpmnIdGenerator generator2 = BpmnIdGenerator.getInstance(document);

    assertThat(generator1).isSameAs(generator2);
  }

  @Test
  void shouldReturnDifferentInstancesForDifferentDocuments() {
    DomDocument document1 = createEmptyBpmnDocument();
    DomDocument document2 = createEmptyBpmnDocument();

    BpmnIdGenerator generator1 = BpmnIdGenerator.getInstance(document1);
    BpmnIdGenerator generator2 = BpmnIdGenerator.getInstance(document2);

    assertThat(generator1).isNotSameAs(generator2);
  }

  @Test
  void shouldNotGenerateIdThatAlreadyExistsInDocument() {
    // Use a seeded Random to get predictable output
    long seed = 12345L;
    DomDocument document = createEmptyBpmnDocument();

    // First, generate an ID with this seed to know what it will produce
    Random random1 = new Random(seed);
    BpmnIdGenerator generator1 = BpmnIdGenerator.createWithRandom(document, random1);
    String predictedId = generator1.generateUniqueId("Test");

    // Now create a document that already has this ID
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("testProcess")
            .startEvent(predictedId) // Use the predicted ID
            .endEvent()
            .done();
    DomDocument documentWithExistingId = modelInstance.getDocument();

    // Create a generator with the same seed - it will try to generate the same ID
    Random random2 = new Random(seed);
    BpmnIdGenerator generator2 = BpmnIdGenerator.createWithRandom(documentWithExistingId, random2);
    String generatedId = generator2.generateUniqueId("Test");

    // The generated ID should be different because the predicted one already exists
    assertThat(generatedId).isNotEqualTo(predictedId);
  }

  @Test
  void shouldNotGenerateDuplicateWhenRandomProducesSameSuffixTwice() {
    DomDocument document = createEmptyBpmnDocument();
    BpmnIdGenerator generator =
        BpmnIdGenerator.createWithRandom(document, createRandomThatRepeatsFirstSuffix());

    String firstId = generator.generateUniqueId("Test");
    // Second call: Random will try to produce the same suffix, but generator should detect
    // the collision and retry, getting a different (third) suffix
    String secondId = generator.generateUniqueId("Test");

    assertThat(secondId).isNotEqualTo(firstId);
  }

  @Test
  void shouldGenerateUniqueIdsWithGetInstance() {
    DomDocument document = createEmptyBpmnDocument();
    BpmnIdGenerator generator = BpmnIdGenerator.getInstance(document);

    // Generate multiple IDs and verify they are all unique
    String id1 = generator.generateUniqueId("Test");
    String id2 = generator.generateUniqueId("Test");
    String id3 = generator.generateUniqueId("Test");

    assertThat(id1).isNotEqualTo(id2);
    assertThat(id1).isNotEqualTo(id3);
    assertThat(id2).isNotEqualTo(id3);
    
    // All should have the correct format
    assertThat(id1).startsWith("Test_");
    assertThat(id2).startsWith("Test_");
    assertThat(id3).startsWith("Test_");
  }

  /**
   * Creates a Random that produces the same suffix sequence twice, then advances normally. This
   * forces the generator to handle a collision with a previously generated ID.
   */
  private Random createRandomThatRepeatsFirstSuffix() {
    return new Random() {
      private final Random inner = new Random(12345L);
      private int callCount = 0;
      private final int[] firstSuffixValues = new int[BpmnIdGenerator.SUFFIX_LENGTH];

      @Override
      public int nextInt(int bound) {
        int index = callCount % BpmnIdGenerator.SUFFIX_LENGTH;
        int suffixNumber = callCount / BpmnIdGenerator.SUFFIX_LENGTH;
        callCount++;

        if (suffixNumber == 0) {
          // First suffix: record the values
          firstSuffixValues[index] = inner.nextInt(bound);
          return firstSuffixValues[index];
        } else if (suffixNumber == 1) {
          // Second suffix: return the same values (forcing collision)
          return firstSuffixValues[index];
        } else {
          // Third suffix onwards: return fresh random values
          return inner.nextInt(bound);
        }
      }
    };
  }

  private DomDocument createEmptyBpmnDocument() {
    BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("testProcess").startEvent().endEvent().done();
    return modelInstance.getDocument();
  }
}
