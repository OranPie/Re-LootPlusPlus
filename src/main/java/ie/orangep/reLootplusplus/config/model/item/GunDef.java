package ie.orangep.reLootplusplus.config.model.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.List;

public final class GunDef {
    private final String itemId;
    private final String displayName;
    private final int durability;
    private final List<String> rawParts;
    private final SourceLoc sourceLoc;

    public GunDef(String itemId, String displayName, int durability, List<String> rawParts, SourceLoc sourceLoc) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.durability = durability;
        this.rawParts = rawParts == null ? List.of() : List.copyOf(rawParts);
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public int durability() {
        return durability;
    }

    public List<String> rawParts() {
        return rawParts;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
