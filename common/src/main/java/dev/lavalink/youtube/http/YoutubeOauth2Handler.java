package dev.lavalink.youtube.http;

import com.grack.nanojson.JsonWriter;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class YoutubeOauth2Handler {
    private static final Logger log = LoggerFactory.getLogger(YoutubeOauth2Handler.class);
    private static int fetchErrorLogCount = 0;

    // no, i haven't leaked anything of mine
    // this (i presume) can be found within youtube's page source
    // ¯\_(ツ)_/¯
    private static final String CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT";
    private static final String SCOPES = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube";
    private static final String OAUTH_FETCH_CONTEXT_ATTRIBUTE = "yt-oauth";
    public static final String OAUTH_INJECT_CONTEXT_ATTRIBUTE = "yt-oauth-token";

    public static class OAuthSession {
        private final String initialRefreshToken;
        private volatile String currentRefreshToken;
        private volatile String tokenType;
        private volatile String accessToken;
        private volatile long tokenExpires;
        private volatile int failureCount;

        public OAuthSession(@NotNull String refreshToken) {
            this.initialRefreshToken = refreshToken;
            this.currentRefreshToken = refreshToken;
            this.tokenExpires = 0;
            this.failureCount = 0;
        }

        public String getInitialRefreshToken() {
            return initialRefreshToken;
        }

        public String getCurrentRefreshToken() {
            return currentRefreshToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public long getTokenExpires() {
            return tokenExpires;
        }

        public boolean shouldRefresh() {
            return !DataFormatTools.isNullOrEmpty(currentRefreshToken) &&
                    (DataFormatTools.isNullOrEmpty(accessToken) || System.currentTimeMillis() >= tokenExpires);
        }

        public boolean isValid() {
            return accessToken != null && tokenType != null && System.currentTimeMillis() < tokenExpires;
        }

        public void updateTokens(JsonBrowser json) {
            JsonBrowser newRefreshToken = json.get("refresh_token");
            long tokenLifespan = json.get("expires_in").asLong(300);
            this.tokenType = json.get("token_type").text();
            this.accessToken = json.get("access_token").text();
            if (!newRefreshToken.isNull() && !DataFormatTools.isNullOrEmpty(newRefreshToken.text())) {
                this.currentRefreshToken = newRefreshToken.text();
            }
            this.tokenExpires = System.currentTimeMillis() + (tokenLifespan * 1000) - 60000;
            this.failureCount = 0;
        }

        public void recordFailure() {
            this.failureCount++;
            this.tokenExpires = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(15);
        }

        public int getFailureCount() {
            return failureCount;
        }
    }

    private final HttpInterfaceManager httpInterfaceManager;

    private boolean enabled;
    private final List<OAuthSession> sessions = new CopyOnWriteArrayList<>();
    private final AtomicInteger sessionIndex = new AtomicInteger(0);

    public YoutubeOauth2Handler(HttpInterfaceManager httpInterfaceManager) {
        this.httpInterfaceManager = httpInterfaceManager;
    }

    public void setRefreshToken(@Nullable String refreshToken, boolean skipInitialization) {
        setRefreshTokens(refreshToken != null ? Collections.singletonList(refreshToken) : Collections.emptyList(), skipInitialization);
    }

    public void setRefreshTokens(@NotNull List<String> refreshTokens, boolean skipInitialization) {
        sessions.clear();
        sessionIndex.set(0);

        List<String> validTokens = new ArrayList<>();
        if (refreshTokens != null) {
            for (String t : refreshTokens) {
                if (!DataFormatTools.isNullOrEmpty(t)) {
                    validTokens.add(t.trim());
                }
            }
        }

        if (!validTokens.isEmpty()) {
            for (String token : validTokens) {
                OAuthSession session = new OAuthSession(token);
                try {
                    JsonBrowser json = createNewAccessToken(session.getCurrentRefreshToken());
                    session.updateTokens(json);
                    sessions.add(session);
                    String masked = token.length() > 6 ? token.substring(token.length() - 6) : token;
                    log.info("Initialized OAuth session for token ending in ...{}", masked);
                } catch (Exception e) {
                    String masked = token.length() > 6 ? token.substring(token.length() - 6) : token;
                    log.error("Failed to initialize OAuth session for token ending in ...{}: {}", masked, e.getMessage());
                }
            }

            if (!sessions.isEmpty()) {
                enabled = true;
                log.info("YouTube OAuth multi-account pool active with {} account session(s)", sessions.size());
            } else {
                log.error("All provided OAuth refresh tokens failed to initialize!");
            }
            return;
        }

        if (!skipInitialization) {
            initializeAccessToken();
        }
    }

    public boolean hasAccessToken() {
        return sessions.stream().anyMatch(OAuthSession::isValid);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldRefreshAccessToken() {
        return enabled && sessions.stream().anyMatch(OAuthSession::shouldRefresh);
    }

    @Nullable
    public String getRefreshToken() {
        return sessions.isEmpty() ? null : sessions.get(0).getCurrentRefreshToken();
    }

    public List<String> getRefreshTokens() {
        List<String> tokens = new ArrayList<>();
        for (OAuthSession s : sessions) {
            tokens.add(s.getCurrentRefreshToken());
        }
        return tokens;
    }

    public List<OAuthSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public boolean isOauthFetchContext(HttpClientContext context) {
        return context.removeAttribute(OAUTH_FETCH_CONTEXT_ATTRIBUTE) == Boolean.TRUE;
    }

    /**
     * Makes a request to YouTube for a device code that users can then authorise to allow
     * this source to make requests using an account access token.
     * This will begin the oauth flow. If a refresh token is present, {@link #refreshAccessToken(boolean)} should
     * be used instead.
     */
    private void initializeAccessToken() {
        JsonBrowser response = fetchDeviceCode();

        log.debug("fetch device code response: {}", response.format());

        String verificationUrl = response.get("verification_url").text();
        String userCode = response.get("user_code").text();
        String deviceCode = response.get("device_code").text();
        long interval = response.get("interval").asLong(0) * 1000;

        log.info("==================================================");
        log.info("!!! DO NOT AUTHORISE WITH YOUR MAIN ACCOUNT, USE A BURNER !!!");
        log.info("OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}", verificationUrl, userCode);
        log.info("!!! DO NOT AUTHORISE WITH YOUR MAIN ACCOUNT, USE A BURNER !!!");
        log.info("==================================================");

        Thread pollThread = new Thread(() -> pollForToken(deviceCode, interval == 0 ? 5000 : interval), "youtube-source-token-poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    /**
     * Retrieves a device code for use with the OAuth flow.
     * The returned payload will contain a user code and a device code, as well as a recommended poll interval,
     * which must be used to complete the flow.
     */
    public JsonBrowser fetchDeviceCode() {
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
            return JsonBrowser.parse(response.getEntity().getContent());
        } catch (IOException e) {
            throw ExceptionTools.toRuntimeException(e);
        }
    }

    /**
     * Retrieve a refresh token from a given device code. This might not yield a successful response
     * if the OAuth flow for the given device code has not yet been completed, or the device code is invalid.
     * @param deviceCode The device code obtained from {@link #fetchDeviceCode()}
     */
    public JsonBrowser fetchRefreshToken(String deviceCode) throws IOException {
        try (HttpInterface httpInterface = getHttpInterface()) {
            return fetchRefreshToken(httpInterface, deviceCode);
        }
    }

    private JsonBrowser fetchRefreshToken(HttpInterface httpInterface, String deviceCode) throws IOException {
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

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            HttpClientTools.assertSuccessWithContent(response, "oauth2 token fetch");
            JsonBrowser parsed = JsonBrowser.parse(response.getEntity().getContent());
            log.debug("oauth2 token fetch response: {}", parsed.format());
            return parsed;
        } catch (IOException e) {
            throw ExceptionTools.toRuntimeException(e);
        }
    }

    private void pollForToken(String deviceCode, long interval) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            while (true) {
                try {
                    JsonBrowser response = fetchRefreshToken(httpInterface, deviceCode);

                    if (!response.get("error").isNull()) {
                        String error = response.get("error").text();

                        switch (error) {
                            case "authorization_pending":
                            case "slow_down":
                                Thread.sleep(interval);
                                continue;
                            case "expired_token":
                                log.error("OAUTH INTEGRATION: The device token has expired. OAuth integration has been canceled.");
                                break;
                            case "access_denied":
                                log.error("OAUTH INTEGRATION: Account linking was denied. OAuth integration has been canceled.");
                                break;
                            default:
                                log.error("Unhandled OAuth2 error: {}", error);
                                break;
                        }

                        return;
                    }

                    OAuthSession session = new OAuthSession(response.get("refresh_token").text());
                    session.updateTokens(response);
                    sessions.add(session);
                    log.info("OAUTH INTEGRATION: Token retrieved successfully. Store your refresh token as this can be reused. ({})", session.getCurrentRefreshToken());
                    enabled = true;
                    return;
                } catch (InterruptedException | RuntimeException e) {
                    log.error("Failed to fetch OAuth2 token response", e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to acquire HTTP interface for token polling", e);
        }
    }

    /**
     * Refreshes an access token using supplied refresh token(s).
     *
     * @param force Whether to forcefully renew the access token, even if it doesn't necessarily
     *              need to be refreshed yet.
     */
    public void refreshAccessToken(boolean force) {
        log.debug("Refreshing access token (force: {})", force);

        if (sessions.isEmpty()) {
            throw new IllegalStateException("Cannot fetch access token without a refresh token!");
        }

        for (OAuthSession session : sessions) {
            if (session.shouldRefresh() || force) {
                synchronized (session) {
                    if (session.shouldRefresh() || force) {
                        try {
                            JsonBrowser json = createNewAccessToken(session.getCurrentRefreshToken());
                            session.updateTokens(json);
                            log.info("YouTube access token refreshed successfully for token ending in ...{}",
                                    session.getCurrentRefreshToken().length() > 6 ?
                                            session.getCurrentRefreshToken().substring(session.getCurrentRefreshToken().length() - 6) :
                                            session.getCurrentRefreshToken());
                        } catch (Throwable t) {
                            session.recordFailure();
                            log.warn("Failed to refresh OAuth session for token ending in ...{}: {}",
                                    session.getCurrentRefreshToken().length() > 6 ?
                                            session.getCurrentRefreshToken().substring(session.getCurrentRefreshToken().length() - 6) :
                                            session.getCurrentRefreshToken(), t.getMessage());
                        }
                    }
                }
            }
        }
    }


    /**
     * Executes the HTTP request to refresh the access token and returns the response.
     *
     * @param refreshToken The refresh token to be included in the request.
     * @return The JSON response as a JsonObject.
     */
    public JsonBrowser createNewAccessToken(String refreshToken) {
        // @formatter:off
        String requestJson = JsonWriter.string()
            .object()
                .value("client_id", CLIENT_ID)
                .value("client_secret", CLIENT_SECRET)
                .value("refresh_token", refreshToken)
                .value("grant_type", "refresh_token")
            .end()
            .done();
        // @formatter:on

        HttpPost request = new HttpPost("https://www.youtube.com/o/oauth2/token");
        StringEntity entity = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
        request.setEntity(entity);

        try (HttpInterface httpInterface = getHttpInterface();
             CloseableHttpResponse response = httpInterface.execute(request)) {
            HttpClientTools.assertSuccessWithContent(response, "oauth2 token fetch");
            JsonBrowser parsed = JsonBrowser.parse(response.getEntity().getContent());

            if (!parsed.get("error").isNull()) {
                throw new RuntimeException("Refreshing access token returned error " + parsed.get("error").text());
            }

            return parsed;
        } catch (IOException e) {
            throw ExceptionTools.toRuntimeException(e);
        }
    }

    public void applyToken(HttpUriRequest request) {
        if (!enabled || sessions.isEmpty()) {
            return;
        }

        int totalSessions = sessions.size();
        for (int i = 0; i < totalSessions; i++) {
            int idx = Math.abs(sessionIndex.getAndIncrement() % totalSessions);
            OAuthSession session = sessions.get(idx);

            if (session.shouldRefresh()) {
                synchronized (session) {
                    if (session.shouldRefresh()) {
                        log.debug("Access token for OAuth session index {} has expired, refreshing...", idx);
                        try {
                            JsonBrowser json = createNewAccessToken(session.getCurrentRefreshToken());
                            session.updateTokens(json);
                        } catch (Throwable t) {
                            session.recordFailure();
                            if (++fetchErrorLogCount <= 3) {
                                log.error("Refreshing YouTube access token for session index {} failed", idx, t);
                            } else {
                                log.debug("Refreshing YouTube access token for session index {} failed", idx, t);
                            }
                            continue; // Try next session in pool
                        }
                        fetchErrorLogCount = 0;
                    }
                }
            }

            if (session.isValid()) {
                String masked = session.getCurrentRefreshToken().length() > 6 ?
                        session.getCurrentRefreshToken().substring(session.getCurrentRefreshToken().length() - 6) :
                        session.getCurrentRefreshToken();
                log.debug("Using OAuth session index {} authorization header (token ending in ...{})", idx, masked);
                request.setHeader("Authorization", String.format("%s %s", session.getTokenType(), session.getAccessToken()));
                return;
            }
        }

        log.warn("No valid OAuth sessions available in the pool to authorize request");
    }

    public void applyToken(HttpUriRequest request, String token) {
        request.setHeader("Authorization", String.format("%s %s", "Bearer", token));
    }

    private HttpInterface getHttpInterface() {
        HttpInterface httpInterface = httpInterfaceManager.getInterface();
        httpInterface.getContext().setAttribute(OAUTH_FETCH_CONTEXT_ATTRIBUTE, true);
        return httpInterface;
    }
}
