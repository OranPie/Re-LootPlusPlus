package ie.orangep.reLootplusplus.lucky.loader;

import java.util.List;
import java.util.Locale;

/**
 * Parsed content of a Lucky Block addon's {@code properties.txt}.
 *
 * <p>Format: one {@code key=value} pair per line; lines starting with {@code /} or {@code #}
 * are comments and are ignored.
 */
public record LuckyAddonProperties(
    int spawnRate,
    int structureChance,
    boolean doDropsOnCreativeMode
) {
    /** Default properties used when {@code properties.txt} is absent. */
    public static final LuckyAddonProperties DEFAULT = new LuckyAddonProperties(200, 2, false);

    public static LuckyAddonProperties parse(List<String> lines) {
        int spawnRate = 200;
        int structureChance = 2;
        boolean doDropsOnCreativeMode = false;

        for (String line : lines) {
            if (line == null) continue;
            line = line.strip();
            if (line.isEmpty() || line.startsWith("/") || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(eq + 1).strip();
            switch (key) {
                case "spawnrate" -> {
                    try { spawnRate = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                }
                case "structurechance" -> {
                    try { structureChance = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                }
                case "dodropsoncreativemode" ->
                    doDropsOnCreativeMode = "true".equalsIgnoreCase(value);
            }
        }
        return new LuckyAddonProperties(spawnRate, structureChance, doDropsOnCreativeMode);
    }
}
