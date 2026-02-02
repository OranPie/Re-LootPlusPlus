package ie.orangep.reLootplusplus.config.model.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class ToolDef {
    private final String itemId;
    private final String displayName;
    private final String materialItemId;
    private final int materialMeta;
    private final ToolType type;
    private final SourceLoc sourceLoc;

    public ToolDef(String itemId, String displayName, String materialItemId, int materialMeta, ToolType type, SourceLoc sourceLoc) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.materialItemId = materialItemId;
        this.materialMeta = materialMeta;
        this.type = type;
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public String materialItemId() {
        return materialItemId;
    }

    public int materialMeta() {
        return materialMeta;
    }

    public ToolType type() {
        return type;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
