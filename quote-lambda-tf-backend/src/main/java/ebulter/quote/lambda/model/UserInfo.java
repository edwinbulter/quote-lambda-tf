package ebulter.quote.lambda.model;

import java.util.List;

public class UserInfo {
    private String username;
    private String email;
    private List<String> groups;
    private boolean enabled;
    private String userStatus;
    private String userCreateDate;
    private String userLastModifiedDate;

    public UserInfo() {
    }

    public UserInfo(String username, String email, List<String> groups, boolean enabled, String userStatus,
                    String userCreateDate, String userLastModifiedDate) {
        this.username = username;
        this.email = email;
        this.groups = groups;
        this.enabled = enabled;
        this.userStatus = userStatus;
        this.userCreateDate = userCreateDate;
        this.userLastModifiedDate = userLastModifiedDate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }

    public String getUserCreateDate() {
        return userCreateDate;
    }

    public void setUserCreateDate(String userCreateDate) {
        this.userCreateDate = userCreateDate;
    }

    public String getUserLastModifiedDate() {
        return userLastModifiedDate;
    }

    public void setUserLastModifiedDate(String userLastModifiedDate) {
        this.userLastModifiedDate = userLastModifiedDate;
    }
}
