package ebulter.quote.lambda.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ebulter.quote.lambda.mapper.QuoteMapper;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.wsmodel.WsZenQuote;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ZenClient {
    private static final String URL = "https://zenquotes.io/api/quotes";
    private static final Gson gson = new Gson();
    private static final Type zenQuoteType = new TypeToken<List<WsZenQuote>>() {}.getType();

    public static Set<Quote> getSomeUniqueQuotes() throws IOException {
        HttpGet request = new HttpGet(URL);
        try (CloseableHttpClient httpClient = HttpClients.createDefault(); CloseableHttpResponse response = httpClient.execute(request)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            List<WsZenQuote> zenQuotes = gson.fromJson(responseBody, zenQuoteType);
            return mapToUniqueQuotes(zenQuotes);
        }
    }

    public static Set<Quote> mapToUniqueQuotes(List<WsZenQuote> wsZenQuotes) {
        if (wsZenQuotes != null) {
            return wsZenQuotes.stream().map(QuoteMapper::mapToQuote).collect(Collectors.toSet());
        }
        return new HashSet<>();
    }


}
