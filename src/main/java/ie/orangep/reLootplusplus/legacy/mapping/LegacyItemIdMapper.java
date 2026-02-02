package ie.orangep.reLootplusplus.legacy.mapping;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;

import java.util.Locale;

public final class LegacyItemIdMapper {
    private static final String[] DYE_MAP = new String[] {
        "black_dye",
        "red_dye",
        "green_dye",
        "brown_dye",
        "blue_dye",
        "purple_dye",
        "cyan_dye",
        "light_gray_dye",
        "gray_dye",
        "pink_dye",
        "lime_dye",
        "yellow_dye",
        "light_blue_dye",
        "magenta_dye",
        "orange_dye",
        "white_dye"
    };

    private LegacyItemIdMapper() {
    }

    public static String mapLegacyRecipeItem(String raw, LegacyWarnReporter warnReporter) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String lowered = raw.toLowerCase(Locale.ROOT).trim();
        String metaPart = null;
        if (lowered.startsWith("minecraft:dye:")) {
            metaPart = lowered.substring("minecraft:dye:".length());
        } else if (lowered.startsWith("dye:")) {
            metaPart = lowered.substring("dye:".length());
        }
        if (metaPart == null) {
            return raw;
        }
        int meta;
        try {
            meta = Integer.parseInt(metaPart);
        } catch (NumberFormatException e) {
            warn(warnReporter, "LegacyItemId", "invalid dye meta " + raw);
            return raw;
        }
        if (meta < 0 || meta >= DYE_MAP.length) {
            warn(warnReporter, "LegacyItemId", "invalid dye meta " + raw);
            return raw;
        }
        String mapped = "minecraft:" + DYE_MAP[meta];
        warn(warnReporter, "LegacyItemId", "mapped recipe item " + raw + " -> " + mapped);
        return mapped;
    }

    private static void warn(LegacyWarnReporter warnReporter, String type, String detail) {
        if (warnReporter != null) {
            warnReporter.warnOnce(type, detail, null);
        }
    }
}
