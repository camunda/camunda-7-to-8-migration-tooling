package io.camunda.migration.data.impl.identity;

import static io.camunda.client.api.search.enums.ResourceType.COMPONENT;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.camunda.bpm.engine.authorization.Resources.APPLICATION;
import static org.camunda.bpm.engine.authorization.Resources.AUTHORIZATION;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ResourceTypeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationManager {

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  public static final Map<Pair<Resources, Permissions>, Set<PermissionType>> PERMISSIONS_MAPPING_TABLE = new HashMap<>();
  public static final Map<Resource, ResourceType> RESOURCE_TYPE_MAPPING_TABLE = new HashMap<>();
  public static final Map<String, String> APP_MAPPING_TABLE = new HashMap<>();
  static {
    RESOURCE_TYPE_MAPPING_TABLE.put(APPLICATION, ResourceType.COMPONENT);
    RESOURCE_TYPE_MAPPING_TABLE.put(AUTHORIZATION, ResourceType.AUTHORIZATION);
  }

  static {
    // Application
    PERMISSIONS_MAPPING_TABLE.put(Pair.of(APPLICATION, Permissions.ALL), getAllSupportedPerms(COMPONENT));
    PERMISSIONS_MAPPING_TABLE.put(Pair.of(APPLICATION, Permissions.ACCESS), Set.of(PermissionType.ACCESS));

    // Authorization
    PERMISSIONS_MAPPING_TABLE.put(Pair.of(AUTHORIZATION, Permissions.ALL), getAllSupportedPerms(ResourceType.AUTHORIZATION));
    PERMISSIONS_MAPPING_TABLE.put(Pair.of(AUTHORIZATION, Permissions.READ), Set.of(PermissionType.READ));
    PERMISSIONS_MAPPING_TABLE.put(Pair.of(AUTHORIZATION, Permissions.UPDATE), Set.of(PermissionType.UPDATE));
    PERMISSIONS_MAPPING_TABLE.put(Pair.of(AUTHORIZATION, Permissions.CREATE), Set.of(PermissionType.CREATE));
    PERMISSIONS_MAPPING_TABLE.put(Pair.of(AUTHORIZATION, Permissions.DELETE), Set.of(PermissionType.DELETE));
  }

  static {
    APP_MAPPING_TABLE.put("*", "*");
    APP_MAPPING_TABLE.put("cockpit", "operate");
    APP_MAPPING_TABLE.put("tasklist", "tasklist");
    APP_MAPPING_TABLE.put("admin", "identity");
  }

  public C8Authorization mapAuthorization(Authorization authorization) {
    Resource c7ResourceType = ResourceTypeUtil.getResourceByType(authorization.getResourceType());
    if (!RESOURCE_TYPE_MAPPING_TABLE.containsKey(c7ResourceType)) {
      // Unsupported resource type
      return null;
    }

    ResourceType c8ResourceType = RESOURCE_TYPE_MAPPING_TABLE.get(c7ResourceType);
    Set<Permission> c7Permissions = decodePermissions(authorization);

    Set<PermissionType> c8Permissions = new HashSet<>();
    for (Permission permission : c7Permissions) {
      if (PERMISSIONS_MAPPING_TABLE.containsKey(Pair.of(c7ResourceType, permission))) {
        Set<PermissionType> c8MappedPermissions = PERMISSIONS_MAPPING_TABLE.get(Pair.of(c7ResourceType, permission));
        c8Permissions.addAll(c8MappedPermissions);
      } else {
        // Unsupported permission
        return null;
      }
    }
    OwnerType ownerType = isNotBlank(authorization.getUserId()) ? OwnerType.USER : OwnerType.GROUP;
    String ownerId = isNotBlank(authorization.getUserId()) ? authorization.getUserId() : authorization.getGroupId();
    String c8ResourceId = APP_MAPPING_TABLE.get(authorization.getResourceId()); // TODO extend for other types
    return new C8Authorization(ownerType, ownerId, c8ResourceType, c8ResourceId, c8Permissions);
  }

  protected static Set<PermissionType> getAllSupportedPerms(ResourceType resourceType) {
    return AuthorizationResourceType
        .valueOf(resourceType.name())
        .getSupportedPermissionTypes()
        .stream()
        .map(permissionType -> PermissionType.valueOf(permissionType.name())) // Needed to convert from io.camunda.zeebe.protocol.record.value.PermissionType to io.camunda.client.api.search.enums.PermissionType
        .collect(Collectors.toSet());
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
