package ebulter.quote.lambda.util;

/**
 * Mock TimeProvider for unit tests
 * Allows controlling time and avoiding actual delays in tests
 */
public class MockTimeProvider implements TimeProvider {
    private long currentTime = 0;
    private long totalSleptTime = 0;

    public MockTimeProvider() {
        this(0);
    }

    public MockTimeProvider(long initialTime) {
        this.currentTime = initialTime;
    }

    @Override
    public long currentTimeMillis() {
        return currentTime;
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
        totalSleptTime += millis;
        // Advance time by the sleep duration
        currentTime += millis;
    }

    /**
     * Advance time by the specified amount
     * @param millis time to advance in milliseconds
     */
    public void advanceTime(long millis) {
        currentTime += millis;
    }

    /**
     * Set the current time
     * @param time time to set in milliseconds
     */
    public void setCurrentTime(long time) {
        this.currentTime = time;
    }

    /**
     * Get the total time that has been "slept"
     * @return total slept time in milliseconds
     */
    public long getTotalSleptTime() {
        return totalSleptTime;
    }

    /**
     * Reset the mock state
     */
    public void reset() {
        currentTime = 0;
        totalSleptTime = 0;
    }
}
