package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.java.Addon;
import mod.lucky.java.loader.AddonResources;
import mod.lucky.kotlin.Pair;
import mod.lucky.java.loader.MainResources;
import mod.lucky.java.loader.LoaderKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(value = LoaderKt.class, remap = false)
public abstract class LuckyLoaderMixin {
    @Inject(method = "loadResources", at = @At("RETURN"), cancellable = true)
    private static void relootplusplus$dedupeLuckyAddons(File gameDir, CallbackInfoReturnable<Pair> cir) {
        Pair result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        Object first = result.getFirst();
        Object second = result.getSecond();
        if (!(second instanceof List)) {
            return;
        }
        List<?> list = (List<?>) second;
        if (list.isEmpty()) {
            return;
        }
        List<AddonResources> filtered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object entry : list) {
            if (!(entry instanceof AddonResources)) {
                continue;
            }
            AddonResources resources = (AddonResources) entry;
            Addon addon = resources.getAddon();
            String addonId = addon == null ? "unknown" : addon.getAddonId();
            if (addonId == null) {
                addonId = "unknown";
            }
            if (seen.add(addonId)) {
                filtered.add(resources);
            } else {
                warnDuplicate(addonId, addon == null ? null : addon.getFile());
            }
        }
        if (filtered.size() == list.size()) {
            return;
        }
        Pair updated = new Pair((MainResources) first, filtered);
        cir.setReturnValue(updated);
    }

    private static void warnDuplicate(String addonId, File file) {
        String where = file == null ? "" : (" from " + file.getName());
        String detail = "skipping duplicate LuckyBlock addonId " + addonId + where;
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        if (reporter != null) {
            reporter.warnOnce("LuckyDuplicateAddon", detail, null);
            return;
        }
        Log.warn("[LootPP-Legacy] LuckyDuplicateAddon {}", detail);
    }
}
