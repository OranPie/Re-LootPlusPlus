package ie.orangep.reLootplusplus.config.model.recipe;

public final class RecipeKey {
    private final char symbol;
    private final RecipeInput input;

    public RecipeKey(char symbol, RecipeInput input) {
        this.symbol = symbol;
        this.input = input;
    }

    public char symbol() {
        return symbol;
    }

    public RecipeInput input() {
        return input;
    }
}
