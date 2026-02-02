package ie.orangep.reLootplusplus.legacy;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;

import java.util.Locale;

public final class LegacyDropSanitizer {
    private LegacyDropSanitizer() {
    }

    private static final ThreadLocal<String> SANITIZE_CONTEXT = new ThreadLocal<>();

    public static String sanitize(String raw, LegacyWarnReporter reporter) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        SANITIZE_CONTEXT.set(preview(raw));
        raw = fixLegacySetblock(raw, reporter);
        raw = fixDanglingCloseParenBeforeAttr(raw, reporter);
        raw = fixMissingGroupCloseBeforeAttr(raw, reporter);
        LegacyDropParts parts = LegacyDropParts.parse(raw);
        String dropPart = parts.dropPart;
        dropPart = fixLeadingCoordTuple(dropPart, reporter);
        dropPart = fixBrokenHashQuote(dropPart, reporter);
        dropPart = fixGroupColonEmpty(dropPart, reporter);
        dropPart = fixTypeIdSemicolon(dropPart, reporter);
        dropPart = fixLegacyGroupSeparators(dropPart, reporter);
        dropPart = fixMissingGroupSeparatorBetweenDrops(dropPart, reporter);
        dropPart = fixStrayTopLevelCloseParen(dropPart, reporter);
        dropPart = fixDirtyQuotes(dropPart, reporter);
        dropPart = fixMissingCommaAfterParen(dropPart, reporter);
        dropPart = fixMissingFunctionHash(dropPart, reporter);
        dropPart = fixUnknownTypeAsEntity(dropPart, reporter);
        dropPart = fixMissingTypedIdEquals(dropPart, reporter);
        dropPart = fixLegacyFallingBlockType(dropPart, reporter);
        dropPart = fixEmptyIdSeparators(dropPart, reporter);
        dropPart = fixDanglingPosOffset(dropPart, reporter);
        dropPart = fixTrailingCommas(dropPart, reporter);
        dropPart = fixSoundIdCase(dropPart, reporter);
        dropPart = fixBareItemIds(dropPart, reporter);
        dropPart = fixLegacyWoolMeta(dropPart, reporter);
        dropPart = fixBareBlockDrops(dropPart, reporter);
        dropPart = fixEscapedDropSeparators(dropPart, reporter);
        dropPart = collapseCommas(dropPart);
        dropPart = trimTopLevelTrailingSemicolon(dropPart);
        dropPart = fixUnclosedGroupAtEnd(dropPart, reporter);
        warnLegacyGroupRepeat(dropPart, reporter);
        String sanitized = parts.rebuild(dropPart, normalizeAttrParts(parts.attrs, reporter));
        if (!raw.equals(sanitized)) {
            warnOnce(reporter, "LuckyDropSanitize", preview(raw) + " -> " + preview(sanitized));
        }
        SANITIZE_CONTEXT.remove();
        return sanitized;
    }

    private static String fixMissingGroupCloseBeforeAttr(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        int start = 0;
        while (start < input.length() && Character.isWhitespace(input.charAt(start))) {
            start++;
        }
        if (start >= input.length() || !startsWithIgnoreCase(input, start, "group")) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 4);
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        boolean changed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
                if (c == '@' && depth > 0) {
                    int j = i + 1;
                    while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                        j++;
                    }
                    if (matchAttrKeyword(input, j) != null) {
                        for (int k = 0; k < depth; k++) {
                            out.append(')');
                        }
                        depth = 0;
                        changed = true;
                        warnOnce(reporter, "LegacyGroupClose", "inserted missing ')' before attrs");
                    }
                }
            }
            out.append(c);
        }
        return changed ? out.toString() : input;
    }

    private static String collapseCommas(String input) {
        String out = input;
        String prev;
        do {
            prev = out;
            out = out.replace(",,", ",");
        } while (!out.equals(prev));
        return out;
    }

    private static String normalizeAttrPart(String input, LegacyWarnReporter reporter) {
        if (input == null) {
            return "";
        }
        String raw = input.trim();
        if (raw.isEmpty()) {
            return "";
        }
        String keyword = matchAttrKeyword(raw, 0);
        if (keyword == null) {
            return raw;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        int i = keyword.length();
        boolean hadEquals = i < raw.length() && raw.charAt(i) == '=';
        if (hadEquals) {
            i++;
        }
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
            i++;
        }
        int valueStart = i;
        if (i < raw.length() && (raw.charAt(i) == '+' || raw.charAt(i) == '-')) {
            i++;
        }
        boolean sawDigit = false;
        boolean sawDot = false;
        while (i < raw.length()) {
            char n = raw.charAt(i);
            if (Character.isDigit(n)) {
                sawDigit = true;
                i++;
                continue;
            }
            if (n == '.' && !sawDot) {
                sawDot = true;
                i++;
                continue;
            }
            break;
        }
        String value = null;
        if (sawDigit) {
            value = raw.substring(valueStart, i);
        }
        if (value == null || value.isEmpty()) {
            value = "1";
            warnOnce(reporter, "LegacyChance", "defaulted " + normalizedKeyword + " to 1");
        } else if (!hadEquals) {
            warnOnce(reporter, "LegacyChance", "added missing '=' for " + normalizedKeyword + "=" + value);
        }
        int j = i;
        while (j < raw.length() && Character.isWhitespace(raw.charAt(j))) {
            j++;
        }
        if (j < raw.length() && raw.charAt(j) == ')') {
            warnOnce(reporter, "LegacyChance", "stripped stray ')' after " + normalizedKeyword);
            j++;
        }
        if (j < raw.length() && raw.charAt(j) == '=') {
            warnOnce(reporter, "LegacyChance", "stripped stray '=' after " + normalizedKeyword);
        }
        if (j < raw.length() && raw.charAt(j) == '/') {
            String removed = raw.substring(j).trim();
            warnOnce(reporter, "LegacyAttrComment", "stripped " + normalizedKeyword + " comment '" + removed + "'");
        }
        return normalizedKeyword + "=" + value;
    }

    private static java.util.List<String> normalizeAttrParts(java.util.List<String> attrs, LegacyWarnReporter reporter) {
        if (attrs == null || attrs.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<String> normalized = new java.util.ArrayList<>(attrs.size());
        for (String attr : attrs) {
            String fixed = normalizeAttrPart(attr, reporter);
            if (fixed != null && !fixed.isEmpty()) {
                normalized.add(fixed);
            }
        }
        return normalized;
    }

    private static String matchAttrKeyword(String input, int index) {
        if (startsWithIgnoreCase(input, index, "luck")) {
            return "luck";
        }
        if (startsWithIgnoreCase(input, index, "chance")) {
            return "chance";
        }
        return null;
    }

    private static boolean startsWithIgnoreCase(String input, int index, String keyword) {
        if (index + keyword.length() > input.length()) {
            return false;
        }
        return input.regionMatches(true, index, keyword, 0, keyword.length());
    }

    private static String trimTopLevelTrailingSemicolon(String input) {
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        int lastNonSpace = -1;
        int lastTopLevelSemicolon = -1;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                } else if (c == ';' && depth == 0) {
                    lastTopLevelSemicolon = i;
                }
            }
            if (!Character.isWhitespace(c)) {
                lastNonSpace = i;
            }
        }
        if (lastNonSpace >= 0 && lastNonSpace == lastTopLevelSemicolon) {
            return input.substring(0, lastNonSpace) + input.substring(lastNonSpace + 1);
        }
        return input;
    }

    private static String fixGroupColonEmpty(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        boolean inSingle = false;
        boolean inDouble = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (!inSingle && !inDouble && input.regionMatches(true, i, "group:", 0, 6)) {
                int j = i + 6;
                while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                    j++;
                }
                if (j < input.length() && input.charAt(j) == '(') {
                    out.append("group");
                    i = j;
                    changed = true;
                    warnOnce(reporter, "LegacyGroupColonEmpty", "converted 'group:(...)' to 'group(...)'");
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return changed ? out.toString() : input;
    }

    private static String fixLeadingCoordTuple(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String trimmed = input.trim();
        if (!trimmed.startsWith("(")) {
            return input;
        }
        int end = trimmed.indexOf(')');
        if (end < 0) {
            return input;
        }
        String inside = trimmed.substring(1, end);
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (!(c == ',' || c == '.' || c == '-' || Character.isDigit(c) || Character.isWhitespace(c))) {
                return input;
            }
        }
        int nextIdx = end + 1;
        if (nextIdx < trimmed.length() && (trimmed.charAt(nextIdx) == ';' || trimmed.charAt(nextIdx) == ',')) {
            String fixed = trimmed.substring(nextIdx + 1);
            warnOnce(reporter, "LegacyCoordTuple", "removed leading coordinate tuple");
            return fixed;
        }
        return input;
    }

    private static String fixMissingCommaAfterParen(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 4);
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        boolean changed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth = Math.max(0, depth - 1);
                    if (depth == 0 && i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (Character.isLetter(next)) {
                            out.append(c).append(',');
                            changed = true;
                            continue;
                        }
                    }
                }
            }
            out.append(c);
        }
        if (changed) {
            warnOnce(reporter, "LegacyComma", "inserted missing ',' after ')'");
        }
        return out.toString();
    }

    private static String fixUnknownTypeAsEntity(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        int segmentStart = 0;
        for (int i = 0; i <= input.length(); i++) {
            char c = i < input.length() ? input.charAt(i) : ';';
            if (i < input.length()) {
                if (!inDouble && c == '\'') {
                    inSingle = !inSingle;
                } else if (!inSingle && c == '"') {
                    inDouble = !inDouble;
                } else if (!inSingle && !inDouble) {
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && depth > 0) {
                        depth--;
                    }
                }
            }
            if (i == input.length() || (c == ';' && depth == 0 && !inSingle && !inDouble)) {
                String segment = input.substring(segmentStart, i);
                out.append(fixUnknownTypeSegment(segment, reporter));
                if (i < input.length()) {
                    out.append(';');
                }
                segmentStart = i + 1;
            }
        }
        return out.toString();
    }

    private static String fixUnknownTypeSegment(String segment, LegacyWarnReporter reporter) {
        if (segment == null || segment.isEmpty()) {
            return segment;
        }
        String type = readTypeTopLevel(segment);
        if (type == null) {
            return segment;
        }
        String lower = type.toLowerCase(Locale.ROOT);
        if (isKnownDropType(lower)) {
            return segment;
        }
        if (containsTopLevelKey(segment, "id")) {
            return segment;
        }
        int idx = indexOfTopLevelType(segment);
        if (idx < 0) {
            return segment;
        }
        int valueStart = idx + "type=".length();
        int valueEnd = valueStart;
        while (valueEnd < segment.length()) {
            char c = segment.charAt(valueEnd);
            if (c == ',' || c == ';' || c == '@' || c == ')') {
                break;
            }
            valueEnd++;
        }
        String replaced = segment.substring(0, idx)
            + "type=entity,ID=" + segment.substring(valueStart, valueEnd)
            + segment.substring(valueEnd);
        warnOnce(reporter, "LegacyTypeEntity", "mapped type=" + type + " to entity ID");
        return replaced;
    }

    private static boolean isKnownDropType(String value) {
        return value.equals("item")
            || value.equals("block")
            || value.equals("entity")
            || value.equals("command")
            || value.equals("effect")
            || value.equals("explosion")
            || value.equals("fill")
            || value.equals("particle")
            || value.equals("sound")
            || value.equals("structure")
            || value.equals("difficulty")
            || value.equals("time")
            || value.equals("message")
            || value.equals("nothing");
    }

    private static String readTypeTopLevel(String segment) {
        int idx = indexOfTopLevelType(segment);
        if (idx < 0) {
            return null;
        }
        int start = idx + "type=".length();
        int i = start;
        while (i < segment.length()) {
            char c = segment.charAt(i);
            if (c == ',' || c == ';' || c == '@' || c == ')') {
                break;
            }
            i++;
        }
        String value = segment.substring(start, i).trim();
        return value.isEmpty() ? null : value;
    }

    private static int indexOfTopLevelType(String segment) {
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
                if (depth == 0 && startsWithIgnoreCase(segment, i, "type=")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean containsTopLevelKey(String segment, String key) {
        String target = key + "=";
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        for (int i = 0; i <= segment.length() - target.length(); i++) {
            char c = segment.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
                if (depth == 0 && segment.regionMatches(true, i, target, 0, target.length())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String fixDanglingCloseParenBeforeAttr(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        boolean changed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth == 0) {
                        int j = i + 1;
                        while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                            j++;
                        }
                        if (j < input.length() && input.charAt(j) == '@') {
                            changed = true;
                            warnOnce(reporter, "LegacyParen", "removed ')' before attrs");
                            continue;
                        }
                    }
                    depth = Math.max(0, depth - 1);
                }
            }
            out.append(c);
        }
        return changed ? out.toString() : input;
    }

    private static String fixLegacyFallingBlockType(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean inQuotes = false;
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                out.append(c);
                continue;
            }
            if (!inQuotes) {
                if (c == '(') depth++;
                if (c == ')') depth = Math.max(0, depth - 1);
                if (depth == 0 && input.startsWith("type=falling_block,ID=", i)) {
                    int start = i + "type=falling_block,ID=".length();
                    int j = start;
                    while (j < input.length()) {
                        char cj = input.charAt(j);
                        if (cj == ',' || cj == ';' || cj == '@') {
                            break;
                        }
                        j++;
                    }
                    String blockId = input.substring(start, j);
                    out.append("type=entity,ID=FallingSand,NBTTag=(Block=").append(blockId).append(")");
                    warnOnce(reporter, "LegacyFallingBlock", "rewrote falling_block to FallingSand");
                    i = j - 1;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String fixEmptyIdSeparators(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String fixed = input.replace("ID=;", "ID=").replace(",;", ";");
        if (!fixed.equals(input)) {
            warnOnce(reporter, "LegacyId", "removed empty ID separators");
        }
        return fixed;
    }

    private static String fixDanglingPosOffset(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean inQuotes = false;
        int depth = 0;
        boolean changed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                out.append(c);
                continue;
            }
            if (!inQuotes) {
                if (c == '(') depth++;
                if (c == ')') depth = Math.max(0, depth - 1);
                if (depth == 0 && input.startsWith("posOffset", i)) {
                    int j = i + "posOffset".length();
                    if (j >= input.length() || input.charAt(j) == ',' || input.charAt(j) == ';' || input.charAt(j) == '@' || input.charAt(j) == ')') {
                        changed = true;
                        i = j - 1;
                        continue;
                    }
                    if (input.charAt(j) == '=' && (j + 1 >= input.length() || input.charAt(j + 1) == ';' || input.charAt(j + 1) == '@' || input.charAt(j + 1) == ')')) {
                        changed = true;
                        i = j;
                        continue;
                    }
                }
            }
            out.append(c);
        }
        if (changed) {
            warnOnce(reporter, "LegacyAttr", "removed dangling posOffset");
        }
        return out.toString();
    }

    private static String fixSoundIdCase(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean inQuotes = false;
        int depth = 0;
        boolean changed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                out.append(c);
                continue;
            }
            if (!inQuotes) {
                if (c == '(') depth++;
                if (c == ')') depth = Math.max(0, depth - 1);
                if (depth == 0 && input.startsWith("type=sound,ID=", i)) {
                    int start = i + "type=sound,ID=".length();
                    int j = start;
                    while (j < input.length()) {
                        char cj = input.charAt(j);
                        if (cj == ',' || cj == ';' || cj == '@') {
                            break;
                        }
                        j++;
                    }
                    String rawId = input.substring(start, j);
                    String fixed = rawId.toLowerCase(Locale.ROOT);
                    if (!fixed.equals(rawId)) {
                        changed = true;
                        warnOnce(reporter, "LegacySound", "normalized sound id " + rawId + " -> " + fixed);
                    }
                    out.append("type=sound,ID=").append(fixed);
                    i = j - 1;
                    continue;
                }
            }
            out.append(c);
        }
        return changed ? out.toString() : input;
    }

    private static String fixTrailingCommas(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        boolean changed = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
                if (depth == 0 && c == ',') {
                    int j = i + 1;
                    while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                        j++;
                    }
                    if (j >= input.length()) {
                        changed = true;
                        warnOnce(reporter, "LegacyComma", "removed trailing ','");
                        continue;
                    }
                    char next = input.charAt(j);
                    if (next == ';' || next == '@' || next == ')') {
                        changed = true;
                        warnOnce(reporter, "LegacyComma", "removed dangling ',' before delimiter");
                        continue;
                    }
                }
            }
            out.append(c);
        }
        return changed ? out.toString() : input;
    }

    private static String fixUnclosedGroupAtEnd(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        int start = 0;
        while (start < input.length() && Character.isWhitespace(input.charAt(start))) {
            start++;
        }
        if (start >= input.length() || !startsWithIgnoreCase(input, start, "group")) {
            return input;
        }
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') depth++;
                if (c == ')') depth = Math.max(0, depth - 1);
            }
        }
        if (depth > 0) {
            StringBuilder out = new StringBuilder(input.length() + depth);
            out.append(input);
            for (int i = 0; i < depth; i++) {
                out.append(')');
            }
            warnOnce(reporter, "LegacyGroupClose", "appended missing ')' at end of group");
            return out.toString();
        }
        return input;
    }

    private static String fixLegacySetblock(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String trimmed = input.trim();
        if (!trimmed.startsWith("type=setblock")) {
            return input;
        }
        int idx = trimmed.indexOf(',');
        String payload = trimmed.substring("type=setblock".length()).trim();
        if (payload.startsWith(",")) {
            payload = payload.substring(1).trim();
        }
        if (payload.isEmpty()) {
            return input;
        }
        String escaped = payload.replace("\"", "\\\"");
        String fixed = "type=command,ID=\"/setblock " + escaped + "\"";
        warnOnce(reporter, "LegacySetblock", "rewrote legacy setblock to command");
        return fixed;
    }

    private static String fixMissingTypedIdEquals(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        java.util.Set<String> idTypes = java.util.Set.of(
            "entity",
            "item",
            "block",
            "particle",
            "sound",
            "effect",
            "structure",
            "fill",
            "command",
            "message",
            "difficulty",
            "time"
        );
        StringBuilder out = new StringBuilder(input.length());
        int depth = 0;
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                out.append(c);
                continue;
            }
            if (!inQuotes) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth = Math.max(0, depth - 1);
                }
                if (depth == 0 && input.startsWith("type=", i)) {
                    int typeStart = i + "type=".length();
                    int typeEnd = typeStart;
                    while (typeEnd < input.length()) {
                        char tc = input.charAt(typeEnd);
                        if (tc == ',' || tc == ';' || tc == '@' || Character.isWhitespace(tc)) {
                            break;
                        }
                        typeEnd++;
                    }
                    String type = input.substring(typeStart, typeEnd).trim().toLowerCase(Locale.ROOT);
                    if (!idTypes.contains(type)) {
                        out.append(c);
                        continue;
                    }
                    int comma = input.indexOf(',', typeEnd);
                    if (comma < 0) {
                        out.append(c);
                        continue;
                    }
                    int start = comma + 1;
                    int j = start;
                    while (j < input.length()) {
                        char cj = input.charAt(j);
                        if (cj == ',' || cj == ';' || cj == '@') {
                            break;
                        }
                        j++;
                    }
                    String token = input.substring(start, j);
                    if (!token.contains("=") && !token.isBlank()) {
                        out.append("type=").append(type).append(",ID=").append(token);
                        warnOnce(reporter, "LegacyEntityId", "inserted missing ID= in type=" + type);
                        i = j - 1;
                        continue;
                    }
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String fixLegacyGroupSeparators(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        boolean inSingle = false;
        boolean inDouble = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (!inSingle && !inDouble) {
                if (c == ')' && i + 1 < input.length() && input.charAt(i + 1) == ',') {
                    int j = i + 2;
                    while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                        j++;
                    }
                    if (startsWithDropToken(input, j)) {
                        out.append(')').append(';');
                        i += 2;
                        changed = true;
                        warnOnce(reporter, "LegacyGroupSeparator", "replaced '),<drop>' with ');<drop>'");
                        continue;
                    }
                }
                if (c == ',') {
                    int j = i + 1;
                    while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                        j++;
                    }
                    if (startsWithDropSeparatorToken(input, j)) {
                        out.append(';');
                        i++;
                        changed = true;
                        warnOnce(reporter, "LegacyGroupSeparator", "replaced ',<drop>' with ';<drop>'");
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return changed ? out.toString() : input;
    }

    private static String fixTypeIdSemicolon(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        boolean inSingle = false;
        boolean inDouble = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (!inSingle && !inDouble && startsWithIgnoreCase(input, i, "type=")) {
                int j = i + 5;
                while (j < input.length()) {
                    char n = input.charAt(j);
                    if (n == ';') {
                        int k = j + 1;
                        while (k < input.length() && Character.isWhitespace(input.charAt(k))) {
                            k++;
                        }
                        if (startsWithIgnoreCase(input, k, "id=")) {
                            out.append(input, i, j).append(',');
                            i = j + 1;
                            changed = true;
                            warnOnce(reporter, "LegacyTypeSeparator", "replaced 'type=...;ID=' with comma");
                            continue;
                        }
                        break;
                    }
                    if (n == ',' || n == '@') {
                        break;
                    }
                    j++;
                }
            }
            out.append(c);
            i++;
        }
        return changed ? out.toString() : input;
    }

    private static String fixMissingGroupSeparatorBetweenDrops(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean pendingClose = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (!inSingle && !inDouble) {
                if (c == ')') {
                    pendingClose = true;
                } else if (c == ';') {
                    pendingClose = false;
                } else if (c == ',') {
                    pendingClose = false;
                } else if (pendingClose && startsWithDropToken(input, i)) {
                    out.append(';');
                    changed = true;
                    warnOnce(reporter, "LegacyGroupSeparator", "inserted missing ';' between drops");
                    pendingClose = false;
                } else if (!Character.isWhitespace(c) && c != '@') {
                    // other content cancels pending close if it's clearly part of same token
                    if (!startsWithDropToken(input, i)) {
                        pendingClose = false;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return changed ? out.toString() : input;
    }

    private static String fixStrayTopLevelCloseParen(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth == 0) {
                        changed = true;
                        warnOnce(reporter, "LegacyParen", "stripped stray ')' at top level");
                        continue;
                    }
                    depth--;
                }
            }
            out.append(c);
        }
        return changed ? out.toString() : input;
    }

    private static String fixDirtyQuotes(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        boolean inSingle = false;
        boolean inDouble = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                if (!inSingle && !inDouble && isStrayQuoteBeforeAttr(input, i)) {
                    changed = true;
                    warnOnce(reporter, "LegacyDirtyQuote", "removed stray '\"' before attribute");
                    i++;
                    continue;
                }
                if (!inSingle && !inDouble && isStrayQuoteBeforeDelimiter(input, i)) {
                    changed = true;
                    warnOnce(reporter, "LegacyDirtyQuote", "removed stray '\"' before delimiter");
                    i++;
                    continue;
                }
                inDouble = !inDouble;
            }
            out.append(c);
            i++;
        }
        return changed ? out.toString() : input;
    }

    private static boolean isStrayQuoteBeforeAttr(String input, int quoteIndex) {
        int prev = quoteIndex - 1;
        while (prev >= 0 && Character.isWhitespace(input.charAt(prev))) {
            prev--;
        }
        if (prev < 0 || input.charAt(prev) != ')') {
            return false;
        }
        int next = quoteIndex + 1;
        while (next < input.length() && Character.isWhitespace(input.charAt(next))) {
            next++;
        }
        return next < input.length() && input.charAt(next) == '@';
    }

    private static boolean isStrayQuoteBeforeDelimiter(String input, int quoteIndex) {
        int next = quoteIndex + 1;
        while (next < input.length() && Character.isWhitespace(input.charAt(next))) {
            next++;
        }
        if (next >= input.length()) {
            return true;
        }
        char c = input.charAt(next);
        return c == ')' || c == ';' || c == '@' || c == ',';
    }

    private static String fixMissingFunctionHash(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
            }
            if (!inSingle && !inDouble && c == '=' && depth >= 0) {
                int j = i + 1;
                while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                    j++;
                }
                if (j < input.length() && input.charAt(j) != '#' && isIdentStart(input.charAt(j))) {
                    int k = j + 1;
                    while (k < input.length() && isIdentPart(input.charAt(k))) {
                        k++;
                    }
                    if (k < input.length() && input.charAt(k) == '(') {
                        out.append(c).append('#');
                        changed = true;
                        warnOnce(reporter, "LegacyFunctionMissingHash", "added '#' before function call");
                        i++;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return changed ? out.toString() : input;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    private static boolean startsWithDropToken(String input, int index) {
        if (index < 0 || index >= input.length()) {
            return false;
        }
        if (startsWithIgnoreCase(input, index, "type=")) {
            return true;
        }
        if (startsWithIgnoreCase(input, index, "id=")) {
            return true;
        }
        return startsWithIgnoreCase(input, index, "group");
    }

    private static boolean startsWithDropSeparatorToken(String input, int index) {
        if (index < 0 || index >= input.length()) {
            return false;
        }
        if (startsWithIgnoreCase(input, index, "type=")) {
            return true;
        }
        return startsWithIgnoreCase(input, index, "group");
    }

    private static String fixBareItemIds(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        int segmentStart = 0;
        for (int i = 0; i <= input.length(); i++) {
            char c = i < input.length() ? input.charAt(i) : ';';
            if (i < input.length()) {
                if (!inDouble && c == '\'') {
                    inSingle = !inSingle;
                } else if (!inSingle && c == '"') {
                    inDouble = !inDouble;
                } else if (!inSingle && !inDouble) {
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && depth > 0) {
                        depth--;
                    }
                }
            }
            if (i == input.length() || (c == ';' && depth == 0 && !inSingle && !inDouble)) {
                String segment = input.substring(segmentStart, i);
                out.append(fixSegmentItemId(segment, reporter));
                if (i < input.length()) {
                    out.append(';');
                }
                segmentStart = i + 1;
            }
        }
        return out.toString();
    }

    private static String fixLegacyWoolMeta(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        int segmentStart = 0;
        for (int i = 0; i <= input.length(); i++) {
            char c = i < input.length() ? input.charAt(i) : ';';
            if (i < input.length()) {
                if (!inDouble && c == '\'') {
                    inSingle = !inSingle;
                } else if (!inSingle && c == '"') {
                    inDouble = !inDouble;
                } else if (!inSingle && !inDouble) {
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && depth > 0) {
                        depth--;
                    }
                }
            }
            if (i == input.length() || (c == ';' && depth == 0 && !inSingle && !inDouble)) {
                String segment = input.substring(segmentStart, i);
                out.append(fixSegmentWoolMeta(segment, reporter));
                if (i < input.length()) {
                    out.append(';');
                }
                segmentStart = i + 1;
            }
        }
        return out.toString();
    }

    private static String fixSegmentWoolMeta(String segment, LegacyWarnReporter reporter) {
        if (segment == null || segment.isEmpty()) {
            return segment;
        }
        String type = readType(segment);
        if (type != null && !"item".equalsIgnoreCase(type)) {
            return segment;
        }
        java.util.List<String> tokens = splitTopLevel(segment, ',');
        String idValue = null;
        String damageValue = null;
        for (String token : tokens) {
            String trimmed = token.trim();
            if (startsWithIgnoreCase(trimmed, 0, "id=")) {
                idValue = trimmed.substring(3).trim();
            } else if (startsWithIgnoreCase(trimmed, 0, "damage=") || startsWithIgnoreCase(trimmed, 0, "meta=")) {
                int eq = trimmed.indexOf('=');
                if (eq >= 0) {
                    damageValue = trimmed.substring(eq + 1).trim();
                }
            }
        }
        if (idValue == null) {
            return segment;
        }
        String idLower = idValue.toLowerCase(Locale.ROOT);
        if (!"wool".equals(idLower) && !idLower.endsWith(":wool")) {
            return segment;
        }
        java.util.List<String> colors = resolveWoolColors(damageValue);
        if (colors.isEmpty()) {
            return segment;
        }
        String replacementId;
        if (colors.size() == 1) {
            replacementId = "minecraft:" + colors.get(0) + "_wool";
        } else {
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < colors.size(); i++) {
                if (i > 0) {
                    list.append(',');
                }
                list.append("minecraft:").append(colors.get(i)).append("_wool");
            }
            replacementId = "#randList(" + list + ")";
        }
        java.util.List<String> outTokens = new java.util.ArrayList<>(tokens.size());
        for (String token : tokens) {
            String trimmed = token.trim();
            if (startsWithIgnoreCase(trimmed, 0, "damage=") || startsWithIgnoreCase(trimmed, 0, "meta=")) {
                continue;
            }
            if (startsWithIgnoreCase(trimmed, 0, "id=")) {
                outTokens.add("ID=" + replacementId);
                continue;
            }
            outTokens.add(token);
        }
        if (reporter != null) {
            warnOnce(reporter, "LegacyItemId", "mapped wool meta -> colored wool");
        }
        return String.join(",", outTokens);
    }

    private static java.util.List<String> resolveWoolColors(String damageValue) {
        String[] dye = new String[] {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        };
        if (damageValue == null || damageValue.isEmpty()) {
            return java.util.List.of(dye[0]);
        }
        String trimmed = damageValue.trim();
        if (trimmed.startsWith("#rand(") && trimmed.endsWith(")")) {
            String inner = trimmed.substring("#rand(".length(), trimmed.length() - 1);
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                Integer min = parseIntSafe(parts[0].trim());
                Integer max = parseIntSafe(parts[1].trim());
                if (min != null && max != null) {
                    int a = Math.max(0, Math.min(min, max));
                    int b = Math.min(15, Math.max(min, max));
                    java.util.List<String> out = new java.util.ArrayList<>();
                    for (int i = a; i <= b; i++) {
                        out.add(dye[i]);
                    }
                    return out;
                }
            }
        }
        Integer single = parseIntSafe(trimmed);
        if (single != null) {
            int idx = Math.max(0, Math.min(15, single));
            return java.util.List.of(dye[idx]);
        }
        java.util.List<String> all = new java.util.ArrayList<>(16);
        java.util.Collections.addAll(all, dye);
        return all;
    }

    private static Integer parseIntSafe(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static java.util.List<String> splitTopLevel(String input, char separator) {
        java.util.List<String> out = new java.util.ArrayList<>();
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        int start = 0;
        for (int i = 0; i <= input.length(); i++) {
            char c = i < input.length() ? input.charAt(i) : separator;
            if (i < input.length()) {
                if (!inDouble && c == '\'') {
                    inSingle = !inSingle;
                } else if (!inSingle && c == '"') {
                    inDouble = !inDouble;
                } else if (!inSingle && !inDouble) {
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && depth > 0) {
                        depth--;
                    }
                }
            }
            if (i == input.length() || (c == separator && depth == 0 && !inSingle && !inDouble)) {
                out.add(input.substring(start, i));
                start = i + 1;
            }
        }
        return out;
    }

    private static String fixBareBlockDrops(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        int segmentStart = 0;
        for (int i = 0; i <= input.length(); i++) {
            char c = i < input.length() ? input.charAt(i) : ';';
            if (i < input.length()) {
                if (!inDouble && c == '\'') {
                    inSingle = !inSingle;
                } else if (!inSingle && c == '"') {
                    inDouble = !inDouble;
                } else if (!inSingle && !inDouble) {
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && depth > 0) {
                        depth--;
                    }
                }
            }
            if (i == input.length() || (c == ';' && depth == 0 && !inSingle && !inDouble)) {
                String segment = input.substring(segmentStart, i);
                out.append(fixBareBlockSegment(segment, reporter));
                if (i < input.length()) {
                    out.append(';');
                }
                segmentStart = i + 1;
            }
        }
        return out.toString();
    }

    private static String fixBareBlockSegment(String segment, LegacyWarnReporter reporter) {
        if (segment.isEmpty()) {
            return segment;
        }
        if (indexOfIgnoreCase(segment, "type=") >= 0 || indexOfIgnoreCase(segment, "id=") >= 0) {
            return segment;
        }
        String blockId = extractBlockId(segment);
        if (blockId == null) {
            return segment;
        }
        String normalized = normalizeBareBlockId(blockId, reporter);
        warnOnce(reporter, "LegacyBlockDrop", "assumed type=block for NBTTag-only drop");
        return "type=block,ID=" + normalized + "," + segment;
    }

    private static String extractBlockId(String segment) {
        String id = extractKeyValue(segment, "BlockState", "Name");
        if (id != null) {
            return id;
        }
        return extractKeyValue(segment, "Block", null);
    }

    private static String extractKeyValue(String segment, String key, String subKey) {
        int keyIndex = indexOfIgnoreCase(segment, key + "=");
        if (keyIndex < 0) {
            return null;
        }
        int start = keyIndex + key.length() + 1;
        if (start >= segment.length()) {
            return null;
        }
        if (subKey != null && start < segment.length() && segment.charAt(start) == '(') {
            int end = findMatchingParen(segment, start);
            if (end < 0) {
                return null;
            }
            String inner = segment.substring(start + 1, end);
            return extractKeyValue(inner, subKey, null);
        }
        int end = start;
        while (end < segment.length()) {
            char c = segment.charAt(end);
            if (c == ',' || c == ';' || c == ')' ) {
                break;
            }
            end++;
        }
        String raw = segment.substring(start, end).trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 1) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return raw.isEmpty() ? null : raw;
    }

    private static int findMatchingParen(String input, int openIndex) {
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = openIndex; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            }
            if (inSingle || inDouble) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String normalizeBareBlockId(String raw, LegacyWarnReporter reporter) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String trimmed = raw.trim();
        if (trimmed.indexOf('#') >= 0) {
            return trimmed;
        }
        String namespace = null;
        String path = trimmed;
        int idx = trimmed.indexOf(':');
        if (idx > 0) {
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        }
        String fixedNamespace = namespace == null ? "minecraft" : namespace.toLowerCase(Locale.ROOT);
        String sanitizedPath = sanitizePath(path);
        String fixed = fixedNamespace + ":" + sanitizedPath;
        if (namespace == null) {
            warnOnce(reporter, "LegacyBlockId", "assumed namespace for '" + trimmed + "' -> '" + fixed + "'");
        } else if (!fixed.equals(trimmed)) {
            warnOnce(reporter, "LegacyBlockId", "sanitized '" + trimmed + "' -> '" + fixed + "'");
        }
        return fixed;
    }

    private static String fixEscapedDropSeparators(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\') {
                int semiIndex = -1;
                int step = 0;
                if (i + 1 < input.length() && input.charAt(i + 1) == ';') {
                    semiIndex = i + 1;
                    step = 2;
                } else if (i + 2 < input.length() && input.charAt(i + 1) == '\\' && input.charAt(i + 2) == ';') {
                    semiIndex = i + 2;
                    step = 3;
                }
                if (semiIndex != -1) {
                    int j = semiIndex + 1;
                    while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                        j++;
                    }
                    if (startsWithDropSeparatorToken(input, j) || startsWithIgnoreCase(input, j, "id=")) {
                        out.append("\\\";");
                        i += step;
                        changed = true;
                        warnOnce(reporter, "LegacyDropSeparator", "converted escaped ';' to '\";' for drop split");
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return changed ? out.toString() : input;
    }

    private static String fixSegmentItemId(String segment, LegacyWarnReporter reporter) {
        String type = readType(segment);
        if (type != null && !"item".equalsIgnoreCase(type)) {
            return segment;
        }
        if (!containsTopLevelKey(segment, "id") && !containsTopLevelKey(segment, "type")) {
            java.util.List<String> tokens = splitTopLevel(segment, ',');
            if (!tokens.isEmpty()) {
                String first = tokens.get(0).trim();
                if (!first.isEmpty() && first.indexOf('=') < 0 && first.indexOf(' ') < 0 && !startsWithIgnoreCase(first, 0, "group")) {
                    String fixedId = normalizeBareItemId(first, reporter);
                    StringBuilder rebuilt = new StringBuilder(segment.length() + 8);
                    rebuilt.append("ID=").append(fixedId);
                    if (tokens.size() > 1) {
                        for (int i = 1; i < tokens.size(); i++) {
                            rebuilt.append(',').append(tokens.get(i));
                        }
                    }
                    warnOnce(reporter, "LegacyItemId", "assumed ID= for bare item '" + first + "'");
                    return rebuilt.toString();
                }
            }
        }
        StringBuilder out = new StringBuilder(segment.length());
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        int i = 0;
        boolean changed = false;
        while (i < segment.length()) {
            char c = segment.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
            }
            if (!inSingle && !inDouble && depth == 0 && startsWithIgnoreCase(segment, i, "id=")) {
                int valueStart = i + 3;
                int j = valueStart;
                while (j < segment.length()) {
                    char n = segment.charAt(j);
                    if (n == ',' || n == ';' || n == '@' || n == ')' ) {
                        break;
                    }
                    j++;
                }
                String rawId = segment.substring(valueStart, j).trim();
                String fixedId = normalizeBareItemId(rawId, reporter);
                out.append(segment, i, valueStart).append(fixedId);
                i = j;
                changed = !fixedId.equals(rawId);
                continue;
            }
            out.append(c);
            i++;
        }
        return changed ? out.toString() : segment;
    }

    private static String readType(String segment) {
        int idx = indexOfIgnoreCase(segment, "type=");
        if (idx < 0) {
            return null;
        }
        int start = idx + 5;
        int i = start;
        while (i < segment.length()) {
            char c = segment.charAt(i);
            if (c == ',' || c == ';' || c == '@' || c == ')') {
                break;
            }
            i++;
        }
        String value = segment.substring(start, i).trim();
        return value.isEmpty() ? null : value;
    }

    private static int indexOfIgnoreCase(String input, String needle) {
        for (int i = 0; i <= input.length() - needle.length(); i++) {
            if (input.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeBareItemId(String rawId, LegacyWarnReporter reporter) {
        if (rawId == null || rawId.isEmpty()) {
            return rawId;
        }
        String trimmed = rawId.trim();
        if (trimmed.isEmpty()) {
            return rawId;
        }
        if (trimmed.startsWith("\"") || trimmed.startsWith("#") || trimmed.startsWith("{")) {
            return trimmed;
        }
        if (trimmed.indexOf('#') >= 0) {
            return trimmed;
        }
        String namespace = null;
        String path = trimmed;
        int idx = trimmed.indexOf(':');
        if (idx > 0) {
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        }
        String mappedPath = mapGoldToGolden(path);
        String loweredPath = path.toLowerCase(Locale.ROOT);
        if ("red_flower".equals(loweredPath)) {
            mappedPath = "poppy";
        } else if ("yellow_flower".equals(loweredPath)) {
            mappedPath = "dandelion";
        }
        String fixedNamespace = namespace == null ? "minecraft" : namespace.toLowerCase(Locale.ROOT);
        String sanitizedPath = sanitizePath(mappedPath);
        String fixed = fixedNamespace + ":" + sanitizedPath;
        if (namespace == null) {
            warnOnce(reporter, "LegacyItemId", "assumed namespace for '" + trimmed + "' -> '" + fixed + "'");
        } else if (!fixed.equals(trimmed)) {
            warnOnce(reporter, "LegacyItemId", "sanitized '" + trimmed + "' -> '" + fixed + "'");
        }
        return fixed;
    }

    private static String sanitizePath(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-') {
                out.append(c);
            } else if (Character.isUpperCase(c)) {
                out.append(Character.toLowerCase(c));
            } else {
                out.append('_');
            }
        }
        return out.toString();
    }

    private static String mapGoldToGolden(String path) {
        if (path == null) {
            return null;
        }
        String lowered = path.toLowerCase(Locale.ROOT);
        if (!lowered.startsWith("gold_")) {
            return path;
        }
        String suffix = lowered.substring("gold_".length());
        switch (suffix) {
            case "shovel":
            case "pickaxe":
            case "axe":
            case "sword":
            case "hoe":
            case "helmet":
            case "chestplate":
            case "leggings":
            case "boots":
            case "horse_armor":
            case "apple":
            case "carrot":
                return "golden_" + suffix;
            default:
                return path;
        }
    }

    private static String warnLegacyGroupRepeat(String input, LegacyWarnReporter reporter) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
            }
            if (depth == 0 && !inSingle && !inDouble && input.regionMatches(true, i, "group:", 0, 6)) {
                LegacyGroupRepeat repeat = LegacyGroupRepeat.parse(input, i + 6);
                if (repeat != null && !repeat.repeatExpr.isEmpty()) {
                    warnOnce(reporter, "LegacyGroupRepeat", "group:" + repeat.repeatExpr + ":(...) detected");
                }
                return input;
            }
        }
        return input;
    }

    private static String fixBrokenHashQuote(String input, LegacyWarnReporter reporter) {
        StringBuilder out = new StringBuilder(input.length());
        boolean changed = false;
        int i = 0;
        while (i < input.length()) {
            if (i + 2 < input.length() && input.charAt(i) == '\'' && input.charAt(i + 1) == '#' && input.charAt(i + 2) == '\'') {
                if (i + 3 < input.length()) {
                    char next = input.charAt(i + 3);
                    if (Character.isLetterOrDigit(next) || next == '_' || next == '(') {
                        out.append('#');
                        i += 3;
                        changed = true;
                        continue;
                    }
                }
            }
            out.append(input.charAt(i));
            i++;
        }
        if (changed) {
            warnOnce(reporter, "LegacyHashQuote", "fixed broken '#'' in drop line");
            return out.toString();
        }
        return input;
    }

    private static final class LegacyDropParts {
        private final String dropPart;
        private final java.util.List<String> attrs;

        private LegacyDropParts(String dropPart, java.util.List<String> attrs) {
            this.dropPart = dropPart;
            this.attrs = attrs;
        }

        private static LegacyDropParts parse(String input) {
            StringBuilder drop = new StringBuilder(input.length());
            java.util.List<String> attrs = new java.util.ArrayList<>();
            StringBuilder currentAttr = null;
            boolean inAttr = false;
            int depth = 0;
            boolean inSingle = false;
            boolean inDouble = false;
            int i = 0;
            while (i < input.length()) {
                char c = input.charAt(i);
                if (!inDouble && c == '\'') {
                    inSingle = !inSingle;
                } else if (!inSingle && c == '"') {
                    inDouble = !inDouble;
                } else if (!inSingle && !inDouble) {
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && depth > 0) {
                        depth--;
                    }
                }
                if (!inSingle && !inDouble && depth == 0) {
                    if (c == '@') {
                        if (inAttr && currentAttr != null) {
                            attrs.add(currentAttr.toString());
                        }
                        inAttr = true;
                        currentAttr = new StringBuilder();
                        i++;
                        continue;
                    }
                    if (!inAttr && c == ',') {
                        int j = i + 1;
                        while (j < input.length() && Character.isWhitespace(input.charAt(j))) {
                            j++;
                        }
                        if (matchAttrKeyword(input, j) != null) {
                            inAttr = true;
                            currentAttr = new StringBuilder();
                            i = j;
                            continue;
                        }
                    }
                }
                if (inAttr) {
                    currentAttr.append(c);
                } else {
                    drop.append(c);
                }
                i++;
            }
            if (inAttr && currentAttr != null) {
                attrs.add(currentAttr.toString());
            }
            return new LegacyDropParts(drop.toString(), attrs);
        }

        private String rebuild(String newDropPart, java.util.List<String> newAttrs) {
            StringBuilder out = new StringBuilder(newDropPart.length() + 8);
            out.append(newDropPart);
            if (newAttrs != null) {
                for (String attr : newAttrs) {
                    if (attr == null || attr.isEmpty()) {
                        continue;
                    }
                    out.append('@').append(attr);
                }
            }
            return out.toString();
        }
    }

    private static final class LegacyGroupRepeat {
        private final String repeatExpr;

        private LegacyGroupRepeat(String repeatExpr) {
            this.repeatExpr = repeatExpr;
        }

        private static LegacyGroupRepeat parse(String input, int startIndex) {
            int depth = 0;
            boolean inSingle = false;
            boolean inDouble = false;
            for (int i = startIndex; i < input.length() - 1; i++) {
                char c = input.charAt(i);
                if (!inDouble && c == '\'') {
                    inSingle = !inSingle;
                } else if (!inSingle && c == '"') {
                    inDouble = !inDouble;
                } else if (!inSingle && !inDouble) {
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && depth > 0) {
                        depth--;
                    }
                }
                if (!inSingle && !inDouble && depth == 0 && c == ':' && input.charAt(i + 1) == '(') {
                    String expr = input.substring(startIndex, i).trim();
                    return new LegacyGroupRepeat(expr);
                }
            }
            return null;
        }
    }

    private static void warnOnce(LegacyWarnReporter reporter, String type, String detail) {
        String ctx = SANITIZE_CONTEXT.get();
        if (ctx != null && !ctx.isBlank()) {
            detail = detail + " @ " + ctx;
        }
        if (reporter == null) {
            Log.warn("[LootPP-Legacy] {} {}", type, detail);
            return;
        }
        reporter.warnOnce(type, detail, null);
    }

    private static String preview(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        int limit = 160;
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, limit) + "...";
    }
}
