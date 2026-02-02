package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.java.loader.AddonResources;
import mod.lucky.java.loader.LoaderKt;
import mod.lucky.kotlin.collections.CollectionsKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.List;
import java.util.Locale;

@Mixin(value = LoaderKt.class, remap = false)
public abstract class LuckyAddonIdFallbackMixin {
    private static final ThreadLocal<File> CURRENT_ADDON = new ThreadLocal<>();

    @Inject(method = "loadAddonResources", at = @At("HEAD"))
    private static void relootplusplus$trackAddon(File addonFile, CallbackInfoReturnable<AddonResources> cir) {
        CURRENT_ADDON.set(addonFile);
    }

    @Inject(method = "loadAddonResources", at = @At("RETURN"))
    private static void relootplusplus$clearAddon(File addonFile, CallbackInfoReturnable<AddonResources> cir) {
        CURRENT_ADDON.remove();
    }

    @Redirect(
        method = "loadAddonResources",
        at = @At(value = "INVOKE", target = "Lmod/lucky/kotlin/collections/CollectionsKt;first(Ljava/util/List;)Ljava/lang/Object;")
    )
    private static Object relootplusplus$safeFirst(List<?> list) {
        if (list == null || list.isEmpty()) {
            String fallback = buildFallbackId();
            warnOnce("LuckyAddonId", "missing addon ids, using " + fallback);
            return fallback;
        }
        return CollectionsKt.first(list);
    }

    private static String buildFallbackId() {
        File file = CURRENT_ADDON.get();
        if (file == null) {
            return "lucky:unknown";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String sanitized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (sanitized.isEmpty()) {
            sanitized = "unknown";
        }
        return "lucky:" + sanitized;
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
