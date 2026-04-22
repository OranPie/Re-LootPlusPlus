package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.block.BlockAdditions;
import ie.orangep.reLootplusplus.config.model.block.CakeBlockDef;
import ie.orangep.reLootplusplus.config.model.block.CropBlockDef;
import ie.orangep.reLootplusplus.config.model.block.GenericBlockDef;
import ie.orangep.reLootplusplus.config.model.block.PlantBlockDef;
import ie.orangep.reLootplusplus.config.model.block.RawBlockAdditionDef;
import ie.orangep.reLootplusplus.config.parse.LineReader;
import ie.orangep.reLootplusplus.config.parse.NumberParser;
import ie.orangep.reLootplusplus.config.parse.Splitter;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackIndex;

import java.util.List;
import java.util.Map;

public final class BlockAdditionsLoader {
    private static final String DIR = "config/block_additions/";

    private final LegacyWarnReporter warnReporter;

    public BlockAdditionsLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public BlockAdditions loadAll(List<AddonPack> packs, PackIndex index) {
        BlockAdditions additions = new BlockAdditions();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            for (Map.Entry<String, List<PackIndex.LineRecord>> entry : files.entrySet()) {
                String path = entry.getKey();
                if (!path.startsWith(DIR) || !path.endsWith(".txt")) {
                    continue;
                }
                String fileName = path.substring(DIR.length());
                for (PackIndex.LineRecord line : entry.getValue()) {
                    String raw = line.rawLine();
                    if (LineReader.isIgnorable(raw)) {
                        continue;
                    }
                    parseLine(fileName, raw, line.sourceLoc(), additions);
                }
            }
        }
        Log.info(
            "Loader",
            "Loaded block additions: generic={}, plants={}, crops={}, cakes={}, raw={}",
            additions.genericBlocks().size(),
            additions.plantBlocks().size(),
            additions.cropBlocks().size(),
            additions.cakeBlocks().size(),
            additions.rawBlocks().size()
        );
        return additions;
    }

    private void parseLine(String fileName, String raw, SourceLoc loc, BlockAdditions additions) {
        switch (fileName) {
            case "generic.txt" -> parseGeneric(raw, loc, additions);
            case "plants.txt" -> parsePlants(raw, loc, additions);
            case "crops.txt" -> parseCrops(raw, loc, additions);
            case "cakes.txt" -> parseCakes(raw, loc, additions);
            default -> {
                warnReporter.warnOnce("LegacyBlockAdditions", "block_additions type " + fileName + " not implemented", null);
                additions.addRaw(new RawBlockAdditionDef(fileName, raw, loc));
            }
        }
    }

    private void parseGeneric(String raw, SourceLoc loc, BlockAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 14) {
            warnReporter.warn("Parse", "block_additions generic wrong parts (" + parts.length + ")", loc);
            return;
        }
        String blockId = parts[0];
        String displayName = parts[1];
        String materialName = parts[2];
        boolean falls = Boolean.parseBoolean(parts[3]);
        boolean beaconBase = Boolean.parseBoolean(parts[4]);
        float hardness = NumberParser.parseFloat(parts[5], 0.0f, warnReporter, loc, "hardness");
        float resistance = NumberParser.parseFloat(parts[6], 0.0f, warnReporter, loc, "resistance");
        String harvestTool = parts[7];
        int harvestLevel = NumberParser.parseInt(parts[8], -1, warnReporter, loc, "harvest_level");
        float light = NumberParser.parseFloat(parts[9], 0.0f, warnReporter, loc, "light");
        float slipperiness = NumberParser.parseFloat(parts[10], 0.6f, warnReporter, loc, "slipperiness");
        int fireSpread = NumberParser.parseInt(parts[11], 0, warnReporter, loc, "fire_spread");
        int flammability = NumberParser.parseInt(parts[12], 0, warnReporter, loc, "flammability");
        int opacity = NumberParser.parseInt(parts[13], -1, warnReporter, loc, "opacity");

        additions.addGeneric(new GenericBlockDef(
            blockId,
            displayName,
            materialName,
            falls,
            beaconBase,
            hardness,
            resistance,
            harvestTool,
            harvestLevel,
            light,
            slipperiness,
            fireSpread,
            flammability,
            opacity,
            loc
        ));
    }

    private void parsePlants(String raw, SourceLoc loc, BlockAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 10) {
            warnReporter.warn("Parse", "block_additions plants wrong parts (" + parts.length + ")", loc);
            return;
        }
        String blockId = parts[0];
        String displayName = parts[1];
        String materialName = parts[2];
        float hardness = NumberParser.parseFloat(parts[3], 0.0f, warnReporter, loc, "hardness");
        float resistance = NumberParser.parseFloat(parts[4], 0.0f, warnReporter, loc, "resistance");
        String harvestTool = parts[5];
        int harvestLevel = NumberParser.parseInt(parts[6], -1, warnReporter, loc, "harvest_level");
        float light = NumberParser.parseFloat(parts[7], 0.0f, warnReporter, loc, "light");
        int fireSpread = NumberParser.parseInt(parts[8], 0, warnReporter, loc, "fire_spread");
        int flammability = NumberParser.parseInt(parts[9], 0, warnReporter, loc, "flammability");

        additions.addPlant(new PlantBlockDef(
            blockId,
            displayName,
            materialName,
            hardness,
            resistance,
            harvestTool,
            harvestLevel,
            light,
            fireSpread,
            flammability,
            loc
        ));
    }

    private void parseCrops(String raw, SourceLoc loc, BlockAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 10) {
            warnReporter.warn("Parse", "block_additions crops wrong parts (" + parts.length + ")", loc);
            return;
        }
        String blockId = parts[0];
        String displayName = parts[1];
        String seedItem = parts[2];
        int seedMeta = NumberParser.parseInt(parts[3], 0, warnReporter, loc, "seed_meta");
        float light = NumberParser.parseFloat(parts[4], 0.0f, warnReporter, loc, "light");
        int fireSpread = NumberParser.parseInt(parts[5], 0, warnReporter, loc, "fire_spread");
        int flammability = NumberParser.parseInt(parts[6], 0, warnReporter, loc, "flammability");
        boolean canBonemeal = Boolean.parseBoolean(parts[7]);
        boolean netherPlant = Boolean.parseBoolean(parts[8]);
        boolean rightClickHarvest = Boolean.parseBoolean(parts[9]);

        additions.addCrop(new CropBlockDef(
            blockId,
            displayName,
            seedItem,
            seedMeta,
            light,
            fireSpread,
            flammability,
            canBonemeal,
            netherPlant,
            rightClickHarvest,
            loc
        ));
    }

    private void parseCakes(String raw, SourceLoc loc, BlockAdditions additions) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length < 12) {
            warnReporter.warn("Parse", "block_additions cakes wrong parts (" + parts.length + ")", loc);
            return;
        }
        String blockId = parts[0];
        String displayName = parts[1];
        float hardness = NumberParser.parseFloat(parts[2], 0.0f, warnReporter, loc, "hardness");
        float resistance = NumberParser.parseFloat(parts[3], 0.0f, warnReporter, loc, "resistance");
        float light = NumberParser.parseFloat(parts[4], 0.0f, warnReporter, loc, "light");
        float slipperiness = NumberParser.parseFloat(parts[5], 0.6f, warnReporter, loc, "slipperiness");
        int fireSpread = NumberParser.parseInt(parts[6], 0, warnReporter, loc, "fire_spread");
        int flammability = NumberParser.parseInt(parts[7], 0, warnReporter, loc, "flammability");
        int bites = NumberParser.parseInt(parts[8], 0, warnReporter, loc, "bites");
        int hunger = NumberParser.parseInt(parts[9], 0, warnReporter, loc, "hunger");
        float saturation = NumberParser.parseFloat(parts[10], 0.0f, warnReporter, loc, "saturation");
        boolean alwaysEdible = Boolean.parseBoolean(parts[11]);
        String effects = parts.length >= 13 ? parts[12] : "";

        additions.addCake(new CakeBlockDef(
            blockId,
            displayName,
            hardness,
            resistance,
            light,
            slipperiness,
            fireSpread,
            flammability,
            bites,
            hunger,
            saturation,
            alwaysEdible,
            effects,
            loc
        ));
    }
}
