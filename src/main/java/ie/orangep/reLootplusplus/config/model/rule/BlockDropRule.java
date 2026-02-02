package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.config.model.drop.DropGroup;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.List;

public final class BlockDropRule {
    private final String blockId;
    private final int blockMeta;
    private final float rarity;
    private final boolean onlyPlayerMined;
    private final boolean dropWithSilk;
    private final boolean affectedByFortune;
    private final List<DropGroup> groups;
    private final SourceLoc sourceLoc;

    public BlockDropRule(
        String blockId,
        int blockMeta,
        float rarity,
        boolean onlyPlayerMined,
        boolean dropWithSilk,
        boolean affectedByFortune,
        List<DropGroup> groups,
        SourceLoc sourceLoc
    ) {
        this.blockId = blockId;
        this.blockMeta = blockMeta;
        this.rarity = rarity;
        this.onlyPlayerMined = onlyPlayerMined;
        this.dropWithSilk = dropWithSilk;
        this.affectedByFortune = affectedByFortune;
        this.groups = List.copyOf(groups);
        this.sourceLoc = sourceLoc;
    }

    public String blockId() {
        return blockId;
    }

    public int blockMeta() {
        return blockMeta;
    }

    public float rarity() {
        return rarity;
    }

    public boolean onlyPlayerMined() {
        return onlyPlayerMined;
    }

    public boolean dropWithSilk() {
        return dropWithSilk;
    }

    public boolean affectedByFortune() {
        return affectedByFortune;
    }

    public List<DropGroup> groups() {
        return groups;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
