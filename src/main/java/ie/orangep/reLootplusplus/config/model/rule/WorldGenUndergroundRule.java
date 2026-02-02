package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

public final class WorldGenUndergroundRule {
    private final String blockId;
    private final int blockMeta;
    private final NbtCompound blockNbt;
    private final float chancePerChunk;
    private final int triesPerChunk;
    private final int veinLenMin;
    private final Integer veinLenMax;
    private final int veinThickMin;
    private final int veinThickMax;
    private final int heightMin;
    private final int heightMax;
    private final List<String> blockBlacklist;
    private final List<String> blockWhitelist;
    private final List<String> beneathMaterialBlacklist;
    private final List<String> beneathMaterialWhitelist;
    private final List<String> biomeBlacklist;
    private final List<String> biomeWhitelist;
    private final List<String> biomeTypeBlacklist;
    private final List<String> biomeTypeWhitelist;
    private final List<String> dimensionBlacklist;
    private final List<String> dimensionWhitelist;
    private final SourceLoc sourceLoc;

    public WorldGenUndergroundRule(
        String blockId,
        int blockMeta,
        NbtCompound blockNbt,
        float chancePerChunk,
        int triesPerChunk,
        int veinLenMin,
        Integer veinLenMax,
        int veinThickMin,
        int veinThickMax,
        int heightMin,
        int heightMax,
        List<String> blockBlacklist,
        List<String> blockWhitelist,
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
        this.chancePerChunk = chancePerChunk;
        this.triesPerChunk = triesPerChunk;
        this.veinLenMin = veinLenMin;
        this.veinLenMax = veinLenMax;
        this.veinThickMin = veinThickMin;
        this.veinThickMax = veinThickMax;
        this.heightMin = heightMin;
        this.heightMax = heightMax;
        this.blockBlacklist = List.copyOf(blockBlacklist);
        this.blockWhitelist = List.copyOf(blockWhitelist);
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

    public float chancePerChunk() {
        return chancePerChunk;
    }

    public int triesPerChunk() {
        return triesPerChunk;
    }

    public int veinLenMin() {
        return veinLenMin;
    }

    public Integer veinLenMax() {
        return veinLenMax;
    }

    public int veinThickMin() {
        return veinThickMin;
    }

    public int veinThickMax() {
        return veinThickMax;
    }

    public int heightMin() {
        return heightMin;
    }

    public int heightMax() {
        return heightMax;
    }

    public List<String> blockBlacklist() {
        return blockBlacklist;
    }

    public List<String> blockWhitelist() {
        return blockWhitelist;
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
