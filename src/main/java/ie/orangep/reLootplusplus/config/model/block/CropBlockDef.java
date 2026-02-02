package ie.orangep.reLootplusplus.config.model.block;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class CropBlockDef {
    private final String blockId;
    private final String displayName;
    private final String seedItemName;
    private final int seedMeta;
    private final float light;
    private final int fireSpread;
    private final int flammability;
    private final boolean canBonemeal;
    private final boolean netherPlant;
    private final boolean rightClickHarvest;
    private final SourceLoc sourceLoc;

    public CropBlockDef(
        String blockId,
        String displayName,
        String seedItemName,
        int seedMeta,
        float light,
        int fireSpread,
        int flammability,
        boolean canBonemeal,
        boolean netherPlant,
        boolean rightClickHarvest,
        SourceLoc sourceLoc
    ) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.seedItemName = seedItemName;
        this.seedMeta = seedMeta;
        this.light = light;
        this.fireSpread = fireSpread;
        this.flammability = flammability;
        this.canBonemeal = canBonemeal;
        this.netherPlant = netherPlant;
        this.rightClickHarvest = rightClickHarvest;
        this.sourceLoc = sourceLoc;
    }

    public String blockId() {
        return blockId;
    }

    public String displayName() {
        return displayName;
    }

    public String seedItemName() {
        return seedItemName;
    }

    public int seedMeta() {
        return seedMeta;
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

    public boolean canBonemeal() {
        return canBonemeal;
    }

    public boolean netherPlant() {
        return netherPlant;
    }

    public boolean rightClickHarvest() {
        return rightClickHarvest;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
