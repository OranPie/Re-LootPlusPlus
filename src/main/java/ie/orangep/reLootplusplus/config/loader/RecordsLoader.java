package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.rule.RecordDef;
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

public final class RecordsLoader {
    private static final String FILE = "config/records/records.txt";

    private final LegacyWarnReporter warnReporter;

    public RecordsLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<RecordDef> loadAll(List<AddonPack> packs, PackIndex index) {
        List<RecordDef> records = new ArrayList<>();
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
                RecordDef record = parseLine(raw, line.sourceLoc());
                if (record != null) {
                    records.add(record);
                }
            }
        }
        Log.LOGGER.info("Loaded {} record definitions", records.size());
        return records;
    }

    private RecordDef parseLine(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "-", 2);
        if (parts.length < 2) {
            warnReporter.warn("Parse", "records wrong parts (" + parts.length + ")", loc);
            return null;
        }
        warnReporter.warnOnce("LegacyRecords", "records definition", loc);
        String name = parts[0];
        String description = parts[1];
        return new RecordDef(name, description, loc);
    }
}
