package dev.lavalink.youtube.cipher;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.ExceptionWithResponseBody;
import dev.lavalink.youtube.YoutubeSource;
import dev.lavalink.youtube.cipher.ScriptExtractionException.ExtractionFailureType;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;

/**
 * Handles parsing and caching of signature ciphers
 */
@SuppressWarnings({"RegExpRedundantEscape", "RegExpUnnecessaryNonCapturingGroup"})
public class LocalSignatureCipherManager implements CipherManager {
    private static final Logger log = LoggerFactory.getLogger(LocalSignatureCipherManager.class);

    private static final String VARIABLE_PART = "[a-zA-Z_\\$][a-zA-Z_0-9\\$]*";
    private static final String VARIABLE_PART_OBJECT_DECLARATION = "[\"']?[a-zA-Z_\\$][a-zA-Z_0-9\\$]*[\"']?";

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(signatureTimestamp|sts):(\\d+)");

    private static final Pattern GLOBAL_VARS_PATTERN = Pattern.compile(
        "('use\\s*strict';)?" +
            "(?<code>var\\s*(?<varname>[a-zA-Z0-9_$]+)\\s*=\\s*" +
            "(?<value>(?:\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')" +
            "\\.split\\((?:\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')\\)" +
            "|\\[(?:(?:\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')\\s*,?\\s*)*\\]" +
            "|\"[^\"]*\"\\.split\\(\"[^\"]*\"\\)))"
    );

    private static final Pattern ACTIONS_PATTERN = Pattern.compile(
        "var\\s+([$A-Za-z0-9_]+)\\s*=\\s*\\{" +
            "\\s*" + VARIABLE_PART_OBJECT_DECLARATION + "\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}\\s*," +
            "\\s*" + VARIABLE_PART_OBJECT_DECLARATION + "\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}\\s*," +
            "\\s*" + VARIABLE_PART_OBJECT_DECLARATION + "\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}\\s*};");

    private static final Pattern SIG_FUNCTION_PATTERN = Pattern.compile(
        "function(?:\\s+" + VARIABLE_PART + ")?\\((" + VARIABLE_PART + ")\\)\\{" +
            VARIABLE_PART + "=" + VARIABLE_PART + ".*?\\(\\1,\\d+\\);return\\s*\\1.*};"
    );

    private static final Pattern N_FUNCTION_PATTERN = Pattern.compile(
        "function\\(\\s*(" + VARIABLE_PART + ")\\s*\\)\\s*\\{" +
            "var\\s*(" + VARIABLE_PART + ")=\\1\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)" +
            ".*?catch\\(\\s*(\\w+)\\s*\\)\\s*\\{" +
            "\\s*return.*?\\+\\s*\\1\\s*}" +
            "\\s*return\\s*\\2\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)};",
        Pattern.DOTALL
    );

    // old?
    private static final Pattern functionPatternOld = Pattern.compile(
        "function\\(\\s*(\\w+)\\s*\\)\\s*\\{" +
            "var\\s*(\\w+)=\\1\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)" +
            ".*?catch\\(\\s*(\\w+)\\s*\\)\\s*\\{" +
            "\\s*return.*?\\+\\s*\\1\\s*}" +
            "\\s*return\\s*\\2\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)};",
        Pattern.DOTALL);

    private final ConcurrentMap<String, SignatureCipher> cipherCache;
    private final ConcurrentMap<String, String> stsCache;
    private final Set<String> dumpedScriptUrls;
    private final ScriptEngine scriptEngine;

    protected volatile CachedPlayerScript cachedPlayerScript;

    /**
     * Create a new local signature cipher manager
     */
    public LocalSignatureCipherManager() {
        this.cipherCache = new ConcurrentHashMap<>();
        this.stsCache = new ConcurrentHashMap<>();
        this.dumpedScriptUrls = new HashSet<>();
        this.scriptEngine = new RhinoScriptEngineFactory().getScriptEngine();
    }

    /**
     * Produces a valid playback URL for the specified track
     *
     * @param httpInterface HTTP interface to use
     * @param playerScript  Address of the script which is used to decipher signatures
     * @param format        The track for which to get the URL
     * @return Valid playback URL
     * @throws IOException On network IO error
     */
    @NotNull
    public URI resolveFormatUrl(@NotNull HttpInterface httpInterface,
                                @NotNull String playerScript,
                                @NotNull StreamFormat format) throws IOException {
        String signature = format.getSignature();
        String nParameter = format.getNParameter();
        URI initialUrl = format.getUrl();

        URIBuilder uri = new URIBuilder(initialUrl);

        SignatureCipher cipher = getCipherScript(httpInterface, playerScript);

        if (!DataFormatTools.isNullOrEmpty(signature)) {
            try {
                uri.setParameter(format.getSignatureKey(), cipher.apply(signature, scriptEngine));
            } catch (ScriptException | NoSuchMethodException e) {
                dumpProblematicScript(cipherCache.get(playerScript).rawScript, playerScript, "Can't transform s parameter " + signature);
            }
        }


        if (!DataFormatTools.isNullOrEmpty(nParameter)) {
            try {
                String transformed = cipher.transform(nParameter, scriptEngine);
                String logMessage = null;

                if (transformed == null) {
                    logMessage = "Transformed n parameter is null, n function possibly faulty";
                } else if (nParameter.equals(transformed)) {
                    logMessage = "Transformed n parameter is the same as input, n function possibly short-circuited";
                } else if (transformed.startsWith("enhanced_except_") || transformed.endsWith("_w8_" + nParameter)) {
                    logMessage = "N function did not complete due to exception";
                }

                if (logMessage != null) {
                    log.warn("{} (in: {}, out: {}, player script: {}, source version: {})",
                        logMessage, nParameter, transformed, playerScript, YoutubeSource.VERSION);
                }

                uri.setParameter("n", transformed);
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

    public CachedPlayerScript getCachedPlayerScript(@NotNull HttpInterface httpInterface) {
        if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
            synchronized (this) {
                if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
                    try {
                        return (cachedPlayerScript = getPlayerScript(httpInterface));
                    } catch (RuntimeException e) {
                        if (e instanceof ExceptionWithResponseBody) {
                            throw throwWithDebugInfo(log, null, e.getMessage(), "html", ((ExceptionWithResponseBody) e).getResponseBody());
                        }

                        throw e;
                    }
                }
            }
        }

        return cachedPlayerScript;
    }

    private SignatureCipher getCipherScript(@NotNull HttpInterface httpInterface,
                                           @NotNull String cipherScriptUrl) throws IOException {
        SignatureCipher cipherKey = cipherCache.get(cipherScriptUrl);

        if (cipherKey == null) {
            synchronized (this) {
                log.debug("Parsing player script {}", cipherScriptUrl);

                try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(CipherUtils.parseTokenScriptUrl(cipherScriptUrl)))) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                        throw new IOException("Received non-success response code " + statusCode + " from script url " +
                            cipherScriptUrl + " ( " + CipherUtils.parseTokenScriptUrl(cipherScriptUrl) + " )");
                    }

                    cipherKey = extractFromScript(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8), cipherScriptUrl);
                    cipherCache.put(cipherScriptUrl, cipherKey);
                }
            }
        }

        return cipherKey;
    }

    public String getRawScript(@NotNull HttpInterface httpInterface,
                               @NotNull String cipherScriptUrl) throws IOException {
        synchronized (this) {
            log.debug("getting raw player script {}", cipherScriptUrl);

            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(CipherUtils.parseTokenScriptUrl(cipherScriptUrl)))) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Received non-success response code " + statusCode + " from script url " +
                        cipherScriptUrl + " ( " + CipherUtils.parseTokenScriptUrl(cipherScriptUrl) + " )");
                }

                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            }
        }
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

            log.error("Problematic YouTube player script {} detected (issue detected with script: {}). Dumped to {} (Source version: {})",
                sourceUrl, issue, path.toAbsolutePath(), YoutubeSource.VERSION);
        } catch (Exception e) {
            log.error("Failed to dump problematic YouTube player script {} (issue detected with script: {})", sourceUrl, issue);
        }
    }

    public String getTimestamp(HttpInterface httpInterface, String sourceUrl) throws IOException {
        // Check cache first
        String cachedSts = stsCache.get(sourceUrl);
        if (cachedSts != null) {
            log.debug("STS cache hit for script URL: {}", sourceUrl);
            return cachedSts;
        }

        synchronized (this) {
            // Double-check after acquiring lock
            cachedSts = stsCache.get(sourceUrl);
            if (cachedSts != null) {
                log.debug("STS cache hit (after lock) for script URL: {}", sourceUrl);
                return cachedSts;
            }

            log.debug("STS cache miss - fetching timestamp from script {}", sourceUrl);

            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(CipherUtils.parseTokenScriptUrl(sourceUrl)))) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Received non-success response code " + statusCode + " from script url " +
                        sourceUrl + " ( " + CipherUtils.parseTokenScriptUrl(sourceUrl) + " )");
                }

                String sts = getScriptTimestamp(httpInterface, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8), sourceUrl);
                stsCache.put(sourceUrl, sts);
                log.debug("Cached STS {} for script URL: {}", sts, sourceUrl);
                return sts;
            }
        }
    }

    public String getScriptTimestamp(HttpInterface httpInterface, String script, String scriptUrl) {
        Matcher scriptTimestamp = TIMESTAMP_PATTERN.matcher(script);
        if (!scriptTimestamp.find()) {
            scriptExtractionFailed(script, scriptUrl, ExtractionFailureType.TIMESTAMP_NOT_FOUND);
        }

        return scriptTimestamp.group(2);
    }

    private SignatureCipher extractFromScript(@NotNull String script, @NotNull String sourceUrl) {
        String timestamp = getScriptTimestamp(null, script, sourceUrl);

        Matcher globalVarsMatcher = GLOBAL_VARS_PATTERN.matcher(script);

        if (!globalVarsMatcher.find()) {
            scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.VARIABLES_NOT_FOUND);
        }

        Matcher sigActionsMatcher = ACTIONS_PATTERN.matcher(script);

        if (!sigActionsMatcher.find()) {
            scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.SIG_ACTIONS_NOT_FOUND);
        }

        Matcher sigFunctionMatcher = SIG_FUNCTION_PATTERN.matcher(script);

        if (!sigFunctionMatcher.find()) {
            scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.DECIPHER_FUNCTION_NOT_FOUND);
        }

        Matcher nFunctionMatcher = N_FUNCTION_PATTERN.matcher(script);

        if (!nFunctionMatcher.find()) {
            scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.N_FUNCTION_NOT_FOUND);
        }

        String globalVars = globalVarsMatcher.group("code");
        String sigActions = sigActionsMatcher.group(0);
        String sigFunction = sigFunctionMatcher.group(0);
        String nFunction = nFunctionMatcher.group(0);

        String nfParameterName = DataFormatTools.extractBetween(nFunction, "(", ")");
        // Remove short-circuit that prevents n challenge transformation
        nFunction = nFunction.replaceAll("if\\s*\\(typeof\\s*[^\\s()]+\\s*===?.*?\\)return " + nfParameterName + "\\s*;?", "");

        return new SignatureCipher(timestamp, globalVars, sigActions, sigFunction, nFunction, script);
    }

    private void scriptExtractionFailed(String script, String sourceUrl, ExtractionFailureType failureType) {
        dumpProblematicScript(script, sourceUrl, "must find " + failureType.friendlyName);
        throw new ScriptExtractionException("Must find " + failureType.friendlyName + " from script: " + sourceUrl, failureType);
    }

    /**
     * Clears the STS cache. Useful for testing and when player script updates are detected.
     */
    public void clearStsCache() {
        stsCache.clear();
        log.debug("STS cache cleared");
    }

    /**
     * Removes a specific STS entry from the cache.
     * @param scriptUrl The player script URL whose STS should be removed from cache
     */
    public void evictStsFromCache(@NotNull String scriptUrl) {
        String removed = stsCache.remove(scriptUrl);
        if (removed != null) {
            log.debug("Evicted STS {} for script URL: {}", removed, scriptUrl);
        }
    }

    /**
     * Returns the current size of the STS cache.
     * @return The number of cached STS entries
     */
    public int getStsCacheSize() {
        return stsCache.size();
    }
}
