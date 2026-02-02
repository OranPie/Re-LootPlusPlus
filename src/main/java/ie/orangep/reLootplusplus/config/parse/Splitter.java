package ie.orangep.reLootplusplus.config.parse;

import java.util.regex.Pattern;

public final class Splitter {
    private Splitter() {
    }

    public static String[] splitRegex(String input, String regex) {
        return splitRegex(input, regex, 0);
    }

    public static String[] splitRegex(String input, String regex, int limit) {
        return Pattern.compile(regex).split(input, limit);
    }
}
