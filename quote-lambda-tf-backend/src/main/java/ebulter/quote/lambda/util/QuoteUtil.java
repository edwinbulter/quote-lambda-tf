package ebulter.quote.lambda.util;

import ebulter.quote.lambda.model.Quote;

public class QuoteUtil {

    public static Quote getErrorQuote(String errorMessage) {
        Quote errorQuote = new Quote();
        errorQuote.setQuoteText(errorMessage);
        return errorQuote;
    }

}
