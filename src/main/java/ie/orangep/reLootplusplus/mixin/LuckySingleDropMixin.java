package ie.orangep.reLootplusplus.mixin;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import mod.lucky.common.LuckyRegistry;
import mod.lucky.common.attribute.Attr;
import mod.lucky.common.attribute.DictAttr;
import mod.lucky.common.drop.SingleDrop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Map;

@Mixin(value = SingleDrop.class, remap = false)
public abstract class LuckySingleDropMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void relootplusplus$guardGet(String k, Object defaultValue, CallbackInfoReturnable<Object> cir) {
        if (k == null) {
            return;
        }
        SingleDrop self = (SingleDrop) (Object) this;
        DictAttr props = self.getProps();
        if (props != null && props.contains(k)) {
            Attr attr = props.get(k);
            if (attr == null) {
                warnOnce("LegacyDropProp", "null attr for key '" + k + "'");
                if (defaultValue != null) {
                    cir.setReturnValue(defaultValue);
                    return;
                }
                Object fallback = fallbackValue(k);
                if (fallback != null) {
                    cir.setReturnValue(fallback);
                    return;
                }
                cir.setReturnValue(null);
            }
            return;
        }
        if (defaultValue != null) {
            return;
        }
        Map<String, Map<String, Object>> defaults = LuckyRegistry.INSTANCE.getDropDefaults();
        Map<String, Object> typeDefaults = defaults.get(self.getType());
        if (typeDefaults == null || !typeDefaults.containsKey(k)) {
            warnOnce("LegacyDropProp", "missing default for '" + self.getType() + "." + k + "'");
            Object fallback = fallbackValue(k);
            if (fallback != null) {
                cir.setReturnValue(fallback);
                return;
            }
            cir.setReturnValue(null);
        }
    }

    private static Object fallbackValue(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if ("id".equals(lower) || "commandsender".equals(lower)) {
            return "";
        }
        if ("displayoutput".equals(lower)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static void warnOnce(String type, String detail) {
        LegacyWarnReporter reporter = RuntimeState.warnReporter();
        if (reporter != null) {
            reporter.warnOnce(type, detail, null);
        }
    }
}
