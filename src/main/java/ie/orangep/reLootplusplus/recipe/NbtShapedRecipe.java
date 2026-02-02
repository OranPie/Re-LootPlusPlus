package ie.orangep.reLootplusplus.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class NbtShapedRecipe implements net.minecraft.recipe.Recipe<net.minecraft.inventory.CraftingInventory> {
    private final ShapedRecipe base;
    private final NbtCompound nbt;

    public NbtShapedRecipe(ShapedRecipe base, NbtCompound nbt) {
        this.base = base;
        this.nbt = nbt;
    }

    @Override
    public boolean matches(net.minecraft.inventory.CraftingInventory inventory, World world) {
        return base.matches(inventory, world);
    }

    @Override
    public ItemStack craft(net.minecraft.inventory.CraftingInventory inventory) {
        return NbtRecipeUtils.withNbt(base.craft(inventory), nbt);
    }

    @Override
    public boolean fits(int width, int height) {
        return base.fits(width, height);
    }

    @Override
    public ItemStack getOutput() {
        return NbtRecipeUtils.withNbt(base.getOutput(), nbt);
    }

    @Override
    public Identifier getId() {
        return base.getId();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.NBT_SHAPED_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.NBT_SHAPED_TYPE;
    }

    public ShapedRecipe base() {
        return base;
    }

    public NbtCompound nbt() {
        return nbt;
    }

    public static final class Serializer implements RecipeSerializer<NbtShapedRecipe> {
        private final ShapedRecipe.Serializer base = new ShapedRecipe.Serializer();

        @Override
        public NbtShapedRecipe read(Identifier id, JsonObject json) {
            ShapedRecipe recipe = base.read(id, json);
            NbtCompound nbt = readResultNbt(json);
            return new NbtShapedRecipe(recipe, nbt);
        }

        @Override
        public NbtShapedRecipe read(Identifier id, PacketByteBuf buf) {
            ShapedRecipe recipe = base.read(id, buf);
            NbtCompound nbt = buf.readNbt();
            return new NbtShapedRecipe(recipe, nbt);
        }

        @Override
        public void write(PacketByteBuf buf, NbtShapedRecipe recipe) {
            base.write(buf, recipe.base);
            buf.writeNbt(recipe.nbt);
        }

        private static NbtCompound readResultNbt(JsonObject json) {
            if (!json.has("result") || !json.get("result").isJsonObject()) {
                return null;
            }
            JsonObject result = json.getAsJsonObject("result");
            if (!result.has("nbt")) {
                return null;
            }
            JsonElement nbtElem = result.get("nbt");
            String raw;
            if (nbtElem.isJsonPrimitive()) {
                JsonPrimitive prim = nbtElem.getAsJsonPrimitive();
                raw = prim.isString() ? prim.getAsString() : prim.toString();
            } else {
                raw = nbtElem.toString();
            }
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return StringNbtReader.parse(raw);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
