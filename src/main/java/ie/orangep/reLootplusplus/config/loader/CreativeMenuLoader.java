package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.general.CreativeMenuEntry;
import ie.orangep.reLootplusplus.config.parse.LineReader;
import ie.orangep.reLootplusplus.config.parse.NumberParser;
import ie.orangep.reLootplusplus.config.parse.Splitter;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackIndex;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CreativeMenuLoader {
    private static final String FILE = "config/general/creative_menu_additions.txt";

    private final LegacyWarnReporter warnReporter;

    public CreativeMenuLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<CreativeMenuEntry> loadAll(List<AddonPack> packs, PackIndex index) {
        List<CreativeMenuEntry> entries = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> lines = files.get(FILE);
            if (lines == null) {
                continue;
            }
            for (PackIndex.LineRecord line : lines) {
                String raw = line.rawLine();
                if (LineReader.isIgnorable(raw)) {
                    continue;
                }
                CreativeMenuEntry entry = parseLine(raw, line.sourceLoc());
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        Log.info("Loader", "Loaded {} creative menu entries", entries.size());
        return entries;
    }

    private CreativeMenuEntry parseLine(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 2) {
            warnReporter.warn("Parse", "creative menu wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String category = parts[0].trim();
        if (category.isEmpty()) {
            category = "Legacy";
        }
        String itemId = normalizeItemId(parts[1], loc);
        if (itemId == null) {
            return null;
        }
        int meta = 0;
        String nbt = "";
        if (parts.length >= 3) {
            String third = parts[2].trim();
            if (!third.isEmpty()) {
                try {
                    meta = Integer.parseInt(third);
                } catch (NumberFormatException ignored) {
                    nbt = third;
                }
            }
        }
        if (parts.length >= 4) {
            nbt = parts[3].trim();
        }
        return new CreativeMenuEntry(category, itemId, meta, nbt, loc);
    }

    private String normalizeItemId(String raw, SourceLoc loc) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String value = trimmed;
        if (!value.contains(":")) {
            warnReporter.warnOnce("LegacyItemId", "missing namespace for " + value, loc);
            value = "lootplusplus:" + value;
        }
        String normalized = sanitizeItemId(value);
        if (!normalized.equals(value)) {
            warnReporter.warnOnce("LegacyItemId", "sanitized '" + value + "' -> '" + normalized + "'", loc);
        }
        if (Identifier.tryParse(normalized) == null) {
            warnReporter.warn("LegacyItemId", "bad item id " + normalized, loc);
            return null;
        }
        return normalized;
    }

    private String sanitizeItemId(String raw) {
        int idx = raw.indexOf(':');
        if (idx <= 0) {
            return raw.toLowerCase(Locale.ROOT);
        }
        String ns = raw.substring(0, idx).toLowerCase(Locale.ROOT);
        String path = raw.substring(idx + 1);
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (net.minecraft.util.Identifier.isPathCharacterValid(c)) {
                out.append(c);
            } else if (Character.isUpperCase(c)) {
                out.append(Character.toLowerCase(c));
            } else {
                out.append('_');
            }
        }
        return ns + ":" + out;
    }
}
