package dev.lavalink.youtube.cipher;

import com.grack.nanojson.JsonWriter;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class SigCipherProxy {
    private static final Logger log = LoggerFactory.getLogger(SigCipherProxy.class);
    private final String proxyUrl;
    private final String proxyPass;

    public SigCipherProxy(String url, String pass) {
        this.proxyPass = pass;
        this.proxyUrl = url;
    }

    public URI getUriFromProxy(HttpInterface httpInterface, String sig, String sigKey, String nParam, URI initial, String playerScript) {
        String targetUrl = proxyUrl.endsWith("/") ? proxyUrl + "decrypt_signature" : proxyUrl + "/decrypt_signature";
        HttpPost request = new HttpPost(targetUrl);

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
        request.addHeader("Authorization", "Bearer " + proxyPass);

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
                throw new IOException("Decryption proxy request failed with status code: " + statusCode + ". Response: " + responseBody);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getTimestampFromScript(HttpInterface httpInterface, String playerScript) {

        String targetUrl = proxyUrl.endsWith("/") ? proxyUrl + "get_sts" : proxyUrl + "/get_sts";
        HttpPost request = new HttpPost(targetUrl);

        String requestBody = JsonWriter.string()
                .object()
                .value("player_url", "https://youtube.com" + playerScript)
                .value("video_id", "test")
                .end()
                .done();
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
        request.addHeader("Authorization", "Bearer " + proxyPass);

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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isReady() {
        return proxyUrl != null;
    }
}
