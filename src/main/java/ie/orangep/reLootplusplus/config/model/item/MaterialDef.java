package ie.orangep.reLootplusplus.config.model.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class MaterialDef {
    private final String itemId;
    private final int meta;
    private final int harvestLevel;
    private final int durability;
    private final float efficiency;
    private final float damage;
    private final int enchantability;
    private final int armorDurabilityFactor;
    private final int[] armorProtection;
    private final SourceLoc sourceLoc;

    public MaterialDef(
        String itemId,
        int meta,
        int harvestLevel,
        int durability,
        float efficiency,
        float damage,
        int enchantability,
        int armorDurabilityFactor,
        int[] armorProtection,
        SourceLoc sourceLoc
    ) {
        this.itemId = itemId;
        this.meta = meta;
        this.harvestLevel = harvestLevel;
        this.durability = durability;
        this.efficiency = efficiency;
        this.damage = damage;
        this.enchantability = enchantability;
        this.armorDurabilityFactor = armorDurabilityFactor;
        this.armorProtection = armorProtection == null ? new int[] {0, 0, 0, 0} : armorProtection.clone();
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public int meta() {
        return meta;
    }

    public int harvestLevel() {
        return harvestLevel;
    }

    public int durability() {
        return durability;
    }

    public float efficiency() {
        return efficiency;
    }

    public float damage() {
        return damage;
    }

    public int enchantability() {
        return enchantability;
    }

    public int armorDurabilityFactor() {
        return armorDurabilityFactor;
    }

    public int[] armorProtection() {
        return armorProtection.clone();
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
