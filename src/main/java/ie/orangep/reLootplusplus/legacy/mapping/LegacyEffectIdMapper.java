package ie.orangep.reLootplusplus.legacy.mapping;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LegacyEffectIdMapper {
    private static final Map<Integer, String> NUMERIC_MAP = new HashMap<>();
    private static final Map<String, String> ALIAS_MAP = new HashMap<>();

    static {
        register(1, "minecraft:speed");
        register(2, "minecraft:slowness");
        register(3, "minecraft:haste");
        register(4, "minecraft:mining_fatigue");
        register(5, "minecraft:strength");
        register(6, "minecraft:instant_health");
        register(7, "minecraft:instant_damage");
        register(8, "minecraft:jump_boost");
        register(9, "minecraft:nausea");
        register(10, "minecraft:regeneration");
        register(11, "minecraft:resistance");
        register(12, "minecraft:fire_resistance");
        register(13, "minecraft:water_breathing");
        register(14, "minecraft:invisibility");
        register(15, "minecraft:blindness");
        register(16, "minecraft:night_vision");
        register(17, "minecraft:hunger");
        register(18, "minecraft:weakness");
        register(19, "minecraft:poison");
        register(20, "minecraft:wither");
        register(21, "minecraft:health_boost");
        register(22, "minecraft:absorption");
        register(23, "minecraft:saturation");
        register(24, "minecraft:glowing");
        register(25, "minecraft:levitation");
        register(26, "minecraft:luck");
        register(27, "minecraft:unluck");
        register(28, "minecraft:slow_falling");
        register(29, "minecraft:conduit_power");
        register(30, "minecraft:dolphins_grace");
        register(31, "minecraft:bad_omen");
        register(32, "minecraft:hero_of_the_village");

        alias("health", "minecraft:instant_health");
        alias("heal", "minecraft:instant_health");
        alias("instanthealth", "minecraft:instant_health");
        alias("instant_health", "minecraft:instant_health");
        alias("damage", "minecraft:instant_damage");
        alias("harm", "minecraft:instant_damage");
        alias("instantdamage", "minecraft:instant_damage");
        alias("instant_damage", "minecraft:instant_damage");
    }

    private LegacyEffectIdMapper() {
    }

    public static Identifier resolve(String raw, LegacyWarnReporter warnReporter, SourceLoc loc) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String mapped = mapAlias(trimmed, warnReporter, loc);
        if (isNumeric(trimmed)) {
            int id = Integer.parseInt(trimmed);
            mapped = NUMERIC_MAP.get(id);
            if (mapped == null) {
                if (warnReporter != null) {
                    warnReporter.warn("LegacyEffect", "unknown numeric id " + trimmed, loc);
                }
                return null;
            }
            if (warnReporter != null) {
                warnReporter.warnOnce("LegacyEffect", "numeric id " + trimmed + " -> " + mapped, loc);
            }
        }
        mapped = applyNamespaceRemap(mapped, warnReporter, loc);
        Identifier parsed = Identifier.tryParse(mapped);
        if (parsed != null) {
            return parsed;
        }
        if (!mapped.contains(":")) {
            String fallback = "minecraft:" + mapped.toLowerCase(Locale.ROOT);
            fallback = applyNamespaceRemap(fallback, warnReporter, loc);
            parsed = Identifier.tryParse(fallback);
            if (parsed != null) {
                if (warnReporter != null) {
                    warnReporter.warnOnce("LegacyEffect", "assumed namespace for " + mapped, loc);
                }
                return parsed;
            }
        }
        if (warnReporter != null) {
            warnReporter.warn("LegacyEffect", "invalid effect id " + raw, loc);
        }
        return null;
    }

    private static boolean isNumeric(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return !raw.isEmpty();
    }

    private static String applyNamespaceRemap(String id, LegacyWarnReporter warnReporter, SourceLoc loc) {
        int idx = id.indexOf(':');
        if (idx <= 0) {
            return id;
        }
        String ns = id.substring(0, idx);
        if (!"potioncore".equalsIgnoreCase(ns)) {
            return id;
        }
        var config = ie.orangep.reLootplusplus.runtime.RuntimeState.config();
        if (config == null || config.potioncoreNamespace == null || config.potioncoreNamespace.isBlank()) {
            return id;
        }
        String target = config.potioncoreNamespace.trim();
        if (target.equalsIgnoreCase("potioncore")) {
            return id;
        }
        String mapped = target.toLowerCase(Locale.ROOT) + ":" + id.substring(idx + 1);
        if (warnReporter != null) {
            warnReporter.warnOnce("LegacyNamespace", "mapped potioncore -> " + target + " for '" + id + "' -> '" + mapped + "'", loc);
        }
        return mapped;
    }

    private static void register(int id, String modern) {
        NUMERIC_MAP.put(id, modern);
    }

    private static void alias(String raw, String modern) {
        ALIAS_MAP.put(raw, modern);
    }

    private static String mapAlias(String raw, LegacyWarnReporter warnReporter, SourceLoc loc) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return raw;
        }
        String ns = null;
        String path = trimmed;
        int idx = trimmed.indexOf(':');
        if (idx > 0) {
            ns = trimmed.substring(0, idx).toLowerCase(Locale.ROOT);
            path = trimmed.substring(idx + 1);
        }
        String key = path.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        String alias = ALIAS_MAP.get(key);
        if (alias == null) {
            return raw;
        }
        if (ns == null || "minecraft".equalsIgnoreCase(ns)) {
            if (warnReporter != null && !alias.equals(raw)) {
                warnReporter.warnOnce("LegacyEffect", "alias " + raw + " -> " + alias, loc);
            }
            return alias;
        }
        return raw;
    }
}
