package ie.orangep.reLootplusplus.lucky.crafting;

import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockItem;
import ie.orangep.reLootplusplus.lucky.block.NativeLuckyBlockEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Map;

/**
 * Dynamic crafting recipe: Lucky Block + luck-modifier item → Lucky Block (luck adjusted).
 *
 * <p>The luck modifier map is populated by {@link LuckyLuckCraftingLoader} from {@code luck_crafting.txt}.
 * Matches any 2-slot grid containing exactly one {@link NativeLuckyBlockItem} (base or addon block)
 * and one registered modifier item.
 *
 * <p>Built-in defaults (used when no addon provides luck_crafting.txt entries):
 * emerald=1, emerald_block=10.
 */
public final class LuckyLuckModifierRecipe extends SpecialCraftingRecipe {

    public static final Identifier ID = new Identifier("lucky", "luck_modifier");
    public static final RecipeSerializer<LuckyLuckModifierRecipe> SERIALIZER =
        new SpecialRecipeSerializer<>(LuckyLuckModifierRecipe::new);

    public LuckyLuckModifierRecipe(Identifier id) {
        super(id);
    }

    @Override
    public boolean matches(CraftingInventory inv, World world) {
        Map<String, Integer> modifiers = LuckyLuckCraftingLoader.getEffectiveModifiers();
        if (modifiers.isEmpty()) return false;

        boolean foundLuckyBlock = false;
        boolean foundModifier = false;
        int luckyBlockCount = 0;
        int modifierCount = 0;
        int nonEmptyCount = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (stack.getItem() instanceof NativeLuckyBlockItem) {
                luckyBlockCount++;
                foundLuckyBlock = true;
            } else {
                // Check if this item is a registered luck modifier
                Identifier itemId = net.minecraft.util.registry.Registry.ITEM.getId(stack.getItem());
                String key = itemId.getPath(); // try without namespace first
                if (!modifiers.containsKey(key)) {
                    key = itemId.toString(); // try full id
                }
                if (modifiers.containsKey(key)) {
                    modifierCount++;
                    foundModifier = true;
                }
            }
        }

        return nonEmptyCount == 2 && luckyBlockCount == 1 && modifierCount == 1 && foundLuckyBlock && foundModifier;
    }

    @Override
    public ItemStack craft(CraftingInventory inv) {
        Map<String, Integer> modifiers = LuckyLuckCraftingLoader.getEffectiveModifiers();
        ItemStack luckyBlockStack = ItemStack.EMPTY;
        int delta = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof NativeLuckyBlockItem) {
                luckyBlockStack = stack;
            } else {
                Identifier itemId = net.minecraft.util.registry.Registry.ITEM.getId(stack.getItem());
                String key = itemId.getPath();
                if (!modifiers.containsKey(key)) key = itemId.toString();
                Integer mod = modifiers.get(key);
                if (mod != null) delta = mod;
            }
        }

        if (luckyBlockStack.isEmpty()) return ItemStack.EMPTY;

        // Copy the lucky block stack and adjust luck
        ItemStack result = luckyBlockStack.copy();
        result.setCount(1);
        int currentLuck = NativeLuckyBlockEntity.getLuckFromItemStack(result);
        int newLuck = Math.max(-100, Math.min(100, currentLuck + delta));
        NativeLuckyBlockEntity.setLuckOnItemStack(result, newLuck);
        return result;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
