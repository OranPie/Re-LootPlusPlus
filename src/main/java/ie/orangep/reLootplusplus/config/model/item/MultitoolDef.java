package ie.orangep.reLootplusplus.config.model.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.List;

public final class MultitoolDef {
    private final String itemId;
    private final String displayName;
    private final String toolType;
    private final List<String> rawParts;
    private final SourceLoc sourceLoc;

    public MultitoolDef(String itemId, String displayName, String toolType, List<String> rawParts, SourceLoc sourceLoc) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.toolType = toolType;
        this.rawParts = rawParts == null ? List.of() : List.copyOf(rawParts);
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public String toolType() {
        return toolType;
    }

    public List<String> rawParts() {
        return rawParts;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
