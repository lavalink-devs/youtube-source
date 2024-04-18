package dev.lavalink.youtube.cipher;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.engine.RhinoScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;

/**
 * Handles parsing and caching of signature ciphers
 */
@SuppressWarnings({"RegExpRedundantEscape", "RegExpUnnecessaryNonCapturingGroup"})
public class SignatureCipherManager {
  private static final Logger log = LoggerFactory.getLogger(SignatureCipherManager.class);

  private static final String VARIABLE_PART = "[a-zA-Z_\\$][a-zA-Z_0-9]*";
  private static final String VARIABLE_PART_DEFINE = "\\\"?" + VARIABLE_PART + "\\\"?";
  private static final String BEFORE_ACCESS = "(?:\\[\\\"|\\.)";
  private static final String AFTER_ACCESS = "(?:\\\"\\]|)";
  private static final String VARIABLE_PART_ACCESS = BEFORE_ACCESS + VARIABLE_PART + AFTER_ACCESS;
  private static final String REVERSE_PART = ":function\\(a\\)\\{(?:return )?a\\.reverse\\(\\)\\}";
  private static final String SLICE_PART = ":function\\(a,b\\)\\{return a\\.slice\\(b\\)\\}";
  private static final String SPLICE_PART = ":function\\(a,b\\)\\{a\\.splice\\(0,b\\)\\}";
  private static final String SWAP_PART = ":function\\(a,b\\)\\{" +
      "var c=a\\[0\\];a\\[0\\]=a\\[b%a\\.length\\];a\\[b(?:%a.length|)\\]=c(?:;return a)?\\}";

  private static final Pattern functionPattern = Pattern.compile(
      "function(?: " + VARIABLE_PART + ")?\\(a\\)\\{" +
      "a=a\\.split\\(\"\"\\);\\s*" +
      "((?:(?:a=)?" + VARIABLE_PART + VARIABLE_PART_ACCESS + "\\(a,\\d+\\);)+)" +
      "return a\\.join\\(\"\"\\)" +
      "\\}"
  );

  private static final Pattern actionsPattern = Pattern.compile(
      "var (" + VARIABLE_PART + ")=\\{((?:(?:" +
      VARIABLE_PART_DEFINE + REVERSE_PART + "|" +
      VARIABLE_PART_DEFINE + SLICE_PART + "|" +
      VARIABLE_PART_DEFINE + SPLICE_PART + "|" +
      VARIABLE_PART_DEFINE + SWAP_PART +
      "),?\\n?)+)\\};"
  );

  private static final String PATTERN_PREFIX = "(?:^|,)\\\"?(" + VARIABLE_PART + ")\\\"?";

  private static final Pattern reversePattern = Pattern.compile(PATTERN_PREFIX + REVERSE_PART, Pattern.MULTILINE);
  private static final Pattern slicePattern = Pattern.compile(PATTERN_PREFIX + SLICE_PART, Pattern.MULTILINE);
  private static final Pattern splicePattern = Pattern.compile(PATTERN_PREFIX + SPLICE_PART, Pattern.MULTILINE);
  private static final Pattern swapPattern = Pattern.compile(PATTERN_PREFIX + SWAP_PART, Pattern.MULTILINE);
  private static final Pattern timestampPattern = Pattern.compile("(signatureTimestamp|sts):(\\d+)");
  private static final Pattern nFunctionPattern = Pattern.compile(
      "function\\(\\s*(\\w+)\\s*\\)\\s*\\{var" +
          "\\s*(\\w+)=\\1\\.split\\(\"\"\\),\\s*(\\w+)=(\\[.*?\\]);\\s*\\3\\[\\d+\\]" +
          "(.*?try)(\\{.*?\\})catch\\(\\s*(\\w+)\\s*\\)\\s*\\" +
          "{\\s*return\"enhanced_except_([A-z0-9-]+)\"\\s*\\+\\s*\\1\\s*}\\s*return\\s*\\2\\.join\\(\"\"\\)\\};", Pattern.DOTALL
  );

  private final ConcurrentMap<String, SignatureCipher> cipherCache;
  private final Set<String> dumpedScriptUrls;
  private final ScriptEngine scriptEngine;
  private final Object cipherLoadLock;

  protected volatile CachedPlayerScript cachedPlayerScript;

  /**
   * Create a new signature cipher manager
   */
  public SignatureCipherManager() {
    this.cipherCache = new ConcurrentHashMap<>();
    this.dumpedScriptUrls = new HashSet<>();
    this.scriptEngine = new RhinoScriptEngineFactory().getScriptEngine();
    this.cipherLoadLock = new Object();
  }

  /**
   * Produces a valid playback URL for the specified track
   * @param httpInterface HTTP interface to use
   * @param playerScript Address of the script which is used to decipher signatures
   * @param format The track for which to get the URL
   * @return Valid playback URL
   * @throws IOException On network IO error
   */
  @NotNull
  public URI resolveFormatUrl(@NotNull HttpInterface httpInterface, @NotNull String playerScript,
                              @NotNull StreamFormat format) throws IOException {
    String signature = format.getSignature();
    String nParameter = format.getNParameter();
    URI initialUrl = format.getUrl();

    URIBuilder uri = new URIBuilder(initialUrl);
    SignatureCipher cipher = getCipherScript(httpInterface, playerScript);

    if (!DataFormatTools.isNullOrEmpty(signature)) {
      uri.setParameter(format.getSignatureKey(), cipher.apply(signature));
    }

    if (!DataFormatTools.isNullOrEmpty(nParameter)) {
      try {
        uri.setParameter("n", cipher.transform(nParameter, scriptEngine));
      } catch (ScriptException | NoSuchMethodException e) {
        // URLs can still be played without a resolved n parameter. It just means they're
        // throttled. But we shouldn't throw an exception anyway as it's not really fatal.
        dumpProblematicScript(cipherCache.get(playerScript).rawScript, playerScript, "Can't transform n parameter " + nParameter + " with " + cipher.nFunction + " n function");
      }
    }

    try {
      return uri.build(); // setParameter("ratebypass", "yes")  -- legacy parameter that will give 403 if tampered with.
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private CachedPlayerScript getPlayerScript(@NotNull HttpInterface httpInterface) {
    synchronized (cipherLoadLock) {
      try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/embed/"))) {
        HttpClientTools.assertSuccessWithContent(response, "fetch player script (embed)");

        String responseText = EntityUtils.toString(response.getEntity());
        String scriptUrl = DataFormatTools.extractBetween(responseText, "\"jsUrl\":\"", "\"");

        if (scriptUrl == null) {
          throw throwWithDebugInfo(log, null, "no jsUrl found", "html", responseText);
        }

        return (cachedPlayerScript = new CachedPlayerScript(scriptUrl));
      } catch (IOException e) {
        throw ExceptionTools.toRuntimeException(e);
      }
    }
  }

  public CachedPlayerScript getCachedPlayerScript(@NotNull HttpInterface httpInterface) {
    if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
      synchronized (cipherLoadLock) {
        if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
          return getPlayerScript(httpInterface);
        }
      }
    }

    return cachedPlayerScript;
  }

  public SignatureCipher getCipherScript(@NotNull HttpInterface httpInterface,
                                         @NotNull String cipherScriptUrl) throws IOException {
    SignatureCipher cipherKey = cipherCache.get(cipherScriptUrl);

    if (cipherKey == null) {
      synchronized (cipherLoadLock) {
        log.debug("Parsing player script {}", cipherScriptUrl);

        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(parseTokenScriptUrl(cipherScriptUrl)))) {
          int statusCode = response.getStatusLine().getStatusCode();

          if (!HttpClientTools.isSuccessWithContent(statusCode)) {
            throw new IOException("Received non-success response code " + statusCode + " from script url " +
                cipherScriptUrl + " ( " + parseTokenScriptUrl(cipherScriptUrl) + " )");
          }

          cipherKey = extractFromScript(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8), cipherScriptUrl);
          cipherCache.put(cipherScriptUrl, cipherKey);
        }
      }
    }

    return cipherKey;
  }

  private List<String> getQuotedFunctions(@Nullable String... functionNames) {
    return Stream.of(functionNames)
        .filter(Objects::nonNull)
        .map(Pattern::quote)
        .collect(Collectors.toList());
  }

  private void dumpProblematicScript(@NotNull String script, @NotNull String sourceUrl,
                                     @NotNull String issue) {
    if (!dumpedScriptUrls.add(sourceUrl)) {
      return;
    }

    try {
      Path path = Files.createTempFile("lavaplayer-yt-player-script", ".js");
      Files.write(path, script.getBytes(StandardCharsets.UTF_8));

      log.error("Problematic YouTube player script {} detected (issue detected with script: {}). Dumped to {}",
          sourceUrl, issue, path.toAbsolutePath());
    } catch (Exception e) {
      log.error("Failed to dump problematic YouTube player script {} (issue detected with script: {})", sourceUrl, issue);
    }
  }

  private SignatureCipher extractFromScript(@NotNull String script, @NotNull String sourceUrl) {
    Matcher actions = actionsPattern.matcher(script);
    Matcher nFunctionMatcher = nFunctionPattern.matcher(script);
    Matcher scriptTimestamp = timestampPattern.matcher(script);

    if (!actions.find()) {
      dumpProblematicScript(script, sourceUrl, "no actions match");
      throw new IllegalStateException("Must find action functions from script: " + sourceUrl);
    }

    String actionBody = actions.group(2);

    String reverseKey = extractDollarEscapedFirstGroup(reversePattern, actionBody);
    String slicePart = extractDollarEscapedFirstGroup(slicePattern, actionBody);
    String splicePart = extractDollarEscapedFirstGroup(splicePattern, actionBody);
    String swapKey = extractDollarEscapedFirstGroup(swapPattern, actionBody);

    Pattern extractor = Pattern.compile(
        "(?:a=)?" + Pattern.quote(actions.group(1)) + BEFORE_ACCESS + "(" +
            String.join("|", getQuotedFunctions(reverseKey, slicePart, splicePart, swapKey)) +
            ")" + AFTER_ACCESS + "\\(a,(\\d+)\\)"
    );

    Matcher functions = functionPattern.matcher(script);
    if (!functions.find()) {
      dumpProblematicScript(script, sourceUrl, "no decipher function match");
      throw new IllegalStateException("Must find decipher function from script.");
    }

    Matcher matcher = extractor.matcher(functions.group(1));

    if (!scriptTimestamp.find()) {
      dumpProblematicScript(script, sourceUrl, "no timestamp match");
      throw new IllegalStateException("Must find timestamp from script: " + sourceUrl);
    }

    String nFunction = "";

    if (nFunctionMatcher.find()) {
      nFunction = nFunctionMatcher.group(0);
    } else {
      // Don't throw any exceptions here since if n function is not extracted audio still can be played
      dumpProblematicScript(script, sourceUrl, "no n function match");
    }

    SignatureCipher cipherKey = new SignatureCipher(nFunction, scriptTimestamp.group(2), script);

    while (matcher.find()) {
      String type = matcher.group(1);

      if (type.equals(swapKey)) {
        cipherKey.addOperation(new CipherOperation(CipherOperationType.SWAP, Integer.parseInt(matcher.group(2))));
      } else if (type.equals(reverseKey)) {
        cipherKey.addOperation(new CipherOperation(CipherOperationType.REVERSE, 0));
      } else if (type.equals(slicePart)) {
        cipherKey.addOperation(new CipherOperation(CipherOperationType.SLICE, Integer.parseInt(matcher.group(2))));
      } else if (type.equals(splicePart)) {
        cipherKey.addOperation(new CipherOperation(CipherOperationType.SPLICE, Integer.parseInt(matcher.group(2))));
      } else {
        dumpProblematicScript(script, sourceUrl, "unknown cipher operation found");
      }
    }

    if (cipherKey.isEmpty()) {
      log.error("No operations detected from cipher extracted from {}.", sourceUrl);
      dumpProblematicScript(script, sourceUrl, "no cipher operations");
    }

    return cipherKey;
  }

  private static String extractDollarEscapedFirstGroup(@NotNull Pattern pattern, @NotNull String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(1).replace("$", "\\$") : null;
  }

  private static URI parseTokenScriptUrl(@NotNull String urlString) {
    try {
      if (urlString.startsWith("//")) {
        return new URI("https:" + urlString);
      } else if (urlString.startsWith("/")) {
        return new URI("https://www.youtube.com" + urlString);
      } else {
        return new URI(urlString);
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public static class CachedPlayerScript {
    public final String url;
    public final long expireTimestampMs;

    protected CachedPlayerScript(@NotNull String url) {
      this.url = url;
      this.expireTimestampMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
    }
  }
}
