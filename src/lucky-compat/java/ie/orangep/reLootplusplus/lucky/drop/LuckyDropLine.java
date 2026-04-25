package ie.orangep.reLootplusplus.lucky.drop;

import ie.orangep.reLootplusplus.lucky.attr.LuckyAttr;

import java.util.List;
import java.util.Map;

/**
 * A single parsed drop line from a Lucky Block {@code drops.txt}.
 *
 * <p>Holds the raw parsed attributes (before template evaluation), the luck weight,
 * and the chance (0.0–1.0 probability gate).
 */
public record LuckyDropLine(
    Map<String, LuckyAttr> attrs,
    int luckWeight,
    float chance,
    List<LuckyDropLine> groupEntries,
    int groupCount,
    boolean isGroup
) {
    /** Creates a regular (non-group) drop line. */
    public static LuckyDropLine of(Map<String, LuckyAttr> attrs, int luckWeight, float chance) {
        return new LuckyDropLine(attrs, luckWeight, chance, null, 1, false);
    }

    /** Creates a group drop line. {@code groupCount} is the number of entries to select (1 = pick one, -1 = all). */
    public static LuckyDropLine group(List<LuckyDropLine> entries, int luckWeight, float chance, int groupCount) {
        return new LuckyDropLine(java.util.Collections.emptyMap(), luckWeight, chance, entries, groupCount, true);
    }

    /** Convenience: returns the {@code type} attribute value (default: "item"). */
    public String type() {
        LuckyAttr v = attrs.get("type");
        if (v == null) {
            for (var e : attrs.entrySet()) {
                if (e.getKey().equalsIgnoreCase("type")) { v = e.getValue(); break; }
            }
        }
        if (v instanceof LuckyAttr.StringAttr s && !s.value().isEmpty()) {
            return s.value().toLowerCase(java.util.Locale.ROOT);
        }
        return "item";
    }

    /** Convenience: returns the {@code ID} / {@code id} attribute value. */
    public String rawId() {
        for (String key : new String[]{"ID", "id", "Id"}) {
            LuckyAttr v = attrs.get(key);
            if (v instanceof LuckyAttr.StringAttr s && !s.value().isEmpty()) return s.value();
        }
        return null;
    }

    /** Convenience: returns the raw string for a key (case-insensitive), or null. */
    public String getString(String key) {
        LuckyAttr v = attrs.get(key);
        if (v == null) {
            for (var e : attrs.entrySet()) {
                if (e.getKey().equalsIgnoreCase(key)) { v = e.getValue(); break; }
            }
        }
        if (v instanceof LuckyAttr.StringAttr s) return s.value();
        return null;
    }

    /**
     * Reconstructs a human-readable representation of this drop line from its parsed attributes.
     * Used for display in the Raw Line screen; this is not round-trip exact.
     */
    public String toDisplayString() {
        if (isGroup) {
            StringBuilder sb = new StringBuilder("group(");
            if (groupEntries != null) {
                for (int i = 0; i < groupEntries.size(); i++) {
                    if (i > 0) sb.append(";");
                    sb.append(groupEntries.get(i).toDisplayString());
                }
            }
            sb.append(")");
            if (luckWeight != 0) sb.append(",@luck=").append(luckWeight);
            if (chance < 1f) sb.append(",@chance=").append(String.format("%.2f", chance));
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (var e : attrs.entrySet()) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(e.getKey()).append("=").append(attrStr(e.getValue()));
        }
        if (luckWeight != 0) sb.append(",@luck=").append(luckWeight);
        if (chance < 1f) sb.append(",@chance=").append(String.format("%.2f", chance));
        return sb.toString();
    }

    private static String attrStr(LuckyAttr attr) {
        if (attr instanceof LuckyAttr.StringAttr s) return s.value();
        if (attr instanceof LuckyAttr.DictAttr d) {
            StringBuilder sb = new StringBuilder("(");
            boolean first = true;
            for (var e : d.entries().entrySet()) {
                if (!first) sb.append(",");
                sb.append(e.getKey()).append("=").append(attrStr(e.getValue()));
                first = false;
            }
            return sb.append(")").toString();
        }
        if (attr instanceof LuckyAttr.ListAttr l) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (var item : l.items()) {
                if (!first) sb.append(";");
                sb.append(attrStr(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        return "?";
    }
}
