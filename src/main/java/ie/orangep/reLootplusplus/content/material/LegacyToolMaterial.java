package ie.orangep.reLootplusplus.content.material;

import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

public final class LegacyToolMaterial implements ToolMaterial {
    private final int miningLevel;
    private final int durability;
    private final float speed;
    private final float attackDamage;
    private final int enchantability;
    private final Ingredient repairIngredient;

    public LegacyToolMaterial(int miningLevel, int durability, float speed, float attackDamage, int enchantability, Ingredient repairIngredient) {
        this.miningLevel = miningLevel;
        this.durability = durability;
        this.speed = speed;
        this.attackDamage = attackDamage;
        this.enchantability = enchantability;
        this.repairIngredient = repairIngredient;
    }

    @Override
    public int getDurability() {
        return durability;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return speed;
    }

    @Override
    public float getAttackDamage() {
        return attackDamage;
    }

    @Override
    public int getMiningLevel() {
        return miningLevel;
    }

    @Override
    public int getEnchantability() {
        return enchantability;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient;
    }
}
