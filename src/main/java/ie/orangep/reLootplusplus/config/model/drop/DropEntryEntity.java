package ie.orangep.reLootplusplus.config.model.drop;

public final class DropEntryEntity implements DropEntry {
    private final String entityId;
    private final int weight;
    private final String nbtRaw;

    public DropEntryEntity(String entityId, int weight, String nbtRaw) {
        this.entityId = entityId;
        this.weight = weight;
        this.nbtRaw = nbtRaw;
    }

    public String entityId() {
        return entityId;
    }

    @Override
    public int weight() {
        return weight;
    }

    public String nbtRaw() {
        return nbtRaw;
    }
}
