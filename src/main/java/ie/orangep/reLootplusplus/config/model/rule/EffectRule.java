package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.config.model.key.ItemKey;
import ie.orangep.reLootplusplus.config.model.key.BlockKey;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.Collections;
import java.util.List;

public final class EffectRule {
    private final String triggerKey;
    private final ItemKey itemKey;
    private final BlockKey blockKey;
    private final String effectId;
    private final int duration;
    private final int amplifier;
    private final float probability;
    private final String particleType;
    private final List<String> setBonusItems;
    private final SourceLoc sourceLoc;

    public EffectRule(
        String triggerKey,
        ItemKey itemKey,
        String effectId,
        int duration,
        int amplifier,
        float probability,
        String particleType,
        List<String> setBonusItems,
        SourceLoc sourceLoc
    ) {
        this.triggerKey = triggerKey;
        this.itemKey = itemKey;
        this.blockKey = null;
        this.effectId = effectId;
        this.duration = duration;
        this.amplifier = amplifier;
        this.probability = probability;
        this.particleType = particleType;
        this.setBonusItems = setBonusItems == null ? Collections.emptyList() : List.copyOf(setBonusItems);
        this.sourceLoc = sourceLoc;
    }

    public EffectRule(
        String triggerKey,
        BlockKey blockKey,
        String effectId,
        int duration,
        int amplifier,
        float probability,
        String particleType,
        SourceLoc sourceLoc
    ) {
        this.triggerKey = triggerKey;
        this.itemKey = null;
        this.blockKey = blockKey;
        this.effectId = effectId;
        this.duration = duration;
        this.amplifier = amplifier;
        this.probability = probability;
        this.particleType = particleType;
        this.setBonusItems = Collections.emptyList();
        this.sourceLoc = sourceLoc;
    }

    public String triggerKey() {
        return triggerKey;
    }

    public ItemKey itemKey() {
        return itemKey;
    }

    public BlockKey blockKey() {
        return blockKey;
    }

    public String effectId() {
        return effectId;
    }

    public int duration() {
        return duration;
    }

    public int amplifier() {
        return amplifier;
    }

    public float probability() {
        return probability;
    }

    public String particleType() {
        return particleType;
    }

    public List<String> setBonusItems() {
        return setBonusItems;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
