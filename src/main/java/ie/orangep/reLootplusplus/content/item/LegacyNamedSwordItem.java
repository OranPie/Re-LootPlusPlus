package ie.orangep.reLootplusplus.content.item;

import ie.orangep.reLootplusplus.legacy.text.LegacyText;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.text.Text;

public final class LegacyNamedSwordItem extends SwordItem {
    private final String displayName;

    public LegacyNamedSwordItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings, String displayName) {
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
