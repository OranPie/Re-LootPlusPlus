package ie.orangep.reLootplusplus.config.model.recipe;

public final class RecipeInput {
    private final String itemId;
    private final int meta;
    private final String nbtRaw;
    private final boolean oreDict;

    public RecipeInput(String itemId, int meta, String nbtRaw, boolean oreDict) {
        this.itemId = itemId;
        this.meta = meta;
        this.nbtRaw = nbtRaw;
        this.oreDict = oreDict;
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

    public boolean oreDict() {
        return oreDict;
    }
}
