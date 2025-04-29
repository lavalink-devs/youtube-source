package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes one signature cipher
 */
public class SignatureCipher {
  private static final Logger log = LoggerFactory.getLogger(SignatureCipher.class);
  private static final Pattern nFunctionTcePattern = Pattern.compile(
      "function\\s*\\((\\w+)\\)\\s*\\{var\\s*\\w+\\s*=\\s*\\1\\[\\w+\\[\\d+\\]\\]\\(\\w+\\[\\d+\\]\\)\\s*,\\s*\\w+\\s*=\\s*\\[.*?\\]\\;.*?catch\\s*\\(\\s*(\\w+)\\s*\\)\\s*\\{return\\s*\\w+\\[\\d+\\]\\s*\\+\\s*\\1\\}\\s*return\\s*\\w+\\[\\w+\\[\\d+\\]\\]\\(\\w+\\[\\d+\\]\\)\\}\\s*\\;",
      Pattern.DOTALL);
  private static final Pattern sigFunctionTcePattern = Pattern.compile("function\\(\\s*([a-zA-Z0-9$])\\s*\\)\\s*\\{" +
      "\\s*\\1\\s*=\\s*\\1\\[(\\w+)\\[\\d+\\]\\]\\(\\2\\[\\d+\\]\\);" +
      "([a-zA-Z0-9$]+)\\[\\2\\[\\d+\\]\\]\\(\\s*\\1\\s*,\\s*\\d+\\s*\\);" +
      "\\s*\\3\\[\\2\\[\\d+\\]\\]\\(\\s*\\1\\s*,\\s*\\d+\\s*\\);" +
      ".*?return\\s*\\1\\[\\2\\[\\d+\\]\\]\\(\\2\\[\\d+\\]\\)\\};");
  private static final Pattern tceSigFunctionActionsPattern = Pattern.compile(
      "var\\s+([A-Za-z0-9_]+)\\s*=\\s*\\{\\s*(?:[A-Za-z0-9_]+)\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}\\s*,\\s*(?:[A-Za-z0-9_]+)\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}\\s*,\\s*(?:[A-Za-z0-9_]+)\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}\\s*\\};");

  private final List<CipherOperation> operations = new ArrayList<>();
  public final String nFunction;
  public final String scriptTimestamp;
  public final String rawScript;
  public final String sigFunction;
  public final String sigFunctionActions;
  public boolean useScriptEngine = false;

  public final TCEVariable tceVariable;

  public SignatureCipher(@NotNull String nFunction, @NotNull String sigFunction, @NotNull String sigFunctionActions,
      @NotNull String timestamp, @NotNull String rawScript, @NotNull TCEVariable tceVariable) {
    this.nFunction = nFunction;
    this.scriptTimestamp = timestamp;
    this.rawScript = rawScript;
    this.sigFunction = sigFunction;
    this.sigFunctionActions = sigFunctionActions;
    this.tceVariable = tceVariable;
    this.useScriptEngine = true;
  }

  public SignatureCipher(@NotNull String nFunction, @NotNull String timestamp, @NotNull String rawScript) {
    this.nFunction = nFunction;
    this.scriptTimestamp = timestamp;
    this.rawScript = rawScript;
    this.tceVariable = null;
    this.sigFunction = null;
    this.sigFunctionActions = null;
  }

  public static SignatureCipher fromRawScript(@NotNull String jsCode, @NotNull String timestamp , @NotNull TCEVariable tce) {
    Matcher nFunctionMatcher = nFunctionTcePattern.matcher(jsCode);
    if (!nFunctionMatcher.find()) {
      log.warn("Failed to find the tce variant n function...");
      return null;
    }

    Matcher sigFunctionMatcher = sigFunctionTcePattern.matcher(jsCode);
    if (!sigFunctionMatcher.find()) {
      log.warn("Failed to find the tce variant sig function....");
      return null;
    }

    Matcher sigFunctionActionsMatcher = tceSigFunctionActionsPattern.matcher(jsCode);
    if (!sigFunctionActionsMatcher.find()) {
      log.warn("Failed to find the tce variant sig function actions...");
      return null;
    }
    String nFunction = nFunctionMatcher.group(0);
    Pattern shortCircuitPattern = Pattern.compile(String.format(
        ";\\s*if\\s*\\(\\s*typeof\\s+[a-zA-Z0-9_$]+\\s*===?\\s*(?:\"undefined\"|'undefined'|%s\\[\\d+\\])\\s*\\)\\s*return\\s+\\w+;",
        tce.getEscapedName()));
    Matcher tceShortCircuitMatcher = shortCircuitPattern.matcher(nFunction);
    if (tceShortCircuitMatcher.find()) {
      log.warn("TCE global variable short circuit detected replacing nFunction...");
      nFunction = nFunction.replaceAll(shortCircuitPattern.toString(), ";");
    }
    return new SignatureCipher(nFunction, sigFunctionMatcher.group(0), sigFunctionActionsMatcher.group(0), timestamp,
        jsCode, tce);
  }

  public boolean isTceScript() {
    return this.tceVariable != null;
  }

  public boolean shouldUseScriptEngine() {
    return this.useScriptEngine;

  }

  /**
   * @param text Text to apply the cipher on
   * @return The result of the cipher on the input text
   */
  public String apply(@NotNull String text, @NotNull ScriptEngine scriptEngine)
      throws ScriptException, NoSuchMethodException {
    String transformed;

    scriptEngine.eval("sig=" + sigFunction + sigFunctionActions + (isTceScript() ? tceVariable.getCode() : ""));
    transformed = (String) ((Invocable) scriptEngine).invokeFunction("sig", text);
    return transformed;
  }

  /**
   * @param text Text to apply the cipher on
   * @return The result of the cipher on the input text
   */
  public String apply(@NotNull String text) {
    StringBuilder builder = new StringBuilder(text);

    for (CipherOperation operation : operations) {
      switch (operation.type) {
        case SWAP:
          int position = operation.parameter % text.length();
          char temp = builder.charAt(0);
          builder.setCharAt(0, builder.charAt(position));
          builder.setCharAt(position, temp);
          break;
        case REVERSE:
          builder.reverse();
          break;
        case SLICE:
        case SPLICE:
          builder.delete(0, operation.parameter);
          break;
        default:
          throw new IllegalStateException("All branches should be covered");
      }
    }

    return builder.toString();
  }

  /**
   * @param text         Text to transform
   * @param scriptEngine JavaScript engine to execute function
   * @return The result of the n parameter transformation
   */
  public String transform(@NotNull String text, @NotNull ScriptEngine scriptEngine)
      throws ScriptException, NoSuchMethodException {
    String transformed;

    scriptEngine.eval("n=" + nFunction + (isTceScript() ? tceVariable.getCode() : ""));
    transformed = (String) ((Invocable) scriptEngine).invokeFunction("n", text);

    return transformed;
  }

  /**
   * @param operation The operation to add to this cipher
   */
  public void addOperation(@NotNull CipherOperation operation) {
    operations.add(operation);
  }

  /**
   * @return True if the cipher contains no operations.
   */
  public boolean isEmpty() {
    return operations.isEmpty();
  }
}
