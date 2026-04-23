package ie.orangep.reLootplusplus.diagnostic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("ReLootPlusPlus");

    public enum DetailLevel {
        OFF,
        SUMMARY,
        DETAIL,
        TRACE;

        static DetailLevel parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return SUMMARY;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "off", "none" -> OFF;
                case "summary", "basic", "info" -> SUMMARY;
                case "detail", "debug" -> DETAIL;
                case "trace", "full", "verbose" -> TRACE;
                default -> SUMMARY;
            };
        }
    }

    private Log() {
    }

    /** Always-visible informational message (operational summaries, bootstrap steps). */
    public static void info(String module, String message, Object... args) {
        LOGGER.info(format(module, message), args);
    }

    /** Summary-level log that is shared by console and in-game sinks. */
    public static void detail(String module, String message, Object... args) {
        if (shouldShowDetail(module, DetailLevel.SUMMARY)) {
            LOGGER.info(format(module, message), args);
        }
    }

    /**
     * Verbose debug trace — gated behind {@code logDebug=true} in config.
     * Use for per-tick / per-drop traces that would flood logs at runtime.
     */
    public static void debug(String module, String message, Object... args) {
        if (shouldShowDetail(module, DetailLevel.DETAIL)) {
            LOGGER.info(format(module, message), args);
        }
    }

    /** Deep trace logs shown only at the highest detail level. */
    public static void trace(String module, String message, Object... args) {
        if (shouldShowDetail(module, DetailLevel.TRACE)) {
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
        return detailLevel().ordinal() >= DetailLevel.DETAIL.ordinal();
    }

    public static DetailLevel detailLevel() {
        var config = ie.orangep.reLootplusplus.runtime.RuntimeState.config();
        if (config == null) {
            return DetailLevel.OFF;
        }
        if (config.logDetailLevel == null || config.logDetailLevel.isBlank()) {
            return config.logDebug ? DetailLevel.TRACE : DetailLevel.SUMMARY;
        }
        return DetailLevel.parse(config.logDetailLevel);
    }

    public static boolean shouldShowDetail(String module, DetailLevel level) {
        if (detailLevel().ordinal() < level.ordinal()) {
            return false;
        }
        return moduleMatches(module);
    }

    private static boolean shouldWarn() {
        var config = ie.orangep.reLootplusplus.runtime.RuntimeState.config();
        return config != null && config.logWarnings;
    }

    private static boolean moduleMatches(String module) {
        var config = ie.orangep.reLootplusplus.runtime.RuntimeState.config();
        if (config == null || config.logDetailFilters == null || config.logDetailFilters.isEmpty()) {
            return true;
        }
        if (module == null || module.isBlank()) {
            return false;
        }
        String normalized = module.trim().toLowerCase(Locale.ROOT);
        List<String> filters = config.logDetailFilters;
        for (String filter : filters) {
            if (filter == null || filter.isBlank()) {
                continue;
            }
            String f = filter.trim().toLowerCase(Locale.ROOT);
            if ("*".equals(f) || normalized.equals(f) || normalized.startsWith(f + ".") || normalized.startsWith(f + ":")
                || normalized.startsWith(f + "/") || normalized.startsWith(f)) {
                return true;
            }
        }
        return false;
    }

    private static String format(String module, String message) {
        if (module == null || module.isBlank()) {
            return "ReLoot++ " + message;
        }
        return "ReLoot++ [" + module + "] " + message;
    }
}
