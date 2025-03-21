package dev.lavalink.youtube.plugin;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier;
import dev.lavalink.youtube.YoutubeAudioPlaylist;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class YoutubeAudioPluginInfoModifier implements AudioPluginInfoModifier {

  @Override
  public JsonObject modifyAudioPlaylistPluginInfo(@NotNull AudioPlaylist playlist) {
    if (playlist instanceof YoutubeAudioPlaylist extendedPlaylist) {
      return new JsonObject(Map.of(
        "url", JsonElementKt.JsonPrimitive(extendedPlaylist.getUrl())
      ));
    }
    return null;
  }

}
