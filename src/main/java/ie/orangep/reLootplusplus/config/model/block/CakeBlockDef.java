package ie.orangep.reLootplusplus.config.model.block;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class CakeBlockDef {
    private final String blockId;
    private final String displayName;
    private final float hardness;
    private final float resistance;
    private final float light;
    private final float slipperiness;
    private final int fireSpread;
    private final int flammability;
    private final int bites;
    private final int hungerRestored;
    private final float saturationRestored;
    private final boolean alwaysEdible;
    private final String potionEffects;
    private final SourceLoc sourceLoc;

    public CakeBlockDef(
        String blockId,
        String displayName,
        float hardness,
        float resistance,
        float light,
        float slipperiness,
        int fireSpread,
        int flammability,
        int bites,
        int hungerRestored,
        float saturationRestored,
        boolean alwaysEdible,
        String potionEffects,
        SourceLoc sourceLoc
    ) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.hardness = hardness;
        this.resistance = resistance;
        this.light = light;
        this.slipperiness = slipperiness;
        this.fireSpread = fireSpread;
        this.flammability = flammability;
        this.bites = bites;
        this.hungerRestored = hungerRestored;
        this.saturationRestored = saturationRestored;
        this.alwaysEdible = alwaysEdible;
        this.potionEffects = potionEffects;
        this.sourceLoc = sourceLoc;
    }

    public String blockId() {
        return blockId;
    }

    public String displayName() {
        return displayName;
    }

    public float hardness() {
        return hardness;
    }

    public float resistance() {
        return resistance;
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

    public int bites() {
        return bites;
    }

    public int hungerRestored() {
        return hungerRestored;
    }

    public float saturationRestored() {
        return saturationRestored;
    }

    public boolean alwaysEdible() {
        return alwaysEdible;
    }

    public String potionEffects() {
        return potionEffects;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
