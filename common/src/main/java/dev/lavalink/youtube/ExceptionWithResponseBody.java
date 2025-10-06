package dev.lavalink.youtube;

public class ExceptionWithResponseBody extends RuntimeException {
    private final String responseBody;

    public ExceptionWithResponseBody(String message, String responseBody) {
        super(message);
        this.responseBody = responseBody;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
