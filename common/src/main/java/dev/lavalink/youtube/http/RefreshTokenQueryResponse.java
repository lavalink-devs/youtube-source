package dev.lavalink.youtube.http;

import com.grack.nanojson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefreshTokenQueryResponse {
    private final JsonObject response;
    private final String error;

    RefreshTokenQueryResponse(JsonObject response) {
        this.response = response;

        if (response.has("error") && !response.isNull("error")) {
            this.error = response.getString("error");
        } else {
            this.error = null;
        }
    }

    @NotNull
    public JsonObject getJsonObject() {
        return this.response;
    }

    @Nullable
    public String getError() {
        return this.error;
    }

    /**
     * @return The type of <u>access</u> token that was generated,
     *         or null if token refreshing failed.
     */
    @Nullable
    public String getTokenType() {
        return this.response.getString("token_type", null);
    }

    /**
     * @return The refresh token, or null if the oauth request failed
     *         or only the access token is being refreshed.
     */
    @Nullable
    public String getRefreshToken() {
        return this.response.getString("refresh_token", null);
    }

    /**
     * @return The generated access token, or null if token refreshing failed.
     */
    @Nullable
    public String getAccessToken() {
        return this.response.getString("access_token", null);
    }

    public long getExpiresInSeconds() {
        return this.response.getLong("expires_in", 0L);
    }
}
