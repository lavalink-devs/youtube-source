package dev.lavalink.youtube.http;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoteCipherHttpContextFilter implements HttpContextFilter {
    private final @Nullable String remotePass;
    private final @Nullable String userAgent;
    private final @NotNull String pluginVersion;

    public RemoteCipherHttpContextFilter(@Nullable String remotePass,
                                         @Nullable String userAgent,
                                         @NotNull String pluginVersion) {
        this.remotePass = remotePass;
        this.userAgent = userAgent;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public void onContextOpen(HttpClientContext context) {
        // No-op
    }

    @Override
    public void onContextClose(HttpClientContext context) {
        // No-op
    }

    @Override
    public void onRequest(HttpClientContext context, HttpUriRequest request, boolean isRepetition) {
        if (!DataFormatTools.isNullOrEmpty(remotePass)) {
            request.addHeader("Authorization", remotePass);
        }

        if (!DataFormatTools.isNullOrEmpty(userAgent)) {
            request.addHeader("User-Agent", userAgent);
        }

        request.addHeader("Plugin-Version", pluginVersion);
    }

    @Override
    public boolean onRequestResponse(HttpClientContext context, HttpUriRequest request, HttpResponse response) {
        return false;
    }

    @Override
    public boolean onRequestException(HttpClientContext context, HttpUriRequest request, Throwable error) {
        return false;
    }
}