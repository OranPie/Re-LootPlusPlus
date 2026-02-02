package ie.orangep.reLootplusplus.config.model.key;

public final class ItemKey {
    private final String itemId;
    private final int meta;
    private final String nbtRaw;

    public ItemKey(String itemId, int meta, String nbtRaw) {
        this.itemId = itemId;
        this.meta = meta;
        this.nbtRaw = nbtRaw;
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
}
