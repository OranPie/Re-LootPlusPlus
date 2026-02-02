package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.runtime.RuntimeState;
import ie.orangep.reLootplusplus.runtime.StackSizeRegistry;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public final class ItemStackSizeMixin {
    @Inject(method = "getMaxCount", at = @At("HEAD"), cancellable = true)
    private void relootplusplus$overrideMaxCount(CallbackInfoReturnable<Integer> cir) {
        StackSizeRegistry registry = RuntimeState.stackSizeRegistry();
        if (registry == null) {
            return;
        }
        int override = registry.overrideFor((Item) (Object) this);
        if (override > 0) {
            cir.setReturnValue(override);
        }
    }
}
