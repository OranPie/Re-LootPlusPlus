package ie.orangep.reLootplusplus.config.loader;

import ie.orangep.reLootplusplus.config.model.rule.WorldGenSurfaceRule;
import ie.orangep.reLootplusplus.config.model.rule.WorldGenUndergroundRule;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class WorldGenLoader {
    private static final String SURFACE = "config/world_gen/surface.txt";
    private static final String UNDERGROUND = "config/world_gen/underground.txt";

    private final LegacyWarnReporter warnReporter;

    public WorldGenLoader(LegacyWarnReporter warnReporter) {
        this.warnReporter = warnReporter;
    }

    public List<WorldGenSurfaceRule> loadSurface(List<AddonPack> packs, PackIndex index) {
        List<WorldGenSurfaceRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> lines = files.get(SURFACE);
            if (lines == null) {
                continue;
            }
            for (PackIndex.LineRecord line : lines) {
                String raw = line.rawLine();
                if (LineReader.isIgnorable(raw)) {
                    continue;
                }
                WorldGenSurfaceRule rule = parseSurface(raw, line.sourceLoc());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        Log.LOGGER.info("Loaded {} surface worldgen rules", rules.size());
        return rules;
    }

    public List<WorldGenUndergroundRule> loadUnderground(List<AddonPack> packs, PackIndex index) {
        List<WorldGenUndergroundRule> rules = new ArrayList<>();
        for (AddonPack pack : packs) {
            Map<String, List<PackIndex.LineRecord>> files = index.filesFor(pack);
            List<PackIndex.LineRecord> lines = files.get(UNDERGROUND);
            if (lines == null) {
                continue;
            }
            for (PackIndex.LineRecord line : lines) {
                String raw = line.rawLine();
                if (LineReader.isIgnorable(raw)) {
                    continue;
                }
                WorldGenUndergroundRule rule = parseUnderground(raw, line.sourceLoc());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        Log.LOGGER.info("Loaded {} underground worldgen rules", rules.size());
        return rules;
    }

    private WorldGenSurfaceRule parseSurface(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length != 20) {
            warnReporter.warn("Parse", "surface wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String blockId = parts[0];
        int blockMeta = NumberParser.parseInt(parts[1], -1, warnReporter, loc, "block_meta");
        if (blockMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "block meta wildcard " + blockMeta, loc);
            blockMeta = 32767;
        }
        NbtCompound nbt = LenientNbtParser.parseOrNull(parts[2], warnReporter, loc, "LegacyNBT");
        boolean bonemeal = Boolean.parseBoolean(parts[3]);
        float chance = NumberParser.parseFloat(parts[4], 0.0f, warnReporter, loc, "chance_per_chunk");
        int triesPerChunk = NumberParser.parseInt(parts[5], 0, warnReporter, loc, "tries_per_chunk");
        int groupSize = NumberParser.parseInt(parts[6], 0, warnReporter, loc, "group_size");
        int triesPerGroup = NumberParser.parseInt(parts[7], 0, warnReporter, loc, "tries_per_group");
        int heightMin = NumberParser.parseInt(parts[8], 0, warnReporter, loc, "height_min");
        int heightMax = NumberParser.parseInt(parts[9], 0, warnReporter, loc, "height_max");

        List<String> beneathBlockBlacklist = splitList(parts[10], loc, "LegacyBlockId");
        List<String> beneathBlockWhitelist = splitList(parts[11], loc, "LegacyBlockId");
        List<String> beneathMaterialBlacklist = splitList(parts[12], loc, "LegacyMaterial");
        List<String> beneathMaterialWhitelist = splitList(parts[13], loc, "LegacyMaterial");
        List<String> biomeBlacklist = splitList(parts[14], loc, "LegacyBiome");
        List<String> biomeWhitelist = splitList(parts[15], loc, "LegacyBiome");
        List<String> biomeTypeBlacklist = splitList(parts[16], loc, "LegacyBiomeType");
        List<String> biomeTypeWhitelist = splitList(parts[17], loc, "LegacyBiomeType");
        List<String> dimensionBlacklist = splitList(parts[18], loc, "LegacyDimension");
        List<String> dimensionWhitelist = splitList(parts[19], loc, "LegacyDimension");

        return new WorldGenSurfaceRule(
            blockId,
            blockMeta,
            nbt,
            bonemeal,
            chance,
            triesPerChunk,
            groupSize,
            triesPerGroup,
            heightMin,
            heightMax,
            beneathBlockBlacklist,
            beneathBlockWhitelist,
            beneathMaterialBlacklist,
            beneathMaterialWhitelist,
            biomeBlacklist,
            biomeWhitelist,
            biomeTypeBlacklist,
            biomeTypeWhitelist,
            dimensionBlacklist,
            dimensionWhitelist,
            loc
        );
    }

    private WorldGenUndergroundRule parseUnderground(String raw, SourceLoc loc) {
        String[] parts = Splitter.splitRegex(raw, "_____");
        if (parts.length != 19) {
            warnReporter.warn("Parse", "underground wrong parts (" + parts.length + ")", loc);
            return null;
        }
        String blockId = parts[0];
        int blockMeta = NumberParser.parseInt(parts[1], -1, warnReporter, loc, "block_meta");
        if (blockMeta < 0) {
            warnReporter.warnOnce("LegacyMeta", "block meta wildcard " + blockMeta, loc);
            blockMeta = 32767;
        }
        NbtCompound nbt = LenientNbtParser.parseOrNull(parts[2], warnReporter, loc, "LegacyNBT");
        float chance = NumberParser.parseFloat(parts[3], 0.0f, warnReporter, loc, "chance_per_chunk");
        int triesPerChunk = NumberParser.parseInt(parts[4], 0, warnReporter, loc, "tries_per_chunk");

        int veinLenMin = 0;
        Integer veinLenMax = null;
        String[] veinLenParts = Splitter.splitRegex(parts[5], "-");
        if (veinLenParts.length >= 1) {
            veinLenMin = NumberParser.parseInt(veinLenParts[0], 0, warnReporter, loc, "vein_len_min");
        }
        if (veinLenParts.length >= 2) {
            veinLenMax = NumberParser.parseInt(veinLenParts[1], veinLenMin, warnReporter, loc, "vein_len_max");
        }

        String[] veinThickParts = Splitter.splitRegex(parts[6], "-");
        int veinThickMin = veinThickParts.length >= 1
            ? NumberParser.parseInt(veinThickParts[0], 0, warnReporter, loc, "vein_thick_min")
            : 0;
        int veinThickMax = veinThickParts.length >= 2
            ? NumberParser.parseInt(veinThickParts[1], veinThickMin, warnReporter, loc, "vein_thick_max")
            : veinThickMin;

        int heightMin = NumberParser.parseInt(parts[7], 0, warnReporter, loc, "height_min");
        int heightMax = NumberParser.parseInt(parts[8], 0, warnReporter, loc, "height_max");

        List<String> blockBlacklist = splitList(parts[9], loc, "LegacyBlockId");
        List<String> blockWhitelist = splitList(parts[10], loc, "LegacyBlockId");
        List<String> beneathMaterialBlacklist = splitList(parts[11], loc, "LegacyMaterial");
        List<String> beneathMaterialWhitelist = splitList(parts[12], loc, "LegacyMaterial");
        List<String> biomeBlacklist = splitList(parts[13], loc, "LegacyBiome");
        List<String> biomeWhitelist = splitList(parts[14], loc, "LegacyBiome");
        List<String> biomeTypeBlacklist = splitList(parts[15], loc, "LegacyBiomeType");
        List<String> biomeTypeWhitelist = splitList(parts[16], loc, "LegacyBiomeType");
        List<String> dimensionBlacklist = splitList(parts[17], loc, "LegacyDimension");
        List<String> dimensionWhitelist = splitList(parts[18], loc, "LegacyDimension");

        return new WorldGenUndergroundRule(
            blockId,
            blockMeta,
            nbt,
            chance,
            triesPerChunk,
            veinLenMin,
            veinLenMax,
            veinThickMin,
            veinThickMax,
            heightMin,
            heightMax,
            blockBlacklist,
            blockWhitelist,
            beneathMaterialBlacklist,
            beneathMaterialWhitelist,
            biomeBlacklist,
            biomeWhitelist,
            biomeTypeBlacklist,
            biomeTypeWhitelist,
            dimensionBlacklist,
            dimensionWhitelist,
            loc
        );
    }

    private List<String> splitList(String raw, SourceLoc loc, String warnType) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = Splitter.splitRegex(raw, "-");
        if (parts.length == 0) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            values.add(part);
        }
        if (!values.isEmpty()) {
            warnReporter.warnOnce(warnType, "list present", loc);
        }
        return values;
    }
}
