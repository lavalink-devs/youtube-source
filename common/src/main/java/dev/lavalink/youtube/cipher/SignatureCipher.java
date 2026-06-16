package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Describes one signature cipher
 */
public class SignatureCipher {
    private static final Logger log = LoggerFactory.getLogger(SignatureCipher.class);

    public final String timestamp;
    public final String globalVars;
    public final String sigActions;
    public final String sigFunction;
    public final String nFunction;
    public final String rawScript;

    public final String bName;
    public final int cTArg1;
    public final int cTArg2;
    public final int bArg3;
    public final int bArg4;
    public final String MZName;
    public final int nsigArg1;
    public final int nsigArg2;
    public final boolean isModern;

    private transient volatile ScriptEngine cachedEngine;
    private transient final Object engineLock = new Object();

    public SignatureCipher(@NotNull String timestamp,
                           @NotNull String globalVars,
                           @NotNull String sigActions,
                           @NotNull String sigFunction,
                           @NotNull String nFunction,
                           @NotNull String rawScript) {
        this.timestamp = timestamp;
        this.globalVars = globalVars;
        this.sigActions = sigActions;
        this.sigFunction = sigFunction;
        this.nFunction = nFunction;
        this.rawScript = rawScript;
        this.bName = null;
        this.cTArg1 = 0;
        this.cTArg2 = 0;
        this.bArg3 = 0;
        this.bArg4 = 0;
        this.MZName = null;
        this.nsigArg1 = 0;
        this.nsigArg2 = 0;
        this.isModern = false;
    }

    public SignatureCipher(@NotNull String timestamp,
                           @NotNull String sigFunction, // cTName
                           @NotNull String bName,
                           int cTArg1,
                           int cTArg2,
                           int bArg3,
                           int bArg4,
                           @NotNull String MZName,
                           int nsigArg1,
                           int nsigArg2,
                           @NotNull String rawScript) {
        this.timestamp = timestamp;
        this.globalVars = "";
        this.sigActions = "";
        this.sigFunction = sigFunction;
        this.nFunction = "";
        this.rawScript = rawScript;
        this.bName = bName;
        this.cTArg1 = cTArg1;
        this.cTArg2 = cTArg2;
        this.bArg3 = bArg3;
        this.bArg4 = bArg4;
        this.MZName = MZName;
        this.nsigArg1 = nsigArg1;
        this.nsigArg2 = nsigArg2;
        this.isModern = true;
    }

    private ScriptEngine getOrCreateEngine() throws ScriptException {
        if (cachedEngine == null) {
            synchronized (engineLock) {
                if (cachedEngine == null) {
                    ScriptEngine engine = new org.mozilla.javascript.engine.RhinoScriptEngineFactory().getScriptEngine();
                    engine.eval("var _yt_player = {};");
                    engine.eval("var location = { href: 'https://www.youtube.com', hostname: 'www.youtube.com', protocol: 'https:', pathname: '/', search: '', hash: '', assign: function() {}, replace: function() {} };");
                    engine.eval("var document = { visibilityState: 'visible', addEventListener: function() {}, removeEventListener: function() {}, createElement: function() { return { style: {}, addEventListener: function() {} }; }, documentElement: { style: {} }, location: location, body: { appendChild: function() {}, removeChild: function() {} } };");
                    engine.eval("var navigator = { userAgent: 'Mozilla/5.0' };");
                    engine.eval("var window = this; var self = this; var parent = this; var top = this;");
                    engine.eval("var XMLHttpRequest = function() { this.open = function() {}; this.send = function() {}; this.setRequestHeader = function() {}; };");
                    engine.eval("var setTimeout = function() {}; var clearTimeout = function() {}; var setInterval = function() {}; var clearInterval = function() {};");
                    engine.eval(rawScript);
                    engine.eval("function decrypt_sig(sig) { return _yt_player." + sigFunction + "(" + cTArg1 + ", " + cTArg2 + ", _yt_player." + bName + "(" + bArg3 + ", " + bArg4 + ", sig)); }");
                    engine.eval("function decrypt_nsig(nsig) { return _yt_player." + MZName + "(" + nsigArg1 + ", " + nsigArg2 + ", nsig); }");
                    cachedEngine = engine;
                }
            }
        }
        return cachedEngine;
    }

    /**
     * @param text Text to apply the cipher on
     * @return The result of the cipher on the input text
     */
    public String apply(@NotNull String text,
                        @NotNull ScriptEngine scriptEngine) throws ScriptException, NoSuchMethodException {
        if (isModern) {
            ScriptEngine engine = getOrCreateEngine();
            synchronized (engine) {
                return (String) ((Invocable) engine).invokeFunction("decrypt_sig", text);
            }
        } else {
            synchronized (scriptEngine) {
                scriptEngine.eval(globalVars + ";" + sigActions + ";decrypt_sig=" + sigFunction);
                return (String) ((Invocable) scriptEngine).invokeFunction("decrypt_sig", text);
            }
        }
    }

//  /**
//   * @param text Text to apply the cipher on
//   * @return The result of the cipher on the input text
//   */
//  public String apply(@NotNull String text) {
//    StringBuilder builder = new StringBuilder(text);
//
//    for (CipherOperation operation : operations) {
//      switch (operation.type) {
//        case SWAP:
//          int position = operation.parameter % text.length();
//          char temp = builder.charAt(0);
//          builder.setCharAt(0, builder.charAt(position));
//          builder.setCharAt(position, temp);
//          break;
//        case REVERSE:
//          builder.reverse();
//          break;
//        case SLICE:
//        case SPLICE:
//          builder.delete(0, operation.parameter);
//          break;
//        default:
//          throw new IllegalStateException("All branches should be covered");
//      }
//    }
//
//    return builder.toString();
//  }

    /**
     * @param text         Text to transform
     * @param scriptEngine JavaScript engine to execute function
     * @return The result of the n parameter transformation
     */
    public String transform(@NotNull String text, @NotNull ScriptEngine scriptEngine)
        throws ScriptException, NoSuchMethodException {
        if (isModern) {
            ScriptEngine engine = getOrCreateEngine();
            synchronized (engine) {
                return (String) ((Invocable) engine).invokeFunction("decrypt_nsig", text);
            }
        } else {
            synchronized (scriptEngine) {
                scriptEngine.eval(globalVars + ";decrypt_nsig=" + nFunction);
                return (String) ((Invocable) scriptEngine).invokeFunction("decrypt_nsig", text);
            }
        }
    }

//  /**
//   * @param operation The operation to add to this cipher
//   */
//  public void addOperation(@NotNull CipherOperation operation) {
//    operations.add(operation);
//  }
//
//  /**
//   * @return True if the cipher contains no operations.
//   */
//  public boolean isEmpty() {
//    return operations.isEmpty();
//  }
}
