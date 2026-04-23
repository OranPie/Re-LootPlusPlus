package ie.orangep.reLootplusplus.lucky.worldgen;

import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.lucky.loader.LuckyAddonData;
import ie.orangep.reLootplusplus.lucky.loader.LuckyNaturalGenEntry;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.BiomePlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightmapPlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;
import net.minecraft.world.gen.placementmodifier.SquarePlacementModifier;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Registers Fabric world-gen features for each addon's {@code natural_gen.txt} entries.
 *
 * <p>Called from {@code Bootstrap} after Phase 4 (block registration). Only simple
 * {@code type=block} entries are registered; group and structure entries are logged and skipped.
 *
 * <p><b>Dimension mapping</b>
 * <ul>
 *   <li>{@code >surface} / {@code >underground} → overworld biomes</li>
 *   <li>{@code >nether} → nether biomes</li>
 *   <li>{@code >end} → end biomes</li>
 * </ul>
 */
public final class LuckyNaturalGenRegistrar {

    private LuckyNaturalGenRegistrar() {}

    /** Registers world-gen features for all addon natural-gen entries. Must be called in onInitialize(). */
    public static void register(List<LuckyAddonData> addonDataList) {
        int total = 0;
        for (LuckyAddonData data : addonDataList) {
            List<LuckyNaturalGenEntry> entries = data.naturalGenEntries();
            if (entries == null || entries.isEmpty()) continue;

            int entryIdx = 0;
            for (LuckyNaturalGenEntry entry : entries) {
                if (!entry.isSimpleBlock()) {
                    Log.debug("NaturalGen",
                        "Skipping non-block natural_gen entry in pack '{}' (groups/structures unsupported): {}",
                        data.packId(), preview(entry.cleanDropLine()));
                    continue;
                }

                String rawBlockId = entry.extractBlockId();
                if (rawBlockId == null || rawBlockId.isBlank()) {
                    Log.warn("NaturalGen",
                        "Cannot extract block ID from natural_gen entry in pack '{}': {}",
                        data.packId(), preview(entry.cleanDropLine()));
                    continue;
                }

                Identifier blockIdent = Identifier.tryParse(rawBlockId.replace('.', '_').toLowerCase(Locale.ROOT));
                if (blockIdent == null) {
                    Log.warn("NaturalGen",
                        "Invalid block identifier '{}' in natural_gen for pack '{}'",
                        rawBlockId, data.packId());
                    continue;
                }

                Block block = Registry.BLOCK.get(blockIdent);
                if (block == Blocks.AIR && !blockIdent.getPath().equals("air")) {
                    Log.warn("NaturalGen",
                        "Block '{}' not found in registry for natural_gen in pack '{}'",
                        blockIdent, data.packId());
                    continue;
                }

                int luck = entry.extractLuck();
                int rarity = Math.max(1, entry.rarity());
                String safePackId = safeId(data.packId());
                String uniqueId = safePackId + "_gen_" + entryIdx;
                String dim = entry.dimension();

                // Register Feature
                LuckyNaturalGenFeature feature = new LuckyNaturalGenFeature(block, luck);
                Identifier featId = new Identifier("re-lootplusplus", uniqueId);
                Registry.register(Registry.FEATURE, featId, feature);

                // Register ConfiguredFeature using constructor (configure() removed in 1.18.2)
                RegistryKey<ConfiguredFeature<?, ?>> cfKey = RegistryKey.of(
                    Registry.CONFIGURED_FEATURE_KEY,
                    new Identifier("re-lootplusplus", uniqueId + "_cfg"));
                @SuppressWarnings("unchecked")
                ConfiguredFeature<DefaultFeatureConfig, LuckyNaturalGenFeature> configured =
                    new ConfiguredFeature<>(feature, DefaultFeatureConfig.INSTANCE);
                Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, cfKey, configured);

                // Register PlacedFeature using constructor (withPlacement() removed in 1.18.2)
                RegistryKey<PlacedFeature> pfKey = RegistryKey.of(
                    Registry.PLACED_FEATURE_KEY,
                    new Identifier("re-lootplusplus", uniqueId + "_placed"));
                PlacedFeature placed = new PlacedFeature(
                    RegistryEntry.of(configured),
                    List.of(
                        RarityFilterPlacementModifier.of(rarity),
                        SquarePlacementModifier.of(),
                        heightmapFor(dim),
                        BiomePlacementModifier.of()
                    )
                );
                Registry.register(BuiltinRegistries.PLACED_FEATURE, pfKey, placed);

                // Add to biomes
                Predicate<net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext> selector = biomeSelectorFor(dim);
                BiomeModifications.addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, pfKey);

                Log.debug("NaturalGen",
                    "Registered '{}' → block={} rarity=1/{} dim={}",
                    data.packId(), blockIdent, rarity, dim);
                entryIdx++;
                total++;
            }
        }
        if (total > 0) {
            Log.info("NaturalGen", "Registered {} lucky natural-gen feature(s) total", total);
        }
    }

    private static HeightmapPlacementModifier heightmapFor(String dim) {
        if ("underground".equals(dim)) {
            return HeightmapPlacementModifier.of(Heightmap.Type.OCEAN_FLOOR_WG);
        }
        return HeightmapPlacementModifier.of(Heightmap.Type.WORLD_SURFACE_WG);
    }

    private static Predicate<net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext> biomeSelectorFor(String dim) {
        return switch (dim) {
            case "nether" -> BiomeSelectors.foundInTheNether();
            case "end" -> BiomeSelectors.foundInTheEnd();
            default -> BiomeSelectors.foundInOverworld();
        };
    }

    private static String safeId(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static String preview(String s) {
        if (s == null) return "(null)";
        return s.length() > 70 ? s.substring(0, 70) + "…" : s;
    }
}
