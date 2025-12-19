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

    @Nested
    class AdminEndpointTests {
        
        private static APIGatewayProxyRequestEvent createEventWithAdminRole(String path, String method) {
            String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
            String payload = base64UrlEncode("{\"sub\":\"admin-user-id\",\"email\":\"admin@example.com\",\"username\":\"adminuser\",\"cognito:groups\":[\"ADMIN\",\"USER\"]}");
            String signature = "mock-signature";
            String mockToken = "Bearer " + header + "." + payload + "." + signature;

            Map<String, String> headers = new HashMap<>();
            headers.put("authorization", mockToken);

            return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withPath(path)
                .withHeaders(headers);
        }

        @Test
        public void handleRequest_ListUsers_WithAdminRole_ShouldReturnUserList() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            List<ebulter.quote.lambda.model.UserInfo> mockUsers = new ArrayList<>();
            mockUsers.add(new ebulter.quote.lambda.model.UserInfo("user1", "user1@example.com", List.of("USER"), true, "CONFIRMED", null, null));
            mockUsers.add(new ebulter.quote.lambda.model.UserInfo("user2", "user2@example.com", List.of("USER", "ADMIN"), true, "CONFIRMED", null, null));
            when(adminServiceMock.listAllUsers()).thenReturn(mockUsers);

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithAdminRole("/api/v1/admin/users", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode(), "Response body: " + response.getBody());
            Assertions.assertTrue(response.getBody().contains("user1@example.com"));
            Assertions.assertTrue(response.getBody().contains("user2@example.com"));
        }

        @Test
        public void handleRequest_ListUsers_WithoutAdminRole_ShouldReturnForbidden() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/api/v1/admin/users", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
        }

        @Test
        public void handleRequest_AddUserToGroup_WithAdminRole_ShouldSucceed() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doNothing().when(adminServiceMock).addUserToGroup(Mockito.eq("user1"), Mockito.eq("USER"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithAdminRole("/api/v1/admin/users/user1/groups/USER", "POST");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            Mockito.verify(adminServiceMock).addUserToGroup("user1", "USER", "adminuser");
        }

        @Test
        public void handleRequest_AddUserToGroup_WithURLEncodedUsername_ShouldDecodeAndSucceed() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            // The URL-encoded username "user%40example.com" should be decoded to "user@example.com"
            Mockito.doNothing().when(adminServiceMock).addUserToGroup(Mockito.eq("user@example.com"), Mockito.eq("USER"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithAdminRole("/api/v1/admin/users/user%40example.com/groups/USER", "POST");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            Mockito.verify(adminServiceMock).addUserToGroup("user@example.com", "USER", "adminuser");
        }

        @Test
        public void handleRequest_AddUserToGroup_WithoutAdminRole_ShouldReturnForbidden() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/api/v1/admin/users/user1/groups/USER", "POST");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
            Mockito.verify(adminServiceMock, Mockito.never()).addUserToGroup(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        }

        @Test
        public void handleRequest_RemoveUserFromGroup_WithAdminRole_ShouldSucceed() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doNothing().when(adminServiceMock).removeUserFromGroup(Mockito.eq("user1"), Mockito.eq("USER"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithAdminRole("/api/v1/admin/users/user1/groups/USER", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            Mockito.verify(adminServiceMock).removeUserFromGroup("user1", "USER", "adminuser");
        }

        @Test
        public void handleRequest_RemoveUserFromGroup_WithURLEncodedUsername_ShouldDecodeAndSucceed() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            // The URL-encoded username "user%40example.com" should be decoded to "user@example.com"
            Mockito.doNothing().when(adminServiceMock).removeUserFromGroup(Mockito.eq("user@example.com"), Mockito.eq("USER"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithAdminRole("/api/v1/admin/users/user%40example.com/groups/USER", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            Mockito.verify(adminServiceMock).removeUserFromGroup("user@example.com", "USER", "adminuser");
        }

        @Test
        public void handleRequest_RemoveUserFromGroup_WithoutAdminRole_ShouldReturnForbidden() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/api/v1/admin/users/user1/groups/USER", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
            Mockito.verify(adminServiceMock, Mockito.never()).removeUserFromGroup(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        }

        @Test
        public void handleRequest_RemoveSelfFromAdminGroup_ShouldReturnError() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doThrow(new IllegalArgumentException("Cannot remove yourself from ADMIN group"))
                .when(adminServiceMock).removeUserFromGroup(Mockito.eq("adminuser"), Mockito.eq("ADMIN"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithAdminRole("/api/v1/admin/users/adminuser/groups/ADMIN", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(400, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("Cannot remove yourself from ADMIN group"));
        }

        @Test
        public void handleRequest_AdminEndpoint_WithNullAdminService_ShouldReturnError() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), null);
            APIGatewayProxyRequestEvent event = createEventWithAdminRole("/api/v1/admin/users", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(400, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("Admin service not configured"));
        }
    }

    @Nested
    class DeleteUserTests {
        @Test
        public void handleRequest_DeleteUser_WithAdminRole_ShouldSucceed() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doNothing().when(adminServiceMock).deleteUser(Mockito.eq("user1"), Mockito.eq("adminuser"));
            Mockito.doNothing().when(userLikeRepositoryMock).deleteAllLikesForUser("user1");
            Mockito.doNothing().when(userViewRepositoryMock).deleteAllViewsForUser("user1");

            QuoteService quoteService = new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock);
            QuoteHandler handler = new QuoteHandler(quoteService, adminServiceMock);
            // Manually set repositories using reflection since constructor sets them to null
            try {
                java.lang.reflect.Field userLikeField = QuoteHandler.class.getDeclaredField("userLikeRepository");
                userLikeField.setAccessible(true);
                userLikeField.set(handler, userLikeRepositoryMock);
                
                java.lang.reflect.Field userViewField = QuoteHandler.class.getDeclaredField("userViewRepository");
                userViewField.setAccessible(true);
                userViewField.set(handler, userViewRepositoryMock);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/users/user1", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            Mockito.verify(adminServiceMock).deleteUser("user1", "adminuser");
            Mockito.verify(userLikeRepositoryMock).deleteAllLikesForUser("user1");
            Mockito.verify(userViewRepositoryMock).deleteAllViewsForUser("user1");
        }

        @Test
        public void handleRequest_DeleteUser_WithoutAdminRole_ShouldReturnForbidden() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/api/v1/admin/users/user1", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
            Mockito.verify(adminServiceMock, Mockito.never()).deleteUser(Mockito.anyString(), Mockito.anyString());
        }

        @Test
        public void handleRequest_DeleteUser_WithURLEncodedUsername_ShouldDecodeAndSucceed() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            // The URL-encoded username "user%40example.com" should be decoded to "user@example.com"
            Mockito.doNothing().when(adminServiceMock).deleteUser(Mockito.eq("user@example.com"), Mockito.eq("adminuser"));
            Mockito.doNothing().when(userLikeRepositoryMock).deleteAllLikesForUser("user@example.com");
            Mockito.doNothing().when(userViewRepositoryMock).deleteAllViewsForUser("user@example.com");

            QuoteService quoteService = new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock);
            QuoteHandler handler = new QuoteHandler(quoteService, adminServiceMock);
            // Manually set repositories using reflection since constructor sets them to null
            try {
                java.lang.reflect.Field userLikeField = QuoteHandler.class.getDeclaredField("userLikeRepository");
                userLikeField.setAccessible(true);
                userLikeField.set(handler, userLikeRepositoryMock);
                
                java.lang.reflect.Field userViewField = QuoteHandler.class.getDeclaredField("userViewRepository");
                userViewField.setAccessible(true);
                userViewField.set(handler, userViewRepositoryMock);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/users/user%40example.com", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            Mockito.verify(adminServiceMock).deleteUser("user@example.com", "adminuser");
            Mockito.verify(userLikeRepositoryMock).deleteAllLikesForUser("user@example.com");
            Mockito.verify(userViewRepositoryMock).deleteAllViewsForUser("user@example.com");
        }

        @Test
        public void handleRequest_DeleteSelf_WithAdminRole_ShouldReturnError() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doThrow(new IllegalArgumentException("Cannot delete yourself"))
                .when(adminServiceMock).deleteUser(Mockito.eq("adminuser"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/users/adminuser", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(400, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("Cannot delete yourself"));
        }

        @Test
        public void handleRequest_DeleteUser_CleansUpAllUserData() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doNothing().when(adminServiceMock).deleteUser(Mockito.eq("user1"), Mockito.eq("adminuser"));
            Mockito.doNothing().when(userLikeRepositoryMock).deleteAllLikesForUser("user1");
            Mockito.doNothing().when(userViewRepositoryMock).deleteAllViewsForUser("user1");

            QuoteService quoteService = new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock);
            QuoteHandler handler = new QuoteHandler(quoteService, adminServiceMock);
            // Manually set repositories using reflection since constructor sets them to null
            try {
                java.lang.reflect.Field userLikeField = QuoteHandler.class.getDeclaredField("userLikeRepository");
                userLikeField.setAccessible(true);
                userLikeField.set(handler, userLikeRepositoryMock);
                
                java.lang.reflect.Field userViewField = QuoteHandler.class.getDeclaredField("userViewRepository");
                userViewField.setAccessible(true);
                userViewField.set(handler, userViewRepositoryMock);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/users/user1", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            // Verify both repositories were called to delete user data
            Mockito.verify(userLikeRepositoryMock, Mockito.times(1)).deleteAllLikesForUser("user1");
            Mockito.verify(userViewRepositoryMock, Mockito.times(1)).deleteAllViewsForUser("user1");
        }

        @Test
        public void handleRequest_DeleteNonExistentUser_ShouldReturnError() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doThrow(new IllegalArgumentException("User not found: nonexistent"))
                .when(adminServiceMock).deleteUser(Mockito.eq("nonexistent"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), adminServiceMock);
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/users/nonexistent", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(400, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("User not found"));
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

    @Nested
    class QuoteManagementEndpointTests {
        
        @Test
        public void handleRequest_GetQuotes_WithDefaultPagination_ShouldReturnQuotes() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(100);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userLikeRepositoryMock.getLikeCount(Mockito.anyInt())).thenReturn(0);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"totalCount\":100"));
            Assertions.assertTrue(response.getBody().contains("\"page\":1"));
            Assertions.assertTrue(response.getBody().contains("\"pageSize\":50"));
            Assertions.assertTrue(response.getBody().contains("\"totalPages\":2"));
        }

        @Test
        public void handleRequest_GetQuotes_WithCustomPageSize_ShouldReturnCorrectPage() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(100);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userLikeRepositoryMock.getLikeCount(Mockito.anyInt())).thenReturn(0);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("page", "2");
            queryParams.put("pageSize", "25");
            event.setQueryStringParameters(queryParams);
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"page\":2"));
            Assertions.assertTrue(response.getBody().contains("\"pageSize\":25"));
            Assertions.assertTrue(response.getBody().contains("\"totalPages\":4"));
        }

        @Test
        public void handleRequest_GetQuotes_WithQuoteTextSearch_ShouldReturnFilteredQuotes() {
            // Arrange
            List<Quote> quotes = new ArrayList<>();
            quotes.add(new Quote(1, "The only way to do great work", "Steve Jobs"));
            quotes.add(new Quote(2, "Innovation distinguishes", "Steve Jobs"));
            quotes.add(new Quote(3, "Stay hungry stay foolish", "Steve Jobs"));
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userLikeRepositoryMock.getLikeCount(Mockito.anyInt())).thenReturn(0);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("quoteText", "great work");
            event.setQueryStringParameters(queryParams);
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"totalCount\":1"));
            Assertions.assertTrue(response.getBody().contains("The only way to do great work"));
        }

        @Test
        public void handleRequest_GetQuotes_WithAuthorSearch_ShouldReturnFilteredQuotes() {
            // Arrange
            List<Quote> quotes = new ArrayList<>();
            quotes.add(new Quote(1, "Quote 1", "Steve Jobs"));
            quotes.add(new Quote(2, "Quote 2", "Albert Einstein"));
            quotes.add(new Quote(3, "Quote 3", "Steve Jobs"));
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userLikeRepositoryMock.getLikeCount(Mockito.anyInt())).thenReturn(0);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("author", "Einstein");
            event.setQueryStringParameters(queryParams);
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"totalCount\":1"));
            Assertions.assertTrue(response.getBody().contains("Albert Einstein"));
        }

        @Test
        public void handleRequest_GetQuotes_WithCombinedSearch_ShouldReturnFilteredQuotes() {
            // Arrange
            List<Quote> quotes = new ArrayList<>();
            quotes.add(new Quote(1, "Great innovation", "Steve Jobs"));
            quotes.add(new Quote(2, "Great work", "Albert Einstein"));
            quotes.add(new Quote(3, "Innovation distinguishes", "Steve Jobs"));
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userLikeRepositoryMock.getLikeCount(Mockito.anyInt())).thenReturn(0);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("quoteText", "innovation");
            queryParams.put("author", "Steve");
            event.setQueryStringParameters(queryParams);
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"totalCount\":2"));
        }

        @Test
        public void handleRequest_GetQuotes_WithSorting_ShouldReturnSortedQuotes() {
            // Arrange
            List<Quote> quotes = new ArrayList<>();
            quotes.add(new Quote(3, "Quote C", "Author C"));
            quotes.add(new Quote(1, "Quote A", "Author A"));
            quotes.add(new Quote(2, "Quote B", "Author B"));
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userLikeRepositoryMock.getLikeCount(Mockito.anyInt())).thenReturn(0);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("sortBy", "quoteText");
            queryParams.put("sortOrder", "asc");
            event.setQueryStringParameters(queryParams);
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            String body = response.getBody();
            int indexA = body.indexOf("Quote A");
            int indexB = body.indexOf("Quote B");
            int indexC = body.indexOf("Quote C");
            Assertions.assertTrue(indexA < indexB && indexB < indexC, "Quotes should be sorted alphabetically");
        }

        @Test
        public void handleRequest_GetQuotes_WithDescendingSorting_ShouldReturnReverseSortedQuotes() {
            // Arrange
            List<Quote> quotes = new ArrayList<>();
            quotes.add(new Quote(1, "Quote A", "Author A"));
            quotes.add(new Quote(2, "Quote B", "Author B"));
            quotes.add(new Quote(3, "Quote C", "Author C"));
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userLikeRepositoryMock.getLikeCount(Mockito.anyInt())).thenReturn(0);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("sortBy", "id");
            queryParams.put("sortOrder", "desc");
            event.setQueryStringParameters(queryParams);
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            String body = response.getBody();
            int index1 = body.indexOf("\"id\":1");
            int index2 = body.indexOf("\"id\":2");
            int index3 = body.indexOf("\"id\":3");
            Assertions.assertTrue(index3 < index2 && index2 < index1, "Quotes should be sorted in descending order");
        }

        @Test
        public void handleRequest_GetQuotes_WithLikeCounts_ShouldIncludeLikeCounts() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(3);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            when(userLikeRepositoryMock.getLikeCount(1)).thenReturn(5);
            when(userLikeRepositoryMock.getLikeCount(2)).thenReturn(3);
            when(userLikeRepositoryMock.getLikeCount(3)).thenReturn(0);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"likeCount\":5"));
            Assertions.assertTrue(response.getBody().contains("\"likeCount\":3"));
            Assertions.assertTrue(response.getBody().contains("\"likeCount\":0"));
        }

        @Test
        public void handleRequest_GetQuotes_WithoutAdminRole_ShouldReturnForbidden() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/api/v1/admin/quotes", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
        }

        @Test
        public void handleRequest_FetchQuotes_ShouldAddNewQuotes() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementServiceMock = 
                Mockito.mock(ebulter.quote.lambda.service.QuoteManagementService.class);
            
            ebulter.quote.lambda.model.QuoteAddResponse mockResponse = 
                new ebulter.quote.lambda.model.QuoteAddResponse(5, 15, "Successfully added 5 new quotes");
            when(quoteManagementServiceMock.fetchAndAddNewQuotes(Mockito.anyString())).thenReturn(mockResponse);

            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementServiceMock
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes/fetch", "POST");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"quotesAdded\":5"));
            Assertions.assertTrue(response.getBody().contains("\"totalQuotes\":15"));
            Assertions.assertTrue(response.getBody().contains("Successfully added 5 new quotes"));
        }

        @Test
        public void handleRequest_FetchQuotes_WithoutAdminRole_ShouldReturnForbidden() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementServiceMock = 
                Mockito.mock(ebulter.quote.lambda.service.QuoteManagementService.class);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementServiceMock
            );
            
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/api/v1/admin/quotes/fetch", "POST");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
        }

        @Test
        public void handleRequest_FetchQuotes_WhenNoNewQuotes_ShouldReturnZeroAdded() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementServiceMock = 
                Mockito.mock(ebulter.quote.lambda.service.QuoteManagementService.class);
            
            ebulter.quote.lambda.model.QuoteAddResponse mockResponse = 
                new ebulter.quote.lambda.model.QuoteAddResponse(0, 10, "No new quotes to add - all fetched quotes already exist");
            when(quoteManagementServiceMock.fetchAndAddNewQuotes(Mockito.anyString())).thenReturn(mockResponse);

            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock, userViewRepositoryMock), 
                adminServiceMock,
                quoteManagementServiceMock
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes/fetch", "POST");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"quotesAdded\":0"));
            Assertions.assertTrue(response.getBody().contains("\"totalQuotes\":10"));
        }
    }
}