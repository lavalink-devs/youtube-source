import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.cipher.SignatureCipher;
import dev.lavalink.youtube.cipher.SignatureCipherManager;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

@Disabled("Disabled since it's intended for manual use. Comment out to enable test")
public class SignatureCipherManagerTest {
    private String SCRIPT_URL = "https://www.youtube.com/s/player/4fcd6e4a/player_ias.vflset/en_US/base.js";

    @Test
    public void testGetCypherScript() throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpInterface httpInterface = new HttpInterface(httpClient, new HttpClientContext(), true, noOpFilter);
            SignatureCipherManager cipherManager = new SignatureCipherManager();

            try {
                SignatureCipher cipher = cipherManager.getCipherScript(httpInterface, SCRIPT_URL);

                Assertions.assertNotNull(cipher);

                Assertions.assertNotNull(cipher.nFunction);
                Assertions.assertFalse(cipher.nFunction.isEmpty());
                Assertions.assertTrue(cipher.nFunction.contains("function("));

            } catch (IOException | IllegalStateException e) {
                Assertions.fail("Failed to get or parse the cipher script: " + e.getMessage());
            }
        }
    }

    @Test
    public void testResolveFormatUrl() throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpInterface httpInterface = new HttpInterface(httpClient, new HttpClientContext(), true, noOpFilter);
            SignatureCipherManager cipherManager = new SignatureCipherManager();

            try {
                URI uri = cipherManager.resolveFormatUrl(httpInterface, SCRIPT_URL, exampleStream);

                Assertions.assertNotNull(uri);
                // Assert that our N param is set
                Assertions.assertTrue(uri.toString().contains("&n="));
            } catch (IOException | IllegalStateException e) {
                Assertions.fail("Failed to get or parse the cipher script: " + e.getMessage());
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

    private StreamFormat exampleStream = new StreamFormat(
            ContentType.APPLICATION_OCTET_STREAM,
            18,
            128000,
            1000000,
            2,
            "",
            "u9L2ScigFKrnmOCJO",
            "testSignature",
            "sig",
            true,
            false);
}