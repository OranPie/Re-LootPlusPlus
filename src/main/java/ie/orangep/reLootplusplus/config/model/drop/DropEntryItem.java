package ie.orangep.reLootplusplus.config.model.drop;

public final class DropEntryItem implements DropEntry {
    private final String itemId;
    private final int minCount;
    private final int maxCount;
    private final int weight;
    private final int meta;
    private final String nbtRaw;

    public DropEntryItem(String itemId, int minCount, int maxCount, int weight, int meta, String nbtRaw) {
        this.itemId = itemId;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.weight = weight;
        this.meta = meta;
        this.nbtRaw = nbtRaw;
    }

    public String itemId() {
        return itemId;
    }

    public int minCount() {
        return minCount;
    }

    public int maxCount() {
        return maxCount;
    }

    @Override
    public int weight() {
        return weight;
    }

    public int meta() {
        return meta;
    }

    public String nbtRaw() {
        return nbtRaw;
    }
}
