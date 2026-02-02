package ie.orangep.reLootplusplus.runtime;

import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.config.ReLootPlusPlusConfig;
import ie.orangep.reLootplusplus.runtime.RuleEngine;

public final class RuntimeState {
    private static volatile BlockDropRegistry blockDropRegistry;
    private static volatile StackSizeRegistry stackSizeRegistry;
    private static volatile LegacyWarnReporter warnReporter;
    private static volatile ReLootPlusPlusConfig config;
    private static volatile RuleEngine ruleEngine;
    private static volatile ie.orangep.reLootplusplus.runtime.ChestLootRegistry chestLootRegistry;
    private static volatile java.util.List<ie.orangep.reLootplusplus.config.model.general.CreativeMenuEntry> creativeMenuEntries;
    private static volatile java.util.List<ie.orangep.reLootplusplus.config.model.rule.ThrownDef> thrownDefs;
    private static volatile ie.orangep.reLootplusplus.config.model.recipe.RecipeDefinitions recipeDefinitions;

    private RuntimeState() {
    }

    public static void init(ReLootPlusPlusConfig configValue, BlockDropRegistry registry, StackSizeRegistry stackRegistry, LegacyWarnReporter reporter) {
        config = configValue;
        blockDropRegistry = registry;
        stackSizeRegistry = stackRegistry;
        warnReporter = reporter;
    }

    public static BlockDropRegistry blockDropRegistry() {
        return blockDropRegistry;
    }

    public static StackSizeRegistry stackSizeRegistry() {
        return stackSizeRegistry;
    }

    public static LegacyWarnReporter warnReporter() {
        return warnReporter;
    }

    public static ReLootPlusPlusConfig config() {
        return config;
    }

    public static void setRuleEngine(RuleEngine engine) {
        ruleEngine = engine;
    }

    public static RuleEngine ruleEngine() {
        return ruleEngine;
    }

    public static void setChestLootRegistry(ie.orangep.reLootplusplus.runtime.ChestLootRegistry registry) {
        chestLootRegistry = registry;
    }

    public static ie.orangep.reLootplusplus.runtime.ChestLootRegistry chestLootRegistry() {
        return chestLootRegistry;
    }

    public static void setCreativeMenuEntries(java.util.List<ie.orangep.reLootplusplus.config.model.general.CreativeMenuEntry> entries) {
        creativeMenuEntries = entries;
    }

    public static java.util.List<ie.orangep.reLootplusplus.config.model.general.CreativeMenuEntry> creativeMenuEntries() {
        return creativeMenuEntries;
    }

    public static void setThrownDefs(java.util.List<ie.orangep.reLootplusplus.config.model.rule.ThrownDef> defs) {
        thrownDefs = defs;
    }

    public static java.util.List<ie.orangep.reLootplusplus.config.model.rule.ThrownDef> thrownDefs() {
        return thrownDefs;
    }

    public static void setRecipeDefinitions(ie.orangep.reLootplusplus.config.model.recipe.RecipeDefinitions defs) {
        recipeDefinitions = defs;
    }

    public static ie.orangep.reLootplusplus.config.model.recipe.RecipeDefinitions recipeDefinitions() {
        return recipeDefinitions;
    }
}
