package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.List;

/**
 * Describes one signature cipher
 */
public class SignatureCipher {
  private final List<CipherOperation> operations = new ArrayList<>();
  public final String nFunction;
  public final String scriptTimestamp;
  public final String rawScript;

  public SignatureCipher(@NotNull String nFunction,
                         @NotNull String timestamp,
                         @NotNull String rawScript) {
    this.nFunction = nFunction;
    this.scriptTimestamp = timestamp;
    this.rawScript = rawScript;
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
   * @param text Text to transform
   * @param scriptEngine JavaScript engine to execute function
   * @return The result of the n parameter transformation
   */
  public String transform(@NotNull String text, @NotNull ScriptEngine scriptEngine) throws ScriptException, NoSuchMethodException {
    String transformed;

    scriptEngine.eval("n=" + nFunction);
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
