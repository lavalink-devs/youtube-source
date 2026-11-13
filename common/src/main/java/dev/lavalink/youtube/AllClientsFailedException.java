package dev.lavalink.youtube;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Thrown when all clients failed to load a track.
 */
public class AllClientsFailedException extends FriendlyException {
    private final List<ClientException> clientExceptions;

    /**
     * @param suppressed The exceptions that caused client failures.
     */
    public AllClientsFailedException(@NotNull List<ClientException> suppressed) {
        super(createMessage(suppressed), Severity.COMMON, null);
        this.clientExceptions = suppressed;
    }

    @NotNull
    public List<ClientException> getClientExceptions() {
        return clientExceptions;
    }

    private static String createMessage(@NotNull List<ClientException> exceptions) {
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);

        printer.format("(yts.version: %s) All clients failed to load the item.", YoutubeSource.VERSION);

        boolean hasLoginOrBotBlock = false;

        for (ClientException exception : exceptions) {
            printer.println();
            printer.println();
            printer.print(exception.getFormattedMessage());

            String msg = exception.getMessage();
            if (msg != null && (msg.contains("requires login") || msg.contains("not a bot") || msg.contains("age verification"))) {
                hasLoginOrBotBlock = true;
            }
        }

        if (hasLoginOrBotBlock) {
            printer.println();
            printer.println();
            printer.print("Safe Handler Notice: YouTube blocked anonymous playback on this IP. Enable OAuth (plugins.youtube.oauth.enabled: true) or configure poToken/visitorData in exp.yml.");
        }

        String result = writer.toString();
        // Safe cap to ensure Discord bot embed description (max 4096 chars) does not crash with ExpectedConstraintError
        if (result.length() > 3000) {
            result = result.substring(0, 2900) + "\n... (truncated for safe display)";
        }

        return result;
    }
}