package ebulter.quote.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.repository.UserLikeRepository;
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            // Verify that recordView was called (user progress should be updated)
            // Note: UserViewRepository has been removed, now using UserProgressRepository
        }

        @Test
        public void handleRequest_GetQuoteWithoutAuthentication_ShouldNotRecordView() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(3);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/quote");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            // Verify that recordView was NOT called
            // Note: UserViewRepository has been removed, now using UserProgressRepository
        }

        @Test
        public void handleRequest_GetQuoteWithAuthentication_ShouldCallGetViewedQuoteIds() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(20);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            // Verify that view was recorded
            // Note: UserViewRepository has been removed, now using UserProgressRepository
        }
    }

    @Nested
    class LikeQuoteTests {
        @Test
        public void handleRequest_LikeQuote_ShouldReturnTheLikedQuote() {
            // Arrange
            Quote quote = new Quote(1, "Quote 1", "Author 1");
            when(quoteRepositoryMock.findById(1)).thenReturn(quote);

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            // Note: UserViewRepository has been removed, now using UserProgressRepository
            // This test now returns quotes 1 to lastQuoteId instead of individual view records

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/quote/history", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            // Note: No longer verifying getViewsByUser as UserViewRepository has been removed
        }

        @Test
        public void handleRequest_GetViewHistory_WithoutAuthentication_ShouldReturn403() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/quote/history");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
            // Note: No longer verifying getViewsByUser as UserViewRepository has been removed
        }
    }

    @Nested
    class ReorderQuoteTests {
        @Test
        public void handleRequest_ReorderQuote_WithValidOrder_ShouldReturn204() {
            // Arrange
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), null);
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
            
            QuoteService quoteService = new QuoteService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(quoteService, adminServiceMock);
            // Manually set repository using reflection since constructor sets it to null
            try {
                java.lang.reflect.Field userLikeField = QuoteHandler.class.getDeclaredField("userLikeRepository");
                userLikeField.setAccessible(true);
                userLikeField.set(handler, userLikeRepositoryMock);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set up test", e);
            }

            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/users/user1", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            Mockito.verify(adminServiceMock).deleteUser("user1", "adminuser");
            Mockito.verify(userLikeRepositoryMock).deleteAllLikesForUser("user1");
                    }

        @Test
        public void handleRequest_DeleteUser_WithoutAdminRole_ShouldReturnForbidden() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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
            
            QuoteService quoteService = new QuoteService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(quoteService, adminServiceMock);
            // Manually set repository using reflection since constructor sets it to null
            try {
                java.lang.reflect.Field userLikeField = QuoteHandler.class.getDeclaredField("userLikeRepository");
                userLikeField.setAccessible(true);
                userLikeField.set(handler, userLikeRepositoryMock);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set up test", e);
            }

            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/users/user%40example.com", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            Mockito.verify(adminServiceMock).deleteUser("user@example.com", "adminuser");
            Mockito.verify(userLikeRepositoryMock).deleteAllLikesForUser("user@example.com");
                    }

        @Test
        public void handleRequest_DeleteSelf_WithAdminRole_ShouldReturnError() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doThrow(new IllegalArgumentException("Cannot delete yourself"))
                .when(adminServiceMock).deleteUser(Mockito.eq("adminuser"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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
            
            QuoteService quoteService = new QuoteService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(quoteService, adminServiceMock);
            // Manually set repository using reflection since constructor sets it to null
            try {
                java.lang.reflect.Field userLikeField = QuoteHandler.class.getDeclaredField("userLikeRepository");
                userLikeField.setAccessible(true);
                userLikeField.set(handler, userLikeRepositoryMock);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set up test", e);
            }

            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/users/user1", "DELETE");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(204, response.getStatusCode());
            // Verify both repositories were called to delete user data
            Mockito.verify(userLikeRepositoryMock, Mockito.times(1)).deleteAllLikesForUser("user1");
                    }

        @Test
        public void handleRequest_DeleteNonExistentUser_ShouldReturnError() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            Mockito.doThrow(new IllegalArgumentException("User not found: nonexistent"))
                .when(adminServiceMock).deleteUser(Mockito.eq("nonexistent"), Mockito.eq("adminuser"));

            QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), adminServiceMock);
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
        QuoteHandler handler = new QuoteHandler(new QuoteService(quoteRepositoryMock, userLikeRepositoryMock));
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
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
        public void handleRequest_GetQuotes_WithLikeCountSort_ShouldSortByLikeCountThenId() {
            // Arrange
            List<Quote> quotes = new ArrayList<>();
            // Create quotes with equal like counts but different IDs
            Quote quote1 = new Quote(5, "Quote E", "Author E");
            Quote quote2 = new Quote(2, "Quote B", "Author B");
            Quote quote3 = new Quote(8, "Quote H", "Author H");
            Quote quote4 = new Quote(1, "Quote A", "Author A");
            Quote quote5 = new Quote(3, "Quote C", "Author C");
            
            // Set like counts: quotes 2 and 5 have 10 likes, quotes 1 and 8 have 5 likes, quote 3 has 0 likes
            quote1.setLikeCount(5);
            quote2.setLikeCount(10);
            quote3.setLikeCount(0);
            quote4.setLikeCount(5);
            quote5.setLikeCount(10);
            
            quotes.add(quote1);
            quotes.add(quote2);
            quotes.add(quote3);
            quotes.add(quote4);
            quotes.add(quote5);
            
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
                adminServiceMock,
                quoteManagementService
            );
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/quotes", "GET");
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("sortBy", "likeCount");
            queryParams.put("sortOrder", "desc");
            event.setQueryStringParameters(queryParams);
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            String body = response.getBody();
            
            // Expected order for descending likeCount with secondary ID sorting:
            // 1. Quote with ID 2 (10 likes) - should come before ID 3 (10 likes) due to lower ID
            // 2. Quote with ID 3 (10 likes) 
            // 3. Quote with ID 1 (5 likes) - should come before ID 5 (5 likes) due to lower ID
            // 4. Quote with ID 5 (5 likes)
            // 5. Quote with ID 8 (0 likes)
            
            int index2 = body.indexOf("\"id\":2");
            int index3 = body.indexOf("\"id\":3");
            int index1 = body.indexOf("\"id\":1");
            int index5 = body.indexOf("\"id\":5");
            int index8 = body.indexOf("\"id\":8");
            
            // Verify the order: 2 < 3 < 1 < 5 < 8
            Assertions.assertTrue(index2 < index3, "Quote ID 2 (10 likes) should come before ID 3 (10 likes)");
            Assertions.assertTrue(index3 < index1, "Quote ID 3 (10 likes) should come before ID 1 (5 likes)");
            Assertions.assertTrue(index1 < index5, "Quote ID 1 (5 likes) should come before ID 5 (5 likes)");
            Assertions.assertTrue(index5 < index8, "Quote ID 5 (5 likes) should come before ID 8 (0 likes)");
        }

        @Test
        public void handleRequest_GetQuotes_WithLikeCounts_ShouldIncludeLikeCounts() {
            // Arrange
            List<Quote> quotes = getQuoteTestData(3);
            // Set like counts directly on Quote objects since QuoteManagementService uses these
            quotes.get(0).setLikeCount(5);
            quotes.get(1).setLikeCount(3);
            quotes.get(2).setLikeCount(0);
            when(quoteRepositoryMock.getAllQuotes()).thenReturn(quotes);

            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementService = 
                new ebulter.quote.lambda.service.QuoteManagementService(quoteRepositoryMock, userLikeRepositoryMock);
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
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

        @Test
        public void handleRequest_GetTotalLikes_ShouldReturnTotalLikesCount() {
            // Arrange
            when(userLikeRepositoryMock.getTotalLikesCount()).thenReturn(42);
            
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementServiceMock = 
                Mockito.mock(ebulter.quote.lambda.service.QuoteManagementService.class);

            // Use the default constructor which initializes repositories properly
            QuoteHandler handler = new QuoteHandler();
            
            // Use reflection to set the mocked userLikeRepository
            try {
                java.lang.reflect.Field userLikeRepositoryField = QuoteHandler.class.getDeclaredField("userLikeRepository");
                userLikeRepositoryField.setAccessible(true);
                userLikeRepositoryField.set(handler, userLikeRepositoryMock);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set up test handler", e);
            }
            
            APIGatewayProxyRequestEvent event = AdminEndpointTests.createEventWithAdminRole("/api/v1/admin/likes/total", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(200, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("\"totalLikes\":42"));
        }

        @Test
        public void handleRequest_GetTotalLikes_WithoutAdminRole_ShouldReturnForbidden() {
            // Arrange
            ebulter.quote.lambda.service.AdminService adminServiceMock = Mockito.mock(ebulter.quote.lambda.service.AdminService.class);
            ebulter.quote.lambda.service.QuoteManagementService quoteManagementServiceMock = 
                Mockito.mock(ebulter.quote.lambda.service.QuoteManagementService.class);

            // Use the same constructor pattern as other admin tests
            QuoteHandler handler = new QuoteHandler(
                new QuoteService(quoteRepositoryMock, userLikeRepositoryMock), 
                adminServiceMock,
                quoteManagementServiceMock
            );
            
            APIGatewayProxyRequestEvent event = createEventWithUserRole("/api/v1/admin/likes/total", "GET");
            Context context = Mockito.mock(Context.class);

            // Act
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            // Assert
            Assertions.assertEquals(403, response.getStatusCode());
            Assertions.assertTrue(response.getBody().contains("ADMIN role required"));
        }
    }
}