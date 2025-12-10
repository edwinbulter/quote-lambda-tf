package ebulter.quote.lambda.service;

import ebulter.quote.lambda.client.ZenClient;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.repository.QuoteRepository;
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

    public QuoteService(QuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
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

    public Quote likeQuote(int idToLike) {
        Quote quote = quoteRepository.findById(idToLike);
        if (quote != null) {
            quote.setLikes(quote.getLikes() + 1);
            quoteRepository.updateLikes(quote);
            return quote;
        } else {
            return QuoteUtil.getErrorQuote("Quote to like not found");
        }
    }

    public List<Quote> getLikedQuotes() {
        return quoteRepository.getLikedQuotes();
    }

}
