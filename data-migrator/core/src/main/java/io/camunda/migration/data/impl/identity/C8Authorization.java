package io.camunda.migration.data.impl.identity;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import java.util.Set;

public record C8Authorization(OwnerType ownerType,
                              String ownerId,
                              ResourceType resourceType,
                              String resourceId,
                              Set<PermissionType> permissions) {
}
