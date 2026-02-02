package ie.orangep.reLootplusplus.config.model.block;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class PlantBlockDef {
    private final String blockId;
    private final String displayName;
    private final String materialName;
    private final float hardness;
    private final float resistance;
    private final String harvestTool;
    private final int harvestLevel;
    private final float light;
    private final int fireSpread;
    private final int flammability;
    private final SourceLoc sourceLoc;

    public PlantBlockDef(
        String blockId,
        String displayName,
        String materialName,
        float hardness,
        float resistance,
        String harvestTool,
        int harvestLevel,
        float light,
        int fireSpread,
        int flammability,
        SourceLoc sourceLoc
    ) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.materialName = materialName;
        this.hardness = hardness;
        this.resistance = resistance;
        this.harvestTool = harvestTool;
        this.harvestLevel = harvestLevel;
        this.light = light;
        this.fireSpread = fireSpread;
        this.flammability = flammability;
        this.sourceLoc = sourceLoc;
    }

    public String blockId() {
        return blockId;
    }

    public String displayName() {
        return displayName;
    }

    public String materialName() {
        return materialName;
    }

    public float hardness() {
        return hardness;
    }

    public float resistance() {
        return resistance;
    }

    public String harvestTool() {
        return harvestTool;
    }

    public int harvestLevel() {
        return harvestLevel;
    }

    public float light() {
        return light;
    }

    public int fireSpread() {
        return fireSpread;
    }

    public int flammability() {
        return flammability;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
