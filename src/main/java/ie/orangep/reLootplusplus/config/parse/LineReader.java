package ie.orangep.reLootplusplus.config.parse;

public final class LineReader {
    private LineReader() {
    }

    public static boolean isIgnorable(String rawLine) {
        if (rawLine == null) {
            return true;
        }
        if (rawLine.isEmpty()) {
            return true;
        }
        String trimmed = rawLine.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (trimmed.charAt(0) == '#') {
            return true;
        }
        if (trimmed.startsWith("//")) {
            return true;
        }
        if (trimmed.startsWith("/")) {
            return true;
        }
        return false;
    }
}
