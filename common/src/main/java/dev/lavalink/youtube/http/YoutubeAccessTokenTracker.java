package dev.lavalink.youtube.http;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import dev.lavalink.youtube.clients.ClientConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class YoutubeAccessTokenTracker {
  private static final Logger log = LoggerFactory.getLogger(YoutubeAccessTokenTracker.class);

  private static final String TOKEN_FETCH_CONTEXT_ATTRIBUTE = "yt-raw";
  private static final long VISITOR_ID_REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(10);

  private final Object tokenLock = new Object();
  private final HttpInterfaceManager httpInterfaceManager;
  private String visitorId;
  private long lastVisitorIdUpdate;

  public YoutubeAccessTokenTracker(@NotNull HttpInterfaceManager httpInterfaceManager) {
    this.httpInterfaceManager = httpInterfaceManager;
  }

  /**
   * Updates the visitor id if more than {@link #VISITOR_ID_REFRESH_INTERVAL} time has passed since last updated.
   */
  public String getVisitorId() {
    long now = System.currentTimeMillis();

    if (visitorId == null || now - lastVisitorIdUpdate < VISITOR_ID_REFRESH_INTERVAL) {
      synchronized (tokenLock) {
        if (now - lastVisitorIdUpdate < VISITOR_ID_REFRESH_INTERVAL) {
          log.debug("YouTube visitor id was recently updated, not updating again right away.");
          return visitorId;
        }

        lastVisitorIdUpdate = now;

        try {
          visitorId = fetchVisitorId();
          log.info("Updating YouTube visitor id succeeded, new one is {}, next update will be after {} seconds.",
              visitorId,
              TimeUnit.MILLISECONDS.toSeconds(VISITOR_ID_REFRESH_INTERVAL)
          );
        } catch (Exception e) {
          log.error("YouTube visitor id update failed.", e);
        }
      }
    }

    return visitorId;
  }

  public boolean isTokenFetchContext(@NotNull HttpClientContext context) {
    return context.getAttribute(TOKEN_FETCH_CONTEXT_ATTRIBUTE) == Boolean.TRUE;
  }

  private String fetchVisitorId() throws IOException {
    try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
      httpInterface.getContext().setAttribute(TOKEN_FETCH_CONTEXT_ATTRIBUTE, true);

      ClientConfig clientConfig = new ClientConfig()
          .withUserAgent("com.google.android.youtube/19.07.39 (Linux; U; Android 11) gzip")
          .withClientName("ANDROID")
          .withClientField("clientVersion", "19.07.39")
          .withClientField("androidSdkVersion", 30)
          .withUserField("lockedSafetyMode", false)
          .setAttributes(httpInterface);

      HttpPost visitorIdPost = new HttpPost("https://youtubei.googleapis.com/youtubei/v1/visitor_id");
      visitorIdPost.setEntity(new StringEntity(clientConfig.toJsonString(), "UTF-8"));

      try (CloseableHttpResponse response = httpInterface.execute(visitorIdPost)) {
        HttpClientTools.assertSuccessWithContent(response, "youtube visitor id");
        JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());
        return json.get("responseContext").get("visitorData").text();
      }
    }
  }
}
