package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class StackSizeRule {
    private final String itemId;
    private final int maxSize;
    private final SourceLoc sourceLoc;

    public StackSizeRule(String itemId, int maxSize, SourceLoc sourceLoc) {
        this.itemId = itemId;
        this.maxSize = maxSize;
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public int maxSize() {
        return maxSize;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
