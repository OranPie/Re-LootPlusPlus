package ie.orangep.reLootplusplus.content.item;

import ie.orangep.reLootplusplus.legacy.text.LegacyText;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.text.Text;

public final class LegacyNamedShovelItem extends ShovelItem {
    private final String displayName;

    public LegacyNamedShovelItem(ToolMaterial material, float attackDamage, float attackSpeed, Settings settings, String displayName) {
        super(material, attackDamage, attackSpeed, settings);
        this.displayName = displayName;
    }

    @Override
    public Text getName(ItemStack stack) {
        if (displayName == null || displayName.isEmpty()) {
            return super.getName(stack);
        }
        return LegacyText.fromLegacy(displayName);
    }
}
