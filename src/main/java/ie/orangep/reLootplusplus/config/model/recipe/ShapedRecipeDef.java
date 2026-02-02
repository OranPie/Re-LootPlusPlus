package ie.orangep.reLootplusplus.config.model.recipe;

import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.Collections;
import java.util.List;

public final class ShapedRecipeDef {
    private final String outputId;
    private final int outputCount;
    private final int outputMeta;
    private final String outputNbt;
    private final String pattern;
    private final List<RecipeKey> keys;
    private final SourceLoc sourceLoc;

    public ShapedRecipeDef(
        String outputId,
        int outputCount,
        int outputMeta,
        String outputNbt,
        String pattern,
        List<RecipeKey> keys,
        SourceLoc sourceLoc
    ) {
        this.outputId = outputId;
        this.outputCount = outputCount;
        this.outputMeta = outputMeta;
        this.outputNbt = outputNbt;
        this.pattern = pattern;
        this.keys = keys == null ? List.of() : List.copyOf(keys);
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

    public String pattern() {
        return pattern;
    }

    public List<RecipeKey> keys() {
        return Collections.unmodifiableList(keys);
    }

    public SourceLoc sourceLoc() {
        return sourceLoc;
    }
}
