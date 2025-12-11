package io.camunda.migration.data.impl.identity;

import io.camunda.client.api.response.Resource;
import io.camunda.client.api.search.enums.OwnerType;
import java.security.Permission;

public record C7Authorization(OwnerType ownerType,
                              String ownerId,
                              Resource resourceType,
                              String resourceId,
                              Permission permission) {

}
