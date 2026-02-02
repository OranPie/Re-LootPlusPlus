package ie.orangep.reLootplusplus.content.item;

import ie.orangep.reLootplusplus.legacy.text.LegacyText;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class LegacyNamedArmorItem extends ArmorItem {
    private final String displayName;

    public LegacyNamedArmorItem(ArmorMaterial material, EquipmentSlot slot, Settings settings, String displayName) {
        super(material, slot, settings);
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
