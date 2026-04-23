package ie.orangep.reLootplusplus.diagnostic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

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

    /** Summary-level informational message (operational summaries, bootstrap steps). */
    public static void info(String module, String message, Object... args) {
        if (shouldShowDetail(module, DetailLevel.SUMMARY)) {
            LOGGER.info(format(module, message), args);
        }
    }

    /** Summary-level log that is shared by console and in-game sinks. */
    public static void detail(String module, String message, Object... args) {
        if (shouldShowDetail(module, DetailLevel.SUMMARY)) {
            LOGGER.info(format(module, message), args);
        }
    }

    /**
     * Verbose debug trace — always written to the debug log file (when open);
     * also printed to console when {@code logDetailLevel=detail} or higher.
     */
    public static void debug(String module, String message, Object... args) {
        DebugFileWriter.write("DEBUG", module, resolveArgs(message, args));
        if (shouldShowDetail(module, DetailLevel.DETAIL)) {
            LOGGER.info(format(module, message), args);
        }
    }

    /**
     * Deep trace — always written to the debug log file (when open);
     * also printed to console only at {@code logDetailLevel=trace}.
     */
    public static void trace(String module, String message, Object... args) {
        DebugFileWriter.write("TRACE", module, resolveArgs(message, args));
        if (shouldShowDetail(module, DetailLevel.TRACE)) {
            LOGGER.info(format(module, message), args);
        }
    }

    /** Non-critical warning — gated behind {@code logWarnings=true} in config. */
    public static void warn(String module, String message, Object... args) {
        if (shouldWarn() && shouldShowDetail(module, DetailLevel.SUMMARY)) {
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

    /** Formats a SLF4J-style message + args array to a plain string for the debug file. */
    private static String resolveArgs(String message, Object[] args) {
        if (args == null || args.length == 0) return message != null ? message : "";
        try {
            return MessageFormatter.arrayFormat(message, args).getMessage();
        } catch (Exception e) {
            return message != null ? message : "";
        }
    }
}
