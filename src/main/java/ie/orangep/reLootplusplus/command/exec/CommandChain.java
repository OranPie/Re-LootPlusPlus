package ie.orangep.reLootplusplus.command.exec;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.ArrayList;
import java.util.List;

public final class CommandChain {
    private final List<String> commands;

    public CommandChain(List<String> commands) {
        this.commands = List.copyOf(commands);
    }

    public List<String> commands() {
        return commands;
    }

    public static CommandChain parse(String raw, LegacyWarnReporter warnReporter, SourceLoc loc) {
        if (raw == null) {
            return new CommandChain(List.of());
        }
        if (raw.contains("&&") || raw.contains("||")) {
            if (warnReporter != null) {
                warnReporter.warn("LegacyCommandChain", "unsupported separator, treating as literal", loc);
            }
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        int bracketDepth = 0;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
            } else if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            }

            if (c == ';' && braceDepth == 0 && bracketDepth == 0) {
                parts.add(trimEnds(current.toString()));
                current.setLength(0);
                continue;
            }
            current.append(c);
        }

        if (current.length() > 0) {
            parts.add(trimEnds(current.toString()));
        }

        return new CommandChain(parts);
    }

    private static String trimEnds(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }
}
