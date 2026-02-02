package ie.orangep.reLootplusplus.config.model.item;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

public final class ArmorDef {
    private final String itemId;
    private final String displayName;
    private final String materialItemId;
    private final int materialMeta;
    private final String textureBase;
    private final ArmorSlotType slotType;
    private final SourceLoc sourceLoc;

    public ArmorDef(
        String itemId,
        String displayName,
        String materialItemId,
        int materialMeta,
        String textureBase,
        ArmorSlotType slotType,
        SourceLoc sourceLoc
    ) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.materialItemId = materialItemId;
        this.materialMeta = materialMeta;
        this.textureBase = textureBase;
        this.slotType = slotType;
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

    public String textureBase() {
        return textureBase;
    }

    public ArmorSlotType slotType() {
        return slotType;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
