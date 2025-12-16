package ebulter.quote.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.repository.UserLikeRepository;
import ebulter.quote.lambda.repository.UserViewRepository;
import ebulter.quote.lambda.service.QuoteService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class QuoteHandlerTest {
    static final Gson gson = new Gson();
    static final Type quoteType = new TypeToken<Quote>() {}.getType();

    @Mock
    QuoteRepository quoteRepositoryMock;
    
    @Mock
    UserLikeRepository userLikeRepositoryMock;
    
    @Mock
    UserViewRepository userViewRepositoryMock;

    public static List<Quote> getQuoteTestData(int numberOfQuotes) {
        List<Quote> quotes = new ArrayList<>();
        for (int i=1; i<=numberOfQuotes; i++) {
            quotes.add(new Quote(i, "Quote"+i, "Author"+1));
        }
        return quotes;
    }

    public static Quote parseJsonForQuote(String json) {
        return gson.fromJson(json, quoteType);
    }

    private static APIGatewayProxyRequestEvent createEventWithUserRole(String path, String method) {
        // Create a mock JWT token with USER group
        // Format: header.payload.signature (we only need a decodable payload for testing)
        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode("{\"sub\":\"test-user-id\",\"email\":\"test@example.com\",\"username\":\"testuser\",\"cognito:groups\":[\"USER\"]}");
        String signature = "mock-signature";
        String mockToken = header + "." + payload + "." + signature;

        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", mockToken);

        return new APIGatewayProxyRequestEvent()
            .withHttpMethod(method)
            .withPath(path)
            .withHeaders(headers);
    }

    private static String base64UrlEncode(String input) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes());
    }

    @Nested
    class GetQuoteTests {
        @Test
        public void handleRequest_GetQuoteGet_ShouldReturnAQuote() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(1);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                    .withHttpMethod("GET")
                    .withPath("/quote");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Quote quote = parseJsonForQuote(response.getBody());
            Assertions.assertEquals(1, quote.getId());
            Assertions.assertEquals(200, response.getStatusCode());
        }

        @Test
        public void handleRequest_GetQuotePost_ShouldReturnTheQuoteWithTheNotExcludedId() {

            // Arrange
            List<Quote> quotes = getQuoteTestData(6);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("[1,2,3,4,5]")
                .withPath("/quote");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Quote quote = parseJsonForQuote(response.getBody());
            Assertions.assertEquals(6, quote.getId());
            Assertions.assertEquals(200, response.getStatusCode());
        }

        @Test
        public void handleRequest_GetQuoteWithAuthentication_ShouldRecordView() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(3);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userViewRepositoryMock.getViewedQuoteIds(Mockito.anyString())).thenReturn(new ArrayList<>());

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            // Verify that recordView was called (saveUserView should be invoked)
            Mockito.verify(userViewRepositoryMock, Mockito.times(1)).saveUserView(Mockito.any());
        }

        @Test
        public void handleRequest_GetQuoteWithoutAuthentication_ShouldNotRecordView() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(3);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/quote");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            // Verify that recordView was NOT called
            Mockito.verify(userViewRepositoryMock, Mockito.never()).saveUserView(Mockito.any());
        }

        @Test
        public void handleRequest_GetQuoteWithAuthentication_ShouldCallGetViewedQuoteIds() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(20);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userViewRepositoryMock.getViewedQuoteIds(Mockito.anyString())).thenReturn(new ArrayList<>());

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            // Verify that getViewedQuoteIds was called for authenticated user
            Mockito.verify(userViewRepositoryMock, Mockito.times(1)).getViewedQuoteIds(Mockito.anyString());
            // Verify that view was recorded
            Mockito.verify(userViewRepositoryMock, Mockito.times(1)).saveUserView(Mockito.any());
        }
    }

    @Nested
    class LikeQuoteTests {
        @Test
        public void handleRequest_LikeQuote_ShouldReturnTheLikedQuote() {
            // Arrange
            Quote quote = new Quote(1, "Quote 1", "Author 1");
            when(quoteRepositoryMock.findById(1)).thenReturn(quote);

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/1/like", "POST");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Quote resultQuote = parseJsonForQuote(response.getBody());
            Assertions.assertEquals(1, resultQuote.getId());
            Assertions.assertEquals("Quote 1", resultQuote.getQuoteText());
            Assertions.assertEquals("Author 1", resultQuote.getAuthor());
            Assertions.assertEquals(200, response.getStatusCode());
        }

        @Test
        public void handleRequest_LikeQuote_WithoutAuthorization_ShouldReturn403() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/quote/1/like");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
        }

        @Test
        public void handleRequest_GetLikedQuotes_Success() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/liked", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
        }

        @Test
        public void handleRequest_UnlikeQuote_WithAuthentication_ShouldReturn204() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/1/unlike", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            // Verify that deleteUserLike was called
            Mockito.verify(userLikeRepositoryMock, Mockito.times(1)).deleteUserLike(Mockito.anyString(), Mockito.eq(1));
        }

        @Test
        public void handleRequest_UnlikeQuote_WithoutAuthorization_ShouldReturn403() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("DELETE")
                .withPath("/quote/1/unlike");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
        }
    }

    @Nested
    class ViewHistoryTests {
        @Test
        public void handleRequest_GetViewHistory_WithAuthentication_ShouldReturnHistory() {
            // Arrange
            when(userViewRepositoryMock.getViewsByUser(Mockito.anyString())).thenReturn(new ArrayList<>());

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/history", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Mockito.verify(userViewRepositoryMock, Mockito.times(1)).getViewsByUser(Mockito.anyString());
        }

        @Test
        public void handleRequest_GetViewHistory_WithoutAuthentication_ShouldReturn403() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/quote/history");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
            // Verify that getViewsByUser was NOT called
            Mockito.verify(userViewRepositoryMock, Mockito.never()).getViewsByUser(Mockito.anyString());
        }
    }

    @Nested
    class ReorderQuoteTests {
        @Test
        public void handleRequest_ReorderQuote_WithValidOrder_ShouldReturn204() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/1/reorder", "PUT");
            event.setBody("{\"order\": 2}");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            // Verify that reorderLikedQuote was called
            Mockito.verify(userLikeRepositoryMock, Mockito.atLeastOnce()).getLikesByUser(Mockito.anyString());
        }

        @Test
        public void handleRequest_ReorderQuote_WithoutAuthorization_ShouldReturn403() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("PUT")
                .withPath("/quote/1/reorder")
                .withBody("{\"order\": 2}");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
        }

        @Test
        public void handleRequest_ReorderQuote_WithInvalidOrder_Zero_ShouldReturn400() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/1/reorder", "PUT");
            event.setBody("{\"order\": 0}");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(400, response.getStatusCode());
        }

        @Test
        public void handleRequest_ReorderQuote_WithInvalidOrder_Negative_ShouldReturn400() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/1/reorder", "PUT");
            event.setBody("{\"order\": -5}");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(400, response.getStatusCode());
        }

        @Test
        public void handleRequest_ReorderQuote_WithMissingOrder_ShouldReturn400() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/1/reorder", "PUT");
            event.setBody("{}");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(400, response.getStatusCode());
        }
    }

    @Test
    public void handleRequest_InvalidRequest() {
        // Arrange
        QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock));
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
            .withHttpMethod("GET")
            .withPath("/invalid");
        Context context = Mockito.mock(Context.class);

        // Act
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        // Assert
        Assertions.assertEquals(400, response.getStatusCode());
    }
}