package dev.lavalink.youtube.sabr;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;
import dev.lavalink.youtube.sabr.protobuf.ProtoReader;
import dev.lavalink.youtube.sabr.protobuf.ProtoWriter;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link SeekableInputStream} that plays a YouTube SABR (Server Adaptive Bitrate) audio stream.
 *
 * <p>SABR formats do not expose a plain progressive download URL. Instead, media is retrieved by
 * POSTing a protobuf {@code VideoPlaybackAbrRequest} to a server ABR streaming URL and parsing the
 * UMP-formatted response into media segments. This class runs that request loop lazily as bytes are
 * consumed, concatenating the initialization segment followed by each media segment in order to
 * reconstruct a continuous (fragmented MP4 or WebM) byte stream that Lavaplayer's container readers
 * can consume.</p>
 *
 * <p>The reconstructed bytes are retained in memory so that arbitrary (including backwards) seeks are
 * supported without re-downloading. For audio this is a few megabytes at most.</p>
 */
public class SabrStream extends SeekableInputStream {
    private static final Logger log = LoggerFactory.getLogger(SabrStream.class);

    private static final long MAX_SKIP_DISTANCE = 512L * 1024L;
    private static final int MAX_REQUEST_RETRIES = 3;
    private static final int ENABLED_TRACK_TYPES_AUDIO_ONLY = 1;
    // A hard cap to guard against pathological request loops that never make progress.
    private static final int MAX_REQUESTS = 10000;

    private final HttpInterface httpInterface;
    private final byte[] ustreamerConfig;
    private final byte[] poToken;
    private final SabrClientInfo clientInfo;
    private final FormatId audioFormatId;
    private final boolean drcEnabled;

    private final ChunkedByteBuffer buffer = new ChunkedByteBuffer();

    private URI serverAbrStreamingUrl;
    private long readPos;
    private boolean finished;
    private int requestNumber;
    private int requestCount;

    private long durationMs;
    private long downloadedDurationMs;

    // Per-stream SABR state.
    private boolean formatInitialized;
    private final List<MediaHeader> lastMediaHeaders = new ArrayList<>();
    private final Set<Integer> downloadedSegments = new HashSet<>();
    private List<BufferedRangeData> cachedBufferedRanges;
    private byte[] playbackCookie;
    private int streamProtectionStatus;

    // SABR context state (mainly for ad handling).
    private final Map<Integer, byte[]> sabrContextValues = new HashMap<>();
    private final Set<Integer> activeSabrContextTypes = new HashSet<>();

    // Transient per-request state.
    private final Map<Integer, PartialSegment> partialSegmentQueue = new HashMap<>();

    public SabrStream(@NotNull HttpInterface httpInterface,
                      @NotNull URI serverAbrStreamingUrl,
                      @NotNull byte[] ustreamerConfig,
                      @Nullable byte[] poToken,
                      @NotNull SabrClientInfo clientInfo,
                      @NotNull FormatId audioFormatId,
                      boolean drcEnabled,
                      long contentLength,
                      long durationMs) {
        super(contentLength, MAX_SKIP_DISTANCE);
        this.httpInterface = httpInterface;
        this.serverAbrStreamingUrl = serverAbrStreamingUrl;
        this.ustreamerConfig = ustreamerConfig;
        this.poToken = poToken;
        this.clientInfo = clientInfo;
        this.audioFormatId = audioFormatId;
        this.drcEnabled = drcEnabled;
        this.durationMs = durationMs > 0 ? durationMs : Long.MAX_VALUE;
    }

    //<editor-fold desc="SeekableInputStream API">
    @Override
    public long getPosition() {
        return readPos;
    }

    @Override
    protected void seekHard(long position) throws IOException {
        ensureAvailable(position);
        readPos = Math.min(position, buffer.length());
    }

    @Override
    public boolean canSeekHard() {
        return true;
    }

    @Override
    public List<AudioTrackInfoProvider> getTrackInfoProviders() {
        return Collections.emptyList();
    }

    @Override
    public int read() throws IOException {
        if (!ensureAvailable(readPos + 1)) {
            return -1;
        }

        int result = buffer.get(readPos) & 0xFF;
        readPos++;
        return result;
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }

        if (!ensureAvailable(readPos + 1)) {
            return -1;
        }

        int available = (int) Math.min(len, buffer.length() - readPos);
        buffer.copyTo(readPos, b, off, available);
        readPos += available;
        return available;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }

        if (!ensureAvailable(readPos + 1)) {
            return 0;
        }

        long skipped = Math.min(n, buffer.length() - readPos);
        readPos += skipped;
        return skipped;
    }

    @Override
    public void close() {
        // Nothing to release; the HTTP interface lifecycle is owned by the caller.
    }
    //</editor-fold>

    /**
     * Ensures at least {@code target} bytes have been downloaded (or the stream has finished),
     * driving SABR requests as needed.
     *
     * @return {@code true} if a byte is available at the current read position.
     */
    private boolean ensureAvailable(long target) throws IOException {
        while (!finished && buffer.length() < target) {
            fetchNextBatch();
        }

        return readPos < buffer.length();
    }

    private void fetchNextBatch() throws IOException {
        if (downloadedDurationMs >= durationMs || requestCount >= MAX_REQUESTS) {
            finished = true;
            return;
        }

        long bytesBefore = buffer.length();
        long durationBefore = downloadedDurationMs;
        boolean redirected = executeRequestWithRetry();

        if (!redirected && buffer.length() == bytesBefore && downloadedDurationMs == durationBefore) {
            // No progress was made and we weren't redirected; assume the stream has ended.
            finished = true;
        }

        if (downloadedDurationMs >= durationMs) {
            finished = true;
        }
    }

    private boolean executeRequestWithRetry() throws IOException {
        IOException lastError = null;

        for (int attempt = 0; attempt <= MAX_REQUEST_RETRIES; attempt++) {
            try {
                return executeRequest();
            } catch (SabrFatalException e) {
                throw new IOException(e.getMessage(), e);
            } catch (IOException e) {
                lastError = e;
                log.debug("SABR request attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        throw lastError != null ? lastError : new IOException("SABR request failed");
    }

    /**
     * Performs a single SABR request/response cycle.
     *
     * @return {@code true} if the response only redirected us to a new URL (no media).
     */
    private boolean executeRequest() throws IOException {
        partialSegmentQueue.clear();
        requestCount++;

        if (cachedBufferedRanges == null) {
            cachedBufferedRanges = buildBufferedRanges();
        }

        byte[] body = buildRequestBody();
        URI url = withRequestNumber(serverAbrStreamingUrl, requestNumber++);

        HttpPost request = new HttpPost(url);
        request.setHeader("Content-Type", "application/x-protobuf");
        request.setHeader("Accept", "application/vnd.yt-ump");
        request.setEntity(new ByteArrayEntity(body));

        byte[] responseBytes;
        int statusCode;

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            statusCode = response.getStatusLine().getStatusCode();

            if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw new IOException("Not success status code: " + statusCode);
            }

            responseBytes = EntityUtils.toByteArray(response.getEntity());
        }

        if (responseBytes == null || responseBytes.length == 0) {
            throw new IOException("Received empty SABR response from server");
        }

        String urlBefore = serverAbrStreamingUrl.toString();
        long bytesBefore = buffer.length();

        try {
            new UmpReader(responseBytes).read(this::handlePart);
        } catch (SabrFatalException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse SABR response", e);
        }

        boolean redirected = !serverAbrStreamingUrl.toString().equals(urlBefore) && buffer.length() == bytesBefore;

        // Once media headers have been processed the cached buffered ranges are stale and must be rebuilt.
        if (buffer.length() != bytesBefore) {
            cachedBufferedRanges = null;
        }

        return redirected;
    }

    //<editor-fold desc="UMP part handling">
    private void handlePart(int type, @NotNull byte[] data) {
        try {
            dispatchPart(type, data);
        } catch (SabrFatalException e) {
            throw e;
        } catch (RuntimeException e) {
            // A single malformed metadata part should not abort the whole stream. Media parts are
            // handled separately (a mismatched segment is dropped and re-requested).
            log.debug("Failed to handle SABR part type {} ({} bytes): {} [{}]",
                type, data.length, e.getMessage(), hexPreview(data));
        }
    }

    private void dispatchPart(int type, @NotNull byte[] data) {
        switch (type) {
            case UmpReader.FORMAT_INITIALIZATION_METADATA:
                handleFormatInitialization(data);
                break;
            case UmpReader.NEXT_REQUEST_POLICY:
                handleNextRequestPolicy(data);
                break;
            case UmpReader.SABR_REDIRECT:
                handleSabrRedirect(data);
                break;
            case UmpReader.SABR_ERROR:
                handleSabrError(data);
                break;
            case UmpReader.SABR_CONTEXT_UPDATE:
                handleSabrContextUpdate(data);
                break;
            case UmpReader.SABR_CONTEXT_SENDING_POLICY:
                handleSabrContextSendingPolicy(data);
                break;
            case UmpReader.STREAM_PROTECTION_STATUS:
                handleStreamProtectionStatus(data);
                break;
            case UmpReader.RELOAD_PLAYER_RESPONSE:
                throw new SabrFatalException("Player response reload requested by server");
            case UmpReader.MEDIA_HEADER:
                handleMediaHeader(data);
                break;
            case UmpReader.MEDIA:
                handleMedia(data);
                break;
            case UmpReader.MEDIA_END:
                handleMediaEnd(data);
                break;
            default:
                break;
        }
    }

    private void handleFormatInitialization(@NotNull byte[] data) {
        FormatInitializationMetadata metadata = FormatInitializationMetadata.parse(new ProtoReader(data));
        formatInitialized = true;

        long computed = metadata.durationMs();
        if (computed > 0) {
            durationMs = computed;
        }

        log.debug("Initialized SABR format {} ({})", metadata.formatKey(), metadata.mimeType);
    }

    private void handleNextRequestPolicy(@NotNull byte[] data) {
        ProtoReader reader = new ProtoReader(data);
        int tag;
        while ((tag = reader.readTag()) != -1) {
            if (reader.getFieldNumber() == 7) {
                // playback_cookie, stored as raw bytes to echo back in the next request.
                playbackCookie = reader.readBytes();
            } else {
                reader.skip();
            }
        }
    }

    private void handleSabrRedirect(@NotNull byte[] data) {
        ProtoReader reader = new ProtoReader(data);
        int tag;
        while ((tag = reader.readTag()) != -1) {
            if (reader.getFieldNumber() == 1) {
                String url = reader.readString();
                try {
                    serverAbrStreamingUrl = new URI(url);
                    log.debug("SABR redirect to {}", url);
                } catch (URISyntaxException e) {
                    log.warn("Received invalid SABR redirect URL: {}", url);
                }
            } else {
                reader.skip();
            }
        }
    }

    private void handleSabrError(@NotNull byte[] data) {
        ProtoReader reader = new ProtoReader(data);
        String errorType = null;
        int code = 0;

        int tag;
        while ((tag = reader.readTag()) != -1) {
            switch (reader.getFieldNumber()) {
                case 1:
                    errorType = reader.readString();
                    break;
                case 2:
                    code = (int) reader.readVarint();
                    break;
                default:
                    reader.skip();
                    break;
            }
        }

        throw new SabrFatalException("SABR error: " + errorType + " (" + code + ")");
    }

    private void handleStreamProtectionStatus(@NotNull byte[] data) {
        ProtoReader reader = new ProtoReader(data);
        int tag;
        while ((tag = reader.readTag()) != -1) {
            if (reader.getFieldNumber() == 1) {
                streamProtectionStatus = (int) reader.readVarint();
            } else {
                reader.skip();
            }
        }

        if (streamProtectionStatus == 3) {
            throw new SabrFatalException("Cannot proceed with SABR stream: attestation required (poToken needed)");
        } else if (streamProtectionStatus == 2) {
            log.debug("SABR attestation pending");
        }
    }

    private void handleSabrContextUpdate(@NotNull byte[] data) {
        ProtoReader reader = new ProtoReader(data);
        int type = -1;
        byte[] value = null;
        int writePolicy = 0;
        boolean sendByDefault = false;

        int tag;
        while ((tag = reader.readTag()) != -1) {
            switch (reader.getFieldNumber()) {
                case 1:
                    type = (int) reader.readVarint();
                    break;
                case 3:
                    value = reader.readBytes();
                    break;
                case 4:
                    sendByDefault = reader.readVarint() != 0;
                    break;
                case 5:
                    writePolicy = (int) reader.readVarint();
                    break;
                default:
                    reader.skip();
                    break;
            }
        }

        if (type == -1 || value == null || value.length == 0) {
            return;
        }

        // KEEP_EXISTING == 2: don't overwrite an existing context of this type.
        if (writePolicy == 2 && sabrContextValues.containsKey(type)) {
            return;
        }

        sabrContextValues.put(type, value);

        if (sendByDefault) {
            activeSabrContextTypes.add(type);
        }
    }

    private void handleSabrContextSendingPolicy(@NotNull byte[] data) {
        ProtoReader reader = new ProtoReader(data);
        int tag;
        while ((tag = reader.readTag()) != -1) {
            int value = (int) reader.readVarint();
            switch (reader.getFieldNumber()) {
                case 1:
                    activeSabrContextTypes.add(value);
                    break;
                case 2:
                    activeSabrContextTypes.remove(value);
                    break;
                case 3:
                    sabrContextValues.remove(value);
                    activeSabrContextTypes.remove(value);
                    break;
                default:
                    break;
            }
        }
    }

    private void handleMediaHeader(@NotNull byte[] data) {
        MediaHeader header = MediaHeader.parse(new ProtoReader(data));
        int segmentNumber = header.segmentNumber();

        if (downloadedSegments.contains(segmentNumber)) {
            // Already have this segment; a duplicate header can be safely ignored.
            return;
        }

        partialSegmentQueue.put(header.headerId, new PartialSegment(header, segmentNumber));
    }

    private void handleMedia(@NotNull byte[] data) {
        if (data.length < 1) {
            return;
        }

        int headerId = data[0] & 0xFF;
        PartialSegment segment = partialSegmentQueue.get(headerId);

        if (segment == null) {
            return;
        }

        segment.data.write(data, 1, data.length - 1);
    }

    private void handleMediaEnd(@NotNull byte[] data) {
        if (data.length < 1) {
            return;
        }

        int headerId = data[0] & 0xFF;
        PartialSegment segment = partialSegmentQueue.remove(headerId);

        if (segment == null) {
            return;
        }

        byte[] segmentBytes = segment.data.toByteArray();

        if (segment.header.contentLength >= 0 && segmentBytes.length != segment.header.contentLength) {
            log.warn("SABR segment {} content length mismatch (expected {}, got {}); discarding",
                segment.segmentNumber, segment.header.contentLength, segmentBytes.length);
            return;
        }

        buffer.append(segmentBytes);
        downloadedSegments.add(segment.segmentNumber);
        downloadedDurationMs += segment.header.effectiveDurationMs();
        lastMediaHeaders.add(segment.header);
    }
    //</editor-fold>

    //<editor-fold desc="Request building">
    private byte[] buildRequestBody() {
        ProtoWriter request = new ProtoWriter();

        // client_abr_state (field 1)
        ProtoWriter abrState = new ProtoWriter();
        abrState.writeVarint(28, Math.max(0, downloadedDurationMs)); // player_time_ms
        abrState.writeFloat(35, 1.0f); // playback_rate
        abrState.writeVarint(34, 1); // visibility
        abrState.writeVarint(40, ENABLED_TRACK_TYPES_AUDIO_ONLY); // enabled_track_types_bitfield
        if (drcEnabled) {
            abrState.writeBool(46, true); // drc_enabled
        }
        request.writeMessage(1, abrState);

        // selected_format_ids (field 2) - only sent once the format has been initialized.
        if (formatInitialized) {
            audioFormatId.writeTo(request, 2);
        }

        // buffered_ranges (field 3)
        if (cachedBufferedRanges != null) {
            for (BufferedRangeData range : cachedBufferedRanges) {
                writeBufferedRange(request, range);
            }
        }

        // video_playback_ustreamer_config (field 5)
        request.writeBytes(5, ustreamerConfig);

        // preferred_audio_format_ids (field 16)
        audioFormatId.writeTo(request, 16);

        // streamer_context (field 19)
        request.writeMessage(19, buildStreamerContext());

        return request.toByteArray();
    }

    private void writeBufferedRange(@NotNull ProtoWriter request, @NotNull BufferedRangeData range) {
        ProtoWriter br = new ProtoWriter();
        range.formatId.writeTo(br, 1);
        br.writeVarint(2, range.startTimeMs);
        br.writeVarint(3, range.durationMs);
        br.writeVarint(4, range.startSegmentIndex);
        br.writeVarint(5, range.endSegmentIndex);
        request.writeMessage(3, br);
    }

    private ProtoWriter buildStreamerContext() {
        ProtoWriter context = new ProtoWriter();

        // client_info (field 1)
        clientInfo.writeTo(context, 1);

        // po_token (field 2)
        if (poToken != null) {
            context.writeBytes(2, poToken);
        }

        // playback_cookie (field 3)
        if (playbackCookie != null) {
            context.writeBytes(3, playbackCookie);
        }

        // sabr_contexts (field 5) / unsent_sabr_contexts (field 6)
        for (Map.Entry<Integer, byte[]> entry : sabrContextValues.entrySet()) {
            if (activeSabrContextTypes.contains(entry.getKey())) {
                ProtoWriter sabrContext = new ProtoWriter();
                sabrContext.writeVarint(1, entry.getKey());
                sabrContext.writeBytes(2, entry.getValue());
                context.writeMessage(5, sabrContext);
            } else {
                context.writeVarint(6, entry.getKey());
            }
        }

        return context;
    }

    @NotNull
    private List<BufferedRangeData> buildBufferedRanges() {
        List<BufferedRangeData> ranges = new ArrayList<>();

        if (!lastMediaHeaders.isEmpty()) {
            long duration = 0;
            for (MediaHeader header : lastMediaHeaders) {
                duration += header.effectiveDurationMs();
            }

            MediaHeader first = lastMediaHeaders.get(0);
            MediaHeader last = lastMediaHeaders.get(lastMediaHeaders.size() - 1);

            BufferedRangeData range = new BufferedRangeData();
            range.formatId = audioFormatId;
            range.startTimeMs = first.startMs;
            range.durationMs = duration;
            range.startSegmentIndex = Math.max(1, first.sequenceNumber);
            range.endSegmentIndex = Math.max(1, last.sequenceNumber);
            ranges.add(range);

            lastMediaHeaders.clear();
        }

        return ranges;
    }

    @NotNull
    private static String hexPreview(@NotNull byte[] data) {
        int count = Math.min(data.length, 64);
        StringBuilder sb = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            sb.append(Character.forDigit((data[i] >> 4) & 0xF, 16));
            sb.append(Character.forDigit(data[i] & 0xF, 16));
        }
        if (data.length > count) {
            sb.append("...");
        }
        return sb.toString();
    }

    @NotNull
    private static URI withRequestNumber(@NotNull URI url, int requestNumber) throws IOException {
        try {
            return new URIBuilder(url).setParameter("rn", Integer.toString(requestNumber)).build();
        } catch (URISyntaxException e) {
            throw new IOException("Failed to build SABR request URL", e);
        }
    }
    //</editor-fold>

    private static class PartialSegment {
        final MediaHeader header;
        final int segmentNumber;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();

        PartialSegment(@NotNull MediaHeader header, int segmentNumber) {
            this.header = header;
            this.segmentNumber = segmentNumber;
        }
    }

    private static class BufferedRangeData {
        FormatId formatId;
        long startTimeMs;
        long durationMs;
        int startSegmentIndex;
        int endSegmentIndex;
    }

    /**
     * Signals a non-recoverable SABR protocol condition that should abort the current stream
     * without retrying (e.g. an explicit server error or a required attestation we cannot satisfy).
     */
    private static class SabrFatalException extends RuntimeException {
        SabrFatalException(String message) {
            super(message);
        }
    }

    /**
     * A growable byte buffer composed of discrete chunks, supporting random-access reads. Avoids the
     * repeated full-array reallocation of a single backing array as the stream grows.
     */
    private static class ChunkedByteBuffer {
        private final List<byte[]> chunks = new ArrayList<>();
        private long length;

        void append(@NotNull byte[] chunk) {
            if (chunk.length == 0) {
                return;
            }

            chunks.add(chunk);
            length += chunk.length;
        }

        long length() {
            return length;
        }

        byte get(long position) {
            long remaining = position;
            for (byte[] chunk : chunks) {
                if (remaining < chunk.length) {
                    return chunk[(int) remaining];
                }
                remaining -= chunk.length;
            }
            throw new IndexOutOfBoundsException("Position " + position + " out of bounds (length " + length + ")");
        }

        void copyTo(long position, @NotNull byte[] dest, int destOffset, int count) {
            long remaining = position;
            int written = 0;

            for (byte[] chunk : chunks) {
                if (written >= count) {
                    break;
                }

                if (remaining >= chunk.length) {
                    remaining -= chunk.length;
                    continue;
                }

                int chunkOffset = (int) remaining;
                int toCopy = Math.min(count - written, chunk.length - chunkOffset);
                System.arraycopy(chunk, chunkOffset, dest, destOffset + written, toCopy);
                written += toCopy;
                remaining = 0;
            }
        }
    }
}