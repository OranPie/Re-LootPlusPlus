package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.List;

public final class ThrownDef {
    private final String itemId;
    private final String displayName;
    private final boolean shines;
    private final float damage;
    private final float velocity;
    private final float gravity;
    private final float inaccuracy;
    private final float dropChance;
    private final List<DropGroup> dropGroups;
    private final SourceLoc sourceLoc;

    public ThrownDef(
        String itemId,
        String displayName,
        boolean shines,
        float damage,
        float velocity,
        float gravity,
        float inaccuracy,
        float dropChance,
        List<DropGroup> dropGroups,
        SourceLoc sourceLoc
    ) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.shines = shines;
        this.damage = damage;
        this.velocity = velocity;
        this.gravity = gravity;
        this.inaccuracy = inaccuracy;
        this.dropChance = dropChance;
        this.dropGroups = List.copyOf(dropGroups);
        this.sourceLoc = sourceLoc;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public boolean shines() {
        return shines;
    }

    public float damage() {
        return damage;
    }

    public float velocity() {
        return velocity;
    }

    public float gravity() {
        return gravity;
    }

    public float inaccuracy() {
        return inaccuracy;
    }

    public float dropChance() {
        return dropChance;
    }

    public List<DropGroup> dropGroups() {
        return dropGroups;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
