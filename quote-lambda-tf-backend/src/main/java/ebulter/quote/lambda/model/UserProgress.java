package ebulter.quote.lambda.model;

public class UserProgress {
    private String username;    // Cognito username (partition key)
    private int lastQuoteId;    // The last quote ID the user has viewed
    private long updatedAt;     // Unix timestamp (milliseconds) of last update

    public UserProgress() {
    }

    public UserProgress(String username, int lastQuoteId, long updatedAt) {
        this.username = username;
        this.lastQuoteId = lastQuoteId;
        this.updatedAt = updatedAt;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getLastQuoteId() {
        return lastQuoteId;
    }

    public void setLastQuoteId(int lastQuoteId) {
        this.lastQuoteId = lastQuoteId;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserProgress that = (UserProgress) o;
        return username.equals(that.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
