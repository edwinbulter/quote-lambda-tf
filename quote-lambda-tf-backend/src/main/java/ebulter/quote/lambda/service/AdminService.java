package ebulter.quote.lambda.service;

import ebulter.quote.lambda.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class AdminService {
    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
    private static final Set<String> VALID_GROUPS = Set.of("USER", "ADMIN");

    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;

    public AdminService(CognitoIdentityProviderClient cognitoClient, String userPoolId) {
        this.cognitoClient = cognitoClient;
        this.userPoolId = userPoolId;
    }

    public List<UserInfo> listAllUsers() {
        logger.info("Listing all users from user pool: {}", userPoolId);
        
        try {
            List<UserInfo> userInfoList = new ArrayList<>();
            String paginationToken = null;
            
            do {
                ListUsersRequest.Builder requestBuilder = ListUsersRequest.builder()
                        .userPoolId(userPoolId)
                        .limit(60);
                
                if (paginationToken != null) {
                    requestBuilder.paginationToken(paginationToken);
                }
                
                ListUsersResponse response = cognitoClient.listUsers(requestBuilder.build());
                
                for (UserType user : response.users()) {
                    String username = user.username();
                    String email = getUserAttribute(user, "email");
                    
                    // Get groups for this user
                    List<String> groups = getUserGroups(username);
                    
                    UserInfo userInfo = new UserInfo(
                            username,
                            email,
                            groups,
                            user.enabled(),
                            user.userStatusAsString(),
                            user.userCreateDate() != null ? user.userCreateDate().toString() : null,
                            user.userLastModifiedDate() != null ? user.userLastModifiedDate().toString() : null
                    );
                    
                    userInfoList.add(userInfo);
                }
                
                paginationToken = response.paginationToken();
            } while (paginationToken != null);
            
            logger.info("Successfully listed {} users", userInfoList.size());
            logJsonAudit("INFO", "list_users", null, null, null, "list", "success", null, null);
            
            return userInfoList;
        } catch (Exception e) {
            logger.error("Failed to list users", e);
            logJsonAudit("ERROR", "list_users", null, null, null, "list", "failure", e.getMessage(), "LIST_USERS_FAILED");
            throw new RuntimeException("Failed to list users: " + e.getMessage(), e);
        }
    }

    public void addUserToGroup(String username, String groupName, String requestingUsername) {
        logger.info("Adding user {} to group {} (requested by {})", username, groupName, requestingUsername);
        
        validateGroupName(groupName);
        
        try {
            AdminAddUserToGroupRequest request = AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .groupName(groupName)
                    .build();
            
            cognitoClient.adminAddUserToGroup(request);
            
            logger.info("Successfully added user {} to group {}", username, groupName);
            logJsonAudit("INFO", "role_change", requestingUsername, username, groupName, "add", "success", null, null);
        } catch (UserNotFoundException e) {
            logger.error("User not found: {}", username);
            logJsonAudit("ERROR", "role_change", requestingUsername, username, groupName, "add", "failure", "User not found", "USER_NOT_FOUND");
            throw new IllegalArgumentException("User not found: " + username);
        } catch (ResourceNotFoundException e) {
            logger.error("Group not found: {}", groupName);
            logJsonAudit("ERROR", "role_change", requestingUsername, username, groupName, "add", "failure", "Group not found", "GROUP_NOT_FOUND");
            throw new IllegalArgumentException("Group not found: " + groupName);
        } catch (Exception e) {
            logger.error("Failed to add user to group", e);
            logJsonAudit("ERROR", "role_change", requestingUsername, username, groupName, "add", "failure", e.getMessage(), "ADD_USER_TO_GROUP_FAILED");
            throw new RuntimeException("Failed to add user to group: " + e.getMessage(), e);
        }
    }

    public void removeUserFromGroup(String username, String groupName, String requestingUsername) {
        logger.info("Removing user {} from group {} (requested by {})", username, groupName, requestingUsername);
        
        validateGroupName(groupName);
        
        // Prevent self-removal from ADMIN group
        if ("ADMIN".equals(groupName) && username.equals(requestingUsername)) {
            logger.warn("User {} attempted to remove themselves from ADMIN group", username);
            logJsonAudit("WARN", "role_change", requestingUsername, username, groupName, "remove", "failure", 
                    "Cannot remove self from ADMIN group", "SELF_REMOVAL_FORBIDDEN");
            throw new IllegalArgumentException("Cannot remove yourself from ADMIN group");
        }
        
        try {
            AdminRemoveUserFromGroupRequest request = AdminRemoveUserFromGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .groupName(groupName)
                    .build();
            
            cognitoClient.adminRemoveUserFromGroup(request);
            
            logger.info("Successfully removed user {} from group {}", username, groupName);
            logJsonAudit("INFO", "role_change", requestingUsername, username, groupName, "remove", "success", null, null);
        } catch (UserNotFoundException e) {
            logger.error("User not found: {}", username);
            logJsonAudit("ERROR", "role_change", requestingUsername, username, groupName, "remove", "failure", "User not found", "USER_NOT_FOUND");
            throw new IllegalArgumentException("User not found: " + username);
        } catch (ResourceNotFoundException e) {
            logger.error("Group not found: {}", groupName);
            logJsonAudit("ERROR", "role_change", requestingUsername, username, groupName, "remove", "failure", "Group not found", "GROUP_NOT_FOUND");
            throw new IllegalArgumentException("Group not found: " + groupName);
        } catch (Exception e) {
            logger.error("Failed to remove user from group", e);
            logJsonAudit("ERROR", "role_change", requestingUsername, username, groupName, "remove", "failure", e.getMessage(), "REMOVE_USER_FROM_GROUP_FAILED");
            throw new RuntimeException("Failed to remove user from group: " + e.getMessage(), e);
        }
    }

    private List<String> getUserGroups(String username) {
        try {
            AdminListGroupsForUserRequest request = AdminListGroupsForUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .build();
            
            AdminListGroupsForUserResponse response = cognitoClient.adminListGroupsForUser(request);
            
            return response.groups().stream()
                    .map(GroupType::groupName)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Failed to get groups for user {}: {}", username, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getUserAttribute(UserType user, String attributeName) {
        return user.attributes().stream()
                .filter(attr -> attr.name().equals(attributeName))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    private void validateGroupName(String groupName) {
        if (!VALID_GROUPS.contains(groupName)) {
            throw new IllegalArgumentException("Invalid group name: " + groupName + ". Must be one of: " + VALID_GROUPS);
        }
    }

    private void logJsonAudit(String level, String event, String requestingUser, String targetUser, 
                              String group, String action, String result, String errorMessage, String errorCode) {
        Map<String, Object> auditLog = new LinkedHashMap<>();
        auditLog.put("timestamp", Instant.now().toString());
        auditLog.put("level", level);
        auditLog.put("event", event);
        
        if (requestingUser != null) {
            auditLog.put("requestingUser", requestingUser);
        }
        if (targetUser != null) {
            auditLog.put("targetUser", targetUser);
        }
        if (group != null) {
            auditLog.put("group", group);
        }
        if (action != null) {
            auditLog.put("action", action);
        }
        
        auditLog.put("result", result);
        
        if (errorMessage != null) {
            auditLog.put("errorMessage", errorMessage);
        }
        if (errorCode != null) {
            auditLog.put("errorCode", errorCode);
        }
        
        // Log as JSON string
        logger.info("AUDIT: {}", new com.google.gson.Gson().toJson(auditLog));
    }
}
