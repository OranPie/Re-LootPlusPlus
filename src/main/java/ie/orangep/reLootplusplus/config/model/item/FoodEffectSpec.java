package ie.orangep.reLootplusplus.config.model.item;

public final class FoodEffectSpec {
    private final String effectId;
    private final int durationTicks;
    private final int amplifier;
    private final float probability;
    private final String particleType;

    public FoodEffectSpec(String effectId, int durationTicks, int amplifier, float probability, String particleType) {
        this.effectId = effectId;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.probability = probability;
        this.particleType = particleType;
    }

    public String effectId() {
        return effectId;
    }

    public int durationTicks() {
        return durationTicks;
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
}
