package ie.orangep.reLootplusplus.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

final class NbtRecipeUtils {
    private NbtRecipeUtils() {
    }

    static ItemStack withNbt(ItemStack base, NbtCompound nbt) {
        if (base == null) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = base.copy();
        if (nbt != null) {
            copy.setNbt(nbt.copy());
        }
        return copy;
    }
}
