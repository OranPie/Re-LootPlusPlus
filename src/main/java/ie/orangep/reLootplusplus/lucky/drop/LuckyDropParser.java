package ie.orangep.reLootplusplus.lucky.drop;

import ie.orangep.reLootplusplus.legacy.LegacyDropSanitizer;
import ie.orangep.reLootplusplus.lucky.attr.LuckyAttr;
import ie.orangep.reLootplusplus.lucky.attr.LuckyAttrParser;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts raw drop text lines from {@code drops.txt} into {@link LuckyDropLine} instances.
 *
 * <p>Processing pipeline per line:
 * <ol>
 *   <li>Skip blank lines and comments ({@code /...})</li>
 *   <li>Run {@link LegacyDropSanitizer#sanitize} for legacy compat</li>
 *   <li>Parse with {@link LuckyAttrParser}</li>
 *   <li>Handle {@code group(...)} syntax into {@link LuckyDropLine#isGroup()} entries</li>
 * </ol>
 */
public final class LuckyDropParser {

    private final LegacyWarnReporter warnReporter;
    private final SourceLoc sourceLoc;

    public LuckyDropParser(LegacyWarnReporter warnReporter, SourceLoc sourceLoc) {
        this.warnReporter = warnReporter;
        this.sourceLoc = sourceLoc;
    }

    /**
     * Parses a list of raw drop text lines into {@link LuckyDropLine} instances.
     * Lines that are blank, comments, or malformed are skipped with a WARN.
     * After successful parse, warns about any attrs not recognised by the action handler.
     */
    public List<LuckyDropLine> parseLines(List<String> rawLines) {
        List<LuckyDropLine> result = new ArrayList<>();
        int lineNo = 0;
        for (String rawLine : rawLines) {
            lineNo++;
            try {
                LuckyDropLine line = parseLine(rawLine);
                if (line != null) result.add(line);
            } catch (Exception e) {
                SourceLoc loc = sourceLoc != null
                    ? new SourceLoc(sourceLoc.packId(), sourceLoc.packPath(),
                                    sourceLoc.innerPath(), lineNo, rawLine)
                    : null;
                warnReporter.warn("LuckyDropParse",
                    "parse error at line " + lineNo + ": " + e.getClass().getSimpleName()
                        + ": " + e.getMessage() + " → " + preview(rawLine), loc);
            }
        }
        return result;
    }

    /**
     * Parses a single raw drop line. Returns null for blank/comment lines.
     */
    public LuckyDropLine parseLine(String rawLine) {
        if (rawLine == null) return null;
        String line = rawLine.strip();
        if (line.isEmpty() || line.startsWith("/")) return null;

        // Sanitize for legacy compat
        line = LegacyDropSanitizer.sanitize(line, warnReporter);
        if (line == null || line.isEmpty() || line.startsWith("/")) return null;

        // Check for group(...) syntax at the top level
        if (isGroupLine(line)) {
            Log.debug("LuckyDrop", "[PARSE] GROUP line: " + preview(line));
            return parseGroupLine(line);
        }

        // Regular attr line
        LuckyAttrParser.ParsedDropLine parsed = LuckyAttrParser.parse(line);
        if (parsed == null) return null;

        // Warn if bare @chance was used (no '=' after @chance)
        if (rawLine.contains("@chance") && !rawLine.contains("@chance=")) {
            warnReporter.warnOnce("LuckyAttrBareChance", "bare @chance normalised to chance=1", sourceLoc);
        }

        LuckyDropLine result = LuckyDropLine.of(parsed.attrs(), parsed.luckWeight(), parsed.chance());
        warnIgnoredFields(result, rawLine);
        return result;
    }

    /** Emits WARNs for any attrs present in the drop that are not used by the action handler. */
    private void warnIgnoredFields(LuckyDropLine drop, String rawLine) {
        Set<String> ignored = LuckyDropFieldRegistry.ignoredFields(drop);
        if (ignored.isEmpty()) return;
        // Sort for stable warnOnce deduplication key (same ignored-field combo = one warn per pack)
        java.util.List<String> sortedIgnored = new java.util.ArrayList<>(ignored);
        java.util.Collections.sort(sortedIgnored);
        String key = "type=" + drop.type() + " ignored=" + sortedIgnored;
        warnReporter.warnOnce("LuckyIgnoredField",
            key + " (e.g. " + preview(rawLine) + ")",
            sourceLoc);
    }

    // -------------------------------------------------------------------------
    // Group parsing
    // -------------------------------------------------------------------------

    /**
     * Checks if the line starts with {@code group(} or {@code group:#count:(}.
     * Must be at the top level (not inside a type= value).
     */
    private static boolean isGroupLine(String line) {
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        // Must start with "group" not as a value of a key=
        return lower.startsWith("group(") || lower.matches("group:#[^:]+:.*");
    }

    /**
     * Parses group lines of the form:
     * <ul>
     *   <li>{@code group(ID=a;ID=b)@luck=1}</li>
     *   <li>{@code group:#rand(1,3):(ID=a;ID=b;ID=c)@luck=2}</li>
     * </ul>
     */
    private LuckyDropLine parseGroupLine(String line) {
        // Extract @luck and @chance suffixes first
        int luckWeight = 0;
        float chance = 1.0f;
        int atIdx = findTopLevelAt(line);
        String suffixPart = atIdx >= 0 ? line.substring(atIdx) : "";
        String groupPart = atIdx >= 0 ? line.substring(0, atIdx).trim() : line.trim();

        // Parse @luck=N @chance=N from suffix
        for (String seg : suffixPart.split("@")) {
            if (seg.startsWith("luck=")) {
                try { luckWeight = Integer.parseInt(seg.substring(5).trim()); } catch (NumberFormatException ignored) {}
            } else if (seg.startsWith("chance=")) {
                try { chance = Float.parseFloat(seg.substring(7).trim()); } catch (NumberFormatException ignored) {}
            }
        }

        // Parse group count and entries content
        int groupCount = 1; // default: pick ALL entries in group
        boolean selectOne = false;
        String entriesContent;

        if (groupPart.toLowerCase(java.util.Locale.ROOT).startsWith("group:#")) {
            // group:#rand(N,M):(entries) or group:#count:(entries)
            int firstColon = groupPart.indexOf(':');
            int secondColon = groupPart.indexOf(':', firstColon + 1);
            String countPart = groupPart.substring(firstColon + 1, secondColon >= 0 ? secondColon : groupPart.length());
            countPart = countPart.trim();
            if (countPart.startsWith("#rand(")) {
                // group:#rand(1,3): — pick 1–3 entries from the sub-group list
                groupCount = -1; // marker: use randCount from inner group
                selectOne = false;
            } else {
                try { groupCount = Integer.parseInt(countPart); } catch (NumberFormatException ignored) { groupCount = 1; }
            }
            // Content is after the second colon
            entriesContent = secondColon >= 0 ? groupPart.substring(secondColon + 1).trim() : "";
            // Strip outer parens if present
            if (entriesContent.startsWith("(") && entriesContent.endsWith(")")) {
                entriesContent = entriesContent.substring(1, entriesContent.length() - 1);
            }
        } else {
            // group(entries)
            int openParen = groupPart.indexOf('(');
            int closeParen = findMatchingClose(groupPart, openParen, '(', ')');
            if (openParen < 0 || closeParen < 0) {
                warnReporter.warn("LuckyDropParse", "malformed group: " + preview(line), sourceLoc);
                return null;
            }
            entriesContent = groupPart.substring(openParen + 1, closeParen);
            groupCount = -1; // ALL entries execute
        }

        // Parse group entries (semicolon-separated drop lines)
        List<LuckyDropLine> entries = parseGroupEntries(entriesContent);
        Log.debug("LuckyDrop", String.format("[PARSE] group parsed — count=%d entries=%d raw='%s'",
            groupCount, entries.size(), preview(entriesContent)));
        return LuckyDropLine.group(entries, luckWeight, chance, groupCount);
    }

    /** Splits group entries by top-level semicolons and parses each. */
    private List<LuckyDropLine> parseGroupEntries(String content) {
        List<LuckyDropLine> entries = new ArrayList<>();
        List<String> parts = splitTopLevel(content, ';');
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                LuckyDropLine entry = parseLine(trimmed);
                if (entry != null) entries.add(entry);
            } catch (Exception e) {
                warnReporter.warn("LuckyDropParse", "error in group entry: " + preview(trimmed), sourceLoc);
            }
        }
        return entries;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Find the first top-level '@' sign (not inside brackets). */
    private static int findTopLevelAt(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') { if (depth > 0) depth--; }
            else if (c == '@' && depth == 0) return i;
        }
        return -1;
    }

    /** Splits a string by {@code separator} at top-level only (respects brackets). */
    private static List<String> splitTopLevel(String s, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') { if (depth > 0) depth--; }
            else if (c == separator && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    /** Finds the matching close bracket position. */
    private static int findMatchingClose(String s, int openIdx, char open, char close) {
        if (openIdx < 0 || openIdx >= s.length()) return -1;
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String preview(String s) {
        if (s == null) return "(null)";
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
