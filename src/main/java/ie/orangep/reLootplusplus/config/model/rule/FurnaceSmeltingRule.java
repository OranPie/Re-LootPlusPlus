package ie.orangep.reLootplusplus.config.model.rule;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.nbt.NbtCompound;

public final class FurnaceSmeltingRule {
    private final String inputId;
    private final int inputMeta;
    private final String outputId;
    private final int outputMeta;
    private final NbtCompound outputNbt;
    private final int amount;
    private final float xp;
    private final SourceLoc sourceLoc;

    public FurnaceSmeltingRule(
        String inputId,
        int inputMeta,
        String outputId,
        int outputMeta,
        NbtCompound outputNbt,
        int amount,
        float xp,
        SourceLoc sourceLoc
    ) {
        this.inputId = inputId;
        this.inputMeta = inputMeta;
        this.outputId = outputId;
        this.outputMeta = outputMeta;
        this.outputNbt = outputNbt;
        this.amount = amount;
        this.xp = xp;
        this.sourceLoc = sourceLoc;
    }

    public String inputId() {
        return inputId;
    }

    public int inputMeta() {
        return inputMeta;
    }

    public String outputId() {
        return outputId;
    }

    public int outputMeta() {
        return outputMeta;
    }

    public NbtCompound outputNbt() {
        return outputNbt;
    }

    public int amount() {
        return amount;
    }

    public float xp() {
        return xp;
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
