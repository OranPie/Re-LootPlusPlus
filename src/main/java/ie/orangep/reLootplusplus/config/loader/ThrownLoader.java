package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.config.model.rule.ThrownDef;
import ie.orangep.reLootplusplus.config.parse.LineReader;
import ie.orangep.reLootplusplus.config.parse.NumberParser;
import ie.orangep.reLootplusplus.config.parse.Splitter;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ThrownLoader {
    private static final String FILE = "config/item_additions/thrown.txt";

    private final LegacyWarnReporter warnReporter;
    private final BlockDropsLoader dropParser;

    public ThrownLoader(LegacyWarnReporter warnReporter, BlockDropsLoader dropParser) {
        this.warnReporter = warnReporter;
        this.dropParser = dropParser;
    }

    public List<ThrownDef> loadAll(List<AddonPack> packs, PackIndex index) {
        List<ThrownDef> defs = new ArrayList<>();
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
                ThrownDef def = parseLine(raw, line.sourceLoc());
                if (def != null) {
                    defs.add(def);
                }
            }
        }
        Log.info("Loader", "Loaded {} thrown definitions", defs.size());
        return defs;
    }

    private ThrownDef parseLine(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 7) {
            warnReporter.warn("Parse", "thrown wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String itemId = normalizeItemId(parts[0], loc);
        String displayName = parts[1];
        boolean shines = Boolean.parseBoolean(parts[2]);
        float damage = NumberParser.parseFloat(parts[3], 0.0f, warnReporter, loc, "damage");
        float velocity = NumberParser.parseFloat(parts[4], 0.0f, warnReporter, loc, "velocity");
        float gravity = NumberParser.parseFloat(parts[5], 0.0f, warnReporter, loc, "gravity");
        float inaccuracy = NumberParser.parseFloat(parts[6], 0.0f, warnReporter, loc, "inaccuracy");
        float dropChance = 0.0f;
        if (parts.length >= 8) {
            dropChance = NumberParser.parseFloat(parts[7], 0.0f, warnReporter, loc, "dropChance");
        }

        List<DropGroup> groups = new ArrayList<>();
        for (int i = 8; i < parts.length; i++) {
            String groupRaw = parts[i];
            if (groupRaw.isEmpty()) {
                continue;
            }
            DropGroup group = dropParser.parseGroupForThrown(groupRaw, loc);
            if (group != null) {
                groups.add(group);
            }
        }

        return new ThrownDef(
            itemId,
            displayName,
            shines,
            damage,
            velocity,
            gravity,
            inaccuracy,
            dropChance,
            groups,
            loc
        );
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
        return normalized;
    }

    private String sanitizeItemId(String raw) {
        int idx = raw.indexOf(':');
        if (idx <= 0) {
            return raw.toLowerCase(java.util.Locale.ROOT);
        }
        String ns = raw.substring(0, idx).toLowerCase(java.util.Locale.ROOT);
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
