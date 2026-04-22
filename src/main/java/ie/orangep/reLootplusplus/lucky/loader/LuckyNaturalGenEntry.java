package ie.orangep.reLootplusplus.lucky.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One entry from a Lucky Block addon's {@code natural_gen.txt}.
 *
 * <p>Format: section headers {@code >surface}, {@code >underground}, {@code >nether}, {@code >end}
 * followed by Lucky drop-format lines with {@code @chance=N} where N is the rarity denominator
 * (1/N chunks receive this generation event).
 *
 * <p>Only simple {@code type=block} entries are registered as world-gen features;
 * group and structure entries emit a log and are skipped.
 */
public record LuckyNaturalGenEntry(
    /** Dimension section: "surface", "underground", "nether", "end". */
    String dimension,
    /** The drop line content without the trailing {@code @chance=N} suffix. */
    String cleanDropLine,
    /** 1/rarity chance per chunk (from {@code @chance=N}). */
    int rarity
) {
    private static final Pattern CHANCE_SUFFIX =
        Pattern.compile("@chance\\s*=\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_ID_PATTERN =
        Pattern.compile("(?:^|,)ID=([^,@(\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LUCK_TILE_PATTERN =
        Pattern.compile("tileEntity\\s*=\\s*\\([^)]*[Ll]uck\\s*=\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);

    /** Returns true if this is a simple {@code type=block} entry (not a group or structure). */
    public boolean isSimpleBlock() {
        String clean = cleanDropLine.trim();
        if (clean.startsWith("group(") || clean.startsWith("group (")) return false;
        return clean.startsWith("type=block") || clean.startsWith("type = block") ||
               (clean.contains("type=block") && !clean.startsWith("group"));
    }

    /** Extracts {@code ID=<blockId>} from a simple {@code type=block} drop line. */
    public String extractBlockId() {
        Matcher m = BLOCK_ID_PATTERN.matcher(cleanDropLine);
        if (m.find()) return m.group(1).strip();
        return null;
    }

    /** Extracts the {@code Luck=N} value from the {@code tileEntity=(Luck=N)} attribute, or 0. */
    public int extractLuck() {
        Matcher m = LUCK_TILE_PATTERN.matcher(cleanDropLine);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    /**
     * Parses a {@code natural_gen.txt} into a list of entries.
     * Lines starting with {@code //} are ignored; blank lines are skipped.
     */
    public static List<LuckyNaturalGenEntry> parseLines(List<String> lines) {
        List<LuckyNaturalGenEntry> result = new ArrayList<>();
        String currentDimension = "surface"; // default before any section header

        for (String line : lines) {
            if (line == null) continue;
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("//")) continue;

            if (stripped.startsWith(">")) {
                currentDimension = stripped.substring(1).strip().toLowerCase(Locale.ROOT);
                if (currentDimension.isEmpty()) currentDimension = "surface";
                continue;
            }

            Matcher m = CHANCE_SUFFIX.matcher(stripped);
            int rarity = 200; // default if no @chance present
            String cleanLine = stripped;
            if (m.find()) {
                try { rarity = Math.max(1, Integer.parseInt(m.group(1))); } catch (NumberFormatException ignored) {}
                cleanLine = stripped.substring(0, m.start()).strip();
            }

            if (cleanLine.isEmpty()) continue;
            result.add(new LuckyNaturalGenEntry(currentDimension, cleanLine, rarity));
        }
        return result;
    }
}
