package dev.lavalink.youtube.sabr;

import dev.lavalink.youtube.sabr.protobuf.ProtoWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The client identity sent within a SABR request's streamer context. The {@code clientName}
 * is the numeric InnerTube client name (e.g. {@code 1} for WEB, {@code 2} for MWEB,
 * {@code 56} for WEB_EMBEDDED_PLAYER).
 */
public class SabrClientInfo {
    // Maps InnerTube client name strings to their numeric client name IDs.
    private static final Map<String, Integer> CLIENT_NAME_IDS = new HashMap<>();

    static {
        CLIENT_NAME_IDS.put("WEB", 1);
        CLIENT_NAME_IDS.put("MWEB", 2);
        CLIENT_NAME_IDS.put("ANDROID", 3);
        CLIENT_NAME_IDS.put("IOS", 5);
        CLIENT_NAME_IDS.put("TVHTML5", 7);
        CLIENT_NAME_IDS.put("ANDROID_MUSIC", 21);
        CLIENT_NAME_IDS.put("ANDROID_VR", 28);
        CLIENT_NAME_IDS.put("WEB_EMBEDDED_PLAYER", 56);
        CLIENT_NAME_IDS.put("WEB_REMIX", 67);
        CLIENT_NAME_IDS.put("TVHTML5_SIMPLY", 75);
        CLIENT_NAME_IDS.put("TVHTML5_SIMPLY_EMBEDDED_PLAYER", 85);
    }

    public final int clientName;
    public final String clientVersion;
    public final String osName;
    public final String osVersion;

    public SabrClientInfo(int clientName,
                          @NotNull String clientVersion,
                          @Nullable String osName,
                          @Nullable String osVersion) {
        this.clientName = clientName;
        this.clientVersion = clientVersion;
        this.osName = osName;
        this.osVersion = osVersion;
    }

    /**
     * Builds SABR client info for a given InnerTube client name and version.
     *
     * @param innerTubeName The InnerTube client name string (e.g. "WEB", "TVHTML5").
     * @param clientVersion The client version.
     * @return The client info, or {@code null} if the client name is unknown or the version is missing.
     */
    @Nullable
    public static SabrClientInfo forClient(@Nullable String innerTubeName, @Nullable String clientVersion) {
        if (innerTubeName == null || clientVersion == null) {
            return null;
        }

        // TODO: this should probably be moved into each client implementation in the form of `setClientOrdinal()`
        Integer clientName = CLIENT_NAME_IDS.get(innerTubeName);

        if (clientName == null) {
            return null;
        }

        return new SabrClientInfo(clientName, clientVersion, null, null);
    }

    public void writeTo(@NotNull ProtoWriter writer, int field) {
        ProtoWriter info = new ProtoWriter();
        info.writeVarint(16, clientName);
        info.writeString(17, clientVersion);

        if (osName != null) {
            info.writeString(18, osName);
        }

        if (osVersion != null) {
            info.writeString(19, osVersion);
        }

        writer.writeMessage(field, info);
    }
}