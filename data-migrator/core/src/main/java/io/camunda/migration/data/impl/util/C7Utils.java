/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.util;

import io.camunda.migration.data.impl.model.FlowNode;
import java.util.Arrays;
import java.util.Map;
import org.camunda.bpm.engine.impl.persistence.entity.ActivityInstanceImpl;
import org.camunda.bpm.engine.impl.persistence.entity.TransitionInstanceImpl;
import org.camunda.bpm.engine.runtime.ActivityInstance;

public class C7Utils {

  public static final String SUB_PROCESS_ACTIVITY_TYPE = "subProcess";
  public static final String MULTI_INSTANCE_BODY_SUFFIX = "#multiInstanceBody";
  public static final String PARALLEL_GATEWAY_ACTIVITY_TYPE = "parallelGateway";

  /**
   * Collects active activity IDs by recursively traversing the activity instance tree.
   */
  public static Map<String, FlowNode> getActiveActivityIdsById(ActivityInstance activityInstance,
                                                               Map<String, FlowNode> activeActivities) {
    Arrays.asList(activityInstance.getChildActivityInstances()).forEach(actInst -> {
      activeActivities.putAll(getActiveActivityIdsById(actInst, activeActivities));

      if (!SUB_PROCESS_ACTIVITY_TYPE.equals(actInst.getActivityType())) {
        activeActivities.put(actInst.getId(),
            new FlowNode(actInst.getActivityId(), ((ActivityInstanceImpl) actInst).getSubProcessInstanceId()));
      }
    });

    /* TODO: Transition instances might map to start before or after.
    When it maps to asyncBefore it should be fine. When it maps to asyncAfter an execution is fired twice in C7 and C8.
     */
    Arrays.asList(activityInstance.getChildTransitionInstances()).forEach(ti -> {
      var transitionInstance = ((TransitionInstanceImpl) ti);
      if (!SUB_PROCESS_ACTIVITY_TYPE.equals(transitionInstance.getActivityType())) {
        activeActivities.put(transitionInstance.getId(),
            new FlowNode(transitionInstance.getActivityId(), transitionInstance.getSubProcessInstanceId()));
      }
    });
    return activeActivities;
  }

}
