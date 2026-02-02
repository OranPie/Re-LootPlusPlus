package ie.orangep.reLootplusplus.bootstrap;

import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.diagnostic.Log;
import ie.orangep.reLootplusplus.pack.AddonPack;
import ie.orangep.reLootplusplus.pack.PackDiscovery;
import ie.orangep.reLootplusplus.pack.PackIndex;
import ie.orangep.reLootplusplus.config.loader.CommandEffectLoader;
import ie.orangep.reLootplusplus.config.loader.EffectLoader;
import ie.orangep.reLootplusplus.config.loader.BlockDropsLoader;
import ie.orangep.reLootplusplus.config.loader.BlockAdditionsLoader;
import ie.orangep.reLootplusplus.config.loader.ChestContentLoader;
import ie.orangep.reLootplusplus.config.loader.FishingLootLoader;
import ie.orangep.reLootplusplus.config.loader.FurnaceRecipesLoader;
import ie.orangep.reLootplusplus.config.loader.ItemAdditionsLoader;
import ie.orangep.reLootplusplus.config.loader.CreativeMenuLoader;
import ie.orangep.reLootplusplus.config.loader.RecordsLoader;
import ie.orangep.reLootplusplus.config.loader.RecipesLoader;
import ie.orangep.reLootplusplus.config.loader.StackSizeLoader;
import ie.orangep.reLootplusplus.config.loader.WorldGenLoader;
import ie.orangep.reLootplusplus.runtime.RuntimeIndex;
import ie.orangep.reLootplusplus.runtime.ThrownRegistry;
import ie.orangep.reLootplusplus.runtime.BlockDropRegistry;
import ie.orangep.reLootplusplus.runtime.EntityDropRegistry;
import ie.orangep.reLootplusplus.runtime.ChestLootRegistry;
import ie.orangep.reLootplusplus.runtime.RuntimeState;
import ie.orangep.reLootplusplus.runtime.StackSizeRegistry;
import ie.orangep.reLootplusplus.runtime.trigger.TriggerType;
import ie.orangep.reLootplusplus.hooks.HookInstaller;
import ie.orangep.reLootplusplus.registry.EntityRegistrar;
import ie.orangep.reLootplusplus.registry.DynamicBlockRegistrar;
import ie.orangep.reLootplusplus.registry.DynamicItemRegistrar;
import ie.orangep.reLootplusplus.recipe.ModRecipes;
import ie.orangep.reLootplusplus.config.loader.ThrownLoader;
import ie.orangep.reLootplusplus.config.loader.EntityDropsLoader;
import ie.orangep.reLootplusplus.hooks.ChestLootHook;
import ie.orangep.reLootplusplus.legacy.mapping.LegacyChestTypeMapper;
import ie.orangep.reLootplusplus.diagnostic.DiagnosticExporter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.List;

public final class Bootstrap {
    public void run() {
        Log.LOGGER.info("Re-LootPlusPlus bootstrap start");
        ModRecipes.register();

        ReLootPlusPlusConfig config = ReLootPlusPlusConfig.load();
        LegacyWarnReporter warnReporter = new LegacyWarnReporter();
        warnReporter.setLogToConsole(config.logLegacyWarnings);
        PackDiscovery discovery = new PackDiscovery(config, warnReporter);
        List<AddonPack> packs = discovery.discover();

        BootstrapReport report = new BootstrapReport();
        report.setPackCount(packs.size());

        PackIndex index = new PackIndex();
        index.indexAll(packs);
        report.setFileCount(index.totalFileCount());
        report.setLineCount(index.totalLineCount());

        CommandEffectLoader commandLoader = new CommandEffectLoader(warnReporter);
        var commandRules = commandLoader.loadAll(packs, index);
        report.putCount("commandRules", commandRules.size());
        EffectLoader effectLoader = new EffectLoader(warnReporter);
        var effectRules = effectLoader.loadAll(packs, index);
        report.putCount("effectRules", effectRules.size());
        BlockDropsLoader blockDropsLoader = new BlockDropsLoader(warnReporter);
        var blockDropAdds = blockDropsLoader.loadAdding(packs, index);
        var blockDropRemovals = blockDropsLoader.loadRemoving(packs, index);
        report.putCount("blockDropAdds", blockDropAdds.size());
        report.putCount("blockDropRemovals", blockDropRemovals.size());
        ChestContentLoader chestContentLoader = new ChestContentLoader(warnReporter);
        var chestAmounts = chestContentLoader.loadAmounts(packs, index);
        var chestLoots = chestContentLoader.loadLoot(packs, index);
        report.putCount("chestAmounts", chestAmounts.size());
        report.putCount("chestLoots", chestLoots.size());
        FishingLootLoader fishingLootLoader = new FishingLootLoader(warnReporter);
        var fishingRules = fishingLootLoader.loadLoot(packs, index);
        report.putCount("fishingLoot", fishingRules.size());
        FurnaceRecipesLoader furnaceRecipesLoader = new FurnaceRecipesLoader(warnReporter);
        var smeltingRules = furnaceRecipesLoader.loadSmelting(packs, index);
        var fuelRules = furnaceRecipesLoader.loadFuels(packs, index);
        report.putCount("furnaceSmelting", smeltingRules.size());
        report.putCount("furnaceFuels", fuelRules.size());
        RecordsLoader recordsLoader = new RecordsLoader(warnReporter);
        var recordDefs = recordsLoader.loadAll(packs, index);
        report.putCount("records", recordDefs.size());
        RecipesLoader recipesLoader = new RecipesLoader(warnReporter);
        var recipes = recipesLoader.loadAll(packs, index);
        report.putCount("recipesShaped", recipes.shaped().size());
        report.putCount("recipesShapeless", recipes.shapeless().size());
        RuntimeState.setRecipeDefinitions(recipes);
        CreativeMenuLoader creativeMenuLoader = new CreativeMenuLoader(warnReporter);
        var creativeMenu = creativeMenuLoader.loadAll(packs, index);
        report.putCount("creativeMenuEntries", creativeMenu.size());
        RuntimeState.setCreativeMenuEntries(creativeMenu);
        StackSizeLoader stackSizeLoader = new StackSizeLoader(warnReporter);
        var stackSizeRules = stackSizeLoader.loadAll(packs, index);
        report.putCount("stackSize", stackSizeRules.size());

        ItemAdditionsLoader itemAdditionsLoader = new ItemAdditionsLoader(warnReporter);
        var itemAdditions = itemAdditionsLoader.loadAll(packs, index);
        report.putCount("itemAdditionsGeneric", itemAdditions.genericItems().size());
        report.putCount("itemAdditionsMaterials", itemAdditions.materials().size());
        report.putCount("itemAdditionsSwords", itemAdditions.swords().size());
        report.putCount("itemAdditionsTools", itemAdditions.tools().size());
        report.putCount("itemAdditionsArmor", itemAdditions.armors().size());
        report.putCount("itemAdditionsFoods", itemAdditions.foods().size());
        report.putCount("itemAdditionsBows", itemAdditions.bows().size());
        report.putCount("itemAdditionsGuns", itemAdditions.guns().size());
        report.putCount("itemAdditionsMultitools", itemAdditions.multitools().size());
        BlockAdditionsLoader blockAdditionsLoader = new BlockAdditionsLoader(warnReporter);
        var blockAdditions = blockAdditionsLoader.loadAll(packs, index);
        report.putCount("blockAdditionsGeneric", blockAdditions.genericBlocks().size());
        report.putCount("blockAdditionsPlants", blockAdditions.plantBlocks().size());
        report.putCount("blockAdditionsCrops", blockAdditions.cropBlocks().size());
        report.putCount("blockAdditionsCakes", blockAdditions.cakeBlocks().size());
        report.putCount("blockAdditionsRaw", blockAdditions.rawBlocks().size());
        WorldGenLoader worldGenLoader = new WorldGenLoader(warnReporter);
        var surfaceGen = worldGenLoader.loadSurface(packs, index);
        var undergroundGen = worldGenLoader.loadUnderground(packs, index);
        report.putCount("worldGenSurface", surfaceGen.size());
        report.putCount("worldGenUnderground", undergroundGen.size());

        ThrownLoader thrownLoader = new ThrownLoader(warnReporter, blockDropsLoader);
        var thrownDefs = thrownLoader.loadAll(packs, index);
        report.putCount("thrownDefs", thrownDefs.size());
        RuntimeState.setThrownDefs(thrownDefs);

        EntityDropsLoader entityDropsLoader = new EntityDropsLoader(warnReporter, blockDropsLoader);
        var entityDropAdds = entityDropsLoader.loadAdding(packs, index);
        report.putCount("entityDropAdds", entityDropAdds.size());

        if (config.dryRun) {
            report.setUniqueWarnCount(warnReporter.uniqueWarnCount());
            report.setTotalWarnCount(warnReporter.totalWarnCount());
            Log.LOGGER.info("Re-LootPlusPlus dry run enabled: parse-only, no registration or hooks.");
            DiagnosticExporter.export(config, warnReporter, report, packs);
            return;
        }

        // Force static registration
        EntityRegistrar.THROWN_ENTITY.toString();
        DynamicBlockRegistrar blockRegistrar = new DynamicBlockRegistrar(warnReporter, config.normalizedDuplicateStrategy());
        blockRegistrar.registerAll(blockAdditions);

        DynamicItemRegistrar itemRegistrar = new DynamicItemRegistrar(warnReporter, config.normalizedDuplicateStrategy());
        itemRegistrar.registerItemAdditions(itemAdditions);
        itemRegistrar.registerThrownItems(thrownDefs);

        ThrownRegistry thrownRegistry = new ThrownRegistry();
        for (var def : thrownDefs) {
            thrownRegistry.register(def);
        }

        BlockDropRegistry blockDropRegistry = new BlockDropRegistry();
        for (var rule : blockDropAdds) {
            blockDropRegistry.addRule(rule);
        }
        for (var removal : blockDropRemovals) {
            blockDropRegistry.addRemoval(removal);
        }

        StackSizeRegistry stackSizeRegistry = new StackSizeRegistry();
        for (var rule : stackSizeRules) {
            stackSizeRegistry.addRule(rule);
        }

        RuntimeState.init(config, blockDropRegistry, stackSizeRegistry, warnReporter);

        EntityDropRegistry entityDropRegistry = new EntityDropRegistry();
        for (var rule : entityDropAdds) {
            entityDropRegistry.addRule(rule);
        }

        ChestLootRegistry chestLootRegistry = new ChestLootRegistry();
        for (var amount : chestAmounts) {
            chestLootRegistry.addAmount(amount);
        }
        for (var loot : chestLoots) {
            chestLootRegistry.addLoot(loot);
        }
        RuntimeState.setChestLootRegistry(chestLootRegistry);

        ChestLootHook chestLootHook = new ChestLootHook(chestLootRegistry, new LegacyChestTypeMapper(warnReporter), warnReporter);
        chestLootHook.install();

        RuntimeIndex runtimeIndex = new RuntimeIndex();
        for (var rule : commandRules) {
            TriggerType trigger = parseTrigger(rule.triggerKey(), warnReporter, rule.sourceLoc());
            if (trigger != null) {
                runtimeIndex.addCommandRule(trigger, rule);
            }
        }
        for (var rule : effectRules) {
            TriggerType trigger = parseTrigger(rule.triggerKey(), warnReporter, rule.sourceLoc());
            if (trigger != null) {
                runtimeIndex.addEffectRule(trigger, rule);
            }
        }

        HookInstaller hooks = new HookInstaller(runtimeIndex, thrownRegistry, blockDropRegistry, entityDropRegistry, warnReporter);
        ServerLifecycleEvents.SERVER_STARTED.register(hooks::install);
        report.setUniqueWarnCount(warnReporter.uniqueWarnCount());
        report.setTotalWarnCount(warnReporter.totalWarnCount());
        DiagnosticExporter.export(config, warnReporter, report, packs);

        Log.LOGGER.info(
            "Re-LootPlusPlus bootstrap complete: packs={}, files={}, lines={}, legacyWarns={}",
            report.packCount(),
            report.fileCount(),
            report.lineCount(),
            report.uniqueWarnCount()
        );
    }

    private TriggerType parseTrigger(String raw, LegacyWarnReporter warnReporter, ie.orangep.reLootplusplus.diagnostic.SourceLoc loc) {
        try {
            return TriggerType.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnReporter.warn("LegacyTrigger", "unknown trigger " + raw, loc);
            return null;
        }
    }
}
