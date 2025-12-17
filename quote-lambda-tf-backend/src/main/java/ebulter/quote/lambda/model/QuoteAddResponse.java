package ebulter.quote.lambda.model;

public class QuoteAddResponse {
    private int quotesAdded;
    private int totalQuotes;
    private String message;

    public QuoteAddResponse() {
    }

    public QuoteAddResponse(int quotesAdded, int totalQuotes, String message) {
        this.quotesAdded = quotesAdded;
        this.totalQuotes = totalQuotes;
        this.message = message;
    }

    public int getQuotesAdded() {
        return quotesAdded;
    }

    public void setQuotesAdded(int quotesAdded) {
        this.quotesAdded = quotesAdded;
    }

    public int getTotalQuotes() {
        return totalQuotes;
    }

    public void setTotalQuotes(int totalQuotes) {
        this.totalQuotes = totalQuotes;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
