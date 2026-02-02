package ie.orangep.reLootplusplus.recipe;

import ie.orangep.reLootplusplus.diagnostic.Log;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class ModRecipes {
    public static final String MOD_ID = "relootplusplus";
    public static final Identifier NBT_SHAPED_ID = new Identifier(MOD_ID, "nbt_shaped");
    public static final Identifier NBT_SHAPELESS_ID = new Identifier(MOD_ID, "nbt_shapeless");

    public static final RecipeType<NbtShapedRecipe> NBT_SHAPED_TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return NBT_SHAPED_ID.toString();
        }
    };
    public static final RecipeType<NbtShapelessRecipe> NBT_SHAPELESS_TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return NBT_SHAPELESS_ID.toString();
        }
    };

    public static final RecipeSerializer<NbtShapedRecipe> NBT_SHAPED_SERIALIZER = new NbtShapedRecipe.Serializer();
    public static final RecipeSerializer<NbtShapelessRecipe> NBT_SHAPELESS_SERIALIZER = new NbtShapelessRecipe.Serializer();

    private static boolean registered;

    private ModRecipes() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(Registry.RECIPE_TYPE, NBT_SHAPED_ID, NBT_SHAPED_TYPE);
        Registry.register(Registry.RECIPE_TYPE, NBT_SHAPELESS_ID, NBT_SHAPELESS_TYPE);
        Registry.register(Registry.RECIPE_SERIALIZER, NBT_SHAPED_ID, NBT_SHAPED_SERIALIZER);
        Registry.register(Registry.RECIPE_SERIALIZER, NBT_SHAPELESS_ID, NBT_SHAPELESS_SERIALIZER);
        Log.LOGGER.info("Registered custom recipe serializers for NBT outputs");
    }
}
