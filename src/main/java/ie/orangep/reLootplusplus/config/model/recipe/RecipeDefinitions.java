package ie.orangep.reLootplusplus.config.model.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecipeDefinitions {
    private final List<ShapedRecipeDef> shaped = new ArrayList<>();
    private final List<ShapelessRecipeDef> shapeless = new ArrayList<>();

    public void addShaped(ShapedRecipeDef def) {
        shaped.add(def);
    }

    public void addShapeless(ShapelessRecipeDef def) {
        shapeless.add(def);
    }

    public List<ShapedRecipeDef> shaped() {
        return Collections.unmodifiableList(shaped);
    }

    public List<ShapelessRecipeDef> shapeless() {
        return Collections.unmodifiableList(shapeless);
    }
}
