package ebulter.quote.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.service.QuoteService;
import ebulter.quote.lambda.util.QuoteUtil;
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

    private final QuoteService quoteService;

    public QuoteHandler() {
        this.quoteService = new QuoteService(new QuoteRepository());
    }

    public QuoteHandler(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String path = event.getPath();
            String httpMethod = event.getHttpMethod();

            logger.info("path={}, httpMethod={}", path, httpMethod);

        if (path.endsWith("/quote")) {
            Set<Integer> idsToExclude;
            if ("POST".equals(httpMethod)) {
                String jsonBody = event.getBody();
                idsToExclude = gson.fromJson(jsonBody, new TypeToken<Set<Integer>>() {}.getType());
            } else {
                idsToExclude = Collections.emptySet();
            }
            Quote quote = quoteService.getQuote(idsToExclude);
            return createResponse(quote);
        } else if (path.endsWith("/like")) {
            // Check authorization for like endpoint
            if (!hasUserRole(event)) {
                return createForbiddenResponse("USER role required to like quotes");
            }
            
            // Log user information for auditing
            logUserInfo(event, "LIKE_QUOTE");
            
            // Extract ID from path like "/quote/75/like"
            String[] pathParts = path.split("/");
            int id = Integer.parseInt(pathParts[pathParts.length - 2]);
            Quote quote = quoteService.likeQuote(id);
            return createResponse(quote);
        } else if (path.endsWith("/liked")) {
            List<Quote> likedQuotes = quoteService.getLikedQuotes();
            return createResponse(likedQuotes);
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

    private static APIGatewayProxyResponseEvent createForbiddenResponse(String message) {
        APIGatewayProxyResponseEvent response = createBaseResponse();
        response.setStatusCode(HttpStatus.SC_FORBIDDEN);
        String responseBody = gson.toJson(QuoteUtil.getErrorQuote(message), quoteType);
        response.setBody(responseBody);
        return response;
    }

    private boolean hasUserRole(APIGatewayProxyRequestEvent event) {
        try {
            APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = event.getRequestContext();
            if (requestContext == null) {
                return false;
            }
            
            Map<String, Object> authorizer = requestContext.getAuthorizer();
            if (authorizer == null || !authorizer.containsKey("claims")) {
                return false;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            
            // Check for Cognito Groups (recommended approach)
            String groups = claims.get("cognito:groups");
            if (groups != null && !groups.isEmpty()) {
                return groups.contains("USER") || groups.contains("ADMIN");
            }
            
            // Fallback: Check for custom:roles attribute (for backward compatibility)
            String roles = claims.get("custom:roles");
            if (roles != null && !roles.isEmpty()) {
                return Arrays.asList(roles.split(",")).contains("USER");
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Error checking user role", e);
            return false;
        }
    }

    private void logUserInfo(APIGatewayProxyRequestEvent event, String action) {
        try {
            APIGatewayProxyRequestEvent.ProxyRequestContext requestContext = event.getRequestContext();
            if (requestContext == null) {
                return;
            }
            
            Map<String, Object> authorizer = requestContext.getAuthorizer();
            if (authorizer == null || !authorizer.containsKey("claims")) {
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            String userId = claims.get("sub");
            String email = claims.get("email");
            logger.info("User action: userId={}, email={}, action={}", userId, email, action);
        } catch (Exception e) {
            logger.warn("Could not log user info", e);
        }
    }

}
