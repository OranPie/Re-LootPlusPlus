package ie.orangep.reLootplusplus.config.model.general;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class CreativeMenuEntry {
    private final String category;
    private final String itemId;
    private final int meta;
    private final String nbtRaw;
    private final SourceLoc sourceLoc;

    public CreativeMenuEntry(String category, String itemId, int meta, String nbtRaw, SourceLoc sourceLoc) {
        this.category = category;
        this.itemId = itemId;
        this.meta = meta;
        this.nbtRaw = nbtRaw;
        this.sourceLoc = sourceLoc;
    }

    public String category() {
        return category;
    }

    public String itemId() {
        return itemId;
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
