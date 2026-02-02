package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

public final class WorldGenSurfaceRule {
    private final String blockId;
    private final int blockMeta;
    private final NbtCompound blockNbt;
    private final boolean bonemeal;
    private final float chancePerChunk;
    private final int triesPerChunk;
    private final int groupSize;
    private final int triesPerGroup;
    private final int heightMin;
    private final int heightMax;
    private final List<String> beneathBlockBlacklist;
    private final List<String> beneathBlockWhitelist;
    private final List<String> beneathMaterialBlacklist;
    private final List<String> beneathMaterialWhitelist;
    private final List<String> biomeBlacklist;
    private final List<String> biomeWhitelist;
    private final List<String> biomeTypeBlacklist;
    private final List<String> biomeTypeWhitelist;
    private final List<String> dimensionBlacklist;
    private final List<String> dimensionWhitelist;
    private final SourceLoc sourceLoc;

    public WorldGenSurfaceRule(
        String blockId,
        int blockMeta,
        NbtCompound blockNbt,
        boolean bonemeal,
        float chancePerChunk,
        int triesPerChunk,
        int groupSize,
        int triesPerGroup,
        int heightMin,
        int heightMax,
        List<String> beneathBlockBlacklist,
        List<String> beneathBlockWhitelist,
        List<String> beneathMaterialBlacklist,
        List<String> beneathMaterialWhitelist,
        List<String> biomeBlacklist,
        List<String> biomeWhitelist,
        List<String> biomeTypeBlacklist,
        List<String> biomeTypeWhitelist,
        List<String> dimensionBlacklist,
        List<String> dimensionWhitelist,
        SourceLoc sourceLoc
    ) {
        this.blockId = blockId;
        this.blockMeta = blockMeta;
        this.blockNbt = blockNbt;
        this.bonemeal = bonemeal;
        this.chancePerChunk = chancePerChunk;
        this.triesPerChunk = triesPerChunk;
        this.groupSize = groupSize;
        this.triesPerGroup = triesPerGroup;
        this.heightMin = heightMin;
        this.heightMax = heightMax;
        this.beneathBlockBlacklist = List.copyOf(beneathBlockBlacklist);
        this.beneathBlockWhitelist = List.copyOf(beneathBlockWhitelist);
        this.beneathMaterialBlacklist = List.copyOf(beneathMaterialBlacklist);
        this.beneathMaterialWhitelist = List.copyOf(beneathMaterialWhitelist);
        this.biomeBlacklist = List.copyOf(biomeBlacklist);
        this.biomeWhitelist = List.copyOf(biomeWhitelist);
        this.biomeTypeBlacklist = List.copyOf(biomeTypeBlacklist);
        this.biomeTypeWhitelist = List.copyOf(biomeTypeWhitelist);
        this.dimensionBlacklist = List.copyOf(dimensionBlacklist);
        this.dimensionWhitelist = List.copyOf(dimensionWhitelist);
        this.sourceLoc = sourceLoc;
    }

    public String blockId() {
        return blockId;
    }

    public int blockMeta() {
        return blockMeta;
    }

    public NbtCompound blockNbt() {
        return blockNbt;
    }

    public boolean bonemeal() {
        return bonemeal;
    }

    public float chancePerChunk() {
        return chancePerChunk;
    }

    public int triesPerChunk() {
        return triesPerChunk;
    }

    public int groupSize() {
        return groupSize;
    }

    public int triesPerGroup() {
        return triesPerGroup;
    }

    public int heightMin() {
        return heightMin;
    }

    public int heightMax() {
        return heightMax;
    }

    public List<String> beneathBlockBlacklist() {
        return beneathBlockBlacklist;
    }

    public List<String> beneathBlockWhitelist() {
        return beneathBlockWhitelist;
    }

    public List<String> beneathMaterialBlacklist() {
        return beneathMaterialBlacklist;
    }

    public List<String> beneathMaterialWhitelist() {
        return beneathMaterialWhitelist;
    }

    public List<String> biomeBlacklist() {
        return biomeBlacklist;
    }

    public List<String> biomeWhitelist() {
        return biomeWhitelist;
    }

    public List<String> biomeTypeBlacklist() {
        return biomeTypeBlacklist;
    }

    public List<String> biomeTypeWhitelist() {
        return biomeTypeWhitelist;
    }

    public List<String> dimensionBlacklist() {
        return dimensionBlacklist;
    }

    public List<String> dimensionWhitelist() {
        return dimensionWhitelist;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
