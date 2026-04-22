package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.rule.FurnaceFuelRule;
import ie.orangep.reLootplusplus.config.model.rule.FurnaceSmeltingRule;
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

public final class FurnaceRecipesLoader {
    private static final String DIR = "config/furnace_recipes/";
    private static final String SMELTING_FILE = DIR + "add_smelting_recipes.txt";
    private static final String FUEL_FILE = DIR + "add_furnace_fuels.txt";

    private final LegacyWarnReporter warnReporter;

    public FurnaceRecipesLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<FurnaceSmeltingRule> loadSmelting(List<AddonPack> packs, PackIndex index) {
        List<FurnaceSmeltingRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> lines = files.get(SMELTING_FILE);
            if (lines == null) {
                continue;
            }
            for (PackIndex.LineRecord line : lines) {
                String raw = line.rawLine();
                if (LineReader.isIgnorable(raw)) {
                    continue;
                }
                FurnaceSmeltingRule rule = parseSmelting(raw, line.sourceLoc());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        Log.info("Loader", "Loaded {} furnace smelting rules", rules.size());
        return rules;
    }

    public List<FurnaceFuelRule> loadFuels(List<AddonPack> packs, PackIndex index) {
        List<FurnaceFuelRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> lines = files.get(FUEL_FILE);
            if (lines == null) {
                continue;
            }
            for (PackIndex.LineRecord line : lines) {
                String raw = line.rawLine();
                if (LineReader.isIgnorable(raw)) {
                    continue;
                }
                FurnaceFuelRule rule = parseFuel(raw, line.sourceLoc());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        Log.info("Loader", "Loaded {} furnace fuel rules", rules.size());
        return rules;
    }

    private FurnaceSmeltingRule parseSmelting(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 6) {
            warnReporter.warn("Parse", "furnace smelting wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String inputId = normalizeItemId(parts[0], loc);
        if (inputId == null) {
            return null;
        }
        int inputMeta = NumberParser.parseInt(parts[1], 0, warnReporter, loc, "input_meta");
        if (inputMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + inputMeta, loc);
            inputMeta = 32767;
        }
        String outputId = normalizeItemId(parts[2], loc);
        if (outputId == null) {
            return null;
        }
        int outputMeta = NumberParser.parseInt(parts[3], 0, warnReporter, loc, "output_meta");
        if (outputMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + outputMeta, loc);
            outputMeta = 32767;
        }
        String nbtRaw = parts[4];
        NbtCompound outputNbt = null;
        if (nbtRaw != null && !nbtRaw.isEmpty() && !"{}".equals(nbtRaw)) {
            outputNbt = LenientNbtParser.parseOrNull(nbtRaw, warnReporter, loc, "LegacyNBT");
            if (outputNbt == null) {
                warnReporter.warn("LegacyNBT", "smelting output nbt parse failed", loc);
                return null;
            }
        }
        int amount = NumberParser.parseInt(parts[5], 1, warnReporter, loc, "amount");
        if (amount < 1) {
            amount = 1;
        }
        float xp = 0.0f;
        if (parts.length >= 7) {
            xp = NumberParser.parseFloat(parts[6], 0.0f, warnReporter, loc, "xp");
            if (xp < 0.0f) {
                xp = 0.0f;
            }
        }
        warnReporter.warnOnce("LegacyFurnace", "furnace smelting rule", loc);
        return new FurnaceSmeltingRule(
            inputId,
            inputMeta,
            outputId,
            outputMeta,
            outputNbt,
            amount,
            xp,
            loc
        );
    }

    private FurnaceFuelRule parseFuel(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 3) {
            warnReporter.warn("Parse", "furnace fuel wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String itemId = normalizeItemId(parts[0], loc);
        if (itemId == null) {
            return null;
        }
        int meta = NumberParser.parseInt(parts[1], 0, warnReporter, loc, "meta");
        if (meta < 0) {
            warnReporter.warnOnce("LegacyMeta", "meta wildcard " + meta, loc);
            meta = 32767;
        }
        int burnTime = NumberParser.parseInt(parts[2], 0, warnReporter, loc, "burn_time");
        if (burnTime < 0) {
            burnTime = 0;
        }
        warnReporter.warnOnce("LegacyFurnace", "furnace fuel rule", loc);
        return new FurnaceFuelRule(itemId, meta, burnTime, loc);
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
