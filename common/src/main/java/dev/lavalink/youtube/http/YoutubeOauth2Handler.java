package dev.lavalink.youtube.http;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class YoutubeOauth2Handler {
    private static final Logger log = LoggerFactory.getLogger(YoutubeAccessTokenTracker.class);
    private static int fetchErrorLogCount = 0;

    // no, i haven't leaked anything of mine
    // this (i presume) can be found within youtube's page source
    // ¯\_(ツ)_/¯
    private static final String CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT";
    private static final String SCOPES = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube";
    private static final String OAUTH_FETCH_CONTEXT_ATTRIBUTE = "yt-oauth";

    private final HttpInterfaceManager httpInterfaceManager;

    private boolean enabled;
    private String refreshToken;

    private String tokenType;
    private String accessToken;
    private long tokenExpires;

    public YoutubeOauth2Handler(HttpInterfaceManager httpInterfaceManager) {
        this.httpInterfaceManager = httpInterfaceManager;
    }

    public void setRefreshToken(@Nullable String refreshToken) {
        this.refreshToken = refreshToken;
        this.tokenExpires = System.currentTimeMillis(); // to trigger an access token refresh

        if (refreshToken == null) {
            initializeAccessToken();
        }
    }

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }

    public boolean isOauthFetchContext(HttpClientContext context) {
        return context.getAttribute(OAUTH_FETCH_CONTEXT_ATTRIBUTE) == Boolean.TRUE;
    }

    /**
     * Makes a request to YouTube for a device code that users can then authorise to allow
     * this source to make requests using an account access token.
     * This will begin the oauth flow. If a refresh token is present, {@link #refreshAccessToken()} should
     * be used instead.
     */
    private void initializeAccessToken() {
        JsonObject response = fetchDeviceCode();
        String verificationUrl = response.getString("verification_url");
        String userCode = response.getString("user_code");
        String deviceCode = response.getString("device_code");

        log.info("==================================================");
        log.info("!!! DO NOT AUTHORISE WITH YOUR MAIN ACCOUNT, USE A BURNER !!!");
        log.info("OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}", verificationUrl, userCode);
        log.info("!!! DO NOT AUTHORISE WITH YOUR MAIN ACCOUNT, USE A BURNER !!!");
        log.info("==================================================");

        // Should this be a daemon?
        new Thread(() -> pollForToken(deviceCode), "youtube-source-token-poller").start();
    }

    private JsonObject fetchDeviceCode() {
        // @formatter:off
        String requestJson = JsonWriter.string()
            .object()
                .value("client_id", CLIENT_ID)
                .value("scope", SCOPES)
                .value("device_id", UUID.randomUUID().toString().replace("-", ""))
                .value("device_model", "ytlr::")
            .end()
            .done();
        // @formatter:on

        HttpPost request = new HttpPost("https://www.youtube.com/o/oauth2/device/code");
        StringEntity body = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
        request.setEntity(body);

        try (HttpInterface httpInterface = getHttpInterface();
             CloseableHttpResponse response = httpInterface.execute(request)) {
            HttpClientTools.assertSuccessWithContent(response, "device code fetch");
            return JsonParser.object().from(response.getEntity().getContent());
        } catch (IOException | JsonParserException e) {
            throw ExceptionTools.toRuntimeException(e);
        }
    }

    private void pollForToken(String deviceCode) {
        // @formatter:off
        String requestJson = JsonWriter.string()
            .object()
                .value("client_id", CLIENT_ID)
                .value("client_secret", CLIENT_SECRET)
                .value("code", deviceCode)
                .value("grant_type", "http://oauth.net/grant_type/device/1.0")
            .end()
            .done();
        // @formatter:on

        HttpPost request = new HttpPost("https://www.youtube.com/o/oauth2/token");
        StringEntity body = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
        request.setEntity(body);

        while (true) {
            try (HttpInterface httpInterface = getHttpInterface();
                 CloseableHttpResponse response = httpInterface.execute(request)) {
                HttpClientTools.assertSuccessWithContent(response, "oauth2 token fetch");
                JsonObject parsed = JsonParser.object().from(response.getEntity().getContent());

                if (parsed.has("error") && !parsed.isNull("error")) {
                    String error = parsed.getString("error");

                    if (error.equals("authorization_pending")) {
                        long interval = parsed.getLong("interval");
                        Thread.sleep(Math.max(5000, interval * 1000));
                        continue;
                    } else if (error.equals("expired_token")) {
                        log.error("OAUTH INTEGRATION: The device token has expired. OAuth integration has been canceled.");
                    } else {
                        log.error("Unhandled OAuth2 error: {}", error);
                    }

                    return;
                }

                tokenType = parsed.getString("token_type");
                accessToken = parsed.getString("access_token");
                refreshToken = parsed.getString("refresh_token");
                tokenExpires = System.currentTimeMillis() + (parsed.getLong("expires_in") * 1000) - 60000;
                log.info("OAUTH INTEGRATION: Token retrieved successfully. Access Token: {}, Refresh Token: {}", accessToken, refreshToken);

                enabled = true;
                return;
            } catch (IOException | JsonParserException | InterruptedException e) {
                log.error("Failed to fetch OAuth2 token response", e);
            }
        }
    }

    private void refreshAccessToken() {
        // SUBTRACT 1 MINUTE FROM TOKEN EXPIRY
    }

    public void applyToken(HttpUriRequest request) {
        if (!enabled || refreshToken == null) {
            return;
        }

        if (System.currentTimeMillis() > tokenExpires) {
            try {
                refreshAccessToken();
            } catch (Throwable t) {
                if (fetchErrorLogCount++ <= 3) {
                    // log fetch errors up to 3 times to avoid spamming logs.
                    // in theory, we can still make requests without an access token,
                    // it's just less likely to succeed, but we shouldn't bloat a user's logs
                    // in the event YT changes something and breaks oauth integration.
                    // anyway, the chances of each error being different is small i think.
                    log.error("Refreshing YouTube access token failed", t);
                }

                // retry in 15 seconds to avoid spamming YouTube with requests.
                tokenExpires = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
                return;
            }
        }

        // check again to ensure updating worked as expected.
        if (accessToken != null && tokenType != null && System.currentTimeMillis() + 60000 < tokenExpires) {
            request.setHeader("Authorization", String.format("%s %s", tokenType, accessToken));
        }
    }

    private HttpInterface getHttpInterface() {
        HttpInterface httpInterface = httpInterfaceManager.getInterface();
        httpInterface.getContext().setAttribute(OAUTH_FETCH_CONTEXT_ATTRIBUTE, true);
        return httpInterface;
    }
}
