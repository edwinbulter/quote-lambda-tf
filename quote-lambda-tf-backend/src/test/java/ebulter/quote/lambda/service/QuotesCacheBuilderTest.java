package ebulter.quote.lambda.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ebulter.quote.lambda.model.Quote;
import ebulter.quote.lambda.model.QuotesCacheData;
import ebulter.quote.lambda.repository.QuoteRepository;
import ebulter.quote.lambda.util.MockTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuotesCacheBuilderTest {

    @Mock
    private QuoteRepository quoteRepository;
    
    @Mock
    private S3Client s3Client;
    
    private QuotesCacheBuilder cacheBuilder;
    private ObjectMapper objectMapper;
    private MockTimeProvider mockTimeProvider;
    
    private static final String BUCKET_NAME = "test-bucket";
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        mockTimeProvider = new MockTimeProvider();
        cacheBuilder = new QuotesCacheBuilder(BUCKET_NAME, mockTimeProvider);
        cacheBuilder.setQuoteRepository(quoteRepository);
        cacheBuilder.setS3Client(s3Client);
        cacheBuilder.setObjectMapper(objectMapper);
    }
    
    @Test
    public void testConcurrentCacheUpdate() throws Exception {
        // Mock S3 to simulate existing cache
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(HeadObjectResponse.builder()
                .eTag("old-etag")
                .metadata(Map.of("version", "1"))
                .build());
        
        // Mock quotes repository
        List<Quote> quotes = List.of(
            new Quote(1, "Test quote 1", "Author 1", 10),
            new Quote(2, "Test quote 2", "Author 2", 5)
        );
        when(quoteRepository.getAllQuotes()).thenReturn(quotes);
        
        // First call should succeed
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder()
                .eTag("new-etag")
                .build());
        
        // Should complete without exception
        assertDoesNotThrow(() -> cacheBuilder.buildAndUploadCache());
        
        // Verify upload was called
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    public void testConditionalCheckFailure() throws Exception {
        // Mock S3 to simulate existing cache
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(HeadObjectResponse.builder()
                .eTag("old-etag")
                .metadata(Map.of("version", "1"))
                .build())
            .thenReturn(HeadObjectResponse.builder()
                .eTag("old-etag")
                .metadata(Map.of("version", "1"))
                .build());
        
        // Mock quotes repository
        List<Quote> quotes = List.of(
            new Quote(1, "Test quote 1", "Author 1", 10),
            new Quote(2, "Test quote 2", "Author 2", 5)
        );
        when(quoteRepository.getAllQuotes()).thenReturn(quotes);
        
        // First call fails with conditional check, second succeeds
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder()
                .statusCode(412)
                .message("Precondition Failed")
                .build())
            .thenReturn(PutObjectResponse.builder()
                .eTag("new-etag")
                .build());
        
        // Should retry and succeed
        assertDoesNotThrow(() -> cacheBuilder.buildAndUploadCache());
        
        // Verify upload was called twice (initial + retry)
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    public void testMaxRetriesExceeded() throws Exception {
        // Mock S3 to simulate existing cache
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(HeadObjectResponse.builder()
                .eTag("old-etag")
                .metadata(Map.of("version", "1"))
                .build());
        
        // Mock quotes repository
        List<Quote> quotes = List.of(
            new Quote(1, "Test quote 1", "Author 1", 10)
        );
        when(quoteRepository.getAllQuotes()).thenReturn(quotes);
        
        // All calls fail with conditional check
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder()
                .statusCode(412)
                .message("Precondition Failed")
                .build());
        
        // Should throw exception after max retries (Phase 2: increased to 5)
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> cacheBuilder.buildAndUploadCache());
        
        assertTrue(exception.getMessage().contains("concurrent modifications") || 
                  exception.getMessage().contains("Cache update failed"));
        
        // Verify upload was called 5 times (Phase 2: max retries increased to 5)
        verify(s3Client, times(5)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    public void testInitialCacheCreation() throws Exception {
        // Mock S3 to simulate no existing cache
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().build());
        
        // Mock quotes repository
        List<Quote> quotes = List.of(
            new Quote(1, "Test quote 1", "Author 1", 10)
        );
        when(quoteRepository.getAllQuotes()).thenReturn(quotes);
        
        // Upload should succeed
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder()
                .eTag("new-etag")
                .build());
        
        // Should complete without exception
        assertDoesNotThrow(() -> cacheBuilder.buildAndUploadCache());
        
        // Verify upload was called
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    public void testVersionIncrement() throws Exception {
        // Mock S3 to simulate existing cache with version 5
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenReturn(HeadObjectResponse.builder()
                .eTag("old-etag")
                .metadata(Map.of("version", "5"))
                .build());
        
        // Mock quotes repository
        List<Quote> quotes = List.of(new Quote(1, "Test quote", "Author", 1));
        when(quoteRepository.getAllQuotes()).thenReturn(quotes);
        
        // Capture the PutObjectRequest to verify version
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenAnswer(invocation -> {
                PutObjectRequest request = invocation.getArgument(0);
                // Verify version is incremented to 6
                assertEquals("6", request.metadata().get("version"));
                return PutObjectResponse.builder().eTag("new-etag").build();
            });
        
        cacheBuilder.buildAndUploadCache();
        
        // Verify headObject was called to get current version
        verify(s3Client, times(1)).headObject(any(HeadObjectRequest.class));
    }
}
