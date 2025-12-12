package io.camunda.migration.data.impl.identity;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ResourceTypeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationManager {

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  public Optional<C8Authorization> mapAuthorization(Authorization authorization) {
    Resource c7ResourceType = ResourceTypeUtil.getResourceByType(authorization.getResourceType());
    if (!C7ToC8AuthorizationRegistry.isSupported(c7ResourceType)) {
      // Unsupported resource type
      return Optional.empty();
    }

    var mappingForResourceType = C7ToC8AuthorizationRegistry.getMappingForResourceType(c7ResourceType);
    ResourceType c8ResourceType = mappingForResourceType.c8ResourceType();
    Set<Permission> c7Permissions = decodePermissions(authorization);

    Set<PermissionType> c8Permissions = new HashSet<>();
    for (Permission permission : c7Permissions) {
      if (mappingForResourceType.isPermissionMappingSupported(permission)) {
        Set<PermissionType> c8MappedPermissions = mappingForResourceType.getMappedPermissions(permission);
        c8Permissions.addAll(c8MappedPermissions);
      } else {
        // Unsupported permission
        return Optional.empty();
      }
    }
    if (!isValidResourceId(mappingForResourceType, authorization.getResourceId())) {
      // Unsupported resource ID
      return Optional.empty();
    }

    String c8ResourceId = mapResourceId(mappingForResourceType, authorization.getResourceId());
    if (c8ResourceId == null) {
      // Cannot map resource ID
      return Optional.empty();
    }

    OwnerType ownerType = isNotBlank(authorization.getUserId()) ? OwnerType.USER : OwnerType.GROUP;
    String ownerId = isNotBlank(authorization.getUserId()) ? authorization.getUserId() : authorization.getGroupId();

    return Optional.of(new C8Authorization(ownerType, ownerId, c8ResourceType, c8ResourceId, c8Permissions));
  }

  private String mapResourceId(AuthorizationMappingEntry authMapping, String resourceId) {
    if (authMapping.needsResourceIdMapping()) {
      return authMapping.getMappedResourceId(resourceId);
    } else {
      return resourceId;
    }
  }

  private boolean isValidResourceId(AuthorizationMappingEntry authMapping, String resourceId) {
    if (resourceId.equals("*")) {
      return true; // Available for all resource types
    } else
      return authMapping.isSpecificResourceIdSupported(); // Specific resource IDs are supported
  }

  protected Set<Permission> decodePermissions(Authorization authorization) {
    int resourceType = authorization.getResourceType();
    Permission[] permissionsForResourceType = processEngineConfiguration.getPermissionProvider().getPermissionsForResource(resourceType);
    Set<Permission> permissions = Arrays.stream(authorization.getPermissions(permissionsForResourceType)).collect(Collectors.toSet());

    if (permissions.stream().anyMatch(permission -> equals(permission, Permissions.ALL))) { // If has ALL
      return permissions.stream().filter(permission -> equals(permission, Permissions.ALL)).collect(Collectors.toSet()); // Return only ALL
    } else { // Else, remove NONE from the list and return singular permissions
      return permissions.stream().filter(permission -> !equals(permission, Permissions.NONE)).collect(Collectors.toSet());
    }
  }

  protected boolean equals(Permission permission1, Permission permission2) {
    return permission1.getName().equals(permission2.getName()) &&
        permission1.getValue() == permission2.getValue();
  }
}
