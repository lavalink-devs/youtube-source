package dev.lavalink.youtube.plugin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class YoutubeRestHandlerBinaryCompatibilityTest {
    private static final String HANDLER_CLASS =
        "dev/lavalink/youtube/plugin/YoutubeRestHandler.class";
    private static final String CROSS_VERSION_CONSTRUCTOR =
        "(ILjava/lang/String;Ljava/lang/Throwable;)V";
    private static final String SPRING_5_TWO_ARGUMENT_CONSTRUCTOR =
        "(Lorg/springframework/http/HttpStatus;Ljava/lang/String;)V";
    private static final String SPRING_5_THREE_ARGUMENT_CONSTRUCTOR =
        "(Lorg/springframework/http/HttpStatus;Ljava/lang/String;Ljava/lang/Throwable;)V";
    private static final String SPRING_6_TWO_ARGUMENT_CONSTRUCTOR =
        "(Lorg/springframework/http/HttpStatusCode;Ljava/lang/String;)V";
    private static final String SPRING_6_THREE_ARGUMENT_CONSTRUCTOR =
        "(Lorg/springframework/http/HttpStatusCode;Ljava/lang/String;Ljava/lang/Throwable;)V";

    @Test
    public void responseStatusExceptionsUseCrossVersionConstructor() throws IOException {
        String constantPool = readHandlerClassAsSingleByteText();

        Assertions.assertTrue(constantPool.contains(CROSS_VERSION_CONSTRUCTOR),
            "Expected the raw status code ResponseStatusException constructor.");
        Assertions.assertFalse(constantPool.contains(SPRING_5_TWO_ARGUMENT_CONSTRUCTOR),
            "Spring 5's HttpStatus constructor is not available on Spring 6.");
        Assertions.assertFalse(constantPool.contains(SPRING_5_THREE_ARGUMENT_CONSTRUCTOR),
            "Spring 5's HttpStatus constructor is not available on Spring 6.");
        Assertions.assertFalse(constantPool.contains(SPRING_6_TWO_ARGUMENT_CONSTRUCTOR),
            "Spring 6's HttpStatusCode constructor is not available on Spring 5.");
        Assertions.assertFalse(constantPool.contains(SPRING_6_THREE_ARGUMENT_CONSTRUCTOR),
            "Spring 6's HttpStatusCode constructor is not available on Spring 5.");
    }

    private static String readHandlerClassAsSingleByteText() throws IOException {
        ClassLoader classLoader = YoutubeRestHandlerBinaryCompatibilityTest.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(HANDLER_CLASS)) {
            Assertions.assertNotNull(input, "Compiled YoutubeRestHandler class was not found.");
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
