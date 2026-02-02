package ie.orangep.reLootplusplus.config.model.block;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class GenericBlockDef {
    private final String blockId;
    private final String displayName;
    private final String materialName;
    private final boolean falls;
    private final boolean beaconBase;
    private final float hardness;
    private final float resistance;
    private final String harvestTool;
    private final int harvestLevel;
    private final float light;
    private final float slipperiness;
    private final int fireSpread;
    private final int flammability;
    private final int opacity;
    private final SourceLoc sourceLoc;

    public GenericBlockDef(
        String blockId,
        String displayName,
        String materialName,
        boolean falls,
        boolean beaconBase,
        float hardness,
        float resistance,
        String harvestTool,
        int harvestLevel,
        float light,
        float slipperiness,
        int fireSpread,
        int flammability,
        int opacity,
        SourceLoc sourceLoc
    ) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.materialName = materialName;
        this.falls = falls;
        this.beaconBase = beaconBase;
        this.hardness = hardness;
        this.resistance = resistance;
        this.harvestTool = harvestTool;
        this.harvestLevel = harvestLevel;
        this.light = light;
        this.slipperiness = slipperiness;
        this.fireSpread = fireSpread;
        this.flammability = flammability;
        this.opacity = opacity;
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

    public boolean falls() {
        return falls;
    }

    public boolean beaconBase() {
        return beaconBase;
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

    public float slipperiness() {
        return slipperiness;
    }

    public int fireSpread() {
        return fireSpread;
    }

    public int flammability() {
        return flammability;
    }

    public int opacity() {
        return opacity;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
