package ebulter.quote.lambda.service;

import ebulter.quote.lambda.client.ZenClient;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.UserLike;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.repository.UserLikeRepository;
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

    public QuoteService(QuoteRepository quoteRepository, UserLikeRepository userLikeRepository) {
        this.quoteRepository = quoteRepository;
        this.userLikeRepository = userLikeRepository;
    }

    public Quote getQuote(final Set<Integer> idsToExclude) {
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
            UserLike userLike = new UserLike(username, quoteId, System.currentTimeMillis());
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
                .filter(quote -> userLikeRepository.getLikeCountForQuote(quote.getId()) > 0)
                .sorted((q1, q2) -> q1.getId() - q2.getId())
                .toList();
    }
    
    public List<Quote> getLikedQuotesByUser(String username) {
        List<UserLike> userLikes = userLikeRepository.getLikesByUser(username);
        return userLikes.stream()
                .map(userLike -> quoteRepository.findById(userLike.getQuoteId()))
                .filter(quote -> quote != null)
                .sorted((q1, q2) -> q1.getId() - q2.getId())
                .toList();
    }

    public int getLikeCount(int quoteId) {
        return userLikeRepository.getLikeCountForQuote(quoteId);
    }

    public boolean hasUserLikedQuote(String username, int quoteId) {
        return userLikeRepository.hasUserLikedQuote(username, quoteId);
    }

}
