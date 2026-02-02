package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.common.drop.DropContext;
import mod.lucky.common.drop.SingleDrop;
import mod.lucky.common.drop.action.DropActionsKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DropActionsKt.class, remap = false)
public abstract class LuckyDropActionsMixin {
    @Inject(method = "doEntityDrop", at = @At("HEAD"), cancellable = true)
    private static void relootplusplus$guardEntityDrop(SingleDrop drop, DropContext context, CallbackInfo ci) {
        if (drop == null || !drop.contains("id")) {
            warnMissingEntity(context);
            ci.cancel();
            return;
        }
        Object id = drop.get("id", "");
        if (id == null || id.toString().isBlank()) {
            warnMissingEntity(context);
            ci.cancel();
        }
    }

    private static void warnMissingEntity(DropContext context) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        if (reporter == null) {
            return;
        }
        reporter.warnOnce("LegacyEntityId", "missing entity id (skipped)", null);
    }
}
