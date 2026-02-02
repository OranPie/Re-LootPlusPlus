package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.common.drop.DropContext;
import mod.lucky.common.drop.DropEvaluatorKt;
import mod.lucky.common.drop.SingleDrop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DropEvaluatorKt.class, remap = false)
public abstract class LuckyDropEvaluatorMixin {
    @Inject(method = "runEvaluatedDrop", at = @At("HEAD"), cancellable = true)
    private static void relootplusplus$guardMissingEntityId(SingleDrop drop, DropContext context, CallbackInfo ci) {
        if (drop == null) {
            return;
        }
        String type = drop.getType();
        if (!"entity".equalsIgnoreCase(type)) {
            return;
        }
        if (!drop.contains("id")) {
            warn(context);
            ci.cancel();
            return;
        }
        Object id = drop.get("id", "");
        if (id == null || id.toString().isBlank()) {
            warn(context);
            ci.cancel();
        }
    }

    private static void warn(DropContext context) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        if (reporter == null) {
            return;
        }
        reporter.warnOnce("LegacyEntityId", "missing entity id (skipped)", null);
    }
}
