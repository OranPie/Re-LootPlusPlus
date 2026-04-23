package ie.orangep.reLootplusplus.legacy.mapping;

import ie.orangep.reLootplusplus.config.CustomRemapStore;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LegacyParticleIdMapper {
    private static final Map<String, String> LEGACY = new HashMap<>();

    static {
        register("largesmoke", "minecraft:large_smoke");
        register("hugeexplosion", "minecraft:explosion");
        register("explode", "minecraft:explosion");
        register("smoke", "minecraft:smoke");
        register("flame", "minecraft:flame");
        register("lava", "minecraft:lava");
        register("reddust", "minecraft:dust");
        register("crit", "minecraft:crit");
        register("magiccrit", "minecraft:enchanted_hit");
        register("happyvillager", "minecraft:happy_villager");
    }

    private LegacyParticleIdMapper() {
    }

    public static Identifier resolve(String raw, LegacyWarnReporter reporter, SourceLoc loc) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String normalized = normalizeId(trimmed, reporter, loc);
        Identifier id = Identifier.tryParse(normalized);
        if (id == null && reporter != null) {
            reporter.warn("LegacyParticle", "invalid particle id " + raw, loc);
        }
        return id;
    }

    public static String normalizeId(String raw, LegacyWarnReporter reporter) {
        return normalizeId(raw, reporter, null);
    }

    private static String normalizeId(String raw, LegacyWarnReporter reporter, SourceLoc loc) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "minecraft:smoke";
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        String direct = LEGACY.get(lowered);
        if (direct != null) {
            if (reporter != null) {
                reporter.warnOnce("LegacyParticle", "mapped " + trimmed + " -> " + direct, loc);
            }
            Log.trace("Legacy", "ParticleId: {} → {}", trimmed, direct);
            return direct;
        }
        String namespace = "minecraft";
        String path = trimmed;
        int idx = trimmed.indexOf(':');
        if (idx > 0) {
            namespace = trimmed.substring(0, idx);
            path = trimmed.substring(idx + 1);
        }
        if (hasUppercase(path)) {
            String snake = toSnakeCase(path);
            if (!snake.equals(path) && reporter != null) {
                reporter.warnOnce("LegacyParticle", "normalized " + path + " -> " + snake, loc);
            }
            path = snake;
        }
        String mapped = LEGACY.getOrDefault(path.toLowerCase(Locale.ROOT), path.toLowerCase(Locale.ROOT));
        String full = namespace.toLowerCase(Locale.ROOT) + ":" + mapped;
        full = CustomRemapStore.map(full, reporter, loc, "particle");
        if (Identifier.tryParse(full) == null) {
            return "minecraft:smoke";
        }
        if (!full.equals(trimmed) && reporter != null) {
            reporter.warnOnce("LegacyParticle", "normalized " + trimmed + " -> " + full, loc);
        }
        return full;
    }

    private static void register(String legacy, String modern) {
        LEGACY.put(legacy.toLowerCase(Locale.ROOT), modern);
    }

    private static boolean hasUppercase(String input) {
        for (int i = 0; i < input.length(); i++) {
            if (Character.isUpperCase(input.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String toSnakeCase(String input) {
        StringBuilder out = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && (Character.isLowerCase(prev) || Character.isDigit(prev))) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else if (c == ' ' || c == '-' || c == '.') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
            prev = c;
        }
        return out.toString();
    }
}
