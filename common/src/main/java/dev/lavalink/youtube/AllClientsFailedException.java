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
     * @param suppressed The exceptions that were caused client failures.
     */
    public AllClientsFailedException(@NotNull List<ClientException> suppressed) {
        super(createMessage(suppressed), Severity.SUSPICIOUS, null);
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

        for (ClientException exception : exceptions) {
            printer.println();
            printer.println();
            printer.print(exception.getFormattedMessage());
        }

        return writer.toString();
    }
}