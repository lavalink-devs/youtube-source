package dev.lavalink.youtube.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.cipher.CipherManager.CachedPlayerScript;
import dev.lavalink.youtube.track.format.StreamFormat;
import dev.lavalink.youtube.track.format.TrackFormats;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
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
    public TrackFormats loadFormats(@NotNull YoutubeAudioSourceManager source,
                                    @NotNull HttpInterface httpInterface,
                                    @NotNull String videoId) throws CannotBeLoaded, IOException {
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

        List<StreamFormat> formats = new ArrayList<>();
        boolean anyFailures = false;

        for (JsonBrowser merged : mergedFormats.values()) {
            if (!extractFormat(merged, formats, isLive)) {
                anyFailures = true;
            }
        }

        for (JsonBrowser adaptive : adaptiveFormats.values()) {
            if (!extractFormat(adaptive, formats, isLive)) {
                anyFailures = true;
            }
        }

        if (formats.isEmpty() && anyFailures) {
            log.warn("Loading formats either failed to load or were skipped due to missing fields, json: {}", streamingData.format());
        }

        return new TrackFormats(formats, playerScript.url);
    }

    protected boolean extractFormat(JsonBrowser formatJson,
                                    List<StreamFormat> formats,
                                    boolean isLive) {
        if (formatJson.isNull() || !formatJson.isMap()) {
            return false;
        }

        String url = formatJson.get("url").text();
        String cipher = formatJson.get("signatureCipher").text();

        Map<String, String> cipherInfo = cipher != null
            ? decodeUrlEncodedItems(cipher, true)
            : Collections.emptyMap();

        if (DataFormatTools.isNullOrEmpty(url) && DataFormatTools.isNullOrEmpty(cipherInfo.get("url"))) {
            log.debug("Client '{}' is missing format URL for itag '{}'. SABR response?", getIdentifier(), formatJson.get("itag").text());
            return false;
        }

        Map<String, String> urlMap = DataFormatTools.isNullOrEmpty(url)
            ? decodeUrlEncodedItems(cipherInfo.get("url"), false)
            : decodeUrlEncodedItems(url, false);

        try {
            long contentLength = formatJson.get("contentLength").asLong(CONTENT_LENGTH_UNKNOWN);
            int itag = (int) formatJson.get("itag").asLong(-1);

            // itag 18 is a legacy format which doesn't have a (valid) content length field.
            if (contentLength == CONTENT_LENGTH_UNKNOWN && !isLive && itag != 18) {
                log.debug("Track is not a live stream, but no contentLength in format {}, skipping", formatJson.format());
                return true; // this isn't considered fatal.
            }

            formats.add(new StreamFormat(
                ContentType.parse(formatJson.get("mimeType").text()),
                itag,
                formatJson.get("bitrate").asLong(Units.BITRATE_UNKNOWN),
                contentLength,
                formatJson.get("audioChannels").asLong(2),
                cipherInfo.getOrDefault("url", url),
                urlMap.get("n"),
                cipherInfo.get("s"),
                cipherInfo.getOrDefault("sp", DEFAULT_SIGNATURE_KEY),
                formatJson.get("audioTrack").get("audioIsDefault").asBoolean(true),
                formatJson.get("isDrc").asBoolean(false)
            ));

            return true;
        } catch (RuntimeException e) {
            log.debug("Failed to parse format {}, skipping", formatJson, e);
            return false;
        }
    }
}
