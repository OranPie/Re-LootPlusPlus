package ie.orangep.reLootplusplus.lucky.crafting;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.io.PackFileReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Reads {@code luck_crafting.txt} from each addon pack and merges all item→luck-delta mappings.
 *
 * <p>Format: one entry per line, {@code item_id=delta}, e.g. {@code diamond=12} or {@code rotten_flesh=-5}.
 * Lines starting with {@code /} are comments. Blank lines are skipped.
 * Legacy 1.8 meta-encoded IDs like {@code golden_apple:1=100} are normalized: the {@code :N} meta suffix
 * is stripped (with WARN) since meta does not exist in 1.18.
 */
public final class LuckyLuckCraftingLoader {

    private static volatile Map<String, Integer> modifiers = Collections.emptyMap();

    /**
     * Built-in fallback modifiers used when no addon provides luck_crafting.txt.
     * These mirror the common values found in virtually all Lucky Block addons.
     */
    private static final Map<String, Integer> BUILT_IN_DEFAULTS = Map.of(
        "emerald",       1,
        "emerald_block", 10,
        "diamond",       2,
        "diamond_block", 20,
        "gold_ingot",    1,
        "gold_block",    9,
        "nether_star",   100
    );

    private LuckyLuckCraftingLoader() {}

    /** Returns the current merged luck-modifier map (item_id → luck delta). */
    public static Map<String, Integer> getModifiers() {
        return modifiers;
    }

    /**
     * Returns the effective modifiers: addon-provided values merged with built-in defaults.
     * Addon values always take precedence. Built-in defaults fill in any gaps.
     */
    public static Map<String, Integer> getEffectiveModifiers() {
        Map<String, Integer> effective = new LinkedHashMap<>(BUILT_IN_DEFAULTS);
        effective.putAll(modifiers); // addon values override built-ins
        return Collections.unmodifiableMap(effective);
    }

    /**
     * Loads (or re-loads) luck_crafting.txt from all packs. Thread-safe via volatile swap.
     */
    public static void load(List<AddonPack> packs, LegacyWarnReporter warnReporter) {
        Map<String, Integer> merged = new LinkedHashMap<>();

        for (AddonPack pack : packs) {
            SourceLoc loc = new SourceLoc(pack.id(),
                pack.zipPath() != null ? pack.zipPath().toString() : pack.id(),
                "luck_crafting.txt", 0, "");
            loadPack(pack, merged, warnReporter, loc);
        }

        modifiers = Collections.unmodifiableMap(merged);
    }

    private static void loadPack(AddonPack pack, Map<String, Integer> out,
                                  LegacyWarnReporter warnReporter, SourceLoc loc) {
        Path zipPath = pack.zipPath();
        if (zipPath == null) return;

        try {
            List<String> lines = null;

            if (Files.isDirectory(zipPath)) {
                // Try both root and config/ subdirectory
                Path f = zipPath.resolve("luck_crafting.txt");
                if (!Files.exists(f)) f = zipPath.resolve("config/luck_crafting.txt");
                if (Files.exists(f)) {
                    byte[] bytes = Files.readAllBytes(f);
                    String content;
                    try {
                        content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                    }
                    if (!content.isEmpty() && content.charAt(0) == '\uFEFF') content = content.substring(1);
                    lines = List.of(content.split("\r?\n", -1));
                }
            } else if (Files.isRegularFile(zipPath)) {
                try (ZipFile zip = new ZipFile(zipPath.toFile())) {
                    var entry = zip.getEntry("luck_crafting.txt");
                    if (entry == null) entry = zip.getEntry("config/luck_crafting.txt");
                    if (entry != null) lines = PackFileReader.readLines(zip, entry);
                }
            }

            if (lines == null) return;

            int lineNum = 0;
            for (String raw : lines) {
                lineNum++;
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("/")) continue;

                // Parse key=value
                int eq = line.indexOf('=');
                if (eq < 1) {
                    warnReporter.warnOnce("LuckyLuckCraft",
                        "malformed luck_crafting line: " + preview(line),
                        new SourceLoc(loc.packId(), loc.packPath(), loc.innerPath(), lineNum, raw));
                    continue;
                }

                String rawId = line.substring(0, eq).strip();
                String rawVal = line.substring(eq + 1).strip();

                // Strip legacy meta suffix (golden_apple:1 → golden_apple)
                String id = rawId;
                if (id.contains(":")) {
                    int colon = id.lastIndexOf(':');
                    String meta = id.substring(colon + 1);
                    if (meta.chars().allMatch(Character::isDigit)) {
                        id = id.substring(0, colon);
                        warnReporter.warnOnce("LegacyMeta",
                            "luck_crafting meta stripped: " + rawId + " → " + id,
                            new SourceLoc(loc.packId(), loc.packPath(), loc.innerPath(), lineNum, raw));
                    }
                }

                int delta;
                try {
                    delta = Integer.parseInt(rawVal);
                } catch (NumberFormatException e) {
                    warnReporter.warnOnce("LuckyLuckCraft",
                        "non-integer luck delta '" + rawVal + "' for " + id,
                        new SourceLoc(loc.packId(), loc.packPath(), loc.innerPath(), lineNum, raw));
                    continue;
                }

                out.put(id, delta);
            }
        } catch (Exception e) {
            warnReporter.warn("LuckyLuckCraft",
                "error reading luck_crafting.txt from " + pack.id() + ": " + e.getMessage(), loc);
        }
    }

    private static String preview(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
