package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.common.attribute.Attr;
import mod.lucky.common.attribute.AttrSpec;
import mod.lucky.common.attribute.AttrType;
import mod.lucky.common.attribute.ParserContext;
import mod.lucky.common.attribute.ParserKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ParserKt.class, remap = false)
public abstract class LuckyParserMixin {
    @Inject(method = "parseSingleKey", at = @At("HEAD"), cancellable = true)
    private static void relootplusplus$allowBareChance(String value, String key, AttrSpec spec, ParserContext context, CallbackInfoReturnable<Attr> cir) {
        if (value == null || key == null) {
            return;
        }
        if ("type".equalsIgnoreCase(key) && !hasTopLevelKey(value, "type=")) {
            warnOnce("LuckyAttr", "missing type=, defaulting to item");
            try {
                cir.setReturnValue(ParserKt.parseAttr("item", spec, context, key, AttrType.STRING));
            } catch (Exception e) {
                warnOnce("LuckyAttr", "failed to coerce missing type: " + e.getMessage());
                cir.setReturnValue(null);
            }
            return;
        }
        if (!"chance".equalsIgnoreCase(key)) {
            return;
        }
        if (value.indexOf('=') >= 0) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (!"chance".equalsIgnoreCase(trimmed)) {
            return;
        }
        warnOnce("LuckyAttr", "bare @chance treated as chance=1");
        try {
            cir.setReturnValue(ParserKt.parseAttr("1", spec, context, key, AttrType.DICT));
        } catch (Exception e) {
            warnOnce("LuckyAttr", "failed to coerce bare @chance: " + e.getMessage());
            cir.setReturnValue(null);
        }
    }

    private static void warnOnce(String type, String detail) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        if (reporter != null) {
            reporter.warnOnce(type, detail, null);
            return;
        }
        Log.warn("[LootPP-Legacy] {} {}", type, detail);
    }

    private static boolean hasTopLevelKey(String input, String needle) {
        if (input == null || needle == null || input.isEmpty()) {
            return false;
        }
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        for (int i = 0; i <= input.length() - needle.length(); i++) {
            char c = input.charAt(i);
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
            }
            if (!inSingle && !inDouble && depth == 0 && input.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }
}
