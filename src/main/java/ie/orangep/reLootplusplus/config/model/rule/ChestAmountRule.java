package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class ChestAmountRule {
    private final String chestType;
    private final int minItems;
    private final int maxItems;
    private final SourceLoc sourceLoc;

    public ChestAmountRule(String chestType, int minItems, int maxItems, SourceLoc sourceLoc) {
        this.chestType = chestType;
        this.minItems = minItems;
        this.maxItems = maxItems;
        this.sourceLoc = sourceLoc;
    }

    public String chestType() {
        return chestType;
    }

    public int minItems() {
        return minItems;
    }

    public int maxItems() {
        return maxItems;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
