package ie.orangep.reLootplusplus.lucky.loader;

import org.jetbrains.annotations.Nullable;

/**
 * Parsed representation of a Lucky Block addon's {@code plugin_init.txt}.
 *
 * <p>Format: one {@code key=value} per line. Lines starting with {@code /} are comments.
 */
public record LuckyPluginInit(
    String blockId,
    @Nullable String swordId,
    @Nullable String bowId,
    @Nullable String potionId
) {

    /**
     * Parses a {@code plugin_init.txt} text. Returns {@code null} if no {@code block_id}/{@code id} is found.
     */
    @Nullable
    public static LuckyPluginInit parse(String text) {
        if (text == null || text.isBlank()) return null;
        String blockId = null, swordId = null, bowId = null, potionId = null;
        for (String rawLine : text.split("\r?\n", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("/")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).strip().toLowerCase(java.util.Locale.ROOT);
            String val = line.substring(eq + 1).strip();
            switch (key) {
                case "block_id", "id" -> blockId = val;
                case "sword_id"       -> swordId = val;
                case "bow_id"         -> bowId = val;
                case "potion_id"      -> potionId = val;
                default               -> { /* ignore unknown keys */ }
            }
        }
        if (blockId == null || blockId.isBlank()) return null;
        return new LuckyPluginInit(blockId, swordId, bowId, potionId);
    }
}
