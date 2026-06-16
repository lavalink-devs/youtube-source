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

    private static final Pattern MODERN_DECIPHER_CALL_PATTERN = Pattern.compile(
        "([a-zA-Z0-9_\\$]+)\\((\\d+),(\\d+),([a-zA-Z0-9_\\$]+)\\((\\d+),(\\d+),[a-zA-Z0-9_\\$]+\\.s\\)\\)"
    );

    private final ConcurrentMap<String, SignatureCipher> cipherCache;
    private final Set<String> dumpedScriptUrls;
    private final ScriptEngine scriptEngine;

    protected volatile CachedPlayerScript cachedPlayerScript;

    /**
     * Create a new local signature cipher manager
     */
    public LocalSignatureCipherManager() {
        this.cipherCache = new ConcurrentHashMap<>();
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
        synchronized (this) {
            log.debug("Timestamp from script {}", sourceUrl);

            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(CipherUtils.parseTokenScriptUrl(sourceUrl)))) {
                int statusCode = response.getStatusLine().getStatusCode();

                if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                    throw new IOException("Received non-success response code " + statusCode + " from script url " +
                        sourceUrl + " ( " + CipherUtils.parseTokenScriptUrl(sourceUrl) + " )");
                }

                return getScriptTimestamp(httpInterface, EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8), sourceUrl);
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

        try {
            return extractModernPlayer(script, timestamp, sourceUrl);
        } catch (Exception e) {
            log.debug("Modern player extraction failed, falling back to legacy: {}", e.getMessage());
        }

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

    private SignatureCipher extractModernPlayer(@NotNull String script, @NotNull String timestamp, @NotNull String sourceUrl) {
        Matcher decipherCallMatcher = MODERN_DECIPHER_CALL_PATTERN.matcher(script);
        if (!decipherCallMatcher.find()) {
            throw new RuntimeException("Modern decipher call site not found");
        }
        String cTName = decipherCallMatcher.group(1);
        int cTArg1 = Integer.parseInt(decipherCallMatcher.group(2));
        int cTArg2 = Integer.parseInt(decipherCallMatcher.group(3));
        String bName = decipherCallMatcher.group(4);
        int bArg3 = Integer.parseInt(decipherCallMatcher.group(5));
        int bArg4 = Integer.parseInt(decipherCallMatcher.group(6));

        // Locate try-catch block start in script
        int tryStart = script.indexOf("try{try{");
        if (tryStart == -1) {
            tryStart = script.indexOf("try { try {");
        }
        if (tryStart == -1) {
            throw new RuntimeException("Failed to locate try-catch block start in script");
        }

        // Find wrapper function name using backward search from tryStart
        int funcIdx = script.lastIndexOf("=function(", tryStart);
        if (funcIdx == -1) {
            throw new RuntimeException("Failed to find wrapper function definition start");
        }
        int nameStart = script.lastIndexOf("\n", funcIdx);
        if (nameStart == -1) {
            nameStart = 0;
        }
        String wrapperDef = script.substring(nameStart, funcIdx);
        Matcher wrapperNameMatcher = Pattern.compile("\\b([a-zA-Z0-9_\\$]+)\\s*$").matcher(wrapperDef);
        if (!wrapperNameMatcher.find()) {
            throw new RuntimeException("Failed to parse wrapper function name");
        }
        String wrapperName = wrapperNameMatcher.group(1);

        // Extract wrapper function bounds
        int wrapperFuncStart = script.indexOf(wrapperName + "=function(");
        if (wrapperFuncStart == -1) {
            throw new RuntimeException("Failed to find wrapper function definition start index");
        }
        int wrapperFuncEnd = script.indexOf("};", wrapperFuncStart);
        if (wrapperFuncEnd == -1) {
            wrapperFuncEnd = wrapperFuncStart + 8000;
        } else {
            wrapperFuncEnd += 2;
        }
        String wrapperFuncBody = script.substring(wrapperFuncStart, Math.min(script.length(), wrapperFuncEnd));

        // Find helper function name inside wrapper function
        Pattern tryPatternNested = Pattern.compile("try\\s*\\{\\s*try\\s*\\{\\s*(?:var|let|const)?\\s*[a-zA-Z0-9_\\$]+[=\\s]+([a-zA-Z0-9_\\$]+)\\(");
        Matcher tryMatcher = tryPatternNested.matcher(wrapperFuncBody);
        String firstFuncName;
        if (tryMatcher.find()) {
            firstFuncName = tryMatcher.group(1);
        } else {
            Pattern tryPatternSimple = Pattern.compile("try\\s*\\{\\s*(?:var|let|const)?\\s*[a-zA-Z0-9_\\$]+[=\\s]+([a-zA-Z0-9_\\$]+)\\(");
            tryMatcher = tryPatternSimple.matcher(wrapperFuncBody);
            if (tryMatcher.find()) {
                firstFuncName = tryMatcher.group(1);
            } else {
                throw new RuntimeException("Failed to find helper function inside wrapper function");
            }
        }

        // Find all calls to wrapper function
        Pattern callerPattern = Pattern.compile(Pattern.quote(wrapperName) + "\\(([a-zA-Z0-9_\\$]+)\\^(\\d+),\\1\\^(\\d+),");
        Matcher callerMatcher = callerPattern.matcher(script);

        int constMZ1 = 0;
        int constMZ2 = 0;
        int constRecur1 = 0;
        int constRecur2 = 0;
        String gCName = null;

        while (callerMatcher.find()) {
            int pos = callerMatcher.start();
            int c1 = Integer.parseInt(callerMatcher.group(2));
            int c2 = Integer.parseInt(callerMatcher.group(3));
            if (pos >= wrapperFuncStart && pos <= wrapperFuncEnd) {
                constRecur1 = c1;
                constRecur2 = c2;
            } else {
                int enclFuncIdx = script.lastIndexOf("=function(", pos);
                if (enclFuncIdx != -1) {
                    int enclNameStart = script.lastIndexOf("\n", enclFuncIdx);
                    if (enclNameStart == -1) {
                        enclNameStart = 0;
                    }
                    String enclDef = script.substring(enclNameStart, enclFuncIdx);
                    Matcher enclMatcher = Pattern.compile("\\b([a-zA-Z0-9_\\$]+)\\s*$").matcher(enclDef);
                    if (enclMatcher.find()) {
                        String enclName = enclMatcher.group(1);
                        if (!enclName.equals(firstFuncName)) {
                            gCName = enclName;
                            constMZ1 = c1;
                            constMZ2 = c2;
                        }
                    }
                }
            }
        }

        // Extract nsig trigger call
        int nsigV = 0;
        int nsigW = 0;
        int firstFuncStart = script.indexOf(firstFuncName + "=function(");
        String firstFuncBody = "";
        if (firstFuncStart != -1) {
            firstFuncBody = script.substring(firstFuncStart, Math.min(script.length(), firstFuncStart + 4000));
        }
        Pattern nsigPattern = Pattern.compile(Pattern.quote(bName) + "\\((\\d+),(\\d+),");
        Matcher trigMatcher = nsigPattern.matcher(firstFuncBody);
        if (trigMatcher.find()) {
            nsigV = Integer.parseInt(trigMatcher.group(1));
            nsigW = Integer.parseInt(trigMatcher.group(2));
        } else {
            int decipherPos = decipherCallMatcher.start();
            int windowStart = Math.max(0, decipherPos - 1000);
            int windowEnd = Math.min(script.length(), decipherPos + 1000);
            String windowText = script.substring(windowStart, windowEnd);
            Matcher windowMatcher = nsigPattern.matcher(windowText);
            while (windowMatcher.find()) {
                int vVal = Integer.parseInt(windowMatcher.group(1));
                int wVal = Integer.parseInt(windowMatcher.group(2));
                if (vVal != bArg3 || wVal != bArg4) {
                    nsigV = vVal;
                    nsigW = wVal;
                    break;
                }
            }
        }

        // Extract legacy constants (const_wC and const_gC)
        Pattern wCDefPattern = Pattern.compile("([a-zA-Z0-9_\\$]+)=function\\(([a-zA-Z0-9_\\$]+),[a-zA-Z0-9_\\$]+,[a-zA-Z0-9_\\$]+,[a-zA-Z0-9_\\$]+\\)\\{var\\s+[a-zA-Z0-9_\\$]+=[a-zA-Z0-9_\\$]+\\^[a-zA-Z0-9_\\$]+;if\\(\\(\\2-\\d+\\^\\d+\\)<\\2");
        Matcher wCMatcher = wCDefPattern.matcher(script);
        int constWC = 0;
        int constGC = 0;
        if (wCMatcher.find()) {
            String wCName = wCMatcher.group(1);
            int bDefStart = script.indexOf(bName + "=function(");
            if (bDefStart != -1) {
                String bBody = script.substring(bDefStart, Math.min(script.length(), bDefStart + 4000));
                Matcher constWCMatcher = Pattern.compile(Pattern.quote(wCName) + "\\((?:2|\\d+),[a-zA-Z0-9_\\$]+\\^(\\d+),").matcher(bBody);
                if (constWCMatcher.find()) {
                    constWC = Integer.parseInt(constWCMatcher.group(1));
                }
            }
            if (gCName != null) {
                int wCDefStart = script.indexOf(wCName + "=function(");
                if (wCDefStart != -1) {
                    String wCBody = script.substring(wCDefStart, Math.min(script.length(), wCDefStart + 4000));
                    Matcher constGCMatcher = Pattern.compile(Pattern.quote(gCName) + "\\((?:2|\\d+),[a-zA-Z0-9_\\$]+\\^(\\d+),").matcher(wCBody);
                    if (constGCMatcher.find()) {
                        constGC = Integer.parseInt(constGCMatcher.group(1));
                    }
                }
            }
        }

        int nsigArg1;
        int nsigArg2;

        if (constRecur1 > 0) {
            // Candidate B (Recursive/Embed)
            int bIi = constMZ1 ^ constMZ2;
            nsigArg1 = bIi ^ constRecur1;
            nsigArg2 = bIi ^ constRecur2;
        } else {
            // Candidate A (Legacy/ES6)
            int bVal = nsigW ^ nsigV;
            int H = bVal ^ constWC ^ constGC;
            nsigArg1 = H ^ constMZ1;
            nsigArg2 = H ^ constMZ2;
        }

        log.debug("Extracted modern player parameters for signature/nsig deciphering: " +
            "cTName={}, cTArg1={}, cTArg2={}, bName={}, bArg3={}, bArg4={}, MZName={}, nsigArg1={}, nsigArg2={}",
            cTName, cTArg1, cTArg2, bName, bArg3, bArg4, wrapperName, nsigArg1, nsigArg2);

        // Construct injected base.js with export statements
        String injection = "\n_yt_player." + cTName + "=" + cTName + ";\n_yt_player." + bName + "=" + bName + ";\n_yt_player." + wrapperName + "=" + wrapperName + ";\n})(_yt_player);";
        String modifiedScript = script.replaceFirst("\\}\\)\\(_yt_player\\);\\s*$", Matcher.quoteReplacement(injection));

        return new SignatureCipher(
            timestamp,
            cTName,
            bName,
            cTArg1,
            cTArg2,
            bArg3,
            bArg4,
            wrapperName,
            nsigArg1,
            nsigArg2,
            modifiedScript
        );
    }

    private void scriptExtractionFailed(String script, String sourceUrl, ExtractionFailureType failureType) {
        dumpProblematicScript(script, sourceUrl, "must find " + failureType.friendlyName);
        throw new ScriptExtractionException("Must find " + failureType.friendlyName + " from script: " + sourceUrl, failureType);
    }
}
