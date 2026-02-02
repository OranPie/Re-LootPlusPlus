package ie.orangep.reLootplusplus.content.material;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public final class LegacyArmorMaterial implements ArmorMaterial {
    private static final int[] BASE_DURABILITY = new int[] {13, 15, 16, 11};

    private final String name;
    private final int durabilityFactor;
    private final int[] protection;
    private final int enchantability;
    private final Ingredient repairIngredient;
    private final float toughness;
    private final float knockbackResistance;
    private final SoundEvent equipSound;

    public LegacyArmorMaterial(
        String name,
        int durabilityFactor,
        int[] protection,
        int enchantability,
        Ingredient repairIngredient,
        float toughness,
        float knockbackResistance
    ) {
        this.name = name;
        this.durabilityFactor = durabilityFactor;
        this.protection = protection == null ? new int[] {0, 0, 0, 0} : protection.clone();
        this.enchantability = enchantability;
        this.repairIngredient = repairIngredient;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.equipSound = SoundEvents.ITEM_ARMOR_EQUIP_GENERIC;
    }

    @Override
    public int getDurability(EquipmentSlot slot) {
        return BASE_DURABILITY[slotIndex(slot)] * durabilityFactor;
    }

    @Override
    public int getProtectionAmount(EquipmentSlot slot) {
        return protection[slotIndex(slot)];
    }

    @Override
    public int getEnchantability() {
        return enchantability;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }

    private int slotIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> 0;
        };
    }
}
