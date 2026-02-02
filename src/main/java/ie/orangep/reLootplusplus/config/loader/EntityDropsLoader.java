package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.config.model.rule.EntityDropRule;
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

public final class EntityDropsLoader {
    private static final String ADDING = "config/entity_drops/adding.txt";
    private static final String ADDING_TYPO = "config/enity_drops/adding.txt";

    private final LegacyWarnReporter warnReporter;
    private final BlockDropsLoader dropParser;

    public EntityDropsLoader(LegacyWarnReporter warnReporter, BlockDropsLoader dropParser) {
        this.warnReporter = warnReporter;
        this.dropParser = dropParser;
    }

    public List<EntityDropRule> loadAdding(List<AddonPack> packs, PackIndex index) {
        List<EntityDropRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> lines = files.get(ADDING);
            if (lines == null) {
                lines = files.get(ADDING_TYPO);
                if (lines != null) {
                    if (!lines.isEmpty()) {
                        warnReporter.warnOnce("LegacyPath", "enity_drops used", lines.get(0).sourceLoc());
                    } else {
                        warnReporter.warnOnce("LegacyPath", "enity_drops used", null);
                    }
                }
            }
            if (lines == null) {
                continue;
            }
            for (PackIndex.LineRecord line : lines) {
                String raw = line.rawLine();
                if (LineReader.isIgnorable(raw)) {
                    continue;
                }
                EntityDropRule rule = parseLine(raw, line.sourceLoc());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        Log.LOGGER.info("Loaded {} entity drop add rules", rules.size());
        return rules;
    }

    private EntityDropRule parseLine(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 2) {
            warnReporter.warn("Parse", "entity_drops adding wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String entityId = parts[0];
        List<DropGroup> groups = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String groupRaw = parts[i];
            if (groupRaw.isEmpty()) {
                continue;
            }
            DropGroup group = dropParser.parseGroupForThrown(groupRaw, loc);
            if (group != null) {
                groups.add(group);
            }
        }
        return new EntityDropRule(entityId, groups, loc);
    }
}
