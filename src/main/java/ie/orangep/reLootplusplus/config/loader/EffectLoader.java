package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.key.ItemKey;
import ie.orangep.reLootplusplus.config.model.key.BlockKey;
import ie.orangep.reLootplusplus.config.model.rule.EffectRule;
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

public final class EffectLoader {
    private static final String DIR = "config/item_effects/";
    private static final String PREFIX_COMMAND = "command_";

    private final LegacyWarnReporter warnReporter;

    public EffectLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<EffectRule> loadAll(List<AddonPack> packs, PackIndex index) {
        List<EffectRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            for (Map.Entry<String, List<PackIndex.LineRecord>> entry : files.entrySet()) {
                String path = entry.getKey();
                if (!path.startsWith(DIR)) {
                    continue;
                }
                String file = path.substring(DIR.length());
                if (file.startsWith(PREFIX_COMMAND) || !file.endsWith(".txt")) {
                    continue;
                }
                String triggerKey = file.substring(0, file.length() - 4);
                rules.addAll(parseFile(triggerKey, entry.getValue()));
            }
        }
        Log.info("Loader", "Loaded {} effect rules", rules.size());
        return rules;
    }

    private List<EffectRule> parseFile(String triggerKey, List<PackIndex.LineRecord> lines) {
        List<EffectRule> rules = new ArrayList<>();
        for (PackIndex.LineRecord line : lines) {
            String raw = line.rawLine();
            if (LineReader.isIgnorable(raw)) {
                continue;
            }
            EffectRule rule = parseLine(triggerKey, raw, line.sourceLoc());
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    private EffectRule parseLine(String triggerKey, String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        boolean isBlockTrigger = isBlockTrigger(triggerKey);
        if (isBlockTrigger) {
            if (parts.length < 7) {
                warnReporter.warn("Parse", "effect block wrong parts (" + parts.length + ")", loc);
                return null;
            }
        } else if (parts.length < 8) {
            warnReporter.warn("Parse", "effect wrong parts (" + parts.length + ")", loc);
            return null;
        }

        String effectId;
        int duration;
        int amplifier;
        float probability;
        String particleType;

        if (isBlockTrigger) {
            String blockName = parts[0];
            int meta = NumberParser.parseInt(parts[1], 0, warnReporter, loc, "block_meta");
            if (meta < 0) {
                warnReporter.warnOnce("LegacyMeta", "block meta wildcard " + meta, loc);
                meta = 32767;
            }
            effectId = parts[2];
            duration = NumberParser.parseInt(parts[3], 10, warnReporter, loc, "duration");
            amplifier = NumberParser.parseInt(parts[4], 0, warnReporter, loc, "amplifier");
            probability = NumberParser.parseFloat(parts[5], 1.0f, warnReporter, loc, "probability");
            particleType = parts[6];

            if (isNumeric(effectId)) {
                warnReporter.warnOnce("LegacyEffect", "numeric id " + effectId, loc);
            }

            BlockKey blockKey = new BlockKey(blockName, meta);
            return new EffectRule(
                triggerKey,
                blockKey,
                effectId,
                duration,
                amplifier,
                probability,
                particleType,
                loc
            );
        }

        String itemName = parts[0];
        if (isWildcardItem(itemName, loc)) {
            itemName = "*";
        } else {
            itemName = normalizeItemId(itemName, loc);
            if (itemName == null) {
                return null;
            }
        }
        int meta = NumberParser.parseInt(parts[1], 0, warnReporter, loc, "item_meta");
        if (meta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
            meta = 32767;
        }

        String nbtRaw = parts[2];
        effectId = parts[3];
        if (isNumeric(effectId)) {
            warnReporter.warnOnce("LegacyEffect", "numeric id " + effectId, loc);
        }

        duration = NumberParser.parseInt(parts[4], 10, warnReporter, loc, "duration");
        amplifier = NumberParser.parseInt(parts[5], 0, warnReporter, loc, "amplifier");
        probability = NumberParser.parseFloat(parts[6], 1.0f, warnReporter, loc, "probability");
        particleType = parts[7];

        List<String> setBonus = parseSetBonus(triggerKey, parts);
        ItemKey itemKey = new ItemKey(itemName, meta, nbtRaw);
        return new EffectRule(
            triggerKey,
            itemKey,
            effectId,
            duration,
            amplifier,
            probability,
            particleType,
            setBonus,
            loc
        );
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static List<String> parseSetBonus(String triggerKey, String[] parts) {
        if (!"wearing_armour".equals(triggerKey)) {
            return List.of();
        }
        if (parts.length >= 11) {
            List<String> extras = new ArrayList<>(3);
            extras.add(parts[8]);
            extras.add(parts[9]);
            extras.add(parts[10]);
            return extras;
        }
        return List.of();
    }

    private static boolean isBlockTrigger(String triggerKey) {
        return "standing_on_block".equals(triggerKey)
            || "inside_block".equals(triggerKey)
            || "blocks_in_inventory".equals(triggerKey)
            || "digging_block_block".equals(triggerKey)
            || "broke_block_block".equals(triggerKey);
    }

    private boolean isWildcardItem(String itemName, SourceLoc loc) {
        if (itemName == null) {
            return false;
        }
        if ("any".equalsIgnoreCase(itemName) || "all".equalsIgnoreCase(itemName)) {
            warnReporter.warnOnce("LegacyItem", "item sentinel " + itemName, loc);
            return true;
        }
        return false;
    }

    private String normalizeItemId(String raw, SourceLoc loc) {
        if (raw == null) {
            return null;
        }
        if (raw.contains(":")) {
            return validateItemId(raw, loc);
        }
        warnReporter.warnOnce("LegacyItemId", "missing namespace for " + raw, loc);
        return validateItemId("minecraft:" + raw, loc);
    }

    private String validateItemId(String raw, SourceLoc loc) {
        if (net.minecraft.util.Identifier.tryParse(raw) == null) {
            warnReporter.warn("LegacyItemId", "bad item id " + raw, loc);
            return null;
        }
        return raw;
    }
}
