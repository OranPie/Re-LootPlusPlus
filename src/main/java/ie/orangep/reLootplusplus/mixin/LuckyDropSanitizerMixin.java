package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.legacy.LegacyDropSanitizer;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.java.loader.LoaderKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = LoaderKt.class, remap = false)
public abstract class LuckyDropSanitizerMixin {
    @ModifyVariable(method = "parseDrops", at = @At("HEAD"), argsOnly = true)
    private static List<String> relootplusplus$sanitizeDrops(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        List<String> merged = new ArrayList<>(lines.size());
        StringBuilder buffer = null;
        boolean warned = false;
        for (String line : lines) {
            if (buffer == null) {
                buffer = new StringBuilder(line);
            } else {
                buffer.append(line.stripLeading());
            }
            String trimmed = buffer.toString().trim();
            if (trimmed.endsWith("\\")) {
                int idx = buffer.lastIndexOf("\\");
                if (idx >= 0) {
                    buffer.deleteCharAt(idx);
                }
                buffer.append(' ');
                if (!warned && reporter != null) {
                    reporter.warnOnce("LegacyLineContinuation", "joined lines ending with \\\\", null);
                    warned = true;
                }
                continue;
            }
            merged.add(buffer.toString());
            buffer = null;
        }
        if (buffer != null) {
            merged.add(buffer.toString());
        }
        List<String> sanitized = new ArrayList<>(merged.size());
        for (String line : merged) {
            sanitized.add(LegacyDropSanitizer.sanitize(line, reporter));
        }
        return sanitized;
    }
}
