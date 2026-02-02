package ie.orangep.reLootplusplus.config.parse;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class NumberParser {
    private NumberParser() {
    }

    public static int parseInt(String raw, int fallback, LegacyWarnReporter warnReporter, SourceLoc loc, String label) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            if (warnReporter != null) {
                warnReporter.warn("Parse", label + " not a number: " + raw, loc);
            }
            return fallback;
        }
    }

    public static float parseFloat(String raw, float fallback, LegacyWarnReporter warnReporter, SourceLoc loc, String label) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            if (warnReporter != null) {
                warnReporter.warn("Parse", label + " not a number: " + raw, loc);
            }
            return fallback;
        }
    }

    public static double parseDouble(String raw, double fallback, LegacyWarnReporter warnReporter, SourceLoc loc, String label) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            if (warnReporter != null) {
                warnReporter.warn("Parse", label + " not a number: " + raw, loc);
            }
            return fallback;
        }
    }
}
