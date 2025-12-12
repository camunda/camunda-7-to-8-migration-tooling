/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.identity;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.OwnerType;
import org.camunda.bpm.engine.IdentityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentityManager {

  @Autowired
  protected CamundaClient camundaClient;

  @Autowired
  protected AuthorizationManager authorizationManager;

  @Autowired
  protected IdentityService identityService;

  /**
   * Checks if owner exists in C8
   * @param owner
   * @return true if it exists, false otherwise
   */
  public boolean ownerExists(Owner owner) {
    Object userOrGroup = null;
    try {
      if (owner.ownerType().equals(OwnerType.USER)) {
        userOrGroup = camundaClient.newUserGetRequest(owner.ownerId()).execute();
      } else if (owner.ownerType().equals(OwnerType.GROUP)) {
        userOrGroup = camundaClient.newGroupGetRequest(owner.ownerId()).execute();
      }
    } catch (ProblemException e) {
      if (e.details().getStatus() == 404) {
        return false;
      }
    }
    return userOrGroup != null;
  }

  /**
   * Migrates an owner (GROUP or USER) from C7 to C8
   * The owner needs to exist in C7 (which might not be the case cause C7 doesn't enforce authorizations to match to an existing User/Group, but C8 does)
   * @param owner
   */
  public void createOwner(Owner owner) {
    try {
      if (owner.ownerType().equals(OwnerType.USER)) {
        var user = identityService.createUserQuery().userId(owner.ownerId()).singleResult();
        camundaClient.newCreateUserCommand()
            .name(user.getFirstName() + " " + user.getLastName())
            .username(user.getId())
            .email(user.getEmail())
            .execute();
      }  else if (owner.ownerType().equals(OwnerType.GROUP)) {
        var group = identityService.createGroupQuery().groupId(owner.ownerId()).singleResult();
        camundaClient.newCreateGroupCommand()
            .groupId(group.getId())
            .name(group.getName())
            .execute();
      }
    } catch (ProblemException e) {
      throw new IllegalStateException("Couldn't create owner", e);
    }
  }

}
