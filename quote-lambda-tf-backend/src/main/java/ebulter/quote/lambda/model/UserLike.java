package ebulter.quote.lambda.model;

public class UserLike {
    private String username;    // Cognito username
    private int quoteId;        // Reference to Quote.id
    private long likedAt;       // Unix timestamp (milliseconds)
    private Integer order;      // Sort order for user's favourites (1, 2, 3, ...)

    public UserLike() {
    }

    public UserLike(String username, int quoteId, long likedAt) {
        this.username = username;
        this.quoteId = quoteId;
        this.likedAt = likedAt;
    }

    public UserLike(String username, int quoteId, long likedAt, Integer order) {
        this.username = username;
        this.quoteId = quoteId;
        this.likedAt = likedAt;
        this.order = order;
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

    public long getLikedAt() {
        return likedAt;
    }

    public void setLikedAt(long likedAt) {
        this.likedAt = likedAt;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserLike userLike = (UserLike) o;
        return quoteId == userLike.quoteId && username.equals(userLike.username);
    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + quoteId;
        return result;
    }
}
