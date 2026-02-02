package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class ChestLootRule {
    private final String chestType;
    private final String itemId;
    private final int minCount;
    private final int maxCount;
    private final int weight;
    private final int meta;
    private final String nbtRaw;
    private final SourceLoc sourceLoc;

    public ChestLootRule(
        String chestType,
        String itemId,
        int minCount,
        int maxCount,
        int weight,
        int meta,
        String nbtRaw,
        SourceLoc sourceLoc
    ) {
        this.chestType = chestType;
        this.itemId = itemId;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.weight = weight;
        this.meta = meta;
        this.nbtRaw = nbtRaw;
        this.sourceLoc = sourceLoc;
    }

    public String chestType() {
        return chestType;
    }

    public String itemId() {
        return itemId;
    }

    public int minCount() {
        return minCount;
    }

    public int maxCount() {
        return maxCount;
    }

    public int weight() {
        return weight;
    }

    public int meta() {
        return meta;
    }

    public String nbtRaw() {
        return nbtRaw;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
