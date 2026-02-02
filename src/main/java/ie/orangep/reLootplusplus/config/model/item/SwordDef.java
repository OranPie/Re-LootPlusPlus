package ie.orangep.reLootplusplus.config.model.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class SwordDef {
    private final String itemId;
    private final String displayName;
    private final String materialItemId;
    private final int materialMeta;
    private final float extraDamage;
    private final SourceLoc sourceLoc;

    public SwordDef(String itemId, String displayName, String materialItemId, int materialMeta, float extraDamage, SourceLoc sourceLoc) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.materialItemId = materialItemId;
        this.materialMeta = materialMeta;
        this.extraDamage = extraDamage;
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

    public float extraDamage() {
        return extraDamage;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
