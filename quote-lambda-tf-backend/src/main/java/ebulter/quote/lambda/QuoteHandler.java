package ebulter.quote.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.QuoteAddResponse;
import ebulter.quote.lambda.model.QuotePageResponse;
import ebulter.quote.lambda.model.UserInfo;
import ebulter.quote.lambda.model.UserProgress;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.repository.UserLikeRepository;
import ebulter.quote.lambda.service.AdminService;
import ebulter.quote.lambda.service.QuoteManagementService;
import ebulter.quote.lambda.service.QuoteService;
import ebulter.quote.lambda.util.QuoteUtil;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;

public class QuoteHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger logger = LoggerFactory.getLogger(QuoteHandler.class);

    private static final Gson gson = new Gson();
    private static final Type quoteType = new TypeToken<Quote>() {}.getType();
    private static final Type quoteListType = new TypeToken<List<Quote>>() {}.getType();
    private static final Type userInfoListType = new TypeToken<List<UserInfo>>() {}.getType();
    private static final Type quotePageResponseType = new TypeToken<QuotePageResponse>() {}.getType();
    private static final Type quoteAddResponseType = new TypeToken<QuoteAddResponse>() {}.getType();

    private final QuoteService quoteService;
    private final AdminService adminService;
    private final QuoteManagementService quoteManagementService;
    private final UserLikeRepository userLikeRepository;

    public QuoteHandler() {
        this.userLikeRepository = new UserLikeRepository();
        QuoteRepository quoteRepository = new QuoteRepository(userLikeRepository);
        this.quoteService = new QuoteService(quoteRepository, userLikeRepository);
        this.quoteManagementService = new QuoteManagementService(quoteRepository, userLikeRepository);
        String userPoolId = System.getenv("USER_POOL_ID");
        if (userPoolId == null || userPoolId.isEmpty()) {
            logger.warn("USER_POOL_ID environment variable not set. Admin features will not work.");
            this.adminService = null;
        } else {
            this.adminService = new AdminService(CognitoIdentityProviderClient.create(), userPoolId);
        }
    }

    public QuoteHandler(QuoteService quoteService) {
        this.quoteService = quoteService;
        this.adminService = null;
        this.quoteManagementService = null;
        this.userLikeRepository = null;
    }

    public QuoteHandler(QuoteService quoteService, AdminService adminService) {
        this.quoteService = quoteService;
        this.adminService = adminService;
        this.quoteManagementService = null;
        this.userLikeRepository = null;
    }

    public QuoteHandler(QuoteService quoteService, AdminService adminService, QuoteManagementService quoteManagementService) {
        this.quoteService = quoteService;
        this.adminService = adminService;
        this.quoteManagementService = quoteManagementService;
        this.userLikeRepository = null;
    }

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String path = event.getPath();
            // Normalize path by removing duplicate slashes
            while (path.contains("//")) {
                path = path.replace("//", "/");
            }
            String httpMethod = event.getHttpMethod();

            logger.info("path={}, httpMethod={}", path, httpMethod);

            // Handle CORS preflight OPTIONS requests
            if ("OPTIONS".equals(httpMethod)) {
                return createOptionsResponse();
            }

        // Sequential navigation endpoints
        if (path.matches(".*/api/v1/quote/\\d+$") && "GET".equals(httpMethod)) {
            // GET /quote/{id} - Get specific quote by ID
            String username = extractUsername(event);
            String[] pathParts = path.split("/");
            int quoteId = Integer.parseInt(pathParts[pathParts.length - 1]);
            
            Quote quote = quoteService.getQuoteById(username, quoteId);
            if (quote == null) {
                return createErrorResponse("Quote not found");
            }
            
            return createResponse(quote);
        } else if (path.matches(".*/api/v1/quote/\\d+/previous") && "GET".equals(httpMethod)) {
            // GET /quote/{id}/previous - Get previous quote
            String username = extractUsername(event);
            if (username == null || username.isEmpty()) {
                return createForbiddenResponse("Authentication required for sequential navigation");
            }
            
            String[] pathParts = path.split("/");
            int currentQuoteId = Integer.parseInt(pathParts[pathParts.length - 2]);
            
            Quote quote = quoteService.getPreviousQuote(username, currentQuoteId);
            if (quote == null) {
                return createErrorResponse("No previous quote available");
            }
            
            return createResponse(quote);
        } else if (path.matches(".*/api/v1/quote/\\d+/next") && "GET".equals(httpMethod)) {
            // GET /quote/{id}/next - Get next quote
            String username = extractUsername(event);
            if (username == null || username.isEmpty()) {
                return createForbiddenResponse("Authentication required for sequential navigation");
            }
            
            String[] pathParts = path.split("/");
            int currentQuoteId = Integer.parseInt(pathParts[pathParts.length - 2]);
            
            Quote quote = quoteService.getNextQuote(username, currentQuoteId);
            if (quote == null) {
                return createErrorResponse("No next quote available");
            }
            
            return createResponse(quote);
        } else if (path.equals("/api/v1/quote/progress") && "GET".equals(httpMethod)) {
            // GET /quote/progress - Get user's current progress
            String username = extractUsername(event);
            if (username == null || username.isEmpty()) {
                return createForbiddenResponse("Authentication required");
            }
            
            UserProgress progress = quoteService.getUserProgress(username);
            if (progress == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("lastQuoteId", 0);
                response.put("username", username);
                return createResponse(response);
            }
            
            // Convert UserProgress to Map for response
            Map<String, Object> response = new HashMap<>();
            response.put("lastQuoteId", progress.getLastQuoteId());
            response.put("username", progress.getUsername());
            response.put("updatedAt", progress.getUpdatedAt());
            return createResponse(response);
        } else if (path.equals("/api/v1/quote/viewed") && "GET".equals(httpMethod)) {
            // GET /quote/viewed - Get all viewed quotes (1 to lastQuoteId)
            String username = extractUsername(event);
            if (username == null || username.isEmpty()) {
                return createForbiddenResponse("Authentication required");
            }
            
            List<Quote> viewedQuotes = quoteService.getViewedQuotes(username);
            return createResponse(viewedQuotes);
        }

        if (path.equals("/api/v1/quote") && ("GET".equals(httpMethod) || "POST".equals(httpMethod))) {
            // Extract username (may be null for unauthenticated users)
            String username = extractUsername(event);
            
            Set<Integer> idsToExclude;
            if ("POST".equals(httpMethod)) {
                String jsonBody = event.getBody();
                idsToExclude = gson.fromJson(jsonBody, new TypeToken<Set<Integer>>() {}.getType());
            } else {
                idsToExclude = new HashSet<>();
            }
            
            // Get quote (will exclude viewed quotes if username provided)
            Quote quote = quoteService.getQuote(username, idsToExclude);
            
            // Record view if user is authenticated
            if (username != null && !username.isEmpty()) {
                quoteService.recordView(username, quote.getId());
            }
            
            return createResponse(quote);
        } else if (path.matches(".*/api/v1/quote/\\d+/like") && "POST".equals(httpMethod)) {
            // Check authorization for like endpoint
            if (!hasUserRole(event)) {
                return createForbiddenResponse("USER role required to like quotes");
            }
            
            // Extract username from token
            String username = extractUsername(event);
            if (username == null) {
                return createErrorResponse("Could not extract username from token");
            }
            
            // Log user information for auditing
            logUserInfo(event, "LIKE_QUOTE");
            
            // Extract ID from path like "/quote/75/like"
            String[] pathParts = path.split("/");
            int id = Integer.parseInt(pathParts[pathParts.length - 2]);
            Quote quote = quoteService.likeQuote(username, id);
            return createResponse(quote);
        } else if (path.matches(".*/api/v1/quote/\\d+/unlike")) {
            // Check authorization
            if (!hasUserRole(event)) {
                return createForbiddenResponse("USER role required to unlike quotes");
            }
            
            // Extract username from token
            String username = extractUsername(event);
            if (username == null) {
                return createErrorResponse("Could not extract username from token");
            }
            
            // Extract ID from path like "/quote/75/unlike"
            String[] pathParts = path.split("/");
            int id = Integer.parseInt(pathParts[pathParts.length - 2]);
            quoteService.unlikeQuote(username, id);
            
            // Return success response
            APIGatewayProxyResponseEvent response = createBaseResponse();
            response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            return response;
        } else if (path.equals("/api/v1/quote/liked")) {
            // Return quotes liked by the authenticated user
            String username = extractUsername(event);
            if (username == null || username.isEmpty()) {
                return createForbiddenResponse("Authentication required to view liked quotes");
            }
            
            List<Quote> likedQuotes = quoteService.getLikedQuotesByUser(username);
            return createResponse(likedQuotes);
        } else if (path.equals("/api/v1/quote/history") && "GET".equals(httpMethod)) {
            // Return user's view history (requires authentication)
            String username = extractUsername(event);
            if (username == null || username.isEmpty()) {
                return createForbiddenResponse("Authentication required to view history");
            }
            
            List<Quote> viewedQuotes = quoteService.getViewedQuotesByUser(username);
            return createResponse(viewedQuotes);
        } else if (path.matches(".*/api/v1/quote/\\d+/reorder") && "PUT".equals(httpMethod)) {
            // Check authorization
            if (!hasUserRole(event)) {
                return createForbiddenResponse("USER role required to reorder favourites");
            }
            
            // Extract username from token
            String username = extractUsername(event);
            if (username == null) {
                return createErrorResponse("Could not extract username from token");
            }
            
            // Extract ID from path like "/quote/75/reorder"
            String[] pathParts = path.split("/");
            int quoteId = Integer.parseInt(pathParts[pathParts.length - 2]);
            
            // Parse request body to get new order
            String jsonBody = event.getBody();
            Map<String, Object> requestBody = gson.fromJson(jsonBody, new TypeToken<Map<String, Object>>() {}.getType());
            Double orderDouble = (Double) requestBody.get("order");
            
            if (orderDouble == null || orderDouble <= 0) {
                return createErrorResponse("Order must be a positive integer");
            }
            
            int newOrder = orderDouble.intValue();
            quoteService.reorderLikedQuote(username, quoteId, newOrder);
            
            // Return success response
            APIGatewayProxyResponseEvent response = createBaseResponse();
            response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            return response;
        } else if (path.startsWith("/api/v1/admin/users") || path.startsWith("/api/v1/admin/quotes") || path.startsWith("/api/v1/admin/likes")) {
            // Admin endpoints - require ADMIN role
            if (!hasAdminRole(event)) {
                return createForbiddenResponse("ADMIN role required");
            }
            
            String username = extractUsername(event);
            if (username == null) {
                return createErrorResponse("Could not extract username from token");
            }
            
            if (path.startsWith("/api/v1/admin/users") && adminService == null) {
                return createErrorResponse("Admin service not configured");
            }
            
            if (path.startsWith("/api/v1/admin/quotes") && quoteManagementService == null) {
                return createErrorResponse("Quote management service not configured");
            }
            
            return handleAdminRequest(event, username, path);
        } else {
            return createErrorResponse("Invalid request");
        }
        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    private static APIGatewayProxyResponseEvent createResponse(List<Quote> quoteList) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_OK);
        String responseBody = gson.toJson(quoteList, quoteListType);
        response.setBody(responseBody);
        return response;
    }

    private static APIGatewayProxyResponseEvent createResponse(Quote quote) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_OK);
        String responseBody = gson.toJson(quote, quoteType);
        response.setBody(responseBody);
        return response;
    }

    private static APIGatewayProxyResponseEvent createResponse(Map<String, Object> data) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_OK);
        String responseBody = gson.toJson(data);
        response.setBody(responseBody);
        return response;
    }

    private static APIGatewayProxyResponseEvent createErrorResponse(String message) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        String responseBody = gson.toJson(QuoteUtil.getErrorQuote(message), quoteType);
        response.setBody(responseBody);
        return response;
    }

    private static APIGatewayProxyResponseEvent createBaseResponse() {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");  // enable CORS
        response.setHeaders(headers);
        return response;
    }

    private static APIGatewayProxyResponseEvent createOptionsResponse() {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(HttpStatus.SC_OK);
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.put("Access-Control-Max-Age", "300");
        response.setHeaders(headers);
        response.setBody("");
        return response;
    }

    private static APIGatewayProxyResponseEvent createForbiddenResponse(String message) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_FORBIDDEN);
        String responseBody = gson.toJson(QuoteUtil.getErrorQuote(message), quoteType);
        response.setBody(responseBody);
        return response;
    }

    private boolean hasUserRole(APIGatewayProxyRequestEvent event) {
        try {
            // Get Authorization header (case-insensitive)
            Map<String, String> headers = event.getHeaders();
            if (headers == null) {
                logger.warn("Authorization failed: headers are null");
                return false;
            }
            
            // API Gateway may lowercase header names
            String authHeader = headers.get("authorization");
            if (authHeader == null) {
                authHeader = headers.get("Authorization");
            }
            
            if (authHeader == null || authHeader.isEmpty()) {
                logger.warn("Authorization failed: no Authorization header. Available headers: " + headers.keySet());
                return false;
            }
            
            // Remove "Bearer " prefix if present
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            
            // Decode JWT (without verification - Cognito already verified it)
            DecodedJWT jwt = JWT.decode(token);
            
            // Check for Cognito Groups
            List<String> groups = jwt.getClaim("cognito:groups").asList(String.class);
            logger.info("cognito:groups claim: " + groups);
            if (groups != null && !groups.isEmpty()) {
                boolean hasAccess = groups.contains("USER") || groups.contains("ADMIN");
                logger.info("Group-based authorization: " + hasAccess);
                return hasAccess;
            }
            
            // Fallback: Check for custom:roles attribute
            String roles = jwt.getClaim("custom:roles").asString();
            logger.info("custom:roles claim: " + roles);
            if (roles != null && !roles.isEmpty()) {
                boolean hasAccess = Arrays.asList(roles.split(",")).contains("USER");
                logger.info("Role-based authorization: " + hasAccess);
                return hasAccess;
            }
            
            logger.warn("Authorization failed: no cognito:groups or custom:roles claim found");
            return false;
        } catch (Exception e) {
            logger.error("Error checking user role", e);
            return false;
        }
    }

    private String extractUsername(APIGatewayProxyRequestEvent event) {
        try {
            Map<String, String> headers = event.getHeaders();
            if (headers == null) {
                return null;
            }
            
            String authHeader = headers.get("authorization");
            if (authHeader == null) {
                authHeader = headers.get("Authorization");
            }
            
            if (authHeader == null || authHeader.isEmpty()) {
                return null;
            }
            
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            DecodedJWT jwt = JWT.decode(token);
            
            // Extract username from Cognito token (access token uses "username", ID token uses "cognito:username")
            String username = jwt.getClaim("username").asString();
            if (username == null || username.isEmpty()) {
                username = jwt.getClaim("cognito:username").asString();
            }
            return username;
        } catch (Exception e) {
            logger.error("Error extracting username", e);
            return null;
        }
    }

    private void logUserInfo(APIGatewayProxyRequestEvent event, String action) {
        try {
            Map<String, String> headers = event.getHeaders();
            if (headers == null) {
                return;
            }
            
            // API Gateway may lowercase header names
            String authHeader = headers.get("authorization");
            if (authHeader == null) {
                authHeader = headers.get("Authorization");
            }
            
            if (authHeader == null || authHeader.isEmpty()) {
                return;
            }
            
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            DecodedJWT jwt = JWT.decode(token);
            
            String userId = jwt.getClaim("sub").asString();
            String email = jwt.getClaim("email").asString();
            logger.info("User action: userId=" + userId + ", email=" + email + ", action=" + action);
        } catch (Exception e) {
            logger.warn("Could not log user info: " + e.getMessage());
        }
    }

    private boolean hasAdminRole(APIGatewayProxyRequestEvent event) {
        try {
            Map<String, String> headers = event.getHeaders();
            if (headers == null) {
                logger.warn("Authorization failed: headers are null");
                return false;
            }
            
            String authHeader = headers.get("authorization");
            if (authHeader == null) {
                authHeader = headers.get("Authorization");
            }
            
            if (authHeader == null || authHeader.isEmpty()) {
                logger.warn("Authorization failed: no Authorization header");
                return false;
            }
            
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            DecodedJWT jwt = JWT.decode(token);
            
            List<String> groups = jwt.getClaim("cognito:groups").asList(String.class);
            logger.info("Checking ADMIN role. cognito:groups: " + groups);
            
            if (groups != null && groups.contains("ADMIN")) {
                logger.info("ADMIN authorization: granted");
                return true;
            }
            
            logger.warn("Authorization failed: ADMIN group not found");
            return false;
        } catch (Exception e) {
            logger.error("Error checking admin role", e);
            return false;
        }
    }

    private APIGatewayProxyResponseEvent handleAdminRequest(APIGatewayProxyRequestEvent event, String requestingUsername, String normalizedPath) {
        String path = normalizedPath;
        String httpMethod = event.getHttpMethod();
        
        logger.info("Handling admin request: path={}, method={}, user={}", path, httpMethod, requestingUsername);
        
        try {
            // GET /admin/users - List all users
            if (path.equals("/api/v1/admin/users") && "GET".equals(httpMethod)) {
                List<UserInfo> users = adminService.listAllUsers();
                return createUserInfoListResponse(users);
            }
            
            // POST /admin/users/{username}/groups/{groupName} - Add user to group
            if (path.matches("/api/v1/admin/users/[^/]+/groups/[^/]+") && "POST".equals(httpMethod)) {
                String[] pathParts = path.split("/");
                String targetUsername = java.net.URLDecoder.decode(pathParts[pathParts.length - 3], "UTF-8");
                String groupName = pathParts[pathParts.length - 1];
                
                adminService.addUserToGroup(targetUsername, groupName, requestingUsername);
                
                APIGatewayProxyResponseEvent response = createBaseResponse();
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
                return response;
            }
            
            // DELETE /admin/users/{username}/groups/{groupName} - Remove user from group
            if (path.matches("/api/v1/admin/users/[^/]+/groups/[^/]+") && "DELETE".equals(httpMethod)) {
                String[] pathParts = path.split("/");
                String targetUsername = java.net.URLDecoder.decode(pathParts[pathParts.length - 3], "UTF-8");
                String groupName = pathParts[pathParts.length - 1];
                
                adminService.removeUserFromGroup(targetUsername, groupName, requestingUsername);
                
                APIGatewayProxyResponseEvent response = createBaseResponse();
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
                return response;
            }
            
            // DELETE /admin/users/{username} - Delete user
            if (path.matches("/api/v1/admin/users/[^/]+$") && "DELETE".equals(httpMethod)) {
                String[] pathParts = path.split("/");
                String targetUsername = java.net.URLDecoder.decode(pathParts[pathParts.length - 1], "UTF-8");
                
                logger.info("Delete user request: targetUsername={}, requestingUsername={}", targetUsername, requestingUsername);
                
                // Delete user from Cognito
                adminService.deleteUser(targetUsername, requestingUsername);
                
                // Delete all user data from DynamoDB
                if (userLikeRepository != null) {
                    logger.info("Deleting all data for user {} from DynamoDB", targetUsername);
                    userLikeRepository.deleteAllLikesForUser(targetUsername);
                    logger.info("Successfully deleted all data for user {}", targetUsername);
                }
                
                APIGatewayProxyResponseEvent response = createBaseResponse();
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
                return response;
            }
            
            // GET /admin/quotes - List all quotes with pagination and search
            if (path.equals("/api/v1/admin/quotes") && "GET".equals(httpMethod)) {
                Map<String, String> queryParams = event.getQueryStringParameters();
                
                int page = 1;
                int pageSize = 50;
                String quoteText = null;
                String author = null;
                String sortBy = "id";
                String sortOrder = "asc";
                
                if (queryParams != null) {
                    if (queryParams.containsKey("page")) {
                        try {
                            page = Integer.parseInt(queryParams.get("page"));
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid page parameter: {}", queryParams.get("page"));
                        }
                    }
                    if (queryParams.containsKey("pageSize")) {
                        try {
                            pageSize = Integer.parseInt(queryParams.get("pageSize"));
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid pageSize parameter: {}", queryParams.get("pageSize"));
                        }
                    }
                    quoteText = queryParams.get("quoteText");
                    author = queryParams.get("author");
                    sortBy = queryParams.getOrDefault("sortBy", "id");
                    sortOrder = queryParams.getOrDefault("sortOrder", "asc");
                }
                
                QuotePageResponse quotePageResponse = quoteManagementService.getQuotesWithPagination(
                    page, pageSize, quoteText, author, sortBy, sortOrder
                );
                return createQuotePageResponse(quotePageResponse);
            }
            
            // POST /admin/quotes/fetch - Fetch and add new quotes from ZEN API
            if (path.equals("/api/v1/admin/quotes/fetch") && "POST".equals(httpMethod)) {
                QuoteAddResponse quoteAddResponse = quoteManagementService.fetchAndAddNewQuotes(requestingUsername);
                return createQuoteAddResponse(quoteAddResponse);
            }
            
            // GET /admin/likes/total - Get total number of likes across all quotes
            if (path.equals("/api/v1/admin/likes/total") && "GET".equals(httpMethod)) {
                // Use userLikeRepository to directly count all likes (much more efficient)
                int totalLikes = userLikeRepository.getTotalLikesCount();
                
                Map<String, Object> response = new HashMap<>();
                response.put("totalLikes", totalLikes);
                return createResponse(response);
            }
            
            return createErrorResponse("Invalid admin request");
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request in admin endpoint: {}", e.getMessage());
            return createErrorResponse(e.getMessage());
        } catch (Exception e) {
            logger.error("Error handling admin request", e);
            return createErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    private static APIGatewayProxyResponseEvent createUserInfoListResponse(List<UserInfo> userInfoList) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_OK);
        String responseBody = gson.toJson(userInfoList, userInfoListType);
        response.setBody(responseBody);
        return response;
    }

    private static APIGatewayProxyResponseEvent createQuotePageResponse(QuotePageResponse quotePageResponse) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_OK);
        String responseBody = gson.toJson(quotePageResponse, quotePageResponseType);
        response.setBody(responseBody);
        return response;
    }

    private static APIGatewayProxyResponseEvent createQuoteAddResponse(QuoteAddResponse quoteAddResponse) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_OK);
        String responseBody = gson.toJson(quoteAddResponse, quoteAddResponseType);
        response.setBody(responseBody);
        return response;
    }

}
