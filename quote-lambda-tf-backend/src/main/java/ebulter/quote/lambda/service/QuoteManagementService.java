package ebulter.quote.lambda.service;

import ebulter.quote.lambda.client.ZenClient;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.QuoteAddResponse;
import ebulter.quote.lambda.model.QuotePageResponse;
import ebulter.quote.lambda.model.QuoteWithLikeCount;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.repository.UserLikeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QuoteManagementService {
    private static final Logger logger = LoggerFactory.getLogger(QuoteManagementService.class);
    
    private final QuoteRepository quoteRepository;
    private final UserLikeRepository userLikeRepository;

    public QuoteManagementService(QuoteRepository quoteRepository, UserLikeRepository userLikeRepository) {
        this.quoteRepository = quoteRepository;
        this.userLikeRepository = userLikeRepository;
    }

    /**
     * Get quotes with pagination, search, and sorting
     */
    public QuotePageResponse getQuotesWithPagination(int page, int pageSize, String quoteText, String author, String sortBy, String sortOrder) {
        logger.info("Getting quotes: page={}, pageSize={}, quoteText={}, author={}, sortBy={}, sortOrder={}", 
                page, pageSize, quoteText, author, sortBy, sortOrder);
        
        try {
            // Get all quotes
            List<Quote> allQuotes = quoteRepository.getAllQuotes();
            logger.info("Retrieved {} quotes from database", allQuotes.size());
            
            // Apply filters
            List<Quote> filteredQuotes = allQuotes.stream()
                .filter(q -> quoteText == null || quoteText.isEmpty() || 
                        q.getQuoteText().toLowerCase().contains(quoteText.toLowerCase()))
                .filter(q -> author == null || author.isEmpty() || 
                        q.getAuthor().toLowerCase().contains(author.toLowerCase()))
                .collect(Collectors.toList());
            
            logger.info("After filtering: {} quotes", filteredQuotes.size());
            
            // Debug: Log first few quotes before sorting
            if ("likecount".equalsIgnoreCase(sortBy) && !filteredQuotes.isEmpty()) {
                logger.info("Before sorting by likeCount - First 5 quotes:");
                filteredQuotes.stream().limit(5).forEach(q -> 
                    logger.info("  Quote ID={}, likeCount={}, text={}", q.getId(), q.getLikeCount(), 
                        q.getQuoteText().substring(0, Math.min(50, q.getQuoteText().length()))));
            }
            
            // Apply sorting
            Comparator<Quote> comparator = getComparator(sortBy, sortOrder);
            filteredQuotes.sort(comparator);
            
            // Debug: Log first few quotes after sorting
            if ("likecount".equalsIgnoreCase(sortBy) && !filteredQuotes.isEmpty()) {
                logger.info("After sorting by likeCount - First 5 quotes:");
                filteredQuotes.stream().limit(5).forEach(q -> 
                    logger.info("  Quote ID={}, likeCount={}, text={}", q.getId(), q.getLikeCount(), 
                        q.getQuoteText().substring(0, Math.min(50, q.getQuoteText().length()))));
            }
            
            // Calculate pagination
            int totalCount = filteredQuotes.size();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalCount);
            
            // Validate page bounds
            if (startIndex >= totalCount && totalCount > 0) {
                logger.warn("Page {} out of bounds, total pages: {}", page, totalPages);
                startIndex = 0;
                endIndex = Math.min(pageSize, totalCount);
                page = 1;
            }
            
            // Get page of quotes
            List<Quote> pageQuotes = startIndex < totalCount ? 
                    filteredQuotes.subList(startIndex, endIndex) : 
                    Collections.emptyList();
            
            // Convert to QuoteWithLikeCount (like counts already populated from getAllQuotes)
            List<QuoteWithLikeCount> quotesWithLikes = pageQuotes.stream()
                .map(q -> new QuoteWithLikeCount(
                    q.getId(),
                    q.getQuoteText(),
                    q.getAuthor(),
                    q.getLikeCount()
                ))
                .collect(Collectors.toList());
            
            logger.info("Returning page {} of {} with {} quotes", page, totalPages, quotesWithLikes.size());
            logJsonAudit("INFO", "quotes_list", null, "list", "success", totalCount, page, pageSize);
            
            return new QuotePageResponse(quotesWithLikes, totalCount, page, pageSize, totalPages);
        } catch (Exception e) {
            logger.error("Failed to get quotes with pagination", e);
            logJsonAudit("ERROR", "quotes_list", e.getMessage(), "list", "failure", 0, page, pageSize);
            throw new RuntimeException("Failed to get quotes: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch and add new quotes from ZEN API
     */
    public QuoteAddResponse fetchAndAddNewQuotes(String requestingUsername) {
        logger.info("Fetching new quotes from ZEN API (requested by {})", requestingUsername);
        
        try {
            // Get current quotes
            List<Quote> currentDatabaseQuotes = quoteRepository.getAllQuotes();
            int initialCount = currentDatabaseQuotes.size();
            logger.info("Current database has {} quotes", initialCount);
            
            // Fetch from ZEN API
            logger.info("Fetching quotes from ZEN API");
            Set<Quote> fetchedQuotes = ZenClient.getSomeUniqueQuotes();
            logger.info("Fetched {} quotes from ZEN API", fetchedQuotes.size());
            
            // Remove duplicates by quoteText (same logic as QuoteService.getQuote)
            fetchedQuotes.removeAll(new HashSet<>(currentDatabaseQuotes));
            logger.info("After removing duplicates: {} new quotes to add", fetchedQuotes.size());
            
            if (fetchedQuotes.isEmpty()) {
                logger.info("No new quotes to add");
                logJsonAudit("INFO", "quotes_fetch", null, "fetch", "success", 0, requestingUsername);
                return new QuoteAddResponse(0, initialCount, "No new quotes to add - all fetched quotes already exist");
            }
            
            // Assign new IDs
            AtomicInteger idGenerator = new AtomicInteger(initialCount + 1);
            fetchedQuotes.forEach(quote -> quote.setId(idGenerator.getAndIncrement()));
            
            // Save to database
            logger.info("Saving {} new quotes to database", fetchedQuotes.size());
            quoteRepository.saveAll(fetchedQuotes);
            
            int quotesAdded = fetchedQuotes.size();
            int totalQuotes = initialCount + quotesAdded;
            
            logger.info("Successfully added {} new quotes. Total quotes: {}", quotesAdded, totalQuotes);
            logJsonAudit("INFO", "quotes_fetch", null, "fetch", "success", quotesAdded, requestingUsername);
            
            return new QuoteAddResponse(quotesAdded, totalQuotes, "Successfully added " + quotesAdded + " new quotes");
        } catch (IOException e) {
            logger.error("Failed to fetch quotes from ZEN API", e);
            logJsonAudit("ERROR", "quotes_fetch", "Failed to fetch from ZEN API: " + e.getMessage(), "fetch", "failure", 0, requestingUsername);
            throw new RuntimeException("Failed to fetch quotes from ZEN API: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to add new quotes", e);
            logJsonAudit("ERROR", "quotes_fetch", e.getMessage(), "fetch", "failure", 0, requestingUsername);
            throw new RuntimeException("Failed to add new quotes: " + e.getMessage(), e);
        }
    }

    /**
     * Get comparator based on sort field and order
     */
    private Comparator<Quote> getComparator(String sortBy, String sortOrder) {
        Comparator<Quote> comparator = switch (sortBy != null ? sortBy.toLowerCase() : "id") {
            case "quotetext" -> Comparator.comparing(q -> q.getQuoteText().toLowerCase());
            case "author" -> Comparator.comparing(q -> q.getAuthor().toLowerCase());
            case "likecount" -> Comparator.comparingInt(Quote::getLikeCount);
            default -> Comparator.comparingInt(Quote::getId);
        };

        // Reverse if descending
        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        
        return comparator;
    }

    /**
     * Log audit event in JSON format
     */
    private void logJsonAudit(String level, String event, String errorMessage, String action, String result, int count, Object... additionalFields) {
        Map<String, Object> auditLog = new LinkedHashMap<>();
        auditLog.put("timestamp", Instant.now().toString());
        auditLog.put("level", level);
        auditLog.put("event", event);
        auditLog.put("action", action);
        auditLog.put("result", result);
        
        if (count > 0 || "quotes_list".equals(event)) {
            auditLog.put("count", count);
        }
        
        if (additionalFields.length > 0) {
            if (additionalFields[0] != null) {
                if ("quotes_list".equals(event)) {
                    auditLog.put("page", additionalFields[0]);
                    if (additionalFields.length > 1) {
                        auditLog.put("pageSize", additionalFields[1]);
                    }
                } else if ("quotes_fetch".equals(event)) {
                    auditLog.put("requestingUser", additionalFields[0]);
                }
            }
        }
        
        if (errorMessage != null) {
            auditLog.put("errorMessage", errorMessage);
        }
        
        try {
            String jsonLog = new com.google.gson.Gson().toJson(auditLog);
            switch (level) {
                case "ERROR":
                    logger.error(jsonLog);
                    break;
                case "WARN":
                    logger.warn(jsonLog);
                    break;
                default:
                    logger.info(jsonLog);
                    break;
            }
        } catch (Exception e) {
            logger.error("Failed to create JSON audit log", e);
        }
    }
}
