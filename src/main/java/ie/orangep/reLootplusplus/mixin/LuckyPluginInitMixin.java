package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.java.loader.LoaderKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

@Mixin(value = LoaderKt.class, remap = false)
public abstract class LuckyPluginInitMixin {
    @Inject(method = "getInputStream", at = @At("RETURN"), cancellable = true)
    private static void relootplusplus$pluginInitFallback(File baseDir, String path, CallbackInfoReturnable<InputStream> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        if (!"plugin_init.txt".equals(path)) {
            return;
        }
        warnOnce("LuckyPluginInit", "missing plugin_init.txt for " + (baseDir == null ? "unknown" : baseDir.getName()));
        cir.setReturnValue(new ByteArrayInputStream(new byte[0]));
    }

    private static void warnOnce(String type, String detail) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        if (reporter != null) {
            reporter.warnOnce(type, detail, null);
            return;
        }
        Log.warn("[LootPP-Legacy] {} {}", type, detail);
    }
}
