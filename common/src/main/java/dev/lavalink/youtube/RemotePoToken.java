package dev.lavalink.youtube;

import com.grack.nanojson.JsonWriter;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class RemotePoToken {
    private static final Logger log = LoggerFactory.getLogger(RemotePoToken.class);

    private final @NotNull String remoteUrl;
    private final String password;

    /**
     * Creates a remote pot client.
     *
     * @param remoteUrl base url of the webpo-generator service.
     * @param password oprional auth pass.
     */
    public RemotePoToken(@NotNull String remoteUrl, @Nullable String password) {
        this.remoteUrl = remoteUrl;
        this.password = password;
    }

    /**
     * Generates a pot for the given content binding.
     *
     * @param httpInterface HTTP interface to use.
     * @param contentBinding videoId or visitorData, depending on the innertube client. If null,
     *                        the remote service generates a visitor-bound token.
     * @return The generated PoToken and the binding used by the remote service.
     * @throws IOException only if the request fails or response is invalid.
     */
    @NotNull
    public Result generate(@NotNull HttpInterface httpInterface, @Nullable String contentBinding) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("generate"));

        log.debug("Generating remote pot for content binding: {}", contentBinding == null ? "remote visitor" : contentBinding);

        if (password != null && !password.isEmpty()) {
            request.setHeader("Authorization", password);
        }

        String requestBody = contentBinding == null
            ? "{}"
            : JsonWriter.string()
                .object()
                .value("content_binding", contentBinding)
                .end()
                .done();

        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            String responseBody = validateAndGetResponseBody(response);

            log.debug("Received response from remote pot generator service: {}", responseBody);

            JsonBrowser json = JsonBrowser.parse(responseBody);
            String token = json.get("poToken").text();

            if (token == null || token.isEmpty()) {
                throw new IOException("Remote pot service did not return a poToken.");
            }

            String binding = json.get("contentBinding").text();
            if (binding == null || binding.isEmpty()) {
                throw new IOException("Remote pot service did not return a contentBinding.");
            }

            return new Result(token, binding);
        }
    }

    public static final class Result {
        private final @NotNull String poToken;
        private final @NotNull String contentBinding;

        public Result(@NotNull String poToken, @NotNull String contentBinding) {
            this.poToken = poToken;
            this.contentBinding = contentBinding;
        }

        @NotNull
        public String getPoToken() {
            return poToken;
        }

        @NotNull
        public String getContentBinding() {
            return contentBinding;
        }
    }

    private String getRemoteEndpoint(@NotNull String path) {
        return remoteUrl.endsWith("/") ? remoteUrl + path : remoteUrl + "/" + path;
    }

    @NotNull
    private String validateAndGetResponseBody(@NotNull HttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        HttpEntity entity = response.getEntity();
        String responseBody = entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

        if (!HttpClientTools.isSuccessWithContent(statusCode)) {
            throw new IOException("Remote pot service request failed with status code: "
                + statusCode + ". Response: " + responseBody);
        }

        if (DataFormatTools.isNullOrEmpty(responseBody)) {
            throw new IOException("Received empty response from remote pot service.");
        }

        return responseBody;
    }
}
