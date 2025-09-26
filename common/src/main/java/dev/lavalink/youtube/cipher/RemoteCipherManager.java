package dev.lavalink.youtube.cipher;

import com.grack.nanojson.JsonWriter;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;

/**
 * Handles parsing and caching of ciphers via a remote proxy
 */
public class RemoteCipherManager implements CipherManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteCipherManager.class);

    private final Object cipherLoadLock;
    private final String remoteUrl;
    private final @Nullable String remotePass;

    protected volatile CachedPlayerScript cachedPlayerScript;

    /**
     * Create a new remote cipher manager
     */
    public RemoteCipherManager(String remoteUrl, @Nullable String remotePass) {
        this.cipherLoadLock = new Object();
        this.remoteUrl = remoteUrl;
        this.remotePass = remotePass;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    @Nullable
    public String getRemotePass() {
        return remotePass;
    }

    /**
     * Produces a valid playback URL for the specified track
     *
     * @param httpInterface HTTP interface to use
     * @param playerScript  Address of the script which is used to decipher signatures
     * @param format        The track for which to get the URL
     * @return Valid playback URL
     * @throws IOException On network IO error
     */
    @NotNull
    public URI resolveFormatUrl(@NotNull HttpInterface httpInterface,
                                @NotNull String playerScript,
                                @NotNull StreamFormat format) throws IOException {
        String signature = format.getSignature();
        String nParameter = format.getNParameter();
        URI initialUrl = format.getUrl();

        URIBuilder uri = new URIBuilder(initialUrl);

        if (!DataFormatTools.isNullOrEmpty(signature)) {
            return getUri(httpInterface, format.getSignature(), format.getSignatureKey(), nParameter, initialUrl, playerScript);
        }

        uri.setParameter("n", decipherN(httpInterface, nParameter, playerScript));
        try {
            return uri.build();
        } catch (URISyntaxException f) {
            throw new RuntimeException(f);
        }
    }

    private CachedPlayerScript getPlayerScript(@NotNull HttpInterface httpInterface) {
        synchronized (cipherLoadLock) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/embed/"))) {
                HttpClientTools.assertSuccessWithContent(response, "fetch player script (embed)");

                String responseText = EntityUtils.toString(response.getEntity());
                String scriptUrl = DataFormatTools.extractBetween(responseText, "\"jsUrl\":\"", "\"");

                if (scriptUrl == null) {
                    throw throwWithDebugInfo(log, null, "no jsUrl found", "html", responseText);
                }

                return (cachedPlayerScript = new CachedPlayerScript(scriptUrl));
            } catch (IOException e) {
                throw ExceptionTools.toRuntimeException(e);
            }
        }
    }

    public CachedPlayerScript getCachedPlayerScript(@NotNull HttpInterface httpInterface) {
        if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
            synchronized (cipherLoadLock) {
                if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
                    return getPlayerScript(httpInterface);
                }
            }
        }

        return cachedPlayerScript;
    }

    public String getTimestamp(HttpInterface httpInterface, String sourceUrl) throws IOException {
        synchronized (cipherLoadLock) {
            log.debug("Timestamp from script {}", sourceUrl);

            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(CipherUtils.parseTokenScriptUrl(sourceUrl)))) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Received non-success response code " + statusCode + " from script url " +
                        sourceUrl + " ( " + CipherUtils.parseTokenScriptUrl(sourceUrl) + " )");
                }
                return getTimestampFromScript(httpInterface, sourceUrl);
            }
        }
    }

    private String getRemoteEndpoint(String path) {
        return remoteUrl.endsWith("/") ? remoteUrl + path : remoteUrl + "/" + path;
    }

    private void applyPass(HttpRequest request) {
        if (remotePass != null && !remotePass.isEmpty()) {
            request.addHeader("Authorization", remotePass);
        }
    }

    private String decipherN(HttpInterface httpInterface, String n, String playerScript) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("decrypt_signature"));

        String requestBody = JsonWriter.string()
            .object()
            .value("player_url", "https://youtube.com" + playerScript)
            .value("encrypted_signature", "")
            .value("n_param", n)
            .value("video_id", "test")
            .end()
            .done();
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
        applyPass(request);

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String responseBody = (entity != null) ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

            if (statusCode >= 200 && statusCode < 300) {
                if (responseBody == null || responseBody.isEmpty()) {
                    throw new IOException("Received empty successful response from decryption proxy.");
                }

                JsonBrowser json = JsonBrowser.parse(responseBody);

                String returnedN = json.get("decrypted_n_sig").text();

                if (returnedN != null && !returnedN.isEmpty()) {
                    return returnedN;
                }
                return "";
            } else {
                throw new IOException("Decryption proxy request failed with status code: " + statusCode + ". Response: " + responseBody);
            }
        }
    }

    private URI getUri(HttpInterface httpInterface, String sig, String sigKey, String nParam, URI initial, String playerScript) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("decrypt_signature"));

        String requestBody = JsonWriter.string()
            .object()
            .value("player_url", "https://youtube.com" + playerScript)
            .value("encrypted_signature", sig)
            .value("n_param", nParam)
            .value("signature_key", sigKey)
            .value("video_id", "test")
            .end()
            .done();
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
        applyPass(request);

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String responseBody = (entity != null) ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

            if (statusCode >= 200 && statusCode < 300) {
                if (responseBody == null || responseBody.isEmpty()) {
                    throw new IOException("Received empty successful response from decryption proxy.");
                }

                JsonBrowser json = JsonBrowser.parse(responseBody);

                String returnedSignature = json.get("decrypted_signature").text();
                String returnedN = json.get("decrypted_n_sig").text();

                URIBuilder uriBuilder = new URIBuilder(initial);

                if (returnedSignature != null && !returnedSignature.isEmpty()) {
                    if (sigKey == null || sigKey.trim().isEmpty()) {
                        log.error("Warning: Decrypted signature received, but sigKey is null or empty. Using default 'sig'.");
                        sigKey = "sig";
                    }
                    uriBuilder.setParameter(sigKey.trim(), returnedSignature);
                } else if (sig != null && !sig.isEmpty()) {
                    log.warn("Warning: Original signature parameter 's' was present, but no decrypted signature returned from proxy.");
                }

                if (returnedN != null && !returnedN.isEmpty()) {
                    uriBuilder.setParameter("n", returnedN);
                } else if (nParam != null && !nParam.isEmpty()) {
                    log.error("Warning: Original parameter 'n' was present, but no decrypted n-parameter returned from proxy.");
                }

                return uriBuilder.build();

            } else {
                throw new IOException("Decryption proxy request failed with status code: " + statusCode + ". Response: " + responseBody + " SIG: " + sig);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String getTimestampFromScript(HttpInterface httpInterface, String playerScript) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("get_sts"));

        String requestBody = JsonWriter.string()
            .object()
            .value("player_url", "https://youtube.com" + playerScript)
            .value("video_id", "test")
            .end()
            .done();
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
        applyPass(request);

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String responseBody = (entity != null) ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

            if (statusCode >= 200 && statusCode < 300) {
                if (responseBody == null || responseBody.isEmpty()) {
                    throw new IOException("Received empty successful response from decryption proxy.");
                }
                log.debug("Received response from proxy: {}", responseBody);

                JsonBrowser json = JsonBrowser.parse(responseBody);

                return json.get("sts").text();
            } else {
                throw new IOException("Decryption proxy request failed with status code: " + statusCode + ". Response: " + responseBody);
            }
        }
    }

}
