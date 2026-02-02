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
        if (rawLine.charAt(0) == '#') {
            return true;
        }
        return rawLine.startsWith("//");
    }
}
