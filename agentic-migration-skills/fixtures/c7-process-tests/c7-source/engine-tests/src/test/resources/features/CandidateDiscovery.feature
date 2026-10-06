# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the License. You may obtain a copy of the License at
#
# http://www.camunda.org/license/
Feature: Candidate test discovery

  Scenario: WithoutEngineExecution
    Given no process is started

  Scenario Outline: MultipleExampleRows
    Given a candidate value "<value>"

    Examples:
      | value |
      | one   |
      | two   |
