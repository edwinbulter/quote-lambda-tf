package ebulter.quote.lambda.model;

import java.util.List;

public class QuotePageResponse {
    private List<QuoteWithLikeCount> quotes;
    private int totalCount;
    private int page;
    private int pageSize;
    private int totalPages;

    public QuotePageResponse() {
    }

    public QuotePageResponse(List<QuoteWithLikeCount> quotes, int totalCount, int page, int pageSize, int totalPages) {
        this.quotes = quotes;
        this.totalCount = totalCount;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
    }

    public List<QuoteWithLikeCount> getQuotes() {
        return quotes;
    }

    public void setQuotes(List<QuoteWithLikeCount> quotes) {
        this.quotes = quotes;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
