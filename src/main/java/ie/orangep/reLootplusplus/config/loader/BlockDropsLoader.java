package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.drop.DropEntry;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryCommand;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryEntity;
import ie.orangep.reLootplusplus.config.model.drop.DropEntryItem;
import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.config.model.rule.BlockDropRemoval;
import ie.orangep.reLootplusplus.config.model.rule.BlockDropRule;
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

public final class BlockDropsLoader {
    private static final String ADDING = "config/block_drops/adding.txt";
    private static final String REMOVING = "config/block_drops/removing.txt";

    private final LegacyWarnReporter warnReporter;

    public BlockDropsLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<BlockDropRule> loadAdding(List<AddonPack> packs, PackIndex index) {
        List<BlockDropRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> lines = files.get(ADDING);
            if (lines == null) {
                continue;
            }
            for (PackIndex.LineRecord line : lines) {
                String raw = line.rawLine();
                if (LineReader.isIgnorable(raw)) {
                    continue;
                }
                BlockDropRule rule = parseAdding(raw, line.sourceLoc());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        Log.LOGGER.info("Loaded {} block drop add rules", rules.size());
        return rules;
    }

    public List<BlockDropRemoval> loadRemoving(List<AddonPack> packs, PackIndex index) {
        List<BlockDropRemoval> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> lines = files.get(REMOVING);
            if (lines == null) {
                continue;
            }
            for (PackIndex.LineRecord line : lines) {
                String raw = line.rawLine();
                if (LineReader.isIgnorable(raw)) {
                    continue;
                }
                BlockDropRemoval rule = parseRemoving(raw, line.sourceLoc());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        Log.LOGGER.info("Loaded {} block drop remove rules", rules.size());
        return rules;
    }

    private BlockDropRemoval parseRemoving(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 3) {
            warnReporter.warn("Parse", "block_drops removing wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String blockId = parts[0];
        int blockMeta = NumberParser.parseInt(parts[1], -1, warnReporter, loc, "block_meta");
        if (blockMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "block meta wildcard " + blockMeta, loc);
            blockMeta = 32767;
        }

        String itemId = parts[2];
        if ("any".equalsIgnoreCase(itemId) || "all".equalsIgnoreCase(itemId)) {
            warnReporter.warnOnce("LegacyItem", "item sentinel " + itemId, loc);
        }

        int itemMeta = -1;
        String nbtRaw = "{}";
        if (parts.length >= 4) {
            itemMeta = NumberParser.parseInt(parts[3], -1, warnReporter, loc, "item_meta");
        }
        if (itemMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "item meta wildcard " + itemMeta, loc);
            itemMeta = 32767;
        }
        if (parts.length >= 5) {
            nbtRaw = parts[4];
            if (!nbtRaw.isEmpty() && !"{}".equals(nbtRaw)) {
                warnReporter.warnOnce("LegacyNBT", "nbt filter present", loc);
            }
        }

        return new BlockDropRemoval(blockId, blockMeta, itemId, itemMeta, nbtRaw, loc);
    }

    private BlockDropRule parseAdding(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 2) {
            warnReporter.warn("Parse", "block_drops adding wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String header = parts[0];
        String[] headerParts = Splitter.splitRegex(header, "-");
        if (headerParts.length < 5) {
            warnReporter.warn("Parse", "block_drops header wrong parts (" + headerParts.length + ")", loc);
            return null;
        }

        String blockId = headerParts[0];
        float rarity = NumberParser.parseFloat(headerParts[1], 1.0f, warnReporter, loc, "rarity");
        boolean onlyPlayerMined = Boolean.parseBoolean(headerParts[2]);
        boolean dropWithSilk = Boolean.parseBoolean(headerParts[3]);
        boolean affectedByFortune = Boolean.parseBoolean(headerParts[4]);
        int blockMeta = -1;
        if (headerParts.length >= 6) {
            blockMeta = NumberParser.parseInt(headerParts[5], -1, warnReporter, loc, "block_meta");
        }
        if (blockMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "block meta wildcard " + blockMeta, loc);
            blockMeta = 32767;
        }

        List<DropGroup> groups = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String groupRaw = parts[i];
            if (groupRaw.isEmpty()) {
                continue;
            }
            DropGroup group = parseGroup(groupRaw, loc);
            if (group != null) {
                groups.add(group);
            }
        }

        return new BlockDropRule(
            blockId,
            blockMeta,
            rarity,
            onlyPlayerMined,
            dropWithSilk,
            affectedByFortune,
            groups,
            loc
        );
    }

    private DropGroup parseGroup(String groupRaw, SourceLoc loc) {
        String[] entriesRaw = Splitter.splitRegex(groupRaw, "%%%%%");
        List<DropEntry> entries = new ArrayList<>();
        for (String entryRaw : entriesRaw) {
            DropEntry entry = parseEntry(entryRaw, loc);
            if (entry != null) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty()) {
            return null;
        }
        return new DropGroup(entries);
    }

    public DropGroup parseGroupForThrown(String groupRaw, SourceLoc loc) {
        return parseGroup(groupRaw, loc);
    }

    private DropEntry parseEntry(String entryRaw, SourceLoc loc) {
        int dash = entryRaw.indexOf('-');
        if (dash <= 0) {
            warnReporter.warn("Parse", "drop entry missing '-': " + entryRaw, loc);
            return null;
        }
        char type = entryRaw.charAt(0);
        String payload = entryRaw.substring(dash + 1);
        return switch (type) {
            case 'i' -> parseItemDrop(payload, loc);
            case 'e' -> parseEntityDrop(payload, loc);
            case 'c' -> parseCommandDrop(payload, loc);
            default -> {
                warnReporter.warn("Parse", "unknown drop type: " + type, loc);
                yield null;
            }
        };
    }

    private DropEntry parseItemDrop(String payload, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(payload, "-", 6);
        if (parts.length < 2) {
            warnReporter.warn("Parse", "item drop wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String itemId = parts[0];
        int minCount = NumberParser.parseInt(parts[1], 0, warnReporter, loc, "min_count");
        int maxCount = minCount;
        int weight = 1;
        int meta = 0;
        String nbt = null;

        if (parts.length >= 3) {
            maxCount = NumberParser.parseInt(parts[2], minCount, warnReporter, loc, "max_count");
        }
        if (maxCount < minCount) {
            maxCount = minCount;
        }
        if (parts.length >= 4) {
            weight = NumberParser.parseInt(parts[3], 1, warnReporter, loc, "weight");
        }
        if (weight <= 0) {
            weight = 1;
        }
        if (parts.length >= 5) {
            meta = NumberParser.parseInt(parts[4], 0, warnReporter, loc, "meta");
            if (meta < 0) {
                meta = 0;
            }
            warnReporter.warnOnce("LegacyMeta", "item meta used " + meta, loc);
        }
        if (parts.length >= 6) {
            nbt = parts[5];
            if (nbt != null && !nbt.isEmpty() && !"{}".equals(nbt)) {
                warnReporter.warnOnce("LegacyNBT", "drop item nbt", loc);
            }
        }

        if (minCount < 0) {
            minCount = 0;
        }
        return new DropEntryItem(itemId, minCount, maxCount, weight, meta, nbt);
    }

    private DropEntry parseEntityDrop(String payload, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(payload, "-", 3);
        String entityId = parts[0];
        int weight = 1;
        String nbt = "{}";
        if (parts.length >= 2) {
            weight = NumberParser.parseInt(parts[1], 1, warnReporter, loc, "weight");
        }
        if (parts.length >= 3) {
            nbt = parts[2];
            if (nbt != null && !nbt.isEmpty() && !"{}".equals(nbt)) {
                warnReporter.warnOnce("LegacyNBT", "drop entity nbt", loc);
            }
        }
        if (weight <= 0) {
            weight = 1;
        }
        return new DropEntryEntity(entityId, weight, nbt);
    }

    private DropEntry parseCommandDrop(String payload, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(payload, "-", 2);
        if (parts.length < 2) {
            warnReporter.warn("Parse", "command drop wrong parts (" + parts.length + ")", loc);
            return null;
        }
        int weight = NumberParser.parseInt(parts[0], 1, warnReporter, loc, "weight");
        if (weight <= 0) {
            weight = 1;
        }
        return new DropEntryCommand(weight, parts[1]);
    }
}
