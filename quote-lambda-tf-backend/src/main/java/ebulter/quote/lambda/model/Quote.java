package ebulter.quote.lambda.model;

public class Quote {
    private int id;
    private String quoteText;
    private String author;
    private int likes;

    public Quote() {
        this.likes = 0;
    }

    public Quote(int id, String quoteText, String author, int likes) {
        this.id = id;
        this.quoteText = quoteText;
        this.author = author;
        this.likes = likes;
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

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Quote quote = (Quote) o;
        return quoteText.equals(quote.quoteText);
    }

    @Override
    public int hashCode() {
        return quoteText.hashCode();
    }
}
