package dev.lavalink.youtube.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.cipher.CipherManager.CachedPlayerScript;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.sabr.SabrClientInfo;
import dev.lavalink.youtube.track.format.StreamFormat;
import dev.lavalink.youtube.track.format.TrackFormats;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.decodeUrlEncodedItems;
import static com.sedmelluq.discord.lavaplayer.tools.Units.CONTENT_LENGTH_UNKNOWN;

public abstract class StreamingNonMusicClient extends NonMusicClient {
    private static final Logger log = LoggerFactory.getLogger(StreamingNonMusicClient.class);

    protected static String DEFAULT_SIGNATURE_KEY = "signature";

    @Override
    @Nullable
    public SabrClientInfo getSabrClientInfo(@NotNull HttpInterface httpInterface) {
        ClientConfig config = getBaseClientConfig(httpInterface);
        Object clientVersion = config.getClientField("clientVersion");
        return SabrClientInfo.forClient(config.getName(), clientVersion == null ? null : clientVersion.toString());
    }

    @Override
    public TrackFormats loadFormats(@NotNull YoutubeAudioSourceManager source,
                                    @NotNull HttpInterface httpInterface,
                                    @NotNull String videoId) throws CannotBeLoaded, IOException {
        preparePlayback(source, httpInterface, videoId);
        JsonBrowser json = loadTrackInfoFromInnertube(source, httpInterface, videoId, null, true);
        JsonBrowser playabilityStatus = json.get("playabilityStatus");
        JsonBrowser videoDetails = json.get("videoDetails");
        CachedPlayerScript playerScript = source.getCipherManager().getCachedPlayerScript(httpInterface);

        boolean isLive = videoDetails.get("isLive").asBoolean(false);

        if ("OK".equals(playabilityStatus.get("status").text()) && playabilityStatus.get("reason").safeText().contains("This live event has ended")) {
            // Long videos after ending of stream don't contain contentLength field as they
            // are still being processed by YouTube.
            isLive = true;
        }

        JsonBrowser streamingData = json.get("streamingData");
        JsonBrowser mergedFormats = streamingData.get("formats");
        JsonBrowser adaptiveFormats = streamingData.get("adaptiveFormats");

        String serverAbrStreamingUrl = streamingData.get("serverAbrStreamingUrl").text();
        String ustreamerConfig = json.get("playerConfig")
            .get("mediaCommonConfig")
            .get("mediaUstreamerRequestConfig")
            .get("videoPlaybackUstreamerConfig")
            .text();

        // SABR playback is only possible when both the streaming URL and ustreamer config are present.
        boolean sabrFieldsAvailable = !DataFormatTools.isNullOrEmpty(serverAbrStreamingUrl)
            && !DataFormatTools.isNullOrEmpty(ustreamerConfig);
        boolean sabrAvailable = supportsSabrPlayback() && sabrFieldsAvailable;
        boolean preferSabr = sabrAvailable && preferSabrPlayback();

        List<StreamFormat> formats = new ArrayList<>();
        boolean anyFailures = false;

        for (JsonBrowser merged : mergedFormats.values()) {
            if (!extractFormat(merged, formats, isLive, preferSabr)) {
                anyFailures = true;
            }
        }

        for (JsonBrowser adaptive : adaptiveFormats.values()) {
            if (!extractFormat(adaptive, formats, isLive, preferSabr)) {
                anyFailures = true;
            }
        }

        if (formats.isEmpty() && sabrAvailable && !preferSabr) {
            log.debug("Client '{}' has no direct formats; falling back to SABR.", getIdentifier());
            formats.clear();
            anyFailures = false;

            for (JsonBrowser merged : mergedFormats.values()) {
                if (!extractFormat(merged, formats, isLive, true)) {
                    anyFailures = true;
                }
            }

            for (JsonBrowser adaptive : adaptiveFormats.values()) {
                if (!extractFormat(adaptive, formats, isLive, true)) {
                    anyFailures = true;
                }
            }
        }

        if (formats.isEmpty() && anyFailures) {
            log.warn("Loading formats either failed to load or were skipped due to missing fields, json: {}", streamingData.format());
        }

        return new TrackFormats(formats, playerScript.url, serverAbrStreamingUrl, ustreamerConfig, getPoToken());
    }

    protected boolean preferSabrPlayback() {
        return true;
    }

    protected boolean extractFormat(JsonBrowser formatJson,
                                    List<StreamFormat> formats,
                                    boolean isLive) {
        return extractFormat(formatJson, formats, isLive, false);
    }

    protected boolean extractFormat(JsonBrowser formatJson,
                                    List<StreamFormat> formats,
                                    boolean isLive,
                                    boolean sabrAvailable) {
        if (formatJson.isNull() || !formatJson.isMap()) {
            return false;
        }

        String url = formatJson.get("url").text();
        String cipher = formatJson.get("signatureCipher").text();

        Map<String, String> cipherInfo = cipher != null
            ? decodeUrlEncodedItems(cipher, true)
            : Collections.emptyMap();

        boolean hasDirectUrl = !DataFormatTools.isNullOrEmpty(url) || !DataFormatTools.isNullOrEmpty(cipherInfo.get("url"));

        if (!hasDirectUrl && !sabrAvailable) {
            log.debug("Client '{}' is missing format URL for itag '{}'. SABR response?", getIdentifier(), formatJson.get("itag").text());
            return false;
        }

        Map<String, String> urlMap = !hasDirectUrl
            ? Collections.emptyMap()
            : (DataFormatTools.isNullOrEmpty(url)
                ? decodeUrlEncodedItems(cipherInfo.get("url"), false)
                : decodeUrlEncodedItems(url, false));

        try {
            long contentLength = formatJson.get("contentLength").asLong(CONTENT_LENGTH_UNKNOWN);
            int itag = (int) formatJson.get("itag").asLong(-1);

            // itag 18 is a legacy format which doesn't have a (valid) content length field.
            if (contentLength == CONTENT_LENGTH_UNKNOWN && !isLive && itag != 18) {
                log.debug("Track is not a live stream, but no contentLength in format {}, skipping", formatJson.format());
                return true; // this isn't considered fatal.
            }

            long lastModified = formatJson.get("lastModified").asLong(0);
            String xtags = formatJson.get("xtags").text();

            formats.add(new StreamFormat(
                ContentType.parse(formatJson.get("mimeType").text()),
                itag,
                formatJson.get("bitrate").asLong(Units.BITRATE_UNKNOWN),
                contentLength,
                formatJson.get("audioChannels").asLong(2),
                hasDirectUrl ? cipherInfo.getOrDefault("url", url) : null,
                urlMap.get("n"),
                cipherInfo.get("s"),
                cipherInfo.getOrDefault("sp", DEFAULT_SIGNATURE_KEY),
                formatJson.get("audioTrack").get("audioIsDefault").asBoolean(true),
                formatJson.get("isDrc").asBoolean(false),
                lastModified,
                xtags
            ));

            return true;
        } catch (RuntimeException e) {
            log.debug("Failed to parse format {}, skipping", formatJson, e);
            return false;
        }
    }
}
