package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class FurnaceFuelRule {
    private final String itemId;
    private final int meta;
    private final int burnTime;
    private final SourceLoc sourceLoc;

    public FurnaceFuelRule(String itemId, int meta, int burnTime, SourceLoc sourceLoc) {
        this.itemId = itemId;
        this.meta = meta;
        this.burnTime = burnTime;
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public int meta() {
        return meta;
    }

    public int burnTime() {
        return burnTime;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
