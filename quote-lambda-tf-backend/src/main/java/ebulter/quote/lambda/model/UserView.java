package ebulter.quote.lambda.model;

public class UserView {
    private String username;    // Cognito username
    private int quoteId;        // Reference to Quote.id
    private long viewedAt;      // Unix timestamp (milliseconds)

    public UserView() {
    }

    public UserView(String username, int quoteId, long viewedAt) {
        this.username = username;
        this.quoteId = quoteId;
        this.viewedAt = viewedAt;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getQuoteId() {
        return quoteId;
    }

    public void setQuoteId(int quoteId) {
        this.quoteId = quoteId;
    }

    public long getViewedAt() {
        return viewedAt;
    }

    public void setViewedAt(long viewedAt) {
        this.viewedAt = viewedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserView userView = (UserView) o;
        return quoteId == userView.quoteId && username.equals(userView.username);
    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + quoteId;
        return result;
    }
}
