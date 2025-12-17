package ebulter.quote.lambda.model;

public class QuoteWithLikeCount {
    private int id;
    private String quoteText;
    private String author;
    private int likeCount;

    public QuoteWithLikeCount() {
    }

    public QuoteWithLikeCount(int id, String quoteText, String author, int likeCount) {
        this.id = id;
        this.quoteText = quoteText;
        this.author = author;
        this.likeCount = likeCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getQuoteText() {
        return quoteText;
    }

    public void setQuoteText(String quoteText) {
        this.quoteText = quoteText;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }
}
