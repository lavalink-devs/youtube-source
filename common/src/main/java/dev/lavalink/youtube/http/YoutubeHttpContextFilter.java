package dev.lavalink.youtube.http;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextRetryCounter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public class YoutubeHttpContextFilter extends BaseYoutubeHttpContextFilter {
  private static final Logger log = LoggerFactory.getLogger(YoutubeHttpContextFilter.class);

  private static final String ATTRIBUTE_RESET_RETRY = "isResetRetry";
  public static final String ATTRIBUTE_USER_AGENT_SPECIFIED = "clientUserAgent";

  private static final HttpContextRetryCounter retryCounter = new HttpContextRetryCounter("yt-token-retry");

  private YoutubeAccessTokenTracker tokenTracker;
  private YoutubeOauth2Handler oauth2Handler;

  public void setTokenTracker(@NotNull YoutubeAccessTokenTracker tokenTracker) {
    this.tokenTracker = tokenTracker;
  }

  public void setOauth2Handler(@NotNull YoutubeOauth2Handler oauth2Handler) {
    this.oauth2Handler = oauth2Handler;
  }

  @Override
  public void onContextOpen(HttpClientContext context) {
    CookieStore cookieStore = context.getCookieStore();

    if (cookieStore == null) {
      cookieStore = new BasicCookieStore();
      context.setCookieStore(cookieStore);
    }

    // Reset cookies for each sequence of requests.
    cookieStore.clear();
  }

  @Override
  public void onRequest(HttpClientContext context,
                        HttpUriRequest request,
                        boolean isRepetition) {
    if (!isRepetition) {
      context.removeAttribute(ATTRIBUTE_RESET_RETRY);
    }

    retryCounter.handleUpdate(context, isRepetition);

    if (tokenTracker.isTokenFetchContext(context)) {
      // Used for fetching visitor id, let's not recurse.
      return;
    }

    if (oauth2Handler.isOauthFetchContext(context)) {
      return;
    }

    String userAgent = context.getAttribute(ATTRIBUTE_USER_AGENT_SPECIFIED, String.class);

    if (userAgent != null) {
      request.setHeader("User-Agent", userAgent);
      request.setHeader("X-Goog-Visitor-Id", tokenTracker.getVisitorId());
      context.removeAttribute(ATTRIBUTE_USER_AGENT_SPECIFIED);
    }

    oauth2Handler.applyToken(request);

//    try {
//      URI uri = new URIBuilder(request.getURI())
//          .setParameter("key", YoutubeConstants.INNERTUBE_ANDROID_API_KEY)
//          .build();
//
//      if (request instanceof HttpRequestBase) {
//        ((HttpRequestBase) request).setURI(uri);
//      } else {
//        throw new IllegalStateException("Cannot update request URI.");
//      }
//    } catch (URISyntaxException e) {
//      throw new RuntimeException(e);
//    }
  }

  @Override
  public boolean onRequestResponse(HttpClientContext context,
                                   HttpUriRequest request,
                                   HttpResponse response) {
    if (response.getStatusLine().getStatusCode() == 429) {
      throw new FriendlyException("This IP address has been blocked by YouTube (429).", COMMON, null);
    }

//    if (tokenTracker.isTokenFetchContext(context) || retryCounter.getRetryCount(context) >= 1) {
//      return false;
//    }
    return false;
  }

  @Override
  public boolean onRequestException(HttpClientContext context,
                                    HttpUriRequest request,
                                    Throwable error) {
    // Always retry once in case of connection reset exception.
    if (HttpClientTools.isConnectionResetException(error)) {
      if (context.getAttribute(ATTRIBUTE_RESET_RETRY) == null) {
        context.setAttribute(ATTRIBUTE_RESET_RETRY, true);
        return true;
      }
    }

    return false;
  }
}
