package ie.orangep.reLootplusplus.config.model.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class GenericItemDef {
    private final String itemId;
    private final String displayName;
    private final boolean shiny;
    private final SourceLoc sourceLoc;

    public GenericItemDef(String itemId, String displayName, boolean shiny, SourceLoc sourceLoc) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.shiny = shiny;
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public boolean shiny() {
        return shiny;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
