package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.nbt.NbtCompound;

public final class FishingLootRule {
    private final FishingLootCategory category;
    private final String itemId;
    private final int stackSize;
    private final float damagePercent;
    private final boolean enchanted;
    private final int weight;
    private final int meta;
    private final NbtCompound nbt;
    private final SourceLoc sourceLoc;

    public FishingLootRule(
        FishingLootCategory category,
        String itemId,
        int stackSize,
        float damagePercent,
        boolean enchanted,
        int weight,
        int meta,
        NbtCompound nbt,
        SourceLoc sourceLoc
    ) {
        this.category = category;
        this.itemId = itemId;
        this.stackSize = stackSize;
        this.damagePercent = damagePercent;
        this.enchanted = enchanted;
        this.weight = weight;
        this.meta = meta;
        this.nbt = nbt;
        this.sourceLoc = sourceLoc;
    }

    public FishingLootCategory category() {
        return category;
    }

    public String itemId() {
        return itemId;
    }

    public int stackSize() {
        return stackSize;
    }

    public float damagePercent() {
        return damagePercent;
    }

    public boolean enchanted() {
        return enchanted;
    }

    public int weight() {
        return weight;
    }

    public int meta() {
        return meta;
    }

    public NbtCompound nbt() {
        return nbt;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
