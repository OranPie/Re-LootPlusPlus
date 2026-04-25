package ie.orangep.reLootplusplus.lucky.loader;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One entry from a Lucky Block addon's {@code structures.txt}.
 *
 * <p>Format: {@code ID=<id>,file=<path>[,centerX=N,centerY=N,centerZ=N,overlayStruct=<id>,blockMode=<mode>]}
 *
 * <p>Lines starting with {@code /} are comments and are skipped.
 *
 * <p>Structure files ({@code .schematic}, {@code .luckystruct}, {@code .nbt}) are loaded at
 * drop-evaluation time via {@link ie.orangep.reLootplusplus.lucky.structure.StructureFileLoader}
 * and placed using {@link ie.orangep.reLootplusplus.lucky.structure.StructurePlacer}.
 */
public record LuckyStructureEntry(
    String id,
    String file,
    int centerX,
    int centerY,
    int centerZ,
    @Nullable String overlayStruct,
    @Nullable String blockMode
) {
    /** Parses one line. Returns {@code null} for comment/blank lines or lines missing ID/file. */
    public static @Nullable LuckyStructureEntry parse(String line) {
        if (line == null) return null;
        line = line.strip();
        if (line.isEmpty() || line.startsWith("/")) return null;

        Map<String, String> attrs = new LinkedHashMap<>();
        for (String part : line.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String key = part.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String val = part.substring(eq + 1).strip();
            attrs.put(key, val);
        }

        String id = attrs.get("id");
        String file = attrs.get("file");
        if (id == null || id.isBlank() || file == null || file.isBlank()) return null;

        int cx = parseIntOr(attrs.get("centerx"), 0);
        int cy = parseIntOr(attrs.get("centery"), 0);
        int cz = parseIntOr(attrs.get("centerz"), 0);
        String overlay = attrs.get("overlaystruct");
        String blockMode = attrs.get("blockmode");

        return new LuckyStructureEntry(id.strip(), file.strip(), cx, cy, cz, overlay, blockMode);
    }

    public static List<LuckyStructureEntry> parseLines(List<String> lines) {
        List<LuckyStructureEntry> result = new ArrayList<>();
        for (String line : lines) {
            LuckyStructureEntry e = parse(line);
            if (e != null) result.add(e);
        }
        return result;
    }

    private static int parseIntOr(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.strip()); } catch (NumberFormatException e) { return def; }
    }
}
