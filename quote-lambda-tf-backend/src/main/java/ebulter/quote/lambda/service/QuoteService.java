package ebulter.quote.lambda.service;

import ebulter.quote.lambda.client.ZenClient;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.UserLike;
import ebulter.quote.lambda.model.UserProgress;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.repository.UserLikeRepository;
import ebulter.quote.lambda.repository.UserProgressRepository;
import ebulter.quote.lambda.util.QuoteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class QuoteService {
    private static final Logger logger = LoggerFactory.getLogger(QuoteService.class);
    private final QuoteRepository quoteRepository;
    private final UserLikeRepository userLikeRepository;
    private UserProgressRepository userProgressRepository;

    public QuoteService(QuoteRepository quoteRepository, UserLikeRepository userLikeRepository) {
        this.quoteRepository = quoteRepository;
        this.userLikeRepository = userLikeRepository;
        try {
            this.userProgressRepository = new UserProgressRepository();
        } catch (Exception e) {
            logger.warn("Could not initialize UserProgressRepository, sequential navigation will be disabled: {}", e.getMessage());
            this.userProgressRepository = null;
        }
    }
    
    // Constructor for testing that allows injecting UserProgressRepository
    public QuoteService(QuoteRepository quoteRepository, UserLikeRepository userLikeRepository, UserProgressRepository userProgressRepository) {
        this.quoteRepository = quoteRepository;
        this.userLikeRepository = userLikeRepository;
        this.userProgressRepository = userProgressRepository;
    }

    public Quote getQuote(String username, final Set<Integer> idsToExclude) {
        // For authenticated users, use sequential navigation if available
        if (username != null && !username.isEmpty()) {
            if (userProgressRepository != null) {
                return getNextSequentialQuote(username);
            } else {
                // Fallback to old behavior if UserProgressRepository is not available
                return getRandomQuoteForUnauthenticatedUser(idsToExclude);
            }
        }
        
        // For unauthenticated users, use the old random approach (but exclude provided IDs)
        return getRandomQuoteForUnauthenticatedUser(idsToExclude);
    }

    /**
     * Get the next sequential quote for an authenticated user
     */
    private Quote getNextSequentialQuote(String username) {
        logger.info("Getting next sequential quote for user: {}", username);
        
        // Get user's current progress
        UserProgress userProgress = userProgressRepository.getUserProgress(username);
        int nextQuoteId;
        
        if (userProgress == null) {
            // New user - start with quote ID 1
            nextQuoteId = 1;
            logger.info("New user {} starting with quote ID: {}", username, nextQuoteId);
        } else {
            // Existing user - get next quote
            nextQuoteId = userProgress.getLastQuoteId() + 1;
            logger.info("User {} progress: lastQuoteId={}, nextQuoteId={}", username, userProgress.getLastQuoteId(), nextQuoteId);
        }
        
        // Check if we need to fetch more quotes from Zen
        int maxId = quoteRepository.getMaxQuoteId();
        if (nextQuoteId > maxId) {
            logger.info("Next quote ID {} exceeds max ID {}, fetching more quotes", nextQuoteId, maxId);
            fetchMoreQuotesIfNeeded();
            maxId = quoteRepository.getMaxQuoteId();
        }
        
        // Get the quote
        Quote quote = quoteRepository.findById(nextQuoteId);
        if (quote == null) {
            logger.warn("Quote with ID {} not found, finding next available quote", nextQuoteId);
            quote = findNextAvailableQuote(nextQuoteId);
        }
        
        if (quote != null) {
            // Update user progress
            userProgressRepository.updateLastQuoteId(username, quote.getId());
            logger.info("Updated user {} progress to lastQuoteId={}", username, quote.getId());
        }
        
        return quote;
    }

    /**
     * Get random quote for unauthenticated users (legacy behavior)
     */
    private Quote getRandomQuoteForUnauthenticatedUser(Set<Integer> idsToExclude) {
        logger.info("Getting random quote for unauthenticated user, excluding {} IDs", idsToExclude.size());
        
        int maxId = quoteRepository.getMaxQuoteId();
        logger.info("Max quote ID in database: {}", maxId);
        
        // Check if we need to fetch more quotes from Zen
        if (maxId < 5 || maxId <= idsToExclude.size()) {
            fetchMoreQuotesIfNeeded();
            maxId = quoteRepository.getMaxQuoteId();
        }
        
        // Try to find a valid quote by checking random IDs
        Random random = new Random();
        int maxAttempts = Math.min(100, maxId);
        Set<Integer> attemptedIds = new HashSet<>();
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int candidateId = random.nextInt(maxId) + 1;
            
            if (idsToExclude.contains(candidateId) || attemptedIds.contains(candidateId)) {
                continue;
            }
            
            attemptedIds.add(candidateId);
            
            Quote quote = quoteRepository.findById(candidateId);
            if (quote != null) {
                logger.info("Selected random quote ID: {} after {} attempts", candidateId, attempt + 1);
                return quote;
            }
        }
        
        // Fallback to full scan
        logger.warn("Could not find valid quote with {} random attempts, falling back to full scan", maxAttempts);
        List<Quote> allQuotes = quoteRepository.getAllQuotes();
        List<Quote> filteredQuotes = allQuotes.stream()
                .filter(quote -> !idsToExclude.contains(quote.getId()))
                .toList();
        
        if (filteredQuotes.isEmpty()) {
            logger.warn("No available quotes after excluding {} IDs", idsToExclude.size());
            return null;
        }
        
        int randomIndex = random.nextInt(filteredQuotes.size());
        Quote selectedQuote = filteredQuotes.get(randomIndex);
        logger.info("Selected quote ID: {} using fallback method", selectedQuote.getId());
        return selectedQuote;
    }

    /**
     * Fetch more quotes from Zen if needed
     */
    private void fetchMoreQuotesIfNeeded() {
        try {
            logger.info("start fetching quotes from Zen");
            Set<Quote> fetchedQuotes = ZenClient.getSomeUniqueQuotes();
            logger.info("finished fetching quotes from Zen, fetched {} quotes", fetchedQuotes.size());
            
            List<Quote> currentDatabaseQuotes = quoteRepository.getAllQuotes();
            fetchedQuotes.removeAll(new HashSet<>(currentDatabaseQuotes));
            AtomicInteger idGenerator = new AtomicInteger(currentDatabaseQuotes.size() + 1);
            fetchedQuotes.forEach(quote -> quote.setId(idGenerator.getAndIncrement()));
            logger.info("start saving {} quotes", fetchedQuotes.size());
            quoteRepository.saveAll(fetchedQuotes);
            logger.info("finished saving {} quotes", fetchedQuotes.size());
        } catch (IOException e) {
            logger.error("Failed to read quotes from ZenQuotes: {}", e.getMessage());
        }
    }

    /**
     * Find the next available quote ID (handles gaps in sequence)
     */
    private Quote findNextAvailableQuote(int startId) {
        int maxId = quoteRepository.getMaxQuoteId();
        for (int id = startId; id <= maxId; id++) {
            Quote quote = quoteRepository.findById(id);
            if (quote != null) {
                logger.info("Found next available quote ID: {}", id);
                return quote;
            }
        }
        return null;
    }

    public Quote likeQuote(String username, int quoteId) {
        Quote quote = quoteRepository.findById(quoteId);
        if (quote != null) {
            // Get max order for user and set new order to max + 1
            int maxOrder = userLikeRepository.getMaxOrderForUser(username);
            int newOrder = maxOrder + 1;
            
            UserLike userLike = new UserLike(username, quoteId, System.currentTimeMillis(), newOrder);
            userLikeRepository.saveUserLike(userLike);
            return quote;
        } else {
            return QuoteUtil.getErrorQuote("Quote to like not found");
        }
    }

    public void unlikeQuote(String username, int quoteId) {
        userLikeRepository.deleteUserLike(username, quoteId);
    }

    public List<Quote> getLikedQuotes() {
        // Get all quotes that have at least one like
        List<Quote> allQuotes = quoteRepository.getAllQuotes();
        return allQuotes.stream()
                .filter(quote -> userLikeRepository.getLikeCount(quote.getId()) > 0)
                .sorted((q1, q2) -> q1.getId() - q2.getId())
                .toList();
    }
    
    public List<Quote> getLikedQuotesByUser(String username) {
        List<UserLike> userLikes = userLikeRepository.getLikesByUser(username);
        // userLikes are already sorted by order from repository
        return userLikes.stream()
                .map(userLike -> quoteRepository.findById(userLike.getQuoteId()))
                .filter(quote -> quote != null)
                .toList();
    }

    public int getLikeCount(int quoteId) {
        return userLikeRepository.getLikeCount(quoteId);
    }

    public boolean hasUserLikedQuote(String username, int quoteId) {
        return userLikeRepository.hasUserLikedQuote(username, quoteId);
    }

    /**
     * Get quote by ID for sequential navigation
     */
    public Quote getQuoteById(String username, int quoteId) {
        logger.info("Getting quote {} for user {}", quoteId, username);
        Quote quote = quoteRepository.findById(quoteId);
        
        if (quote != null && username != null && !username.isEmpty() && userProgressRepository != null) {
            // Update user progress to this quote ID
            userProgressRepository.updateLastQuoteId(username, quoteId);
            logger.info("Updated user {} progress to lastQuoteId={}", username, quoteId);
        }
        
        return quote;
    }

    /**
     * Get previous quote for sequential navigation
     */
    public Quote getPreviousQuote(String username, int currentQuoteId) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        
        logger.info("Getting previous quote for user {} from current ID {}", username, currentQuoteId);
        
        // Find previous available quote
        for (int id = currentQuoteId - 1; id >= 1; id--) {
            Quote quote = quoteRepository.findById(id);
            if (quote != null) {
                // Update user progress
                userProgressRepository.updateLastQuoteId(username, id);
                logger.info("Updated user {} progress to previous quote ID {}", username, id);
                return quote;
            }
        }
        
        logger.info("No previous quote available for user {}", username);
        return null;
    }

    /**
     * Get next quote for sequential navigation
     */
    public Quote getNextQuote(String username, int currentQuoteId) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        
        logger.info("Getting next quote for user {} from current ID {}", username, currentQuoteId);
        
        // Find next available quote
        int maxId = quoteRepository.getMaxQuoteId();
        for (int id = currentQuoteId + 1; id <= maxId; id++) {
            Quote quote = quoteRepository.findById(id);
            if (quote != null) {
                // Update user progress
                userProgressRepository.updateLastQuoteId(username, id);
                logger.info("Updated user {} progress to next quote ID {}", username, id);
                return quote;
            }
        }
        
        logger.info("No next quote available for user {}", username);
        return null;
    }

    /**
     * Get user's current progress (last quote ID)
     */
    public UserProgress getUserProgress(String username) {
        if (username == null || username.isEmpty() || userProgressRepository == null) {
            return null;
        }
        return userProgressRepository.getUserProgress(username);
    }

    /**
     * Get all quotes from 1 to lastQuoteId for Viewed Quotes screen
     */
    public List<Quote> getViewedQuotes(String username) {
        if (username == null || username.isEmpty() || userProgressRepository == null) {
            return List.of();
        }
        
        UserProgress userProgress = userProgressRepository.getUserProgress(username);
        if (userProgress == null) {
            return List.of();
        }
        
        int lastQuoteId = userProgress.getLastQuoteId();
        logger.info("Getting quotes 1 to {} for user {}", lastQuoteId, username);
        
        List<Quote> viewedQuotes = new ArrayList<>();
        for (int id = 1; id <= lastQuoteId; id++) {
            Quote quote = quoteRepository.findById(id);
            if (quote != null) {
                viewedQuotes.add(quote);
            }
        }
        
        logger.info("Found {} quotes for user {}", viewedQuotes.size(), username);
        return viewedQuotes;
    }

    public void recordView(String username, int quoteId) {
        if (username != null && !username.isEmpty()) {
            if (userProgressRepository != null) {
                // Update user progress instead of recording individual views
                UserProgress currentProgress = userProgressRepository.getUserProgress(username);
                if (currentProgress == null) {
                    currentProgress = new UserProgress(username, quoteId, System.currentTimeMillis());
                } else {
                    currentProgress.setLastQuoteId(quoteId);
                    currentProgress.setUpdatedAt(System.currentTimeMillis());
                }
                userProgressRepository.saveUserProgress(currentProgress);
                logger.info("Updated user progress for user {} to lastQuoteId {}", username, quoteId);
            } else {
                logger.warn("Cannot record view: UserProgressRepository is not initialized");
            }
        }
    }

    public List<Quote> getViewedQuotesByUser(String username) {
        // Return quotes 1 to lastQuoteId using the new sequential system
        if (userProgressRepository == null) {
            logger.warn("Cannot get viewed quotes: UserProgressRepository is not initialized");
            return new ArrayList<>();
        }
        
        UserProgress progress = userProgressRepository.getUserProgress(username);
        if (progress == null || progress.getLastQuoteId() <= 0) {
            return new ArrayList<>();
        }
        
        List<Quote> viewedQuotes = new ArrayList<>();
        for (int i = 1; i <= progress.getLastQuoteId(); i++) {
            Quote quote = quoteRepository.findById(i);
            if (quote != null) {
                viewedQuotes.add(quote);
            }
        }
        return viewedQuotes;
    }

    /**
     * Reorder a liked quote for a user
     * Updates all affected likes to maintain sequential ordering
     */
    public void reorderLikedQuote(String username, int quoteId, int newOrder) {
        List<UserLike> allLikes = userLikeRepository.getLikesByUser(username);
        
        // Find the like to move
        UserLike likeToMove = allLikes.stream()
                .filter(like -> like.getQuoteId() == quoteId)
                .findFirst()
                .orElse(null);
        
        if (likeToMove == null) {
            logger.warn("Quote {} not found in user {}'s likes", quoteId, username);
            return;
        }
        
        int oldOrder = likeToMove.getOrder() != null ? likeToMove.getOrder() : allLikes.indexOf(likeToMove) + 1;
        
        if (oldOrder == newOrder) {
            return; // No change needed
        }
        
        // Update orders for affected likes
        if (newOrder > oldOrder) {
            // Moving down: decrement orders between oldOrder and newOrder
            allLikes.stream()
                    .filter(like -> like.getOrder() != null && like.getOrder() > oldOrder && like.getOrder() <= newOrder)
                    .forEach(like -> {
                        like.setOrder(like.getOrder() - 1);
                        userLikeRepository.saveUserLike(like);
                    });
        } else {
            // Moving up: increment orders between newOrder and oldOrder
            allLikes.stream()
                    .filter(like -> like.getOrder() != null && like.getOrder() >= newOrder && like.getOrder() < oldOrder)
                    .forEach(like -> {
                        like.setOrder(like.getOrder() + 1);
                        userLikeRepository.saveUserLike(like);
                    });
        }
        
        // Set the moved item to new order
        likeToMove.setOrder(newOrder);
        userLikeRepository.saveUserLike(likeToMove);
        
        logger.info("Reordered quote {} for user {} from order {} to {}", quoteId, username, oldOrder, newOrder);
    }

}
