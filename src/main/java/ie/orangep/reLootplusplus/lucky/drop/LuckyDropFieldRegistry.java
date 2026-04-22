package ie.orangep.reLootplusplus.lucky.drop;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registry of known attribute field names for each Lucky drop type.
 *
 * <p>Used by {@link LuckyDropParser} to emit {@code LuckyIgnoredField} WARNs for any attrs
 * that appear in a drop line but are not read by the corresponding action handler.
 *
 * <p>All field names are stored in <b>lower-case</b> so comparisons are case-insensitive.
 */
public final class LuckyDropFieldRegistry {

    private LuckyDropFieldRegistry() {}

    /** Fields present in every drop type (always allowed). */
    private static final Set<String> UNIVERSAL = Set.of(
        "type", "id"
    );

    /**
     * Per-type known fields (lower-cased). A field not present in this set
     * (and not in {@link #UNIVERSAL}) is considered "ignored" for that type.
     */
    private static final Map<String, Set<String>> KNOWN = Map.ofEntries(
        Map.entry("item", Set.of(
            "type", "id",
            "amount", "damage", "meta",
            "name",                      // display name shorthand
            "nbttag",                    // nbt payload (DictAttr or StringAttr)
            "ench",                      // inline enchantments list
            "nbt"                        // alternate nbt key used by some addons
        )),
        Map.entry("entity", Set.of(
            "type", "id",
            "amount",
            "nbttag",                    // entity NBT payload
            "posx", "posy", "posz",
            "posoffset",
            "posoffsety",
            "onfire", "fire",
            "delay",
            "reinitialize"
        )),
        Map.entry("block", Set.of(
            "type", "id",
            "pos",
            "tileentity"                 // both cases resolved by getString
        )),
        Map.entry("chest", Set.of(
            "type", "id",
            "pos", "posx", "posy", "posz",
            "posoffset", "posoffsety",
            "nbttag",                    // Items=[...] and other block entity NBT
            "items"                      // shorthand item list (alias for NBTTag.Items)
        )),
        Map.entry("throw", Set.of(
            "type", "id",
            "amount",
            "pos", "posx", "posy", "posz",
            "posoffset", "posoffsety",
            "nbttag",                    // Motion=[dx,dy,dz] and item NBT
            "motion"                     // top-level velocity shorthand
        )),
        Map.entry("throwable", Set.of(
            "type", "id",
            "amount",
            "pos", "posx", "posy", "posz",
            "posoffset", "posoffsety",
            "nbttag",
            "motion"
        )),
        Map.entry("command", Set.of(
            "type", "id",
            "command"
        )),
        Map.entry("structure", Set.of(
            "type", "id"
        )),
        Map.entry("fill", Set.of(
            "type", "id",
            "pos2",
            "posoffset",
            "size"
        )),
        Map.entry("message", Set.of(
            "type", "id",
            "message"
        )),
        Map.entry("effect", Set.of(
            "type", "id",
            "duration",
            "amplifier"
        )),
        Map.entry("explosion", Set.of(
            "type", "id",
            "radius",
            "damage",
            "fire"
        )),
        Map.entry("sound", Set.of(
            "type", "id",
            "volume",
            "pitch"
        )),
        Map.entry("particle", Set.of(
            "type", "id",
            "particleamount",
            "amount"
        )),
        Map.entry("time", Set.of(
            "type", "id"
        )),
        Map.entry("difficulty", Set.of(
            "type", "id"
        )),
        Map.entry("nothing", Set.of(
            "type", "id"
        ))
    );

    /**
     * Returns the set of ignored field names for the given drop (all lower-cased).
     * Returns an empty set if the type is unknown or all fields are recognised.
     *
     * @param drop the parsed drop line to inspect
     * @return a (possibly empty) set of lower-cased attr keys that are not used by the action handler
     */
    public static Set<String> ignoredFields(LuckyDropLine drop) {
        if (drop == null || drop.isGroup() || drop.attrs().isEmpty()) return Set.of();

        String type = drop.type(); // already lower-cased
        Set<String> known = KNOWN.get(type);
        if (known == null) return Set.of(); // unknown type — will warn elsewhere

        Set<String> ignored = new java.util.LinkedHashSet<>();
        for (String rawKey : drop.attrs().keySet()) {
            String lower = rawKey.toLowerCase(Locale.ROOT);
            // Universal fields and type-specific known fields are all OK
            if (!UNIVERSAL.contains(lower) && !known.contains(lower)) {
                ignored.add(rawKey); // keep original casing for display
            }
        }
        return ignored;
    }
}
