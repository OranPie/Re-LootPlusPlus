package ie.orangep.reLootplusplus.content.item;

import ie.orangep.reLootplusplus.legacy.text.LegacyText;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class LootThrownItem extends Item {
    private final boolean shines;
    private final String displayName;

    public LootThrownItem(Settings settings, boolean shines, String displayName) {
        super(settings);
        this.shines = shines;
        this.displayName = displayName;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return shines || super.hasGlint(stack);
    }

    @Override
    public Text getName(ItemStack stack) {
        if (displayName == null || displayName.isEmpty()) {
            return super.getName(stack);
        }
        return LegacyText.fromLegacy(displayName);
    }
}
