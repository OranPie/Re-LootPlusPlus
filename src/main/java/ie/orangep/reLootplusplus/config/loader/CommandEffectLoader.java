package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.key.ItemKey;
import ie.orangep.reLootplusplus.config.model.key.BlockKey;
import ie.orangep.reLootplusplus.config.model.rule.CommandRule;
import ie.orangep.reLootplusplus.config.parse.LineReader;
import ie.orangep.reLootplusplus.config.parse.Splitter;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CommandEffectLoader {
    private static final String DIR = "config/item_effects/";
    private static final String PREFIX = "command_";

    private final LegacyWarnReporter warnReporter;

    public CommandEffectLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<CommandRule> loadAll(List<AddonPack> packs, PackIndex index) {
        List<CommandRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            for (Map.Entry<String, List<PackIndex.LineRecord>> entry : files.entrySet()) {
                String path = entry.getKey();
                if (!path.startsWith(DIR)) {
                    continue;
                }
                String file = path.substring(DIR.length());
                if (!file.startsWith(PREFIX) || !file.endsWith(".txt")) {
                    continue;
                }
                String triggerKey = file.substring(PREFIX.length(), file.length() - 4);
                rules.addAll(parseFile(triggerKey, entry.getValue()));
            }
        }
        Log.LOGGER.info("Loaded {} command effect rules", rules.size());
        return rules;
    }

    private List<CommandRule> parseFile(String triggerKey, List<PackIndex.LineRecord> lines) {
        List<CommandRule> rules = new ArrayList<>();
        for (PackIndex.LineRecord line : lines) {
            String raw = line.rawLine();
            if (LineReader.isIgnorable(raw)) {
                continue;
            }
            CommandRule rule = parseLine(triggerKey, raw, line.sourceLoc());
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    private CommandRule parseLine(String triggerKey, String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        boolean isBlockTrigger = isBlockTrigger(triggerKey);
        if (isBlockTrigger) {
            if (parts.length < 4) {
                warnReporter.warn("Parse", "command_effect block wrong parts (" + parts.length + ")", loc);
                return null;
            }
        } else if (parts.length < 5) {
            warnReporter.warn("Parse", "command_effect wrong parts (" + parts.length + ")", loc);
            return null;
        }

        if (isBlockTrigger) {
            String blockName = parts[0];
            int meta;
            try {
                meta = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                warnReporter.warn("Parse", "block_meta not a number: " + parts[1], loc);
                return null;
            }
            if (meta < 0) {
                warnReporter.warnOnce("LegacyMeta", "block meta wildcard " + meta, loc);
                meta = 32767;
            }
            double probability = 1.0;
            try {
                probability = Double.parseDouble(parts[2]);
            } catch (NumberFormatException e) {
                warnReporter.warn("Parse", "probability not a number: " + parts[2], loc);
            }
            String command = joinParts(parts, 3, "_____");
            BlockKey blockKey = new BlockKey(blockName, meta);
            return new CommandRule(triggerKey, blockKey, probability, command, loc);
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
        int meta;
        try {
            meta = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            warnReporter.warn("Parse", "item_meta not a number: " + parts[1], loc);
            return null;
        }
        if (meta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
            meta = 32767;
        }

        String nbtRaw = parts[2];
        double probability = 1.0;
        try {
            probability = Double.parseDouble(parts[3]);
        } catch (NumberFormatException e) {
            warnReporter.warn("Parse", "probability not a number: " + parts[3], loc);
        }

        String command = joinParts(parts, 4, "_____");
        List<String> setBonus = parseSetBonus(triggerKey, parts, loc);
        ItemKey itemKey = new ItemKey(itemName, meta, nbtRaw);
        return new CommandRule(triggerKey, itemKey, probability, command, setBonus, loc);
    }

    private List<String> parseSetBonus(String triggerKey, String[] parts, SourceLoc loc) {
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
            || "digging_block_block".equals(triggerKey);
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

    private static String joinParts(String[] parts, int start, String sep) {
        if (start >= parts.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (i > start) {
                sb.append(sep);
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
