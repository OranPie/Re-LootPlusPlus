package ie.orangep.reLootplusplus.config.model.recipe;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.Collections;
import java.util.List;

public final class ShapelessRecipeDef {
    private final String outputId;
    private final int outputCount;
    private final int outputMeta;
    private final String outputNbt;
    private final List<RecipeInput> inputs;
    private final SourceLoc sourceLoc;

    public ShapelessRecipeDef(
        String outputId,
        int outputCount,
        int outputMeta,
        String outputNbt,
        List<RecipeInput> inputs,
        SourceLoc sourceLoc
    ) {
        this.outputId = outputId;
        this.outputCount = outputCount;
        this.outputMeta = outputMeta;
        this.outputNbt = outputNbt;
        this.inputs = inputs == null ? List.of() : List.copyOf(inputs);
        this.sourceLoc = sourceLoc;
    }

    public String outputId() {
        return outputId;
    }

    public int outputCount() {
        return outputCount;
    }

    public int outputMeta() {
        return outputMeta;
    }

    public String outputNbt() {
        return outputNbt;
    }

    public List<RecipeInput> inputs() {
        return Collections.unmodifiableList(inputs);
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
