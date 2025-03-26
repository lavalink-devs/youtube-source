import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.cipher.SignatureCipherManager;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

@Disabled("Disabled since it's intended for manual use. Comment out to enable test")
public class SignatureCipherManagerTest {
    private TestCase[] scripts = new TestCase[]{
            new TestCase("https://www.youtube.com/s/player/4fcd6e4a/player_ias.vflset/en_US/base.js", "o_L251jm8yhZkWtBW", "lXoxI3XvToqn6A", "2aq0aqSyOoJXtK73m-uME_jv7-pT15gOFC02RFkGMqWpzEICs69VdbwQ0LDp1v7j8xx92efCJlYFYb1sUkkBSPOlPmXgIARw8JQ0qOAOAA", "wAOAOq0QJ8ARAIgXmPlOPSBkkUs1bYFYlJCfe29xx8q7v1pDL0QwbdV96sCIEzpWqMGkFR20CFOg51Tp-7vj_EMu-m37KtXJoOySqa0"),
            new TestCase("https://www.youtube.com/s/player/363db69b/player_ias.vflset/en_US/base.js", "eWYu5d5YeY_4LyEDc", "XJQqf-N7Xra3gg", "2aq0aqSyOoJXtK73m-uME_jv7-pT15gOFC02RFkGMqWpzEICs69VdbwQ0LDp1v7j8xx92efCJlYFYb1sUkkBSPOlPmXgIARw8JQ0qOAOAA", "0aqSyOoJXtK73m-uME_jv7-pT15gOFC02RFkGMqWpz2ICs6EVdbwQ0LDp1v7j8xx92efCJlYFYb1sUkkBSPOlPmXgIARw8JQ0qOAOAA")
    };

    @Test
    public void testResolveFormatUrl() throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpInterface httpInterface = new HttpInterface(httpClient, new HttpClientContext(), true, noOpFilter);
            for (TestCase test: scripts) {
                SignatureCipherManager cipherManager = new SignatureCipherManager();

                try {
                    URI uri = cipherManager.resolveFormatUrl(httpInterface, test.uri, getTestStream(test));

                    Assertions.assertNotNull(uri);
                    // Assert that our N param is set
                    Assertions.assertTrue(uri.toString().contains("n=" + test.expectedN));
                    Assertions.assertTrue(uri.toString().contains("sig=" + test.expectedSig));
                } catch (IOException | IllegalStateException e) {
                    Assertions.fail("Failed to get or parse the cipher script: " + e.getMessage());
                }
            }
        }
    }

    HttpContextFilter noOpFilter = new HttpContextFilter() {
        @Override
        public void onContextOpen(HttpClientContext context) {
            // No operation
        }

        @Override
        public void onRequest(HttpClientContext context, org.apache.http.client.methods.HttpUriRequest request, boolean isRepetition) {
            // No operation
        }

        @Override
        public boolean onRequestResponse(HttpClientContext context, org.apache.http.client.methods.HttpUriRequest request, org.apache.http.HttpResponse response) {
            return false; // Do not retry by default
        }

        @Override
        public boolean onRequestException(HttpClientContext context, org.apache.http.client.methods.HttpUriRequest request, Throwable exception) {
            return false; // Do not retry by default
        }

        @Override
        public void onContextClose(HttpClientContext context) {
            // No operation
        }
    };

    private StreamFormat getTestStream(TestCase testCase) {
        return new StreamFormat(
                ContentType.APPLICATION_OCTET_STREAM,
                18,
                128000,
                1000000,
                2,
                "",
                testCase.nParam,
                testCase.signature,
                "sig",
                true,
                false);
    }

    private class TestCase {
        String uri;
        String nParam;
        String expectedN;
        String signature;
        String expectedSig;

        public TestCase(String uri, String nParam, String expectedN, String signature, String expectedSig) {
            this.uri = uri;
            this.nParam = nParam;
            this.expectedN = expectedN;
            this.signature = signature;
            this.expectedSig = expectedSig;
        }
    }
}