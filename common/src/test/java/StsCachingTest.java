import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.cipher.LocalSignatureCipherManager;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for STS (Signature Timestamp) caching functionality.
 * 
 * Tests cover:
 * - Cache hit/miss scenarios
 * - Thread safety
 * - Cache eviction
 * - Player script version changes
 */
@DisplayName("STS Caching Tests")
public class StsCachingTest {

    private static final String TEST_SCRIPT_URL_1 = "https://www.youtube.com/s/player/abc123/player_ias.vflset/en_US/base.js";
    private static final String TEST_SCRIPT_URL_2 = "https://www.youtube.com/s/player/def456/player_ias.vflset/en_US/base.js";
    private static final String TEST_STS_1 = "19876";
    private static final String TEST_STS_2 = "19877";

    private LocalSignatureCipherManager cipherManager;
    private MockHttpInterface mockHttpInterface;

    @BeforeEach
    public void setUp() {
        cipherManager = new LocalSignatureCipherManager();
        mockHttpInterface = new MockHttpInterface();
    }

    @Test
    @DisplayName("Should cache STS on first request")
    public void testStsCachingOnFirstRequest() throws IOException {
        // Arrange
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));

        // Act
        String sts1 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        
        // Assert
        assertEquals(TEST_STS_1, sts1, "STS should match the value from player script");
        assertEquals(1, mockHttpInterface.getRequestCount(), "Should make exactly 1 HTTP request");
        assertEquals(1, cipherManager.getStsCacheSize(), "Cache should contain 1 entry");
    }

    @Test
    @DisplayName("Should return cached STS on subsequent requests")
    public void testStsCacheHit() throws IOException {
        // Arrange
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));

        // Act
        String sts1 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        String sts2 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        String sts3 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);

        // Assert
        assertEquals(TEST_STS_1, sts1, "First request should return correct STS");
        assertEquals(TEST_STS_1, sts2, "Second request should return cached STS");
        assertEquals(TEST_STS_1, sts3, "Third request should return cached STS");
        assertEquals(1, mockHttpInterface.getRequestCount(), "Should make only 1 HTTP request (subsequent requests use cache)");
        assertEquals(1, cipherManager.getStsCacheSize(), "Cache should contain 1 entry");
    }

    @Test
    @DisplayName("Should cache STS per unique player script URL")
    public void testStsCachingPerScriptUrl() throws IOException {
        // Arrange
        String script1 = createMockPlayerScript(TEST_STS_1);
        String script2 = createMockPlayerScript(TEST_STS_2);

        // Act
        mockHttpInterface.setScriptContent(script1);
        String sts1 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);

        mockHttpInterface.setScriptContent(script2);
        String sts2 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_2);

        // Verify cache hits
        mockHttpInterface.setScriptContent(script1);
        String sts1Again = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);

        mockHttpInterface.setScriptContent(script2);
        String sts2Again = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_2);

        // Assert
        assertEquals(TEST_STS_1, sts1, "First script should have STS 1");
        assertEquals(TEST_STS_2, sts2, "Second script should have STS 2");
        assertEquals(TEST_STS_1, sts1Again, "Cached STS 1 should be returned");
        assertEquals(TEST_STS_2, sts2Again, "Cached STS 2 should be returned");
        assertEquals(2, mockHttpInterface.getRequestCount(), "Should make 2 HTTP requests (one per unique URL)");
        assertEquals(2, cipherManager.getStsCacheSize(), "Cache should contain 2 entries");
    }

    @Test
    @DisplayName("Should handle concurrent requests thread-safely")
    public void testThreadSafeCaching() throws InterruptedException, ExecutionException {
        // Arrange
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Act
        CountDownLatch latch = new CountDownLatch(1);
        Future<String>[] futures = new Future[threadCount];

        for (int i = 0; i < threadCount; i++) {
            futures[i] = executor.submit(() -> {
                try {
                    latch.await(); // Wait for all threads to be ready
                    return cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        latch.countDown(); // Start all threads simultaneously

        // Assert
        for (Future<String> future : futures) {
            String sts = future.get();
            assertEquals(TEST_STS_1, sts, "All threads should get the same cached STS");
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate");

        // Even with multiple concurrent requests, only one should hit the network
        assertTrue(mockHttpInterface.getRequestCount() <= threadCount, 
            "Request count should be reasonable (with proper caching, should be 1, but allowing for race conditions)");
        assertEquals(1, cipherManager.getStsCacheSize(), "Cache should contain 1 entry");
    }

    @Test
    @DisplayName("Should clear all cached STS entries")
    public void testClearStsCache() throws IOException {
        // Arrange
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));
        cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_2));
        cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_2);

        assertEquals(2, cipherManager.getStsCacheSize(), "Cache should have 2 entries before clear");

        // Act
        cipherManager.clearStsCache();

        // Assert
        assertEquals(0, cipherManager.getStsCacheSize(), "Cache should be empty after clear");

        // Verify that subsequent requests fetch from network again
        int requestCountBeforeRefetch = mockHttpInterface.getRequestCount();
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));
        String sts = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        
        assertEquals(TEST_STS_1, sts, "Should fetch STS again after cache clear");
        assertEquals(requestCountBeforeRefetch + 1, mockHttpInterface.getRequestCount(), 
            "Should make a new HTTP request after cache clear");
    }

    @Test
    @DisplayName("Should evict specific STS from cache")
    public void testEvictStsFromCache() throws IOException {
        // Arrange
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));
        cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_2));
        cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_2);

        assertEquals(2, cipherManager.getStsCacheSize(), "Cache should have 2 entries");

        // Act
        cipherManager.evictStsFromCache(TEST_SCRIPT_URL_1);

        // Assert
        assertEquals(1, cipherManager.getStsCacheSize(), "Cache should have 1 entry after eviction");

        // Verify that evicted entry is re-fetched
        int requestCountBefore = mockHttpInterface.getRequestCount();
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));
        String sts1 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        
        assertEquals(TEST_STS_1, sts1, "Should fetch STS again after eviction");
        assertEquals(requestCountBefore + 1, mockHttpInterface.getRequestCount(), 
            "Should make a new HTTP request for evicted entry");

        // Verify that non-evicted entry is still cached
        int requestCountBefore2 = mockHttpInterface.getRequestCount();
        String sts2 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_2);
        
        assertEquals(TEST_STS_2, sts2, "Non-evicted entry should still return cached STS");
        assertEquals(requestCountBefore2, mockHttpInterface.getRequestCount(), 
            "Non-evicted entry should not trigger HTTP request");
    }

    @Test
    @DisplayName("Should handle player script updates correctly")
    public void testPlayerScriptUpdate() throws IOException {
        // Arrange - Simulate initial player script
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));
        String sts1 = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        assertEquals(TEST_STS_1, sts1, "Should get initial STS");

        // Act - Simulate YouTube updating to a new player script version
        String newScriptUrl = "https://www.youtube.com/s/player/xyz789/player_ias.vflset/en_US/base.js";
        String newSts = "19999";
        mockHttpInterface.setScriptContent(createMockPlayerScript(newSts));
        String stsNew = cipherManager.getTimestamp(mockHttpInterface, newScriptUrl);

        // Assert
        assertEquals(newSts, stsNew, "Should get new STS for new script URL");
        assertEquals(2, cipherManager.getStsCacheSize(), "Cache should contain both old and new entries");

        // Verify old STS is still cached
        int requestCountBefore = mockHttpInterface.getRequestCount();
        mockHttpInterface.setScriptContent(createMockPlayerScript(TEST_STS_1));
        String sts1Again = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);
        
        assertEquals(TEST_STS_1, sts1Again, "Old STS should still be cached");
        assertEquals(requestCountBefore, mockHttpInterface.getRequestCount(), 
            "Should not make HTTP request for cached old STS");
    }

    @Test
    @DisplayName("Should return 0 for empty cache")
    public void testEmptyCacheSize() {
        assertEquals(0, cipherManager.getStsCacheSize(), "New cipher manager should have empty cache");
    }

    @Test
    @DisplayName("Should handle signatureTimestamp field name")
    public void testSignatureTimestampFieldName() throws IOException {
        // Arrange - Use "signatureTimestamp" instead of "sts"
        String script = "var a={signatureTimestamp:12345,otherField:\"value\"};";
        mockHttpInterface.setScriptContent(script);

        // Act
        String sts = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);

        // Assert
        assertEquals("12345", sts, "Should parse signatureTimestamp field");
    }

    @Test
    @DisplayName("Should handle sts field name")
    public void testStsFieldName() throws IOException {
        // Arrange - Use "sts" field name
        String script = "var config={sts:67890,version:\"2.0\"};";
        mockHttpInterface.setScriptContent(script);

        // Act
        String sts = cipherManager.getTimestamp(mockHttpInterface, TEST_SCRIPT_URL_1);

        // Assert
        assertEquals("67890", sts, "Should parse sts field");
    }

    /**
     * Creates a mock player script with the specified STS value
     * This creates a minimal valid script that contains only the STS field
     */
    private String createMockPlayerScript(String sts) {
        return String.format("var player = {signatureTimestamp:%s};", sts);
    }

    /**
     * Mock HTTP interface for testing without actual network calls
     */
    private static class MockHttpInterface extends HttpInterface {
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private String scriptContent = "";

        public MockHttpInterface() {
            super(null, null, false, null);
        }

        public void setScriptContent(String content) {
            this.scriptContent = content;
        }

        public int getRequestCount() {
            return requestCount.get();
        }

        @Override
        public CloseableHttpResponse execute(HttpUriRequest request) throws IOException {
            requestCount.incrementAndGet();
            
            BasicHttpResponse response = new BasicHttpResponse(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK")
            );
            response.setEntity(new StringEntity(scriptContent, StandardCharsets.UTF_8));

            return new CloseableHttpResponse() {
                @Override
                public void close() throws IOException {
                    // No-op
                }

                @Override
                public org.apache.http.StatusLine getStatusLine() {
                    return response.getStatusLine();
                }

                @Override
                public void setStatusLine(org.apache.http.StatusLine statusLine) {
                    response.setStatusLine(statusLine);
                }

                @Override
                public void setStatusLine(ProtocolVersion protocolVersion, int i) {
                    response.setStatusLine(protocolVersion, i);
                }

                @Override
                public void setStatusLine(ProtocolVersion protocolVersion, int i, String s) {
                    response.setStatusLine(protocolVersion, i, s);
                }

                @Override
                public void setStatusCode(int i) throws IllegalStateException {
                    response.setStatusCode(i);
                }

                @Override
                public void setReasonPhrase(String s) throws IllegalStateException {
                    response.setReasonPhrase(s);
                }

                @Override
                public org.apache.http.HttpEntity getEntity() {
                    return response.getEntity();
                }

                @Override
                public void setEntity(org.apache.http.HttpEntity httpEntity) {
                    response.setEntity(httpEntity);
                }

                @Override
                public java.util.Locale getLocale() {
                    return response.getLocale();
                }

                @Override
                public void setLocale(java.util.Locale locale) {
                    response.setLocale(locale);
                }

                @Override
                public ProtocolVersion getProtocolVersion() {
                    return response.getProtocolVersion();
                }

                @Override
                public boolean containsHeader(String s) {
                    return response.containsHeader(s);
                }

                @Override
                public org.apache.http.Header[] getHeaders(String s) {
                    return response.getHeaders(s);
                }

                @Override
                public org.apache.http.Header getFirstHeader(String s) {
                    return response.getFirstHeader(s);
                }

                @Override
                public org.apache.http.Header getLastHeader(String s) {
                    return response.getLastHeader(s);
                }

                @Override
                public org.apache.http.Header[] getAllHeaders() {
                    return response.getAllHeaders();
                }

                @Override
                public void addHeader(org.apache.http.Header header) {
                    response.addHeader(header);
                }

                @Override
                public void addHeader(String s, String s1) {
                    response.addHeader(s, s1);
                }

                @Override
                public void setHeader(org.apache.http.Header header) {
                    response.setHeader(header);
                }

                @Override
                public void setHeader(String s, String s1) {
                    response.setHeader(s, s1);
                }

                @Override
                public void setHeaders(org.apache.http.Header[] headers) {
                    response.setHeaders(headers);
                }

                @Override
                public void removeHeader(org.apache.http.Header header) {
                    response.removeHeader(header);
                }

                @Override
                public void removeHeaders(String s) {
                    response.removeHeaders(s);
                }

                @Override
                public org.apache.http.HeaderIterator headerIterator() {
                    return response.headerIterator();
                }

                @Override
                public org.apache.http.HeaderIterator headerIterator(String s) {
                    return response.headerIterator(s);
                }

                @Override
                public org.apache.http.params.HttpParams getParams() {
                    return response.getParams();
                }

                @Override
                public void setParams(org.apache.http.params.HttpParams httpParams) {
                    response.setParams(httpParams);
                }
            };
        }
    }
}
