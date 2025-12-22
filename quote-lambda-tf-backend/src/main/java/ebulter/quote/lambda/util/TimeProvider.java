package ebulter.quote.lambda.util;

/**
 * Time provider interface for testability
 * Allows mocking time in unit tests to avoid actual delays
 */
public interface TimeProvider {
    /**
     * Returns the current time in milliseconds
     */
    long currentTimeMillis();
    
    /**
     * Sleep for the specified milliseconds
     * @param millis time to sleep in milliseconds
     * @throws InterruptedException if sleep is interrupted
     */
    void sleep(long millis) throws InterruptedException;
}
