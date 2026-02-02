package ie.orangep.reLootplusplus.diagnostic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("ReLootPlusPlus");

    private Log() {
    }

    public static void warn(String message, Object... args) {
        if (shouldWarn()) {
            LOGGER.warn(message, args);
        }
    }

    private static boolean shouldWarn() {
        var config = ie.orangep.reLootplusplus.runtime.RuntimeState.config();
        if (config == null) {
            return true;
        }
        return config.logLegacyWarnings;
    }
}
