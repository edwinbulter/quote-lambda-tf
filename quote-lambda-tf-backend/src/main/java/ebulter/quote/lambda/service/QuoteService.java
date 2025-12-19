package ebulter.quote.lambda.service;

import ebulter.quote.lambda.client.ZenClient;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.UserLike;
import ebulter.quote.lambda.model.UserView;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.repository.UserLikeRepository;
import ebulter.quote.lambda.repository.UserViewRepository;
import ebulter.quote.lambda.util.QuoteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class QuoteService {
    private static final Logger logger = LoggerFactory.getLogger(QuoteService.class);
    private final QuoteRepository quoteRepository;
    private final UserLikeRepository userLikeRepository;
    private final UserViewRepository userViewRepository;

    public QuoteService(QuoteRepository quoteRepository, UserLikeRepository userLikeRepository, UserViewRepository userViewRepository) {
        this.quoteRepository = quoteRepository;
        this.userLikeRepository = userLikeRepository;
        this.userViewRepository = userViewRepository;
    }

    public Quote getQuote(String username, final Set<Integer> idsToExclude) {
        // If username provided, add their viewed quotes to exclusion list
        if (username != null && !username.isEmpty()) {
            List<Integer> viewedIds = userViewRepository.getViewedQuoteIds(username);
            idsToExclude.addAll(viewedIds);
            logger.info("User {} has viewed {} quotes, excluding them", username, viewedIds.size());
        }
        
        logger.info("start reading all quotes from DB, idsToExclude.size() = {}", idsToExclude.size());
        List<Quote> currentDatabaseQuotes = quoteRepository.getAllQuotes();
        logger.info("finished reading all quotes from DB, read {} quotes from DB", currentDatabaseQuotes.size());
        if (currentDatabaseQuotes.size() < 5 || currentDatabaseQuotes.size() <= idsToExclude.size()) {
            try {
                logger.info("start fetching quotes from Zen");
                Set<Quote> fetchedQuotes = ZenClient.getSomeUniqueQuotes();
                logger.info("finished fetching quotes from Zen, fetched {} quotes", fetchedQuotes.size());
                //Note: In the following statement, the quotes are compared (and subsequently removed) solely by quoteText.
                fetchedQuotes.removeAll(new HashSet<>(currentDatabaseQuotes));
                AtomicInteger idGenerator = new AtomicInteger(currentDatabaseQuotes.size() + 1);
                fetchedQuotes.forEach(quote -> quote.setId(idGenerator.getAndIncrement()));
                logger.info("start saving {} quotes", fetchedQuotes.size());
                quoteRepository.saveAll(fetchedQuotes);
                logger.info("finished saving {} quotes", fetchedQuotes.size());
                currentDatabaseQuotes = quoteRepository.getAllQuotes();
                logger.info("The database now has {} quotes", currentDatabaseQuotes.size());
            } catch (IOException e) {
                logger.error("Failed to read quotes from ZenQuotes: {}", e.getMessage());
            }
        }
        Random random = new Random();
        if (!idsToExclude.isEmpty()) {
            List<Quote> filteredDatabaseQuotes = currentDatabaseQuotes.stream().filter(quote -> !idsToExclude.contains(quote.getId())).toList();
            int randomIndex = random.nextInt(filteredDatabaseQuotes.size());
            return filteredDatabaseQuotes.get(randomIndex);
        } else {
            int randomIndex = random.nextInt(currentDatabaseQuotes.size());
            return currentDatabaseQuotes.get(randomIndex);
        }
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
     * Record that a user viewed a quote
     */
    public void recordView(String username, int quoteId) {
        if (username != null && !username.isEmpty()) {
            UserView userView = new UserView(username, quoteId, System.currentTimeMillis());
            userViewRepository.saveUserView(userView);
            logger.info("Recorded view for user {} on quote {}", username, quoteId);
        }
    }

    /**
     * Get all quotes viewed by a user, in chronological order
     */
    public List<Quote> getViewedQuotesByUser(String username) {
        List<UserView> userViews = userViewRepository.getViewsByUser(username);
        return userViews.stream()
                .map(userView -> quoteRepository.findById(userView.getQuoteId()))
                .filter(quote -> quote != null)
                .toList();
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
