package ie.orangep.reLootplusplus.diagnostic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("ReLootPlusPlus");

    private Log() {
    }

    /** Always-visible informational message (operational summaries, bootstrap steps). */
    public static void info(String module, String message, Object... args) {
        LOGGER.info(format(module, message), args);
    }

    /**
     * Verbose debug trace — gated behind {@code logDebug=true} in config.
     * Use for per-tick / per-drop traces that would flood logs at runtime.
     */
    public static void debug(String module, String message, Object... args) {
        if (isDebugEnabled()) {
            LOGGER.info(format(module, message), args);
        }
    }

    /** Non-critical warning — gated behind {@code logWarnings=true} in config. */
    public static void warn(String module, String message, Object... args) {
        if (shouldWarn()) {
            LOGGER.warn(format(module, message), args);
        }
    }

    /** Always-visible warning — used by {@link ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter} and critical non-IO issues. */
    public static void warnAlways(String module, String message, Object... args) {
        LOGGER.warn(format(module, message), args);
    }

    /** Always-visible error — used for I/O failures and critical bootstrap errors. */
    public static void error(String module, String message, Object... args) {
        LOGGER.error(format(module, message), args);
    }

    /** Returns true when verbose debug output is enabled ({@code logDebug=true}). */
    public static boolean isDebugEnabled() {
        var config = ie.orangep.reLootplusplus.runtime.RuntimeState.config();
        return config != null && config.logDebug;
    }

    private static boolean shouldWarn() {
        var config = ie.orangep.reLootplusplus.runtime.RuntimeState.config();
        return config != null && config.logWarnings;
    }

    private static String format(String module, String message) {
        if (module == null || module.isBlank()) {
            return "ReLoot++ " + message;
        }
        return "ReLoot++ [" + module + "] " + message;
    }
}
