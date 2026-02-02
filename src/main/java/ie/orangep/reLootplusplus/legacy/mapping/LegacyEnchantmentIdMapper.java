package ie.orangep.reLootplusplus.legacy.mapping;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LegacyEnchantmentIdMapper {
    private static final Map<Integer, String> NUMERIC_MAP = new HashMap<>();

    static {
        register(0, "minecraft:protection");
        register(1, "minecraft:fire_protection");
        register(2, "minecraft:feather_falling");
        register(3, "minecraft:blast_protection");
        register(4, "minecraft:projectile_protection");
        register(5, "minecraft:respiration");
        register(6, "minecraft:aqua_affinity");
        register(7, "minecraft:thorns");
        register(8, "minecraft:depth_strider");
        register(9, "minecraft:frost_walker");
        register(10, "minecraft:binding_curse");
        register(16, "minecraft:sharpness");
        register(17, "minecraft:smite");
        register(18, "minecraft:bane_of_arthropods");
        register(19, "minecraft:knockback");
        register(20, "minecraft:fire_aspect");
        register(21, "minecraft:looting");
        register(32, "minecraft:efficiency");
        register(33, "minecraft:silk_touch");
        register(34, "minecraft:unbreaking");
        register(35, "minecraft:fortune");
        register(48, "minecraft:power");
        register(49, "minecraft:punch");
        register(50, "minecraft:flame");
        register(51, "minecraft:infinity");
        register(61, "minecraft:luck_of_the_sea");
        register(62, "minecraft:lure");
        register(70, "minecraft:mending");
        register(71, "minecraft:vanishing_curse");
    }

    private LegacyEnchantmentIdMapper() {
    }

    public static String resolve(String raw, LegacyWarnReporter warnReporter, SourceLoc loc) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (isNumeric(trimmed)) {
            int id = Integer.parseInt(trimmed);
            String mapped = NUMERIC_MAP.get(id);
            if (mapped == null) {
                if (warnReporter != null) {
                    warnReporter.warn("LegacyEnchant", "unknown numeric id " + trimmed, loc);
                }
                return null;
            }
            if (warnReporter != null) {
                warnReporter.warnOnce("LegacyEnchant", "numeric id " + trimmed + " -> " + mapped, loc);
            }
            return mapped;
        }
        if (!trimmed.contains(":")) {
            String assumed = "minecraft:" + trimmed.toLowerCase(Locale.ROOT);
            if (warnReporter != null) {
                warnReporter.warnOnce("LegacyEnchant", "assumed namespace for " + trimmed, loc);
            }
            return assumed;
        }
        return trimmed.toLowerCase(Locale.ROOT);
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

    private static void register(int id, String modern) {
        NUMERIC_MAP.put(id, modern);
    }
}
