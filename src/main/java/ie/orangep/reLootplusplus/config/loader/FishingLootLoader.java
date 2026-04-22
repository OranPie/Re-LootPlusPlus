package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.rule.FishingLootCategory;
import ie.orangep.reLootplusplus.config.model.rule.FishingLootRule;
import ie.orangep.reLootplusplus.config.parse.LineReader;
import ie.orangep.reLootplusplus.config.parse.NumberParser;
import ie.orangep.reLootplusplus.config.parse.Splitter;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.legacy.nbt.LenientNbtParser;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackIndex;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FishingLootLoader {
    private static final String DIR = "config/fishing_loot/";

    private final LegacyWarnReporter warnReporter;

    public FishingLootLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<FishingLootRule> loadLoot(List<AddonPack> packs, PackIndex index) {
        List<FishingLootRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            for (Map.Entry<String, List<PackIndex.LineRecord>> entry : files.entrySet()) {
                String path = entry.getKey();
                if (!isLootFile(path)) {
                    continue;
                }
                FishingLootCategory category = categoryFor(path);
                for (PackIndex.LineRecord line : entry.getValue()) {
                    String raw = line.rawLine();
                    if (LineReader.isIgnorable(raw)) {
                        continue;
                    }
                    FishingLootRule rule = parseLoot(raw, line.sourceLoc(), category);
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }
        }
        Log.info("Loader", "Loaded {} fishing loot rules", rules.size());
        return rules;
    }

    private FishingLootRule parseLoot(String raw, SourceLoc loc, FishingLootCategory category) {
        String[] parts = Splitter.splitRegex(raw, "-", 7);
        if (parts.length < 5) {
            warnReporter.warn("Parse", "fishing_loot wrong parts (" + parts.length + ")", loc);
            return null;
        }
        warnReporter.warnOnce("LegacyFishing", "fishing loot rule", loc);

        String itemId = normalizeItemId(parts[0], loc);
        if (itemId == null) {
            return null;
        }
        int stackSize = NumberParser.parseInt(parts[1], 1, warnReporter, loc, "stack_size");
        float damagePercent = NumberParser.parseFloat(parts[2], 0.0f, warnReporter, loc, "damage_percent");
        boolean enchanted = Boolean.parseBoolean(parts[3]);
        int weight = NumberParser.parseInt(parts[4], 1, warnReporter, loc, "weight");
        int meta = 0;
        NbtCompound nbt = null;

        if (stackSize < 1) {
            stackSize = 1;
        }
        if (damagePercent < 0) {
            damagePercent = 0.0f;
        }
        if (weight < 1) {
            weight = 1;
        }
        if (parts.length >= 6) {
            meta = NumberParser.parseInt(parts[5], 0, warnReporter, loc, "meta");
            if (meta < 0) {
                warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
                meta = 32767;
            }
        }
        if (parts.length >= 7) {
            nbt = LenientNbtParser.parseOrNull(parts[6], warnReporter, loc, "LegacyNBT");
            if (parts[6] != null && !parts[6].isEmpty() && !"{}".equals(parts[6]) && nbt == null) {
                warnReporter.warn("LegacyNBT", "fishing nbt parse failed", loc);
                return null;
            }
        }

        return new FishingLootRule(
            category,
            itemId,
            stackSize,
            damagePercent,
            enchanted,
            weight,
            meta,
            nbt,
            loc
        );
    }

    private static boolean isLootFile(String path) {
        if (!path.startsWith(DIR) || !path.endsWith(".txt")) {
            return false;
        }
        String name = path.substring(DIR.length());
        return !name.contains("amount");
    }

    private static FishingLootCategory categoryFor(String path) {
        String name = path.substring(DIR.length());
        if (name.startsWith("fish_")) {
            return FishingLootCategory.FISH;
        }
        if (name.startsWith("junk_")) {
            return FishingLootCategory.JUNK;
        }
        if (name.startsWith("treasure_")) {
            return FishingLootCategory.TREASURE;
        }
        return FishingLootCategory.LEGACY;
    }

    private String normalizeItemId(String raw, SourceLoc loc) {
        if (raw.contains(":")) {
            return validateId(raw, loc);
        }
        warnReporter.warnOnce("LegacyItemId", "missing namespace for " + raw, loc);
        return validateId("minecraft:" + raw, loc);
    }

    private String validateId(String raw, SourceLoc loc) {
        if (net.minecraft.util.Identifier.tryParse(raw) == null) {
            warnReporter.warn("LegacyItemId", "bad item id " + raw, loc);
            return null;
        }
        return raw;
    }
}
