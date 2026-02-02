package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.rule.StackSizeRule;
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

public final class StackSizeLoader {
    private static final String FILE = "config/stack_size/stack_sizes.txt";

    private final LegacyWarnReporter warnReporter;

    public StackSizeLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<StackSizeRule> loadAll(List<AddonPack> packs, PackIndex index) {
        List<StackSizeRule> rules = new ArrayList<>();
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
                StackSizeRule rule = parseLine(raw, line.sourceLoc());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        Log.LOGGER.info("Loaded {} stack size rules", rules.size());
        return rules;
    }

    private StackSizeRule parseLine(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 2) {
            warnReporter.warn("Parse", "stack_size wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String itemId = normalizeItemId(parts[0], loc);
        if (itemId == null) {
            return null;
        }
        int size = NumberParser.parseInt(parts[1], 64, warnReporter, loc, "stack_size");
        if (size < 1) {
            warnReporter.warn("Parse", "stack_size invalid " + size, loc);
            return null;
        }
        warnReporter.warnOnce("LegacyStackSize", "stack size override", loc);
        return new StackSizeRule(itemId, size, loc);
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
