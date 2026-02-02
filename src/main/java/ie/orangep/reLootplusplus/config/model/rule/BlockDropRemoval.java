package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class BlockDropRemoval {
    private final String blockId;
    private final int blockMeta;
    private final String itemId;
    private final int itemMeta;
    private final String nbtRaw;
    private final SourceLoc sourceLoc;

    public BlockDropRemoval(
        String blockId,
        int blockMeta,
        String itemId,
        int itemMeta,
        String nbtRaw,
        SourceLoc sourceLoc
    ) {
        this.blockId = blockId;
        this.blockMeta = blockMeta;
        this.itemId = itemId;
        this.itemMeta = itemMeta;
        this.nbtRaw = nbtRaw;
        this.sourceLoc = sourceLoc;
    }

    public String blockId() {
        return blockId;
    }

    public int blockMeta() {
        return blockMeta;
    }

    public String itemId() {
        return itemId;
    }

    public int itemMeta() {
        return itemMeta;
    }

    public String nbtRaw() {
        return nbtRaw;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
