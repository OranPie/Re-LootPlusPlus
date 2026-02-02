package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyItemIdMapper;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.fabric.game.AddonCraftingRecipeKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = AddonCraftingRecipeKt.class, remap = false)
public abstract class LuckyAddonCraftingMixin {
    @ModifyVariable(method = "getIngredient", at = @At("HEAD"), argsOnly = true)
    private static String relootplusplus$mapLegacyRecipeItem(String id) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        String mapped = LegacyItemIdMapper.mapLegacyRecipeItem(id, reporter);
        return mapped == null ? id : mapped;
    }
}
