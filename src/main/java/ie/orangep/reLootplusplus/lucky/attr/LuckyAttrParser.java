package ie.orangep.reLootplusplus.lucky.attr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Lucky Block's {@code key=value} attribute format used in {@code drops.txt}.
 *
 * <h3>Grammar summary</h3>
 * <pre>
 * drop-line    ::= attrs-part luck-suffixes
 * luck-suffixes::= ('@' key '=' value)*          // @luck=N, @chance=N
 * attrs-part   ::= key-value (',' key-value)*
 * key-value    ::= key '=' value
 * value        ::= string-value | dict-value | list-value
 * dict-value   ::= '(' attrs-part ')'
 * list-value   ::= '[' list-items ']'
 * list-items   ::= value (';' value)*
 * string-value ::= [^,;()[\]=]*  (may include template vars like #rand(1,3))
 * </pre>
 *
 * <h3>Special cases handled</h3>
 * <ul>
 *   <li>Bare {@code @chance} (no {@code =} value) → normalised to {@code chance=1} (caller WARNs).</li>
 *   <li>Comments start with {@code /}; empty lines are ignored by caller.</li>
 *   <li>Malformed input → best-effort parse with the remaining well-formed fragment.</li>
 * </ul>
 */
public final class LuckyAttrParser {

    private final String input;
    private int pos;

    private LuckyAttrParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses the full attribute string from a drop line.
     * Returns null if {@code raw} is null/blank/comment.
     */
    public static ParsedDropLine parse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("/")) return null;

        // Split off trailing @luck=N and @chance=N suffixes BEFORE parsing attrs.
        // Suffixes start at the LAST top-level '@' that is followed by luck= or chance= or just 'chance'.
        int luckWeight = 0;
        float chance = 1.0f;

        // Scan from end for @luck= and @chance= tokens at the top level
        // (outside any brackets).
        int[] atIdxs = findTopLevelAtSigns(trimmed);
        int attrEnd = trimmed.length();
        for (int idx : atIdxs) {
            String suffix = trimmed.substring(idx + 1); // after '@'
            if (suffix.startsWith("luck=")) {
                try {
                    luckWeight = Integer.parseInt(suffix.substring(5).trim());
                } catch (NumberFormatException ignored) {
                }
                attrEnd = Math.min(attrEnd, idx);
            } else if (suffix.startsWith("chance=")) {
                try {
                    chance = Float.parseFloat(suffix.substring(7).trim());
                } catch (NumberFormatException ignored) {
                }
                attrEnd = Math.min(attrEnd, idx);
            } else if (suffix.equalsIgnoreCase("chance")) {
                // Bare @chance — normalise to chance=1 (caller may WARN).
                chance = 1.0f;
                attrEnd = Math.min(attrEnd, idx);
            }
        }

        String attrsPart = trimmed.substring(0, attrEnd).trim();
        if (attrsPart.isEmpty()) {
            return new ParsedDropLine(new LinkedHashMap<>(), luckWeight, chance);
        }

        LuckyAttrParser parser = new LuckyAttrParser(attrsPart);
        Map<String, LuckyAttr> attrs = parser.parseAttrs();
        return new ParsedDropLine(attrs, luckWeight, chance);
    }

    // -------------------------------------------------------------------------
    // Internal parsing
    // -------------------------------------------------------------------------

    /** Parses a sequence of key=value pairs separated by commas. */
    private Map<String, LuckyAttr> parseAttrs() {
        Map<String, LuckyAttr> result = new LinkedHashMap<>();
        skipWhitespace();
        while (pos < input.length()) {
            int prevPos = pos;
            String key = parseKey();
            if (key == null || key.isEmpty()) {
                if (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                } else if (pos == prevPos && pos < input.length()) {
                    pos++; // skip unexpected char to avoid infinite loop
                }
                skipWhitespace();
                continue;
            }
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == '=') {
                pos++; // consume '='
                LuckyAttr value = parseValue();
                result.put(key, value);
            } else {
                // Key without value — treat as key=true (e.g. bare flags)
                result.put(key, LuckyAttr.StringAttr.of(key));
            }
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ',') {
                pos++; // consume ','
                skipWhitespace();
            }
        }
        return result;
    }

    /** Parses comma-separated key=value pairs inside a dict context ending at {@code endChar}. */
    private Map<String, LuckyAttr> parseDictAttrs(char endChar) {
        Map<String, LuckyAttr> result = new LinkedHashMap<>();
        skipWhitespace();
        while (pos < input.length() && input.charAt(pos) != endChar) {
            int prevPos = pos;
            String key = parseKey();
            if (key == null || key.isEmpty()) {
                if (pos < input.length() && (input.charAt(pos) == ',' || input.charAt(pos) == ';')) {
                    pos++;
                } else if (pos == prevPos && pos < input.length()) {
                    pos++; // skip unexpected char (e.g. ']' inside dict) to break infinite loop
                }
                skipWhitespace();
                continue;
            }
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == '=') {
                pos++; // consume '='
                LuckyAttr value = parseValue();
                result.put(key, value);
            } else {
                result.put(key, LuckyAttr.StringAttr.of(key));
            }
            skipWhitespace();
            if (pos < input.length() && (input.charAt(pos) == ',' || input.charAt(pos) == ';')) {
                pos++;
                skipWhitespace();
            }
        }
        return result;
    }

    /** Reads a key: alphanumeric + '_' + '#' and dot characters. Stops at '=' ',' ';' '(' ')' '[' ']'. */
    private String parseKey() {
        skipWhitespace();
        int start = pos;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '=' || c == ',' || c == ';' || c == '(' || c == ')' || c == '[' || c == ']') break;
            pos++;
        }
        return input.substring(start, pos).trim();
    }

    /** Parses a value: string, dict (...), or list [...]. */
    private LuckyAttr parseValue() {
        skipWhitespace();
        if (pos >= input.length()) return LuckyAttr.StringAttr.of("");

        char c = input.charAt(pos);
        if (c == '(') {
            pos++; // consume '('
            Map<String, LuckyAttr> dict = parseDictAttrs(')');
            if (pos < input.length() && input.charAt(pos) == ')') pos++; // consume ')'
            return LuckyAttr.DictAttr.of(dict);
        } else if (c == '[') {
            pos++; // consume '['
            List<LuckyAttr> items = parseListItems();
            if (pos < input.length() && input.charAt(pos) == ']') pos++; // consume ']'
            return LuckyAttr.ListAttr.of(items);
        } else {
            return LuckyAttr.StringAttr.of(parseStringValue());
        }
    }

    /** Parses a list of values separated by ';' inside [...]. */
    private List<LuckyAttr> parseListItems() {
        List<LuckyAttr> items = new ArrayList<>();
        skipWhitespace();
        while (pos < input.length() && input.charAt(pos) != ']') {
            int prevPos = pos;
            LuckyAttr item = parseValue();
            skipWhitespace();
            if (pos == prevPos) {
                // parseValue made no progress (e.g. stuck on ')' closing an outer dict).
                // Skip the stuck character to break the infinite loop.
                pos++;
                continue;
            }
            items.add(item);
            if (pos < input.length() && input.charAt(pos) == ';') {
                pos++; // consume ';'
                skipWhitespace();
            }
        }
        return items;
    }

    /**
     * Parses a raw string value that may include nested balanced bracket groups (template calls).
     * Stops at top-level ',' ';' ')' ']' that are not inside brackets.
     */
    private String parseStringValue() {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '(' || c == '[') {
                depth++;
                sb.append(c);
                pos++;
            } else if (c == ')' || c == ']') {
                if (depth == 0) break; // closing bracket belongs to parent
                depth--;
                sb.append(c);
                pos++;
            } else if ((c == ',' || c == ';') && depth == 0) {
                break; // separator at top level
            } else {
                // Handle quoted strings
                if (c == '"') {
                    sb.append(c);
                    pos++;
                    while (pos < input.length()) {
                        char q = input.charAt(pos);
                        sb.append(q);
                        pos++;
                        if (q == '"') break;
                        if (q == '\\' && pos < input.length()) {
                            sb.append(input.charAt(pos));
                            pos++;
                        }
                    }
                } else {
                    sb.append(c);
                    pos++;
                }
            }
        }
        return sb.toString().trim();
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    // -------------------------------------------------------------------------
    // Top-level @ sign detection
    // -------------------------------------------------------------------------

    /**
     * Returns the positions of '@' characters that are at the top level
     * (not inside any parentheses or brackets).
     */
    private static int[] findTopLevelAtSigns(String s) {
        List<Integer> result = new ArrayList<>();
        int depth = 0;
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuote) {
                if (c == '\\' && i + 1 < s.length()) {
                    i++; // skip escaped char
                } else if (c == '"') {
                    inQuote = false;
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                if (depth > 0) depth--;
            } else if (c == '@' && depth == 0) {
                result.add(i);
            }
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    public record ParsedDropLine(Map<String, LuckyAttr> attrs, int luckWeight, float chance) {

        /** Returns the string value for a key, checking both lower-case and original. */
        public String getString(String key) {
            LuckyAttr v = attrs.get(key);
            if (v == null) v = attrs.get(key.toLowerCase(java.util.Locale.ROOT));
            if (v == null) {
                // Lucky Block is case-insensitive for some keys: try case-insensitive search
                for (Map.Entry<String, LuckyAttr> e : attrs.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(key)) {
                        v = e.getValue();
                        break;
                    }
                }
            }
            if (v instanceof LuckyAttr.StringAttr s) return s.value();
            return v != null ? v.toString() : null;
        }

        public LuckyAttr get(String key) {
            LuckyAttr v = attrs.get(key);
            if (v == null) {
                for (Map.Entry<String, LuckyAttr> e : attrs.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(key)) {
                        return e.getValue();
                    }
                }
            }
            return v;
        }

        /** Returns the drop type string: defaults to "item" if absent. */
        public String type() {
            String t = getString("type");
            return t != null && !t.isEmpty() ? t.toLowerCase(java.util.Locale.ROOT) : "item";
        }

        /** Returns the ID (tries "ID", "id", "Id"). */
        public String id() {
            String v = getString("ID");
            if (v == null) v = getString("id");
            if (v == null) v = getString("Id");
            return v;
        }
    }
}
