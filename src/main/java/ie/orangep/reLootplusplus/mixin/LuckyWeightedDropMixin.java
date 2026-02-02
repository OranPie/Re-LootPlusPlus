package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.LegacyDropSanitizer;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.common.drop.WeightedDropKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = WeightedDropKt.class, remap = false)
public abstract class LuckyWeightedDropMixin {
    @ModifyVariable(method = "dropsFromStrList", at = @At("HEAD"), argsOnly = true)
    private static List<String> relootplusplus$sanitizeDropList(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        List<String> sanitized = new ArrayList<>(lines.size());
        for (String line : lines) {
            sanitized.add(LegacyDropSanitizer.sanitize(line, reporter));
        }
        return sanitized;
    }
}
