package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.rule.ChestAmountRule;
import ie.orangep.reLootplusplus.config.model.rule.ChestLootRule;
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

public final class ChestContentLoader {
    private static final String DIR = "config/chest_content/";

    private final LegacyWarnReporter warnReporter;

    public ChestContentLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<ChestAmountRule> loadAmounts(List<AddonPack> packs, PackIndex index) {
        List<ChestAmountRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            for (Map.Entry<String, List<PackIndex.LineRecord>> entry : files.entrySet()) {
                String path = entry.getKey();
                if (!isAmountFile(path)) {
                    continue;
                }
                for (PackIndex.LineRecord line : entry.getValue()) {
                    String raw = line.rawLine();
                    if (LineReader.isIgnorable(raw)) {
                        continue;
                    }
                    ChestAmountRule rule = parseAmount(raw, line.sourceLoc());
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }
        }
        Log.LOGGER.info("Loaded {} chest amount rules", rules.size());
        return rules;
    }

    public List<ChestLootRule> loadLoot(List<AddonPack> packs, PackIndex index) {
        List<ChestLootRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            for (Map.Entry<String, List<PackIndex.LineRecord>> entry : files.entrySet()) {
                String path = entry.getKey();
                if (!isLootFile(path)) {
                    continue;
                }
                for (PackIndex.LineRecord line : entry.getValue()) {
                    String raw = line.rawLine();
                    if (LineReader.isIgnorable(raw)) {
                        continue;
                    }
                    ChestLootRule rule = parseLoot(raw, line.sourceLoc());
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }
        }
        Log.LOGGER.info("Loaded {} chest loot rules", rules.size());
        return rules;
    }

    private ChestAmountRule parseAmount(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "-");
        if (parts.length != 3) {
            warnReporter.warn("Parse", "chest_amounts wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String chestType = parts[0];
        warnReporter.warnOnce("LegacyChestType", "chest type " + chestType, loc);
        int min = NumberParser.parseInt(parts[1], 0, warnReporter, loc, "min_items");
        int max = NumberParser.parseInt(parts[2], min, warnReporter, loc, "max_items");
        if (min < 0) {
            min = 0;
        }
        if (max < min) {
            max = min;
        }
        return new ChestAmountRule(chestType, min, max, loc);
    }

    private ChestLootRule parseLoot(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "-", 7);
        if (parts.length < 5) {
            warnReporter.warn("Parse", "chest_loot wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String chestType = parts[0];
        warnReporter.warnOnce("LegacyChestType", "chest type " + chestType, loc);
        String itemId = parts[1];
        int min = NumberParser.parseInt(parts[2], 0, warnReporter, loc, "min_count");
        int max = NumberParser.parseInt(parts[3], min, warnReporter, loc, "max_count");
        int weight = NumberParser.parseInt(parts[4], 1, warnReporter, loc, "weight");
        int meta = 0;
        String nbt = "{}";

        if (min < 0) {
            min = 0;
        }
        if (max < min) {
            max = min;
        }
        if (weight < 0) {
            weight = 0;
        }
        if (parts.length >= 6) {
            meta = NumberParser.parseInt(parts[5], 0, warnReporter, loc, "meta");
            if (meta < 0) {
                meta = 0;
            }
            warnReporter.warnOnce("LegacyMeta", "chest meta used " + meta, loc);
        }
        if (parts.length >= 7) {
            nbt = parts[6];
            if (!nbt.isEmpty() && !"{}".equals(nbt)) {
                warnReporter.warnOnce("LegacyNBT", "chest nbt", loc);
            }
        }

        return new ChestLootRule(chestType, itemId, min, max, weight, meta, nbt, loc);
    }

    private static boolean isAmountFile(String path) {
        if (!path.startsWith(DIR) || !path.endsWith(".txt")) {
            return false;
        }
        String name = path.substring(DIR.length());
        return name.contains("amount");
    }

    private static boolean isLootFile(String path) {
        if (!path.startsWith(DIR) || !path.endsWith(".txt")) {
            return false;
        }
        String name = path.substring(DIR.length());
        return !name.contains("amount");
    }
}
