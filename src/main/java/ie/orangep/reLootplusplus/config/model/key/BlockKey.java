package ie.orangep.reLootplusplus.config.model.key;

public final class BlockKey {
    private final String blockId;
    private final int meta;

    public BlockKey(String blockId, int meta) {
        this.blockId = blockId;
        this.meta = meta;
    }

    public String blockId() {
        return blockId;
    }

    public int meta() {
        return meta;
    }
}
