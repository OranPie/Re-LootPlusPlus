package ie.orangep.reLootplusplus.lucky.loader;

import ie.orangep.reLootplusplus.lucky.drop.LuckyDropLine;
import ie.orangep.reLootplusplus.pack.AddonPack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * All data parsed from a single Lucky Block addon pack.
 */
public record LuckyAddonData(
    String packId,
    AddonPack pack,
    @Nullable LuckyPluginInit pluginInit,
    /** Raw drop text lines (used for UI/display only). */
    List<String> dropLines,
    List<String> bowDropLines,
    List<String> swordDropLines,
    List<String> potionDropLines,
    /** Pre-parsed drop lines — ready for use without re-parsing at block-break time. */
    List<LuckyDropLine> parsedDrops,
    List<LuckyDropLine> parsedBowDrops,
    List<LuckyDropLine> parsedSwordDrops,
    List<LuckyDropLine> parsedPotionDrops,
    @Nullable LuckyAddonProperties properties,
    List<LuckyStructureEntry> structureEntries,
    List<LuckyNaturalGenEntry> naturalGenEntries
) {
    /** Convenience: returns the effective {@link LuckyAddonProperties}, falling back to defaults. */
    public LuckyAddonProperties effectiveProperties() {
        return properties != null ? properties : LuckyAddonProperties.DEFAULT;
    }

    /** Creates an instance with empty parsed-drop lists (for addons with no drops file). */
    public static LuckyAddonData withoutDrops(String packId, AddonPack pack,
                                               @Nullable LuckyPluginInit pluginInit,
                                               @Nullable LuckyAddonProperties properties,
                                               List<LuckyStructureEntry> structureEntries,
                                               List<LuckyNaturalGenEntry> naturalGenEntries) {
        return new LuckyAddonData(
            packId, pack, pluginInit,
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(),
            properties, structureEntries, naturalGenEntries
        );
    }
}
