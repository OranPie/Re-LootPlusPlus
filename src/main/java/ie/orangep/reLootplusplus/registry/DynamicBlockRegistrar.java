package ie.orangep.reLootplusplus.registry;

import ie.orangep.reLootplusplus.config.model.block.BlockAdditions;
import ie.orangep.reLootplusplus.config.model.block.GenericBlockDef;
import ie.orangep.reLootplusplus.config.model.block.PlantBlockDef;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.SourceLoc;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.Material;
import net.minecraft.block.PlantBlock;
import ie.orangep.reLootplusplus.content.item.LegacyNamedBlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.HashMap;
import java.util.Map;

public final class DynamicBlockRegistrar {
    private final LegacyWarnReporter warnReporter;
    private final Map<String, Material> materialMap = new HashMap<>();
    private final String duplicateStrategy;

    public DynamicBlockRegistrar(LegacyWarnReporter warnReporter, String duplicateStrategy) {
        this.warnReporter = warnReporter;
        this.duplicateStrategy = duplicateStrategy == null ? "suffix" : duplicateStrategy;
        materialMap.put("stone", Material.STONE);
        materialMap.put("rock", Material.STONE);
        materialMap.put("metal", Material.METAL);
        materialMap.put("iron", Material.METAL);
        materialMap.put("wood", Material.WOOD);
        materialMap.put("plant", Material.PLANT);
        materialMap.put("plants", Material.PLANT);
        materialMap.put("gourd", Material.GOURD);
        materialMap.put("air", Material.AIR);
    }

    public void registerAll(BlockAdditions additions) {
        for (GenericBlockDef def : additions.genericBlocks()) {
            registerGeneric(def);
        }
        for (PlantBlockDef def : additions.plantBlocks()) {
            registerPlant(def);
        }
        if (!additions.cropBlocks().isEmpty() || !additions.cakeBlocks().isEmpty() || !additions.rawBlocks().isEmpty()) {
            warnReporter.warnOnce("LegacyBlockAdditions", "some block_additions types are parsed but not registered", null);
        }
    }

    private void registerGeneric(GenericBlockDef def) {
        Identifier id = normalizeId(def.blockId(), def.sourceLoc());
        if (id == null) {
            return;
        }
        id = resolveDuplicateBlockId(id, def.sourceLoc());
        if (id == null) {
            return;
        }
        Material material = resolveMaterial(def.materialName(), def.sourceLoc());
        AbstractBlock.Settings settings = AbstractBlock.Settings.of(material)
            .strength(def.hardness(), def.resistance());
        int light = clampLight(def.light());
        if (light > 0) {
            settings.luminance(state -> light);
        }
        if (def.slipperiness() > 0.0f) {
            settings.slipperiness(def.slipperiness());
        }
        if (def.opacity() >= 0 && def.opacity() < 15) {
            settings.nonOpaque();
        }
        if (def.harvestLevel() >= 0 && !"none".equalsIgnoreCase(def.harvestTool())) {
            warnReporter.warnOnce("LegacyHarvest", "harvest tool/level not fully supported", def.sourceLoc());
            settings.requiresTool();
        }

        Block block = def.falls() ? new FallingBlock(settings) : new Block(settings);
        Registry.register(Registry.BLOCK, id, block);
        Registry.register(Registry.ITEM, id, new LegacyNamedBlockItem(block, new Item.Settings(), def.displayName()));
        applyFlammability(block, def.fireSpread(), def.flammability());
        if (def.beaconBase()) {
            warnReporter.warnOnce("LegacyBeaconBase", "beacon base not supported for " + id, def.sourceLoc());
        }
    }

    private void registerPlant(PlantBlockDef def) {
        Identifier id = normalizeId(def.blockId(), def.sourceLoc());
        if (id == null) {
            return;
        }
        id = resolveDuplicateBlockId(id, def.sourceLoc());
        if (id == null) {
            return;
        }
        Material material = resolveMaterial(def.materialName(), def.sourceLoc());
        AbstractBlock.Settings settings = AbstractBlock.Settings.of(material)
            .strength(def.hardness(), def.resistance())
            .noCollision()
            .nonOpaque();
        int light = clampLight(def.light());
        if (light > 0) {
            settings.luminance(state -> light);
        }
        if (def.harvestLevel() >= 0 && !"none".equalsIgnoreCase(def.harvestTool())) {
            warnReporter.warnOnce("LegacyHarvest", "harvest tool/level not fully supported", def.sourceLoc());
            settings.requiresTool();
        }
        Block block = new PlantBlock(settings);
        Registry.register(Registry.BLOCK, id, block);
        Registry.register(Registry.ITEM, id, new LegacyNamedBlockItem(block, new Item.Settings(), def.displayName()));
        applyFlammability(block, def.fireSpread(), def.flammability());
    }

    private Identifier normalizeId(String raw, SourceLoc loc) {
        String value = raw;
        if (!raw.contains(":")) {
            warnReporter.warnOnce("LegacyBlockId", "missing namespace for " + raw, loc);
            value = "lootplusplus:" + raw;
        }
        Identifier id = Identifier.tryParse(value);
        if (id == null) {
            warnReporter.warn("LegacyBlockId", "bad block id " + value, loc);
        }
        return id;
    }

    private Identifier resolveDuplicateBlockId(Identifier id, SourceLoc loc) {
        if (!Registry.BLOCK.containsId(id)) {
            return id;
        }
        if ("ignore".equalsIgnoreCase(duplicateStrategy)) {
            warnReporter.warnOnce("DuplicateBlock", "skipping duplicate " + id, loc);
            return null;
        }
        String basePath = id.getPath();
        String namespace = id.getNamespace();
        int counter = 2;
        Identifier candidate = new Identifier(namespace, basePath + "_" + counter);
        while (Registry.BLOCK.containsId(candidate)) {
            counter++;
            candidate = new Identifier(namespace, basePath + "_" + counter);
        }
        warnReporter.warnOnce("DuplicateBlock", "renamed " + id + " -> " + candidate, loc);
        return candidate;
    }

    private Material resolveMaterial(String name, SourceLoc loc) {
        if (name == null) {
            return Material.STONE;
        }
        Material material = materialMap.get(name.toLowerCase(java.util.Locale.ROOT));
        if (material == null) {
            warnReporter.warnOnce("LegacyMaterial", "unknown material " + name, loc);
            return Material.STONE;
        }
        return material;
    }

    private void applyFlammability(Block block, int fireSpread, int flammability) {
        if (fireSpread > 0 || flammability > 0) {
            FlammableBlockRegistry.getDefaultInstance().add(block, fireSpread, flammability);
        }
    }

    private int clampLight(float light) {
        int value = Math.round(light);
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 15);
    }
}
